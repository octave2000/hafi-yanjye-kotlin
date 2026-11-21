package com.hafiyanjye.app.data.realtime.transport

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.realtime.RealtimeHub
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/**
 * Who among the people I chat with has the app open, for chat's online dots.
 *
 * The phone only ever joins its own presence channel (the engine signs no
 * one else's), so it cannot watch other people's channels. The engine knows:
 * someone counts as online while they hold an SSE stream or their presence
 * channel (RealtimeConnection keeps both open while the app is on screen).
 * This asks `GET /api/v1/chat/presence` the way the web's usePresence does:
 * again every [REFRESH_MS], and at once on every resync.
 *
 * Use: `Presence(container.client, container.hub).online(listOf(otherUserId))`.
 */
class Presence(private val client: ApiClient, private val hub: RealtimeHub) {
    /**
     * The ids among [userIds] who are online, while collected. Only people I
     * share a conversation with can be online (the engine drops others).
     * Emits nothing until the first answer; a failed check keeps the last answer.
     */
    fun online(userIds: Collection<Int>): Flow<Set<Int>> {
        val ids = userIds.filter { it > 0 }.distinct().sorted()
        if (ids.isEmpty()) return flowOf(emptySet())
        return channelFlow {
            val wakeUps = Channel<Unit>(Channel.CONFLATED)
            launch { hub.resyncs.collect { wakeUps.trySend(Unit) } }
            while (true) {
                ask(ids)?.let { send(it) }
                withTimeoutOrNull(REFRESH_MS) { wakeUps.receive() }
            }
        }.distinctUntilChanged()
    }

    /** True while [userId] is online; false until known. */
    fun isOnline(userId: Int): Flow<Boolean> = channelFlow {
        send(false)
        online(listOf(userId)).collect { send(userId in it) }
    }.distinctUntilChanged()

    private suspend fun ask(ids: List<Int>): Set<Int>? =
        try {
            client.get<PresenceAnswer>(PATH, mapOf("ids" to ids.joinToString(","))).online.toSet()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    /** Engine PresenceDto. */
    @Serializable
    private data class PresenceAnswer(val online: List<Int> = emptyList())

    private companion object {
        const val PATH = "/api/v1/chat/presence"
        const val REFRESH_MS = 20_000L
    }
}
