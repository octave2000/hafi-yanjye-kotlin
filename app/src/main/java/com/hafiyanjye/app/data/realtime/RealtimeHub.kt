package com.hafiyanjye.app.data.realtime

import com.hafiyanjye.app.data.api.HafiJson
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

/**
 * One live event from the engine (engine/src/shared/contracts.ts, RealtimeEvent).
 * [type] is e.g. "ping.new", "session.updated", "chat.message"; [data] is the
 * whole payload, including `type` and the delivery id `eid`.
 */
data class RealtimeEvent(val type: String, val data: JsonObject) {
    fun int(name: String): Int? = runCatching { data[name]?.jsonPrimitive?.intOrNull }.getOrNull()
    fun string(name: String): String? = runCatching { data[name]?.jsonPrimitive?.contentOrNull }.getOrNull()

    /** A nested object decoded as a DTO, e.g. `event.decode<Message>("message")`. Null when absent or malformed. */
    inline fun <reified T> decode(name: String): T? =
        runCatching { data[name]?.let { HafiJson.decodeFromJsonElement(serializer<T>(), it) } }.getOrNull()

    companion object {
        const val PingNew = "ping.new"
        const val PingUpdated = "ping.updated"
        const val SessionUpdated = "session.updated"
        const val MatchFound = "match.found"
        const val MatchHired = "match.hired"
        const val TripLocation = "trip.location"
        const val TripUpdated = "trip.updated"
        const val Notification = "notification"
        const val ChatMessage = "chat.message"
        const val ChatRead = "chat.read"
        const val ChatTyping = "chat.typing"
    }
}

/**
 * Where live events arrive and screens listen. The transport (SSE stream and
 * the Hubfly WebSocket, data/realtime/transport) publishes into it; the same
 * event comes over both routes, so it is delivered once, by its `eid`.
 *
 * Screens: collect [events] (filter by type and id) and reload on [resyncs],
 * which fires whenever events may have been missed (reconnect, back from the
 * background).
 */
class RealtimeHub {
    private val _events = MutableSharedFlow<RealtimeEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private val _resyncs = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val resyncs: SharedFlow<Unit> = _resyncs.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    /** True while at least one live route is open. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val seen = LinkedHashSet<String>()

    /** Transport only. Returns false when this delivery was already published. */
    fun publish(event: RealtimeEvent): Boolean {
        val eid = event.string("eid")
        if (eid != null) {
            synchronized(seen) {
                if (!seen.add(eid)) return false
                if (seen.size > MAX_SEEN) seen.iterator().apply { next(); remove() }
            }
        }
        _events.tryEmit(event)
        return true
    }

    /** Transport and app lifecycle: something may have been missed, reload. */
    fun requestResync() {
        _resyncs.tryEmit(Unit)
    }

    /** Transport only. */
    fun setConnected(value: Boolean) {
        _connected.value = value
    }

    private companion object {
        const val MAX_SEEN = 300
    }
}
