package com.hafiyanjye.app.data.profile

import com.hafiyanjye.app.AppContainer
import com.hafiyanjye.app.data.realtime.RealtimeEvent
import com.hafiyanjye.app.data.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Keeps the bell's unread count (`badges.notificationsUnread`) right for the
 * whole app, whether or not the inbox is open: loaded at sign-in, again on
 * every live "notification" event and whenever events may have been missed.
 * Started once from HafiApp.
 */
class NotificationsTracker(private val container: AppContainer) {

    private val api = ProfileApi(container.client)

    fun start() {
        container.appScope.launch {
            container.session.state
                .map { (it as? SessionState.SignedIn)?.me?.id }
                .distinctUntilChanged()
                .collectLatest { accountId ->
                    // A count belongs to one account; never show the last person's.
                    container.badges.notificationsUnread.value = 0
                    if (accountId == null) return@collectLatest
                    merge(
                        flowOf(Unit),
                        container.hub.events.filter { it.type == RealtimeEvent.Notification },
                        container.hub.resyncs,
                    ).collect { load() }
                }
        }
    }

    private suspend fun load() {
        try {
            container.badges.notificationsUnread.value = api.notifications().unread
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Offline or a hiccup: the count stays as it was until the next event or resync.
        }
    }
}
