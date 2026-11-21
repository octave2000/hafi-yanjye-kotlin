package com.hafiyanjye.app.data.realtime.transport

import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.hafiyanjye.app.AppContainer
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the two live routes (the SSE stream and the Hubfly WebSocket) feeding
 * `container.hub` while someone is signed in and the app is on screen, as the
 * web's useEventStream and useChatSocket do together.
 *
 * - A round of both routes runs while `session.state` is SignedIn and the app
 *   is in the foreground; it ends [BACKGROUND_GRACE_MS] after the app leaves
 *   the screen, at sign-out, and when another account signs in (the next
 *   round fetches that account's channels; nothing of the old one is reused).
 * - The network coming back (or changing) ends the round and opens a fresh one
 *   at once, instead of waiting out a backoff on sockets of a dead network.
 * - `hub.connected` is true while either route is open.
 * - `hub.requestResync()` whenever events may have been missed, as the web's
 *   resyncAll: a route opened again after a drop; the first route opened in a
 *   round that follows another round of the same account (back from the
 *   background, network back); the app coming back on screen while the
 *   routes were still open.
 *
 * All network work and frame parsing run on [Dispatchers.IO]. Call [start]
 * once from the main thread (HafiApp.onCreate).
 */
class RealtimeConnection(private val container: AppContainer) {
    private val foreground = MutableStateFlow(false)

    /** Bumped to drop the current connections and open fresh ones. */
    private val generation = MutableStateFlow(0)
    private val http by lazy { container.http.streamingClient() }

    /** True while a round is running: a return to the screen then resyncs at once. */
    private val roundRunning = AtomicBoolean(false)
    private var started = false

    private val signedIn: Boolean
        get() = container.session.state.value is SessionState.SignedIn

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        watchForeground()
        watchNetwork()
        val userId = container.session.state
            .map { (it as? SessionState.SignedIn)?.me?.id }
            .distinctUntilChanged()
        // Leaving the screen is only believed after a moment: a photo picker or a quick app switch is not a goodbye.
        val onScreen = foreground.transformLatest { visible ->
            if (!visible) delay(BACKGROUND_GRACE_MS)
            emit(visible)
        }
        container.appScope.launch {
            var lastUserId: Int? = null
            combine(userId, onScreen, generation) { id, visible, round ->
                if (visible && id != null) id to round else null
            }
                .distinctUntilChanged()
                .collectLatest { wanted ->
                    if (wanted == null) return@collectLatest
                    val accountId = wanted.first
                    val catchUp = accountId == lastUserId
                    lastUserId = accountId
                    withContext(Dispatchers.IO) { runRoutes(catchUp) }
                }
        }
    }

    /**
     * Both routes, until cancelled (signed out, in the background, or a fresh
     * round). [catchUp]: this account had a round before, so whatever happened
     * in between is reloaded once the first route is open.
     */
    private suspend fun runRoutes(catchUp: Boolean) {
        val hub = container.hub
        val token = container.session.token()
        val rejectSession = {
            // An old response must not sign out a newly signed-in account.
            if (token != null && container.session.token() == token) container.session.onRejected()
        }
        val pendingCatchUp = AtomicBoolean(catchUp)
        val sseOpen = AtomicBoolean(false)
        val socketOpen = AtomicBoolean(false)
        fun onRouteChange(route: AtomicBoolean, open: Boolean) {
            route.set(open)
            hub.setConnected(sseOpen.get() || socketOpen.get())
            if (open && pendingCatchUp.getAndSet(false)) hub.requestResync()
        }
        roundRunning.set(true)
        try {
            coroutineScope {
                launch {
                    SseStream(
                        client = container.client,
                        http = http,
                        hub = hub,
                        onOpenChange = { onRouteChange(sseOpen, it) },
                        onReconnect = hub::requestResync,
                        onUnauthorized = rejectSession,
                    ).run()
                }
                launch {
                    // No realtime section: the stream alone carries every event.
                    val realtime = chatConfig()?.realtime ?: return@launch
                    PusherSocket(
                        client = container.client,
                        http = http,
                        hub = hub,
                        wsUrl = realtime.wsUrl,
                        key = realtime.key,
                        channels = realtime.channels,
                        onOpenChange = { onRouteChange(socketOpen, it) },
                        onReconnect = hub::requestResync,
                        onUnauthorized = rejectSession,
                    ).run()
                }
            }
        } finally {
            roundRunning.set(false)
            hub.setConnected(false)
        }
    }

    /** Where the WebSocket lives. Retries while the engine can't be reached; null when it refuses. */
    private suspend fun chatConfig(): ChatConfig? {
        var attempt = 0
        while (true) {
            try {
                return container.client.get<ChatConfig>(CHAT_CONFIG_PATH)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ApiException) {
                if (error.status in 400..499 && error.status != 408 && error.status != 429) return null
            } catch (_: Exception) {
                // Anything else is temporary: keep trying without requiring an app restart.
            }
            delay(backoffMillis(attempt))
            attempt += 1
        }
    }

    private fun watchForeground() {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foreground.value = true
                // Back on screen with the routes still open: catch up now, as the web does when a tab shows
                // again. When they were closed, the next round catches up as soon as it is open.
                if (roundRunning.get() && signedIn) container.hub.requestResync()
            }

            override fun onStop(owner: LifecycleOwner) {
                foreground.value = false
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }

    private fun watchNetwork() {
        val manager = container.app.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            // Registering reports the network already in use: that one is not a return.
            private val skipFirst = AtomicBoolean(runCatching { manager.activeNetwork != null }.getOrDefault(false))

            override fun onAvailable(network: Network) {
                if (skipFirst.getAndSet(false)) return
                if (!foreground.value || !signedIn) return
                generation.update { it + 1 }
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
    }

    /** `GET /api/v1/chat/config` (engine ChatConfigDto). */
    @Serializable
    internal data class ChatConfig(val realtime: Realtime? = null) {
        /** Where the Hubfly WebSocket lives and the account's own channels (private and presence). */
        @Serializable
        data class Realtime(val wsUrl: String, val key: String, val channels: List<String> = emptyList())
    }

    private companion object {
        const val BACKGROUND_GRACE_MS = 5_000L
        const val CHAT_CONFIG_PATH = "/api/v1/chat/config"
    }
}
