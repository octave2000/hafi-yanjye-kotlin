package com.hafiyanjye.app.realtime

import com.hafiyanjye.app.data.realtime.transport.ChannelAuth
import com.hafiyanjye.app.data.realtime.transport.Pusher
import com.hafiyanjye.app.data.realtime.transport.PusherConnection
import com.hafiyanjye.app.data.realtime.transport.PusherFrame
import com.hafiyanjye.app.data.realtime.transport.Reconnect
import com.hafiyanjye.app.data.realtime.transport.backoffMillis
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PusherProtocolTest {
    private fun frame(text: String): PusherFrame = requireNotNull(Pusher.parse(text)) { text }

    private fun connectionEstablished(data: String) =
        Pusher.connection(frame("""{"event":"pusher:connection_established","data":$data}"""))

    @Test
    fun `the url carries the key and protocol 7`() {
        assertEquals(
            "wss://x.rt.hubfly.app/app/abc123?protocol=7&client=hafi-android&version=1.0&flash=false",
            Pusher.url("wss://x.rt.hubfly.app//", "abc123"),
        )
        assertTrue(Pusher.url("wss://x", "a b/c").startsWith("wss://x/app/a%20b%2Fc?"))
    }

    @Test
    fun `connection_established has its data as a json string`() {
        val established = frame(
            """{"event":"pusher:connection_established",""" +
                """"data":"{\"socket_id\":\"8123.4567\",\"activity_timeout\":30}"}""",
        )
        assertEquals(Pusher.CONNECTION_ESTABLISHED, established.event)
        assertTrue(established.isProtocol)
        assertEquals(PusherConnection("8123.4567", 30_000), Pusher.connection(established))
    }

    @Test
    fun `the activity timeout is kept between 10 and 120 seconds`() {
        fun timeout(seconds: String) =
            connectionEstablished("""{"socket_id":"1.2","activity_timeout":$seconds}""").activityTimeoutMs
        assertEquals(10_000L, timeout("1"))
        assertEquals(120_000L, timeout("900"))
        assertNull(timeout("0"))
        assertNull(connectionEstablished("\"{}\"").socketId)
    }

    @Test
    fun `an app event's double-encoded data becomes the live event`() {
        val message = frame(
            """{"event":"chat.message","channel":"private-devuser-1","data":""" +
                """"{\"type\":\"chat.message\",\"conversationId\":4,""" +
                """\"message\":{\"id\":9,\"body\":\"Muraho \\\"neza\\\"\"},\"eid\":\"e-9\"}"}""",
        )
        assertEquals("private-devuser-1", message.channel)
        val event = requireNotNull(Pusher.appEvent(message))
        assertEquals("chat.message", event.type)
        assertEquals(4, event.int("conversationId"))
        assertEquals("e-9", event.string("eid"))
        val body = event.data["message"]?.jsonObject?.get("body")?.jsonPrimitive?.content
        assertEquals("Muraho \"neza\"", body)
    }

    @Test
    fun `data that is already an object, plain text or missing`() {
        val ping = Pusher.appEvent(frame("""{"event":"ping.new","data":{"pingId":7}}"""))
        assertEquals(7, ping?.int("pingId"))
        val text = frame("""{"event":"note","data":"not json"}""")
        assertEquals(JsonPrimitive("not json"), text.data)
        assertNull(Pusher.appEvent(text))
        assertNull(Pusher.appEvent(frame("""{"event":"ping.new"}""")))
    }

    @Test
    fun `protocol frames are never app events`() {
        val names = listOf(
            "pusher:pong",
            "pusher:error",
            "pusher_internal:subscription_succeeded",
            "pusher_internal:member_added",
        )
        for (name in names) {
            assertNull(name, Pusher.appEvent(frame("""{"event":"$name","data":"{}"}""")))
        }
    }

    @Test
    fun `what is not a frame is null`() {
        assertNull(Pusher.parse("nope"))
        assertNull(Pusher.parse("[1]"))
        assertNull(Pusher.parse("""{"data":"{}"}"""))
    }

    @Test
    fun `ping and pong frames`() {
        assertEquals("pusher:ping", frame(Pusher.ping()).event)
        assertEquals("pusher:pong", frame(Pusher.pong()).event)
        assertEquals(JsonObject(emptyMap()), frame(Pusher.pong()).data)
    }

    @Test
    fun `subscribe frames carry the signature, and channel_data only for presence`() {
        assertEquals(
            """{"event":"pusher:subscribe","data":{"channel":"private-user-1","auth":"key:sig"}}""",
            Pusher.subscribe("private-user-1", ChannelAuth("key:sig", null)),
        )
        assertEquals(
            """{"event":"pusher:subscribe","data":{"channel":"presence-user-1","auth":"key:sig",""" +
                """"channel_data":"{\"user_id\":\"1\"}"}}""",
            Pusher.subscribe("presence-user-1", ChannelAuth("key:sig", """{"user_id":"1"}""")),
        )
        assertEquals(
            """{"event":"pusher:subscribe","data":{"channel":"public"}}""",
            Pusher.subscribe("public", null),
        )
    }

    @Test
    fun `which channels need a signature and what is asked of the engine`() {
        assertTrue(Pusher.needsAuth("private-devuser-3"))
        assertTrue(Pusher.needsAuth("presence-user-3"))
        assertFalse(Pusher.needsAuth("news"))
        assertEquals(
            """{"socket_id":"1.2","channel_name":"private-user-3"}""",
            Pusher.authBody("1.2", "private-user-3"),
        )
    }

    @Test
    fun `the engine's auth answer`() {
        assertEquals(ChannelAuth("k:s", null), Pusher.parseAuth("""{"auth":"k:s"}"""))
        assertEquals(
            ChannelAuth("k:s", """{"user_id":"1"}"""),
            Pusher.parseAuth("""{"auth":"k:s","channel_data":"{\"user_id\":\"1\"}"}"""),
        )
        assertNull(Pusher.parseAuth("""{"ok":false,"error":{"code":"FORBIDDEN"}}"""))
        assertNull(Pusher.parseAuth("<html>"))
        assertNull(Pusher.parseAuth("""{"auth":""}"""))
        assertNull(Pusher.parseAuth("""{"auth":"  "}"""))
    }

    @Test
    fun `close codes decide the reconnect`() {
        assertEquals(Reconnect.Never, Pusher.afterClose(4000))
        assertEquals(Reconnect.Never, Pusher.afterClose(4099))
        assertEquals(Reconnect.Backoff, Pusher.afterClose(4100))
        assertEquals(Reconnect.Soon, Pusher.afterClose(4200))
        assertEquals(Reconnect.Soon, Pusher.afterClose(4201))
        assertEquals(Reconnect.Backoff, Pusher.afterClose(1000))
        assertEquals(Reconnect.Backoff, Pusher.afterClose(1006))
    }

    @Test
    fun `backoff doubles from one second, is capped at thirty and adds up to half a second`() {
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L),
            (0..6).map { backoffMillis(it, 0.0) },
        )
        assertEquals(30_000L, backoffMillis(40, 0.0))
        assertEquals(30_000L, backoffMillis(Int.MAX_VALUE, 0.0))
        assertEquals(1_250L, backoffMillis(0, 0.5))
        assertEquals(30_500L, backoffMillis(9, 1.0))
        repeat(50) { assertTrue(backoffMillis(2) in 4_000L..4_500L) }
    }
}
