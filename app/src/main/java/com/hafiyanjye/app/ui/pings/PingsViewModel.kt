package com.hafiyanjye.app.ui.pings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.api.Ping
import com.hafiyanjye.app.data.api.PingStatus
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.pings.Availability
import com.hafiyanjye.app.data.pings.PingsApi
import com.hafiyanjye.app.data.pings.Review
import com.hafiyanjye.app.data.realtime.RealtimeEvent
import com.hafiyanjye.app.data.realtime.RealtimeHub
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.Loader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Something to say once, as a toast. */
data class PingNotice(val message: String, val tone: Tone = Tone.Neutral)

/** The three answers the sheet can send, so only the pressed button shows progress. */
enum class AnswerAction { Available, NotNow, Withdraw }

/** The open answer sheet: which ping, what is being sent, and the engine's refusal if any. */
data class AnswerSheetState(val pingId: Int, val busy: AnswerAction? = null, val error: String? = null)

/** State and actions of the job seeker's home; survives rotation, so an open sheet stays open. */
class PingsViewModel(private val api: PingsApi, hub: RealtimeHub) : ViewModel() {

    val profile = Loader(viewModelScope) { api.profile() }

    /** The work profile's id once there is one; the numbers below need a profile to exist. */
    private val profileId = MutableStateFlow<Int?>(null)

    private suspend fun awaitProfileId(): Int = profileId.filterNotNull().first()

    val active = Loader(viewModelScope) { api.pings(PingsApi.Scope.Active) }

    val stats = Loader(viewModelScope) {
        awaitProfileId()
        api.stats()
    }

    val ranking = Loader(viewModelScope) {
        awaitProfileId()
        api.ranking()
    }

    val reviews: Loader<List<Review>> = Loader(viewModelScope) { api.reviews(awaitProfileId()) }

    /** Lucide icon names by service name, for pings in services this person does not offer. */
    val categoryIcons = Loader(viewModelScope) {
        api.categories().associate { it.nameEn to it.icon }
    }

    /** Past pings are only fetched once the History tab is opened. */
    private var historyLoader: Loader<List<Ping>>? = null
    private val _history = MutableStateFlow<Load<List<Ping>>>(Load.Loading)
    val history: StateFlow<Load<List<Ping>>> = _history.asStateFlow()

    private val _sheet = MutableStateFlow<AnswerSheetState?>(null)
    val sheet: StateFlow<AnswerSheetState?> = _sheet.asStateFlow()

    /** The opened ping as last known, so the sheet keeps its content while the lists reload. */
    private val _openedPing = MutableStateFlow<Ping?>(null)
    val openedPing: StateFlow<Ping?> = _openedPing.asStateFlow()

    /** Shown at once when the switch is flipped; cleared when the engine has answered. */
    private val _pendingAvailability = MutableStateFlow<Availability?>(null)
    val pendingAvailability: StateFlow<Availability?> = _pendingAvailability.asStateFlow()

    /** Pings that arrived while the screen was open: highlighted for a moment. */
    private val _fresh = MutableStateFlow<Set<Int>>(emptySet())
    val fresh: StateFlow<Set<Int>> = _fresh.asStateFlow()

    /** Pings whose own answer made the hire, so the live "hired" event is not announced twice. */
    private val hiredByAnswer = mutableSetOf<Int>()

    private val _notices = MutableSharedFlow<PingNotice>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notices: SharedFlow<PingNotice> = _notices.asSharedFlow()

    init {
        viewModelScope.launch {
            profile.state
                .map { (it as? Load.Ready)?.value?.id }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { id ->
                    val first = profileId.value == null
                    profileId.value = id
                    // A profile made after the screen opened: its reviews were never asked for.
                    if (!first) reviews.refresh()
                }
        }
        viewModelScope.launch { hub.resyncs.collect { refreshAll() } }
        viewModelScope.launch { hub.events.collect(::onEvent) }
        viewModelScope.launch { followActivePings() }
    }

    /** An answer changes the stats; the open sheet follows the newest copy of its ping. */
    private suspend fun followActivePings() {
        var previousStatuses: List<Pair<Int, PingStatus>>? = null
        active.state.collect { loaded ->
            if (loaded !is Load.Ready) return@collect
            val statuses = loaded.value.map { it.id to it.status }
            if (previousStatuses != null && previousStatuses != statuses) stats.refresh()
            previousStatuses = statuses
            val openId = _sheet.value?.pingId
            loaded.value.firstOrNull { it.id == openId }?.let { _openedPing.value = it }
        }
    }

    private fun onEvent(event: RealtimeEvent) {
        when (event.type) {
            RealtimeEvent.PingNew -> {
                refreshPings()
                stats.refresh()
                event.int("pingId")?.let(::highlight)
                val notice = if (event.string("direct") == "true") {
                    "Direct hire request: someone picked you. Accept and you're hired."
                } else {
                    "New job nearby: someone close to you needs your help."
                }
                _notices.tryEmit(PingNotice(notice, Tone.Accent))
            }

            RealtimeEvent.PingUpdated -> {
                refreshPings()
                stats.refresh()
            }

            RealtimeEvent.MatchHired -> {
                refreshPings()
                stats.refresh()
                val pingId = event.int("pingId")
                if (pingId == null || !hiredByAnswer.remove(pingId)) {
                    _notices.tryEmit(HIRED)
                }
            }

            RealtimeEvent.MatchFound -> {
                refreshPings()
                _notices.tryEmit(
                    PingNotice("A job provider chose you. Their contact is on this screen.", Tone.Accent),
                )
            }

            // A cancel, a change or a release by the job provider.
            RealtimeEvent.SessionUpdated -> {
                refreshPings()
                stats.refresh()
            }

            RealtimeEvent.Notification -> refreshPings()
        }
    }

    private fun highlight(pingId: Int) {
        _fresh.update { it + pingId }
        viewModelScope.launch {
            delay(HIGHLIGHT_MS)
            _fresh.update { it - pingId }
        }
    }

    private fun refreshPings() {
        active.refresh()
        historyLoader?.refresh()
    }

    fun refreshAll() {
        profile.refresh()
        refreshPings()
        stats.refresh()
        ranking.refresh()
        reviews.refresh()
    }

    /**
     * Polls open pings while the screen is in front, as the web does every 10 s:
     * a countdown that a lost live event left behind is corrected within seconds.
     */
    suspend fun keepPingsFresh() {
        while (true) {
            delay(POLL_MS)
            active.refresh()
        }
    }

    fun showHistory() {
        if (historyLoader != null) return
        val loader = Loader(viewModelScope) { api.pings(PingsApi.Scope.History) }
        historyLoader = loader
        viewModelScope.launch { loader.state.collect { _history.value = it } }
    }

    fun refreshHistory() {
        historyLoader?.refresh()
    }

    fun setAvailability(next: Availability) {
        if (_pendingAvailability.value != null) return
        _pendingAvailability.value = next
        viewModelScope.launch {
            try {
                val updated = api.setAvailability(next)
                profile.mutate { updated }
                // Going online clears "paused" and the missed count.
                stats.refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.userMessage().ifBlank { "Couldn't change availability." }
                _notices.tryEmit(PingNotice(message, Tone.Danger))
                profile.refresh()
            } finally {
                _pendingAvailability.value = null
            }
        }
    }

    /** Opening a ping is what marks it seen (the job provider sees "seen" too). */
    fun open(ping: Ping) {
        _openedPing.value = ping
        _sheet.value = AnswerSheetState(ping.id)
        if (ping.status != PingStatus.Sent) return
        viewModelScope.launch {
            try {
                replace(api.markSeen(ping.id))
                stats.refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Not worth a message: answering marks it seen as well.
            }
        }
    }

    /** Ignored while an answer is on its way, so its result is never lost. */
    fun closeSheet() {
        if (_sheet.value?.busy == null) _sheet.value = null
    }

    fun clearSheetError() {
        _sheet.update { it?.copy(error = null) }
    }

    fun answerAvailable(pingId: Int, quoteRwf: Int?, note: String) = act(
        pingId,
        AnswerAction.Available,
        PingNotice("Sent! We'll tell you if the job provider picks you.", Tone.Accent),
    ) { api.answerAvailable(pingId, quoteRwf, note) }

    fun answerNotNow(pingId: Int) = act(
        pingId,
        AnswerAction.NotNow,
        PingNotice("Okay, we won't bother you with this one."),
    ) { api.answerNotNow(pingId) }

    fun withdraw(pingId: Int) = act(
        pingId,
        AnswerAction.Withdraw,
        PingNotice("You withdrew from this job."),
    ) { api.withdraw(pingId) }

    private fun act(pingId: Int, action: AnswerAction, done: PingNotice, send: suspend () -> Ping) {
        val current = _sheet.value
        if (current == null || current.pingId != pingId || current.busy != null) return
        _sheet.value = current.copy(busy = action, error = null)
        // Accepting a direct request hires at once, and the live "hired" event can beat the response.
        val direct = _openedPing.value?.takeIf { it.id == pingId }?.direct == true
        if (action == AnswerAction.Available && direct) hiredByAnswer += pingId
        viewModelScope.launch {
            try {
                val updated = send()
                val hired = updated.match?.hired == true
                if (!hired) hiredByAnswer -= pingId
                replace(updated)
                _sheet.value = null
                _notices.tryEmit(if (action == AnswerAction.Available && hired) HIRED else done)
                refreshPings()
                stats.refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                hiredByAnswer -= pingId
                _sheet.update { sheet ->
                    if (sheet?.pingId == pingId) sheet.copy(busy = null, error = error.userMessage()) else sheet
                }
                // The engine may have refused because the ping changed: show what is true now.
                refreshPings()
            }
        }
    }

    private fun replace(ping: Ping) {
        active.mutate { list -> list.map { if (it.id == ping.id) ping else it } }
        historyLoader?.mutate { list -> list.map { if (it.id == ping.id) ping else it } }
        if (_openedPing.value?.id == ping.id) _openedPing.value = ping
    }

    private companion object {
        const val HIGHLIGHT_MS = 4_000L
        const val POLL_MS = 10_000L
        val HIRED = PingNotice("You're hired! The job provider's contact is on this screen.", Tone.Accent)
    }
}
