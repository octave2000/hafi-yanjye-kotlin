package com.hafiyanjye.app.realtime

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.realtime.RealtimeEvent
import com.hafiyanjye.app.data.realtime.RealtimeHub
import com.hafiyanjye.app.data.realtime.transport.Pusher
import com.hafiyanjye.app.data.realtime.transport.PusherSocket
import com.hafiyanjye.app.data.realtime.transport.SseStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.TimeUnit

class TransportRoundTripTest {
    private val server = MockWebServer()
    private val hub = RealtimeHub()
    private val http = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
    private lateinit var client: ApiClient

    @Before
    fun setUp() {
        server.start()
        client = ApiClient(server.url("/").toString(), http, { "session_abc" }, { })
    }

    @After
    fun tearDown() {
        server.close()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    private fun stream(body: String) =
        MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "text/event-stream; charset=utf-8")
            .chunkedBody(body, 7)
            .build()

    private fun json(code: Int, body: String) =
        MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    private fun webSocket(listener: WebSocketListener) = MockResponse.Builder().webSocketUpgrade(listener).build()

    @Test
    fun `the stream publishes events, reconnects, resyncs and stops on 401`() = runBlocking {
        val pingNew = "event: ping.new\ndata: {\"type\":\"ping.new\",\"pingId\":7,\"jobId\":3,\"eid\":\"e-1\"}\n\n"
        server.enqueue(stream("retry: 3000\n\n: keep-alive\n\n$pingNew"))
        server.enqueue(MockResponse.Builder().code(503).body("busy").build())
        server.enqueue(
            stream(
                // The same delivery again: the hub drops it by its eid.
                pingNew +
                    "event: chat.message\r\n" +
                    "data: {\"type\":\"chat.message\",\"conversationId\":4,\"eid\":\"e-2\"}\r\n\r\n",
            ),
        )
        server.enqueue(
            json(401, """{"ok":false,"error":{"code":"UNAUTHORIZED","message":"Authentication required"}}"""),
        )

        val events = Collections.synchronizedList(mutableListOf<RealtimeEvent>())
        val collector = launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
            hub.events.collect { events += it }
        }
        val open = Collections.synchronizedList(mutableListOf<Boolean>())
        val attempts = Collections.synchronizedList(mutableListOf<Int>())
        var reconnects = 0
        var rejected = 0

        withTimeout(10_000) {
            SseStream(
                client = client,
                http = http,
                hub = hub,
                onOpenChange = { open += it },
                onReconnect = { reconnects++ },
                backoff = {
                    attempts += it
                    0
                },
                onUnauthorized = { rejected++ },
            ).run()
        }
        collector.cancelAndJoin()

        assertEquals(listOf("ping.new", "chat.message"), events.map { it.type })
        assertEquals(7, events[0].int("pingId"))
        assertEquals(4, events[1].int("conversationId"))
        assertEquals(1, reconnects)
        assertEquals(1, rejected)
        // Opened, dropped, refused (503), opened again, dropped, refused for good.
        assertEquals(listOf(true, false, false, true, false, false), open.toList())
        // The attempt count restarts after every successful open.
        assertEquals(listOf(0, 1, 0), attempts.toList())

        assertEquals(4, server.requestCount)
        val sent = server.takeRequest()
        assertEquals("GET", sent.method)
        assertEquals("/api/v1/events/stream", sent.url.encodedPath)
        assertEquals("Bearer session_abc", sent.headers["Authorization"])
        assertEquals("text/event-stream", sent.headers["Accept"])
    }

    @Test
    fun `cancelling ends an open stream at once`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/event-stream")
                .body(": " + "a".repeat(200) + "\n\n")
                .throttleBody(1, 1, TimeUnit.SECONDS)
                .build(),
        )
        val opened = CompletableDeferred<Unit>()
        val open = Collections.synchronizedList(mutableListOf<Boolean>())
        withTimeout(10_000) {
            val job = launch {
                SseStream(
                    client = client,
                    http = http,
                    hub = hub,
                    onOpenChange = {
                        open += it
                        if (it) opened.complete(Unit)
                    },
                ).run()
            }
            opened.await()
            job.cancelAndJoin()
        }
        assertEquals(listOf(true, false), open.toList())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `the socket retries transient auth and stops when the session is rejected`() = runBlocking {
        var authorizations = 0
        var rejected = 0
        val pusher = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"event":"pusher:connection_established","data":{"socket_id":"11.22"}}""")
            }
        }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/app/key123" -> webSocket(pusher)
                "/api/realtime/auth" -> {
                    authorizations++
                    MockResponse.Builder().code(if (authorizations == 1) 503 else 401).body("{}").build()
                }
                else -> MockResponse.Builder().code(404).build()
            }
        }
        withTimeout(10_000) {
            PusherSocket(
                client = client,
                http = http,
                hub = hub,
                wsUrl = server.url("/").toString(),
                key = "key123",
                channels = listOf("private-devuser-1"),
                backoff = { 0 },
                onUnauthorized = { rejected++ },
            ).run()
        }
        assertEquals(2, authorizations)
        assertEquals(1, rejected)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `the socket signs both channels, answers pings, publishes events and obeys a final close`() = runBlocking {
        val received = Collections.synchronizedList(mutableListOf<String>())
        val authBodies = Collections.synchronizedList(mutableListOf<String>())
        val pusher = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(
                    """{"event":"pusher:connection_established",""" +
                        """"data":"{\"socket_id\":\"11.22\",\"activity_timeout\":30}"}""",
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                received += text
                val subscribed = received.count { "pusher:subscribe" in it }
                if (subscribed == 2 && "pusher:subscribe" in text) {
                    webSocket.send(
                        """{"event":"pusher_internal:subscription_succeeded","channel":"private-devuser-1",""" +
                            """"data":"{}"}""",
                    )
                    webSocket.send(
                        """{"event":"match.found","channel":"private-devuser-1",""" +
                            """"data":"{\"type\":\"match.found\",\"sessionId\":5,\"jobId\":2,\"eid\":\"e-7\"}"}""",
                    )
                    webSocket.send("""{"event":"pusher:ping","data":{}}""")
                }
                if ("pusher:pong" in text) webSocket.close(4001, "app gone")
            }
        }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/app/key123" -> webSocket(pusher)
                "/api/realtime/auth" -> {
                    val body = request.body?.utf8().orEmpty()
                    authBodies += "${request.headers["Authorization"]} $body"
                    val answer = if ("presence-" in body) {
                        """{"auth":"key123:p","channel_data":"{\"user_id\":\"1\"}"}"""
                    } else {
                        """{"auth":"key123:s"}"""
                    }
                    json(200, answer)
                }
                else -> MockResponse.Builder().code(404).build()
            }
        }

        val events = Collections.synchronizedList(mutableListOf<RealtimeEvent>())
        val collector = launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
            hub.events.collect { events += it }
        }
        val open = Collections.synchronizedList(mutableListOf<Boolean>())
        withTimeout(10_000) {
            PusherSocket(
                client = client,
                http = http,
                hub = hub,
                wsUrl = server.url("/").toString().trimEnd('/'),
                key = "key123",
                channels = listOf("private-devuser-1", "presence-devuser-1"),
                onOpenChange = { open += it },
                backoff = { 0 },
            ).run()
        }
        collector.cancelAndJoin()

        assertEquals(listOf(true, false), open.toList())
        assertEquals(listOf("match.found"), events.map { it.type })
        assertEquals(5, events.single().int("sessionId"))
        assertEquals(
            setOf(
                """Bearer session_abc {"socket_id":"11.22","channel_name":"private-devuser-1"}""",
                """Bearer session_abc {"socket_id":"11.22","channel_name":"presence-devuser-1"}""",
            ),
            authBodies.toSet(),
        )
        assertTrue(
            received.contains(
                """{"event":"pusher:subscribe","data":{"channel":"private-devuser-1","auth":"key123:s"}}""",
            ),
        )
        assertTrue(
            received.contains(
                """{"event":"pusher:subscribe","data":{"channel":"presence-devuser-1","auth":"key123:p",""" +
                    """"channel_data":"{\"user_id\":\"1\"}"}}""",
            ),
        )
        assertTrue(received.contains(Pusher.pong()))
    }
}
