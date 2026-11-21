package com.hafiyanjye.app.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.match.JobWithSession
import com.hafiyanjye.app.data.match.MatchApi
import com.hafiyanjye.app.data.match.MatchSession
import com.hafiyanjye.app.data.match.MatchedPerson
import com.hafiyanjye.app.data.match.RefineResult
import com.hafiyanjye.app.data.match.Reply
import com.hafiyanjye.app.data.realtime.RealtimeEvent
import com.hafiyanjye.app.data.realtime.RealtimeHub
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.Loader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A short message after an action: what happened, or why it failed. */
data class Notice(val text: String, val tone: Tone)

/** Who the person sheet is about. */
sealed interface PersonDetails {
    /** Someone on the live map before the match: no name, no photo, no number. */
    data class Anonymous(val state: PersonState, val reply: Reply?) : PersonDetails

    /** Someone revealed after the match: the pick or another person who said yes. */
    data class Revealed(val person: MatchedPerson, val isPick: Boolean) : PersonDetails
}

/** What sits over the request screen. Kept here so it survives rotation and closes when its action succeeds. */
sealed interface Overlay {
    data object Budget : Overlay
    data object ConfirmCancel : Overlay
    data object Hire : Overlay
    data object Reject : Overlay
    data class Person(val details: PersonDetails) : Overlay
}

/** The answer to "Need something more?", and the pick it was asked about. */
data class Refined(val pickId: Int, val result: RefineResult)

/** One request and its live matching, for the job provider. */
class RequestViewModel(
    private val api: MatchApi,
    private val hub: RealtimeHub,
    private val jobId: Int,
) : ViewModel() {

    private val _notFound = MutableStateFlow(false)

    /** True when the engine said this request is not ours or does not exist. */
    val notFound: StateFlow<Boolean> = _notFound.asStateFlow()

    val request: Loader<JobWithSession> = Loader(viewModelScope) {
        try {
            api.job(jobId).also { _notFound.value = false }
        } catch (error: ApiException) {
            _notFound.value = error.status == 404 || error.status == 403
            throw error
        }
    }

    private val _busy = MutableStateFlow<String?>(null)

    /** The action in progress (one of [Actions]), or null. One at a time, as on the web. */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _overlay = MutableStateFlow<Overlay?>(null)
    val overlay: StateFlow<Overlay?> = _overlay.asStateFlow()

    private val _refined = MutableStateFlow<Refined?>(null)
    val refined: StateFlow<Refined?> = _refined.asStateFlow()

    // A channel, not a shared flow: a toast sent while the screen rotates waits for it.
    private val noticeChannel = Channel<Notice>(Channel.BUFFERED)
    val notices: Flow<Notice> = noticeChannel.receiveAsFlow()

    private val visible = MutableStateFlow(false)

    /** A live event arrived while a fetch was already running: fetch once more when it ends. */
    private var refreshAgain = false

    init {
        viewModelScope.launch { hub.resyncs.collect { refresh() } }
        viewModelScope.launch { hub.events.filter(::concernsThisRequest).collect { refresh() } }
        viewModelScope.launch {
            request.state.collect { state ->
                if (refreshAgain && state is Load.Ready && !state.refreshing) {
                    refreshAgain = false
                    request.refresh()
                }
            }
        }
        viewModelScope.launch { pollWhileOffline() }
    }

    /** Reload now; when a load is already running, run one more after it so no change is missed. */
    fun refresh() {
        val state = request.state.value
        val running = state is Load.Loading || (state is Load.Ready && state.refreshing)
        if (running) refreshAgain = true else request.refresh()
    }

    /** The screen is on show (resumed) or not: polling and the catch-up on return depend on it. */
    fun setVisible(shown: Boolean) {
        val cameBack = shown && !visible.value
        visible.value = shown
        if (cameBack && request.state.value is Load.Ready) refresh()
    }

    /**
     * Live events keep the screen fresh. Only while they cannot arrive (the
     * connection is down) and the screen is on show, ask the engine on a timer
     * that suits the state; a finished request never polls.
     */
    private suspend fun pollWhileOffline() {
        while (viewModelScope.isActive) {
            val interval = current()?.session?.state?.let(::pollIntervalMs)
            delay(interval ?: IDLE_CHECK_MS)
            if (interval != null && visible.value && !hub.connected.value) refresh()
        }
    }

    private fun current(): JobWithSession? = (request.state.value as? Load.Ready)?.value

    private fun concernsThisRequest(event: RealtimeEvent): Boolean {
        if (event.type != RealtimeEvent.SessionUpdated && event.type != RealtimeEvent.MatchFound) return false
        val sessionId = current()?.session?.id
        return event.int("jobId") == jobId || (sessionId != null && event.int("sessionId") == sessionId)
    }

    /** Show the engine's answer at once, then confirm with a fresh load (an older load may still land). */
    private fun show(session: MatchSession) {
        request.mutate { it.copy(session = session) }
        refresh()
    }

    private fun show(data: JobWithSession) {
        request.mutate { data }
        refresh()
    }

    /** Show a sheet or dialog over the request. */
    fun open(overlay: Overlay) {
        _overlay.value = overlay
    }

    /** Close whatever is open; an action already sent still finishes and reports in a toast. */
    fun close() {
        _overlay.value = null
    }

    private fun closeIf(predicate: (Overlay) -> Boolean) {
        _overlay.value?.let { if (predicate(it)) _overlay.value = null }
    }

    private fun act(key: String, onFailure: () -> Unit = {}, block: suspend () -> Notice?) {
        if (_busy.value != null) return
        _busy.value = key
        viewModelScope.launch {
            try {
                block()?.let { noticeChannel.send(it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onFailure()
                noticeChannel.send(Notice(error.userMessage(), Tone.Danger))
            } finally {
                _busy.value = null
            }
        }
    }

    // ─── The request ────────────────────────────────────────────────────────

    fun cancel() = act(Actions.Cancel) {
        show(api.cancel(jobId))
        closeIf { it == Overlay.ConfirmCancel }
        Notice("Request cancelled. Nobody else will be pinged.", Tone.Accent)
    }

    fun setBudget(budgetRwf: Int) = act(Actions.Budget) {
        show(api.setBudget(jobId, budgetRwf))
        closeIf { it == Overlay.Budget }
        Notice("Budget updated. People nearby will see the new amount.", Tone.Accent)
    }

    fun setNotifyMe(on: Boolean) {
        val before = current()?.job?.notifyMe ?: return
        // The switch moves at once; it goes back if the engine says no.
        request.mutate { it.copy(job = it.job.copy(notifyMe = on)) }
        act(Actions.Notify, onFailure = { request.mutate { it.copy(job = it.job.copy(notifyMe = before)) } }) {
            show(api.setNotifyMe(jobId, on))
            val text = if (on) {
                "We will let you know as soon as someone is free."
            } else {
                "Okay, no alerts for this request."
            }
            Notice(text, Tone.Accent)
        }
    }

    fun retry() = act(Actions.Retry) {
        val wasDirect = current()?.session?.direct != null
        show(api.retry(jobId))
        Notice(if (wasDirect) "Asking people nearby now." else "Searching a wider area now.", Tone.Accent)
    }

    // ─── The session ────────────────────────────────────────────────────────

    fun shortlistNow(sessionId: Int) = act(Actions.Shortlist) {
        show(api.shortlistNow(sessionId))
        null
    }

    fun answer(sessionId: Int, roundNo: Int, optionId: String) = act(Actions.answer(optionId)) {
        show(api.answer(sessionId, roundNo, optionId))
        null
    }

    fun choose(sessionId: Int, person: MatchedPerson) = act(Actions.choose(person.workerId)) {
        show(api.choose(sessionId, person.workerId))
        _refined.value = null
        closeIf { it is Overlay.Person }
        Notice("Done. You're now matched with ${person.displayName}.", Tone.Accent)
    }

    fun reject(sessionId: Int, reason: String) = act(Actions.Reject) {
        val next = api.reject(sessionId, reason)
        show(next)
        _refined.value = null
        closeIf { it == Overlay.Reject }
        val text = next.pick?.let { "Here is your next match: ${it.displayName}." }
            ?: "Finding new people near you. They'll appear here as they reply."
        Notice(text, Tone.Accent)
    }

    fun pingMore(sessionId: Int, categoryName: String) = act(Actions.PingMore) {
        val result = api.pingMore(sessionId)
        show(result.session)
        if (result.pinged > 0) {
            val who = categoryNoun(categoryName, result.pinged)
            Notice("Asked ${result.pinged} more $who. Their yeses will appear here.", Tone.Accent)
        } else {
            val radius = result.session.askingMore?.radiusKm ?: result.session.stats.radiusKm
            val who = categoryNoun(categoryName, 2)
            Notice("No new $who are online within ${kmNumber(radius)} km right now.", Tone.Neutral)
        }
    }

    fun refine(sessionId: Int, pickId: Int, wish: String) {
        if (wish.trim().length < 2) return
        _refined.value = null
        act(Actions.Refine) {
            _refined.value = Refined(pickId, api.refine(sessionId, wish))
            null
        }
    }

    fun hire(sessionId: Int, firstName: String) = act(Actions.Hire) {
        show(api.hire(sessionId))
        closeIf { it == Overlay.Hire }
        Notice("You hired $firstName. We let them know.", Tone.Accent)
    }

    fun complete(sessionId: Int) = act(Actions.Complete) {
        show(api.complete(sessionId))
        Notice("Marked as done. How did it go?", Tone.Accent)
    }

    fun review(sessionId: Int, rating: Int, text: String) = act(Actions.Review) {
        api.review(sessionId, rating, text)
        request.mutate { it.copy(session = it.session.copy(reviewed = true, canReview = false)) }
        refresh()
        Notice("Thanks, review sent.", Tone.Accent)
    }

    /** Keys for [busy]: which button shows its spinner. */
    object Actions {
        const val Cancel = "cancel"
        const val Budget = "budget"
        const val Notify = "notify"
        const val Retry = "retry"
        const val Shortlist = "shortlist"
        const val Reject = "reject"
        const val PingMore = "ping-more"
        const val Refine = "refine"
        const val Hire = "hire"
        const val Complete = "complete"
        const val Review = "review"
        private const val AnswerPrefix = "answer:"

        fun answer(optionId: String) = "$AnswerPrefix$optionId"

        fun choose(workerId: Int) = "choose:$workerId"

        fun isAnswer(key: String?) = key?.startsWith(AnswerPrefix) == true
    }

    private companion object {
        /** With nothing to poll for, look again this often whether polling is needed. */
        const val IDLE_CHECK_MS = 5_000L
    }
}
