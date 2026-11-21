package com.hafiyanjye.app.data.realtime.transport

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.realtime.RealtimeHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import java.nio.CharBuffer
import java.util.concurrent.TimeUnit

/**
 * The engine's Server-Sent Events stream (`GET /api/v1/events/stream`), the
 * route that works wherever HTTP does. [run] keeps one stream open until its
 * coroutine is cancelled, publishing every event to [hub] and reconnecting
 * with capped exponential backoff; it returns only when the engine refuses the
 * session (401: the session signs out on its own).
 *
 * [http] must tolerate a quiet stream: the engine writes a heartbeat every
 * 25 s, so its read timeout has to be longer than that (see [streamingClient]).
 */
internal class SseStream(
    private val client: ApiClient,
    private val http: OkHttpClient,
    private val hub: RealtimeHub,
    private val onOpenChange: (open: Boolean) -> Unit = {},
    /** The stream opened again after a drop: events in the gap were missed. */
    private val onReconnect: () -> Unit = {},
    private val backoff: (attempt: Int) -> Long = { backoffMillis(it) },
    private val onUnauthorized: () -> Unit = {},
) {
    private enum class End { Dropped, Unauthorized }

    suspend fun run() {
        var attempt = 0
        var openedBefore = false
        while (true) {
            val end = try {
                streamOnce {
                    if (openedBefore) onReconnect()
                    openedBefore = true
                    attempt = 0
                    onOpenChange(true)
                }
            } finally {
                onOpenChange(false)
            }
            if (end == End.Unauthorized) return
            delay(backoff(attempt))
            attempt += 1
        }
    }

    /** One connection, from request to the moment it drops. */
    private suspend fun streamOnce(onOpen: () -> Unit): End = coroutineScope {
        val request = try {
            client.newRequest(PATH)
                .header("Accept", EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .build()
        } catch (_: ApiException) {
            return@coroutineScope End.Dropped
        }
        val call = http.newCall(request)
        // A blocked read only ends when the call is cancelled.
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            withContext(Dispatchers.IO) {
                try {
                    call.execute().use { response ->
                        if (response.code == 401) {
                            onUnauthorized()
                            return@withContext End.Unauthorized
                        }
                        if (!response.isSuccessful) return@withContext End.Dropped
                        val mediaType = response.body.contentType()?.let { "${it.type}/${it.subtype}" }
                        // A captive portal or proxy page is not the stream: retry later.
                        if (mediaType != EVENT_STREAM) return@withContext End.Dropped
                        onOpen()
                        val reader = response.body.byteStream().reader(Charsets.UTF_8)
                        val parser = SseParser()
                        val buffer = CharArray(4096)
                        while (true) {
                            val read = reader.read(buffer)
                            if (read < 0) break
                            for (message in parser.feed(CharBuffer.wrap(buffer, 0, read))) {
                                message.toRealtimeEvent()?.let(hub::publish)
                            }
                        }
                        End.Dropped
                    }
                } catch (_: IOException) {
                    End.Dropped
                }
            }
        } finally {
            watcher.cancel()
        }
    }

    companion object {
        const val PATH = "/api/v1/events/stream"
        private const val EVENT_STREAM = "text/event-stream"
    }
}

/**
 * The app's HTTP client (same connection pool) for the two live routes: no
 * call timeout, and a read timeout of three missed heartbeats instead of the
 * API's 20 s, so a quiet stream stays open but a dead one is noticed.
 */
internal fun OkHttpClient.streamingClient(): OkHttpClient = newBuilder()
    .readTimeout(STREAM_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .callTimeout(0, TimeUnit.MILLISECONDS)
    .build()

/** The engine's keep-alive is every 25 s (engine/src/core/realtime/sse.ts). */
internal const val STREAM_READ_TIMEOUT_MS = 80_000L
