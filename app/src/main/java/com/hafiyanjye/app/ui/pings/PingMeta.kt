package com.hafiyanjye.app.ui.pings

import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.api.Ping
import com.hafiyanjye.app.data.api.PingStatus
import com.hafiyanjye.app.data.api.Urgency
import com.hafiyanjye.app.data.pings.Availability
import com.hafiyanjye.app.data.pings.WorkStats
import java.util.Locale
import kotlin.math.roundToInt

/* The rules of a ping as the job seeker sees it. Mirrors web/src/components/provider/ping-meta.ts. */

/** What a ping is to the person looking at it right now. */
enum class PingView(val label: String, val tone: Tone) {
    Chosen("You were chosen", Tone.Accent),
    New("New", Tone.Accent),
    Seen("Needs answer", Tone.Warn),
    Waiting("Waiting for the job provider", Tone.Info),
    Declined("Not now", Tone.Neutral),
    Expired("Expired", Tone.Neutral),
    Withdrawn("Withdrawn", Tone.Neutral);

    /** Still open for "available" or "not now". */
    val answerable: Boolean get() = this == New || this == Seen
}

/**
 * [secondsLeft] comes from the phone's clock, so a ping closes on screen the
 * second it runs out, before the engine's "expired" arrives.
 */
fun pingView(ping: Ping, secondsLeft: Long): PingView = when {
    ping.match != null -> PingView.Chosen
    ping.status == PingStatus.Available -> PingView.Waiting
    ping.status == PingStatus.NotNow -> PingView.Declined
    ping.status == PingStatus.Withdrawn -> PingView.Withdrawn
    ping.status == PingStatus.Expired || secondsLeft <= 0 -> PingView.Expired
    ping.status == PingStatus.Sent -> PingView.New
    else -> PingView.Seen
}

/** The last minute of an open ping is shown in red (with the clock, not by colour alone). */
fun isLastMinute(view: PingView, secondsLeft: Long): Boolean = view.answerable && secondsLeft in 1..60

/** 0 → "0:00", 75 → "1:15", 3725 → "1:02:05". */
fun formatCountdown(seconds: Long): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

/** The same, for a screen reader: "1 minute 15 seconds". */
fun spokenCountdown(seconds: Long): String {
    if (seconds <= 0) return "no time"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    fun unit(n: Long, word: String) = if (n == 1L) "1 $word" else "$n ${word}s"
    return listOfNotNull(
        unit(h, "hour").takeIf { h > 0 },
        unit(m, "minute").takeIf { m > 0 },
        unit(s, "second").takeIf { s > 0 && h == 0L },
    ).joinToString(" ")
}

fun urgencyLabel(urgency: Urgency): String = when (urgency) {
    Urgency.Now -> "Right now"
    Urgency.Today -> "Today"
    Urgency.ThisWeek -> "This week"
    Urgency.Flexible -> "Flexible"
}

fun urgencyTone(urgency: Urgency): Tone = when (urgency) {
    Urgency.Now -> Tone.Danger
    Urgency.Today -> Tone.Warn
    Urgency.ThisWeek -> Tone.Info
    Urgency.Flexible -> Tone.Neutral
}

// ── Quotes ──────────────────────────────────────────────────────────────────

const val NOTE_MAX = 280
const val QUOTE_MIN_RWF = 500
const val QUOTE_MAX_RWF = 10_000_000

/** A one-tap quote around the budget. */
data class QuoteChip(val label: String, val value: Int)

private fun roundRwf(amount: Double): Int = maxOf(QUOTE_MIN_RWF, (amount / 500).roundToInt() * 500)

/** Quick picks around the job provider's budget; none when they gave no budget. */
fun quoteChips(budgetRwf: Int?): List<QuoteChip> {
    val budget = budgetRwf ?: return emptyList()
    return listOf(
        QuoteChip("−10%", roundRwf(budget * 0.9)),
        QuoteChip("Budget", roundRwf(budget.toDouble())),
        QuoteChip("+10%", roundRwf(budget * 1.1)),
        QuoteChip("+25%", roundRwf(budget * 1.25)),
    )
}

/** What the quote field holds, checked before anything is sent. */
sealed interface QuoteInput {
    /** Nothing typed: the answer goes without a quote. */
    data object None : QuoteInput
    data class Amount(val rwf: Int) : QuoteInput
    data class Invalid(val message: String) : QuoteInput
}

/** What the typed quote means, by the engine's limits (500 to 10,000,000 RWF, whole numbers). */
fun parseQuote(typed: String): QuoteInput {
    val digits = typed.trim()
    if (digits.isEmpty()) return QuoteInput.None
    val value = digits.toLongOrNull()
    return when {
        value == null || value < QUOTE_MIN_RWF ->
            QuoteInput.Invalid("Enter a quote of at least 500 RWF, or leave it empty.")
        value > QUOTE_MAX_RWF -> QuoteInput.Invalid("A quote can be at most 10,000,000 RWF.")
        else -> QuoteInput.Amount(value.toInt())
    }
}

// ── The page around the pings ───────────────────────────────────────────────

/** Jobs this person was chosen for come first, on their own. */
fun chosenPings(active: List<Ping>): List<Ping> = active.filter { it.match != null }

fun otherPings(active: List<Ping>): List<Ping> = active.filter { it.match == null }

/** Pings not opened yet (the count on the "New pings" tab). */
fun unreadCount(active: List<Ping>): Int = otherPings(active).count { it.status == PingStatus.Sent }

/** Which honest nudge to show while offline, from the engine's own numbers. */
enum class HabitBanner { Paused, Missed }

fun habitBanner(availability: Availability, stats: WorkStats?): HabitBanner? = when {
    availability == Availability.Online || stats == null -> null
    stats.pausedAt != null -> HabitBanner.Paused
    stats.missedWhileOffline > 0 -> HabitBanner.Missed
    else -> null
}

fun jobsToGo(count: Int): String = if (count == 1) "1 job to go" else "$count jobs to go"

fun missedTitle(count: Int): String =
    "$count ${if (count == 1) "job" else "jobs"} near you went out while you were offline"

/** "a plumber", "an electrician". */
fun withArticle(word: String): String {
    val w = word.lowercase(Locale.US)
    return "${if (w.firstOrNull() in setOf('a', 'e', 'i', 'o', 'u')) "an" else "a"} $w"
}

/** 38.0 → "38", 38.75 → "38.8": ranking points as the web writes them. */
fun points(value: Double): String = if (value.isFinite() && value == Math.floor(value)) {
    value.toLong().toString()
} else {
    String.format(Locale.US, "%.1f", value)
}

/** This week's quoted earnings to the nearest thousand; null when there are none. */
fun roundedEarnings(quotedEarningsRwf: Int): Int? =
    if (quotedEarningsRwf > 0) (quotedEarningsRwf / 1000.0).roundToInt() * 1000 else null

const val READY_SCORE = 60
const val STRONG_SCORE = 85

fun completenessWord(score: Int): String = when {
    score >= STRONG_SCORE -> "Strong"
    score >= READY_SCORE -> "Ready for jobs"
    else -> "Getting started"
}
