package com.hafiyanjye.app.realtime

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.realtime.RealtimeHub
import com.hafiyanjye.app.data.realtime.transport.Presence
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PresenceTest {
    private val server = MockWebServer()
    private val hub = RealtimeHub()
    private val http = OkHttpClient()
    private lateinit var presence: Presence

    @Before
    fun setUp() {
        server.start()
        val client = ApiClient(server.url("/").toString(), http, { "session_abc" }, { })
        presence = Presence(client, hub)
    }

    @After
    fun tearDown() {
        server.close()
        http.dispatcher.executorService.shutdown()
    }

    private fun answer(online: String) =
        MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body("""{"ok":true,"data":{"online":$online},"meta":{"requestId":"r"}}""")
            .build()

    @Test
    fun `asks the engine for the people given and asks again on resync`() = runBlocking {
        server.enqueue(answer("[19]"))
        server.enqueue(answer("[]"))
        val seen = withTimeout(10_000) {
            val updates = launch {
                // Wait for the first answer before asking for a resync, so the second ask is the resync's.
                while (server.requestCount < 1) delay(10)
                delay(50)
                hub.requestResync()
            }
            val values = presence.online(listOf(19, 4, 19, -1)).take(2).toList()
            updates.join()
            values
        }
        assertEquals(listOf(setOf(19), emptySet<Int>()), seen)
        val first = server.takeRequest()
        assertEquals("/api/v1/chat/presence", first.url.encodedPath)
        assertEquals("4,19", first.url.queryParameter("ids"))
        assertEquals("Bearer session_abc", first.headers["Authorization"])
    }

    @Test
    fun `no one to ask about is no one online, without a request`() = runBlocking {
        assertEquals(emptySet<Int>(), presence.online(emptyList()).first())
        assertEquals(0, server.requestCount)
    }
}
