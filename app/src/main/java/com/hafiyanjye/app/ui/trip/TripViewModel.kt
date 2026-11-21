package com.hafiyanjye.app.ui.trip

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.AppContainer
import com.hafiyanjye.app.data.api.Role
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.location.MapsKey
import com.hafiyanjye.app.data.realtime.RealtimeEvent
import com.hafiyanjye.app.data.trip.DelayReason
import com.hafiyanjye.app.data.trip.RouteProgress
import com.hafiyanjye.app.data.trip.Trip
import com.hafiyanjye.app.data.trip.TripAction
import com.hafiyanjye.app.data.trip.TripApi
import com.hafiyanjye.app.data.trip.TripPosition
import com.hafiyanjye.app.data.trip.TripRoute
import com.hafiyanjye.app.data.trip.TripRoutes
import com.hafiyanjye.app.data.trip.TripRouting
import com.hafiyanjye.app.data.trip.TripSharing
import com.hafiyanjye.app.data.trip.TripStatus
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.Loader
import com.hafiyanjye.app.ui.location.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.max

/** The road route and where the person is on it; null whenever the screen should fall back to a straight line. */
data class TripNav(val route: TripRoute, val progress: RouteProgress)

/** A one-off message for a toast. */
data class TripMessage(val text: String, val failed: Boolean = false)

/** The live trip of one hired session, for both people: the trip, the road route, and the hired person's actions. */
class TripViewModel(private val sessionId: Int, private val container: AppContainer) : ViewModel() {
    private val api = TripApi(container.client)
    private val routing = TripRouting(container.http)
    private val hub = container.hub

    val loader = Loader(viewModelScope) { api.trip(sessionId) }
        .refreshOn(hub.resyncs)
        .refreshOn(
            hub.events.filter {
                it.int("sessionId") == sessionId &&
                    (it.type == RealtimeEvent.TripUpdated || it.type == RealtimeEvent.SessionUpdated)
            },
        )

    private val route = MutableStateFlow<TripRoute?>(null)

    /** Null when off the route, before a route, or when no route service answers: then a dashed straight line. */
    val nav: StateFlow<TripNav?> = combine(loader.state, route) { load, current ->
        val trip = (load as? Load.Ready)?.value ?: return@combine null
        val position = trip.position ?: return@combine null
        if (current == null || !routeWanted(trip)) return@combine null
        val progress = TripRoutes.progress(current, GeoPoint(position.lat, position.lng))
        if (TripRoutes.isOffRoute(progress, position.accuracy)) null else TripNav(current, progress)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Which action is being sent, or null. */
    val busy = MutableStateFlow<TripAction?>(null)
    val delaySending = MutableStateFlow(false)
    val delayError = MutableStateFlow<String?>(null)

    /** Bumped when a delay notice went through, so the sheet closes. */
    val delaysSent = MutableStateFlow(0)

    private val _messages = Channel<TripMessage>(Channel.BUFFERED)
    val messages: Flow<TripMessage> = _messages.receiveAsFlow()

    private var routeJob: Job? = null
    private var routeAsks = 0
    private var lastAskAt = 0L
    private var failedAt = 0L
    private var preferredMode: String? = null

    init {
        viewModelScope.launch {
            hub.events
                .filter { it.type == RealtimeEvent.TripLocation && it.int("sessionId") == sessionId }
                .collect(::moveTo)
        }
        viewModelScope.launch {
            loader.state.collect { load ->
                val trip = (load as? Load.Ready)?.value ?: return@collect
                stopSharingWhenOver(trip)
                planRoute()
            }
        }
    }

    /**
     * A new position: moved in place, without asking the engine again. The
     * event has no accuracy, so the last one stays. A position while the trip
     * is not shown as under way means it just started: reload.
     */
    private fun moveTo(event: RealtimeEvent) {
        val trip = (loader.state.value as? Load.Ready)?.value
        val lat = event.string("lat")?.toDoubleOrNull()
        val lng = event.string("lng")?.toDoubleOrNull()
        if (trip == null || lat == null || lng == null || trip.status != TripStatus.OnTheWay || !trip.live) {
            loader.refresh()
            return
        }
        val at = event.string("at")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
        val heading = event.string("heading")?.toDoubleOrNull()
        loader.mutate { current ->
            val previous = current.position
            current.copy(
                position = TripPosition(
                    lat = lat,
                    lng = lng,
                    heading = heading,
                    accuracy = previous?.accuracy,
                    at = at,
                ),
            )
        }
    }

    /** The hired person's phone stops sending once the trip is no longer under way, whoever ended it. */
    private fun stopSharingWhenOver(trip: Trip) {
        val underWay = trip.live && trip.role == Role.Worker && trip.status == TripStatus.OnTheWay
        if (!underWay && TripSharing.sessionId.value == sessionId) TripSharing.stop(container.app)
    }

    private fun routeWanted(trip: Trip): Boolean {
        val km = TripText.straightKm(trip) ?: return false
        return MapsKey.present &&
            trip.live &&
            trip.status == TripStatus.OnTheWay &&
            km * 1000 >= TripText.RouteMinMeters
    }

    /**
     * Asks for a road route when there is none or the person left it, as the
     * web's use-trip-route: at most every 30 s, 2 min after a failure, and 30
     * times per open screen, since each request is billed.
     */
    private fun planRoute() {
        if (routeJob?.isActive == true) return
        routeJob = viewModelScope.launch {
            while (true) {
                val trip = (loader.state.value as? Load.Ready)?.value ?: return@launch
                val position = trip.position ?: return@launch
                if (!routeWanted(trip) || routeAsks >= MaxRouteRequests) return@launch
                val here = GeoPoint(position.lat, position.lng)
                val current = route.value
                val needed = current == null ||
                    TripRoutes.isOffRoute(TripRoutes.progress(current, here), position.accuracy)
                if (!needed) return@launch
                val readyAt = max(
                    lastAskAt + MinRouteIntervalMs,
                    if (failedAt != 0L) failedAt + RetryAfterFailMs else 0L,
                )
                val now = SystemClock.elapsedRealtime()
                if (lastAskAt != 0L && now < readyAt) {
                    // A phone standing still sends no new position: wait here instead of for one.
                    delay(readyAt - now)
                    continue
                }
                lastAskAt = now
                routeAsks += 1
                val next = routing.route(here, TripText.destination(trip), preferredMode)
                if (next != null) {
                    preferredMode = next.mode
                    failedAt = 0L
                    route.value = next
                } else {
                    failedAt = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    /** Start trip: the engine first, then this phone starts sharing. Location access is asked before this. */
    fun start() = perform(TripAction.Start) { trip ->
        TripSharing.start(container.app, sessionId, trip.hirer.name, trip.destination.label)
        "Trip started. ${TripText.capitalized(TripText.hirer(trip))} can follow you live."
    }

    /** Sharing again after the app was closed or sharing was cut, without restarting the trip. */
    fun resumeSharing() {
        val trip = (loader.state.value as? Load.Ready)?.value ?: return
        TripSharing.start(container.app, sessionId, trip.hirer.name, trip.destination.label)
    }

    fun arrived() = perform(TripAction.Arrived) { trip ->
        "${TripText.capitalized(TripText.hirer(trip))} knows you've arrived."
    }

    fun stopSharing() = perform(TripAction.Stop) { "Location sharing stopped." }

    private fun perform(action: TripAction, done: (Trip) -> String) {
        val trip = (loader.state.value as? Load.Ready)?.value ?: return
        if (busy.value != null) return
        busy.value = action
        // A position sent after "arrived" or "stop" would put the trip back on the way: stop sending first.
        if (action != TripAction.Start) TripSharing.stop(container.app)
        viewModelScope.launch {
            try {
                api.action(sessionId, action)
                _messages.send(TripMessage(done(trip)))
                loader.refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _messages.send(TripMessage(error.userMessage(), failed = true))
            } finally {
                busy.value = null
            }
        }
    }

    fun sendDelay(minutes: Int, reason: DelayReason, note: String) {
        val trip = (loader.state.value as? Load.Ready)?.value ?: return
        if (delaySending.value) return
        delaySending.value = true
        delayError.value = null
        viewModelScope.launch {
            try {
                api.delay(sessionId, minutes, reason, note.trim().ifEmpty { null })
                _messages.send(TripMessage("${TripText.capitalized(TripText.hirer(trip))} knows you're running late."))
                delaysSent.value += 1
                loader.refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                delayError.value = error.userMessage()
            } finally {
                delaySending.value = false
            }
        }
    }

    fun clearDelayError() {
        delayError.value = null
    }

    private companion object {
        const val MinRouteIntervalMs = 30_000L
        const val RetryAfterFailMs = 120_000L
        const val MaxRouteRequests = 30
    }
}
