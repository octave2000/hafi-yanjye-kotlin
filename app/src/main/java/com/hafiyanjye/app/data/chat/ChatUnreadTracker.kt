package com.hafiyanjye.app.data.chat

import com.hafiyanjye.app.AppContainer
import com.hafiyanjye.app.data.realtime.RealtimeEvent
import com.hafiyanjye.app.data.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Keeps `badges.chatUnread` right everywhere in the app (the Chat tab count),
 * leaving out the thread on screen, whose messages are being read as they
 * arrive. Started once from HafiApp.
 */
class ChatUnreadTracker(private val container: AppContainer) {

    private val api = ChatApi(container.client)

    /** The newest list from the engine, so a thread opening or closing recounts without a request. */
    private var lastList: ConversationList? = null

    @OptIn(FlowPreview::class)
    fun start() {
        container.appScope.launch {
            val signedInId = container.session.state
                .map { (it as? SessionState.SignedIn)?.me?.id }
                .distinctUntilChanged()
            signedInId.collectLatest { meId ->
                lastList = null
                container.badges.chatUnread.value = 0
                if (meId == null) return@collectLatest
                // Scoped to this account: signing out or switching cancels both collectors.
                coroutineScope {
                    launch {
                        ChatSignals.openThread.collect { publish() }
                    }
                    val chatEvents = container.hub.events.filter {
                        it.type == RealtimeEvent.ChatMessage || it.type == RealtimeEvent.ChatRead
                    }
                    // A burst of events (a few photos sent at once) becomes one request.
                    merge(flowOf(Unit), container.hub.resyncs, chatEvents, ChatSignals.unreadChanged)
                        .debounce(DEBOUNCE_MS)
                        .collect { reload() }
                }
            }
        }
    }

    private suspend fun reload() {
        try {
            lastList = api.conversations()
            publish()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the last count on screen; the next event or resync tries again.
        }
    }

    private fun publish() {
        val list = lastList ?: return
        container.badges.chatUnread.value = unreadOutside(list, ChatSignals.openThread.value)
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
