package com.hafiyanjye.app.data.realtime.transport

import com.hafiyanjye.app.data.realtime.RealtimeEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URLEncoder
import kotlin.random.Random

/*
 * The wire formats of the two live routes, with no I/O: Server-Sent Events
 * (engine/src/core/realtime/sse.ts) and the Pusher Channels protocol 7 that
 * Hubfly Managed Realtime speaks (web/src/lib/realtime/socket.ts).
 */

/* ---------------------------------- backoff --------------------------------- */

internal const val MAX_BACKOFF_MS = 30_000L

/**
 * Wait before reconnect number [attempt] (0 = the first): 1 s, 2 s, 4 s …
 * capped at 30 s, plus up to 0.5 s of jitter so many phones don't return at once.
 */
internal fun backoffMillis(attempt: Int, jitter: Double = Random.nextDouble()): Long {
    val base = if (attempt >= 15) MAX_BACKOFF_MS else minOf(MAX_BACKOFF_MS, 1_000L shl attempt.coerceAtLeast(0))
    return base + (jitter.coerceIn(0.0, 1.0) * 500).toLong()
}

/* ------------------------------------ SSE ----------------------------------- */

private const val BYTE_ORDER_MARK = '\uFEFF'

/** One dispatched server-sent event. [event] is null when the stream named none. */
internal data class SseMessage(val event: String?, val data: String, val id: String?)

/**
 * Incremental Server-Sent Events parser. Feed it text as it arrives, cut
 * anywhere (in the middle of a line, between CR and LF); it returns the events
 * completed by that piece. Comments (`: keep-alive`) and `retry:` are skipped.
 */
internal class SseParser {
    private val line = StringBuilder()
    private var skipLineFeed = false
    private var started = false

    private var event: String? = null
    private val data = StringBuilder()
    private var hasData = false
    private var lastId: String? = null

    fun feed(chunk: CharSequence): List<SseMessage> {
        val completed = ArrayList<SseMessage>(0)
        for (char in chunk) {
            if (!started) {
                started = true
                if (char == BYTE_ORDER_MARK) continue
            }
            if (skipLineFeed) {
                skipLineFeed = false
                if (char == '\n') continue
            }
            when (char) {
                '\r', '\n' -> {
                    skipLineFeed = char == '\r'
                    endLine()?.let(completed::add)
                }
                else -> line.append(char)
            }
        }
        return completed
    }

    private fun endLine(): SseMessage? {
        val text = line.toString()
        line.setLength(0)
        if (text.isEmpty()) return dispatch()
        if (text[0] == ':') return null
        val colon = text.indexOf(':')
        val field = if (colon < 0) text else text.substring(0, colon)
        var value = if (colon < 0) "" else text.substring(colon + 1)
        if (value.startsWith(' ')) value = value.substring(1)
        when (field) {
            "event" -> event = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
            "id" -> if ('\u0000' !in value) lastId = value
        }
        return null
    }

    private fun dispatch(): SseMessage? {
        val message = if (hasData) SseMessage(event?.takeIf { it.isNotEmpty() }, data.toString(), lastId) else null
        event = null
        data.setLength(0)
        hasData = false
        return message
    }
}

/**
 * A live event from an event name and its JSON payload, as the web does
 * (`{ ...data, type }`). Null when the payload is not a JSON object.
 */
internal fun realtimeEvent(type: String, payload: JsonElement?): RealtimeEvent? {
    val body = payload as? JsonObject ?: return null
    return RealtimeEvent(type, JsonObject(body + ("type" to JsonPrimitive(type))))
}

/** The live event carried by one SSE message; unnamed messages and malformed payloads are ignored. */
internal fun SseMessage.toRealtimeEvent(): RealtimeEvent? {
    val type = event ?: return null
    return realtimeEvent(type, parseJsonOrNull(data))
}

/** Engine payloads and Pusher frames; anything that is not JSON is ignored, never thrown. */
internal fun parseJsonOrNull(text: String): JsonElement? =
    runCatching { Json.parseToJsonElement(text) }.getOrNull()

/* ---------------------------------- Pusher ---------------------------------- */

/** One Pusher frame. [data] is already decoded when the server sent it as a JSON string. */
internal data class PusherFrame(val event: String, val channel: String?, val data: JsonElement?) {
    val isProtocol: Boolean get() = event.startsWith("pusher")
}

/** What `pusher:connection_established` says. */
internal data class PusherConnection(val socketId: String?, val activityTimeoutMs: Long?)

/** The engine's answer to `POST /api/realtime/auth`. */
internal data class ChannelAuth(val auth: String, val channelData: String?)

/** What a closed Pusher connection asks of the client. */
internal enum class Reconnect { Never, Soon, Backoff }

/** Pusher Channels protocol 7: frames, the URL, and the close-code rules, as web/src/lib/realtime/socket.ts. */
internal object Pusher {
    const val CONNECTION_ESTABLISHED = "pusher:connection_established"
    const val PING = "pusher:ping"
    const val PONG = "pusher:pong"
    const val DEFAULT_ACTIVITY_MS = 120_000L
    const val PONG_TIMEOUT_MS = 30_000L
    const val SOON_MS = 500L

    /** wss://host/app/<key>?protocol=7… */
    fun url(wsUrl: String, key: String): String {
        val encodedKey = URLEncoder.encode(key, "UTF-8").replace("+", "%20")
        return "${wsUrl.trimEnd('/')}/app/$encodedKey?protocol=7&client=hafi-android&version=1.0&flash=false"
    }

    /** Null when the text is not a frame. Pusher sends `data` as a JSON string: it is parsed when it is one. */
    fun parse(text: String): PusherFrame? {
        val root = parseJsonOrNull(text) as? JsonObject ?: return null
        val event = (root["event"] as? JsonPrimitive)?.contentOrNull ?: return null
        val channel = (root["channel"] as? JsonPrimitive)?.contentOrNull
        val raw = root["data"]
        val data = if (raw is JsonPrimitive && raw.isString) parseJsonOrNull(raw.content) ?: raw else raw
        return PusherFrame(event, channel, data)
    }

    fun connection(frame: PusherFrame): PusherConnection {
        val info = frame.data as? JsonObject
        val socketId = (info?.get("socket_id") as? JsonPrimitive)?.contentOrNull
        val seconds = (info?.get("activity_timeout") as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
        return PusherConnection(socketId, seconds?.coerceIn(10, 120)?.times(1000))
    }

    fun ping(): String = """{"event":"pusher:ping","data":{}}"""
    fun pong(): String = """{"event":"pusher:pong","data":{}}"""

    fun subscribe(channel: String, auth: ChannelAuth?): String = buildJsonObject {
        put("event", "pusher:subscribe")
        putJsonObject("data") {
            put("channel", channel)
            if (auth != null) {
                put("auth", auth.auth)
                auth.channelData?.let { put("channel_data", it) }
            }
        }
    }.toString()

    fun needsAuth(channel: String): Boolean = channel.startsWith("private-") || channel.startsWith("presence-")

    /** Body of `POST /api/realtime/auth`. */
    fun authBody(socketId: String, channel: String): String = buildJsonObject {
        put("socket_id", socketId)
        put("channel_name", channel)
    }.toString()

    /** The engine answers the bare `{ auth, channel_data? }` (no envelope). */
    fun parseAuth(body: String): ChannelAuth? {
        val root = parseJsonOrNull(body) as? JsonObject ?: return null
        val auth = (root["auth"] as? JsonPrimitive)
            ?.takeIf { it.isString && it.content.isNotBlank() }
            ?.content
            ?: return null
        val channelData = (root["channel_data"] as? JsonPrimitive)?.contentOrNull
        return ChannelAuth(auth, channelData)
    }

    /** 4000–4099: the app is gone, the key revoked or over quota, never retry. 4200–4299: retry at once. */
    fun afterClose(code: Int): Reconnect = when (code) {
        in 4000..4099 -> Reconnect.Never
        in 4200..4299 -> Reconnect.Soon
        else -> Reconnect.Backoff
    }

    /** The app event in a frame, or null for protocol frames and payloads that are not objects. */
    fun appEvent(frame: PusherFrame): RealtimeEvent? =
        if (frame.isProtocol) null else realtimeEvent(frame.event, frame.data)
}
