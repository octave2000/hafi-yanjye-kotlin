package com.hafiyanjye.app.realtime

import com.hafiyanjye.app.data.realtime.RealtimeEvent
import com.hafiyanjye.app.data.realtime.RealtimeHub
import com.hafiyanjye.app.data.realtime.transport.Pusher
import com.hafiyanjye.app.data.realtime.transport.PusherConnection
import com.hafiyanjye.app.data.realtime.transport.SseParser
import com.hafiyanjye.app.data.realtime.transport.toRealtimeEvent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * Bytes captured from the local engine and Hubfly realtime (2026-11-18): one
 * `chat.typing` delivery that arrived on both routes with the same `eid`.
 */
class EngineCaptureTest {
    private val sseWire = "retry: 3000\n\n" +
        "event: chat.typing\n" +
        "data: {\"type\":\"chat.typing\",\"conversationId\":10,\"userId\":1," +
        "\"eid\":\"6e2d8b49-7175-45ac-8e16-a59f973f26a0\"}\n\n" +
        ": keep-alive\n\n"

    private val socketFrames = listOf(
        """{"event":"pusher:connection_established",""" +
            """"data":"{\"socket_id\":\"5869209131.9967995683\",\"activity_timeout\":120}"}""",
        """{"event":"pusher_internal:subscription_succeeded","channel":"private-devuser-19","data":"{}"}""",
        """{"event":"pusher_internal:subscription_succeeded","channel":"presence-devuser-19",""" +
            """"data":"{\"presence\":{\"ids\":[\"19\"],\"hash\":{\"19\":null},\"count\":1}}"}""",
        """{"event":"pusher:pong","data":{}}""",
        """{"event":"chat.typing","channel":"private-devuser-19",""" +
            """"data":"{\"type\":\"chat.typing\",\"conversationId\":10,\"userId\":1,""" +
            """\"eid\":\"6e2d8b49-7175-45ac-8e16-a59f973f26a0\"}"}""",
    )

    @Test
    fun `the engine's stream gives one chat typing event`() {
        val events = SseParser().feed(sseWire).mapNotNull { it.toRealtimeEvent() }
        assertEquals(1, events.size)
        val typing = events.single()
        assertEquals(RealtimeEvent.ChatTyping, typing.type)
        assertEquals(10, typing.int("conversationId"))
        assertEquals(1, typing.int("userId"))
    }

    @Test
    fun `hubfly frames give the socket id, the timeout and the same event`() {
        val frames = socketFrames.mapNotNull(Pusher::parse)
        assertEquals(socketFrames.size, frames.size)
        assertEquals(
            PusherConnection("5869209131.9967995683", 120_000),
            Pusher.connection(frames.first()),
        )
        val appEvents = frames.mapNotNull(Pusher::appEvent)
        assertEquals(listOf(RealtimeEvent.ChatTyping), appEvents.map { it.type })
        assertEquals("private-devuser-19", frames.last().channel)
        assertNull(Pusher.appEvent(frames[2]))
    }

    @Test
    fun `the copy from the second route is published once`() = runBlocking {
        val hub = RealtimeHub()
        val published = Collections.synchronizedList(mutableListOf<RealtimeEvent>())
        val collector = launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
            hub.events.collect { published += it }
        }
        val fromStream = SseParser().feed(sseWire).mapNotNull { it.toRealtimeEvent() }.single()
        val fromSocket = socketFrames.mapNotNull(Pusher::parse).mapNotNull(Pusher::appEvent).single()

        assertTrue(hub.publish(fromStream))
        assertFalse(hub.publish(fromSocket))
        collector.cancelAndJoin()

        assertEquals(1, published.size)
        assertEquals(fromStream.data, fromSocket.data)
    }
}
