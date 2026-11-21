package com.hafiyanjye.app.data.realtime.transport

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.realtime.RealtimeHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Hubfly Managed Realtime, spoken directly (Pusher Channels protocol 7 over
 * OkHttp's WebSocket, no SDK), the same way as web/src/lib/realtime/socket.ts:
 * connect to `<wsUrl>/app/<key>`, take the socket id from
 * `pusher:connection_established`, join [channels] (each private or presence
 * channel signed by the engine, `POST /api/realtime/auth`), answer pings, ping
 * after `activity_timeout` of silence and treat 30 s without an answer as a
 * dead link, reconnect with backoff and join again.
 *
 * [run] holds the connection until its coroutine is cancelled; it returns
 * only when the server says never to come back (close codes 4000–4099).
 */
internal class PusherSocket(
    private val client: ApiClient,
    private val http: OkHttpClient,
    private val hub: RealtimeHub,
    private val wsUrl: String,
    private val key: String,
    private val channels: List<String>,
    private val onOpenChange: (open: Boolean) -> Unit = {},
    /** Connected again after a drop: nothing sent in the gap is replayed. */
    private val onReconnect: () -> Unit = {},
    private val backoff: (attempt: Int) -> Long = { backoffMillis(it) },
    private val onUnauthorized: () -> Unit = {},
) {
    private sealed interface Incoming {
        data class Text(val text: String) : Incoming
        data class Closed(val code: Int) : Incoming
    }

    suspend fun run() {
        var attempt = 0
        var openedBefore = false
        while (true) {
            val code = try {
                connectOnce {
                    if (openedBefore) onReconnect()
                    openedBefore = true
                    attempt = 0
                    onOpenChange(true)
                }
            } finally {
                onOpenChange(false)
            }
            when (Pusher.afterClose(code)) {
                Reconnect.Never -> return
                Reconnect.Soon -> delay(Pusher.SOON_MS)
                Reconnect.Backoff -> delay(backoff(attempt))
            }
            attempt += 1
        }
    }

    /** One connection; returns the code it closed with (1006 when it just broke). */
    private suspend fun connectOnce(onOpen: () -> Unit): Int = coroutineScope {
        val inbox = Channel<Incoming>(Channel.UNLIMITED)
        val request = try {
            Request.Builder().url(Pusher.url(wsUrl, key)).build()
        } catch (_: IllegalArgumentException) {
            return@coroutineScope ABNORMAL
        }
        val socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                inbox.trySend(Incoming.Text(text))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // Echoing the server's code fails for 1005/1006, which OkHttp refuses to send.
                webSocket.close(NORMAL, null)
                inbox.trySend(Incoming.Closed(code))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                inbox.trySend(Incoming.Closed(code))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                inbox.trySend(Incoming.Closed(ABNORMAL))
            }
        })
        var closeCode = NORMAL
        try {
            val code = receive(socket, inbox, onOpen)
            if (code == NO_PONG) closeCode = NO_PONG
            code
        } finally {
            coroutineContext.cancelChildren()
            socket.close(closeCode, if (closeCode == NO_PONG) "no pong" else "bye")
            // A close handshake can hang on a dead network; do not keep the account online.
            socket.cancel()
            inbox.close()
        }
    }

    /** Handles frames until the connection ends; returns its close code. */
    private suspend fun CoroutineScope.receive(
        socket: WebSocket,
        inbox: Channel<Incoming>,
        onOpen: () -> Unit,
    ): Int {
        var activityMs = Pusher.DEFAULT_ACTIVITY_MS
        var awaitingPong = false
        var established = false
        while (true) {
            // Any frame counts as activity; silence first earns a ping, then a verdict.
            val wait = if (!established || awaitingPong) Pusher.PONG_TIMEOUT_MS else activityMs
            val incoming = withTimeoutOrNull(wait) { inbox.receive() }
            if (incoming == null) {
                if (!established) return ABNORMAL
                if (awaitingPong) return NO_PONG
                if (!socket.send(Pusher.ping())) return ABNORMAL
                awaitingPong = true
                continue
            }
            val text = when (incoming) {
                is Incoming.Closed -> return incoming.code
                is Incoming.Text -> incoming.text
            }
            awaitingPong = false
            val frame = Pusher.parse(text) ?: continue
            when (frame.event) {
                Pusher.CONNECTION_ESTABLISHED -> {
                    if (established) continue
                    val info = Pusher.connection(frame)
                    val socketId = info.socketId?.takeIf { it.isNotBlank() } ?: return ABNORMAL
                    established = true
                    info.activityTimeoutMs?.let { activityMs = it }
                    onOpen()
                    // Children of this connection: a signature that arrives after it dropped is never sent.
                    for (channel in channels.distinct()) launch { subscribe(socket, socketId, channel, inbox) }
                }
                Pusher.PING -> socket.send(Pusher.pong())
                else -> if (established && frame.channel in channels) Pusher.appEvent(frame)?.let(hub::publish)
            }
        }
    }

    private suspend fun subscribe(socket: WebSocket, socketId: String, channel: String, inbox: Channel<Incoming>) {
        var attempt = 0
        while (true) {
            val answer = if (Pusher.needsAuth(channel)) authorize(socketId, channel) else null
            if (answer?.status == 401) {
                onUnauthorized()
                inbox.trySend(Incoming.Closed(4001))
                return
            }
            val auth = answer?.body?.let(Pusher::parseAuth)
            if (!Pusher.needsAuth(channel) || auth != null) {
                if (!socket.send(Pusher.subscribe(channel, auth))) {
                    inbox.trySend(Incoming.Closed(ABNORMAL))
                } else if (attempt > 0) {
                    onReconnect()
                }
                return
            }
            if (answer != null && answer.status in 400..499 && answer.status != 408 && answer.status != 429) return
            // A transient auth failure must not leave a healthy socket permanently unsubscribed.
            delay(backoff(attempt))
            attempt += 1
        }
    }

    /** Each signature belongs to this socket; cancellation also cancels its HTTP call. */
    private suspend fun authorize(socketId: String, channel: String): AuthResponse? {
        val request = try {
            client.newRequest(AUTH_PATH).post(Pusher.authBody(socketId, channel).toRequestBody(JSON)).build()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        }
        return withTimeoutOrNull(AUTH_TIMEOUT_MS) { http.newCall(request).awaitAuth() }
    }

    companion object {
        const val AUTH_PATH = "/api/realtime/auth"
        private const val AUTH_TIMEOUT_MS = 20_000L
        private const val NORMAL = 1000
        private const val ABNORMAL = 1006
        private const val NO_PONG = 4201
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** The engine's answer to one signature request; [body] only when it succeeded. */
private data class AuthResponse(val status: Int, val body: String?)

/** Keep the status for session rejection and retry decisions; cancellation cancels the call. */
private suspend fun Call.awaitAuth(): AuthResponse? = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resume(null)
        }

        override fun onResponse(call: Call, response: Response) {
            val answer = response.use {
                val body = if (it.isSuccessful) runCatching { it.body.string() }.getOrNull() else null
                AuthResponse(it.code, body)
            }
            continuation.resume(answer)
        }
    })
}
