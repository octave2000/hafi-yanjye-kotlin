package com.hafiyanjye.app.ui.match

import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.data.api.SessionState
import com.hafiyanjye.app.data.match.MatchSession
import com.hafiyanjye.app.data.match.PingStats
import com.hafiyanjye.app.data.match.PriceUnit
import com.hafiyanjye.app.data.match.Reply
import com.hafiyanjye.app.ui.location.GeoPoint
import java.time.Instant
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/*
 * What the request screen decides without Android: which view a session shows,
 * what its countdown reads, how people and prices are worded and where the
 * anonymous people sit on the map. Mirrors web/src/components/match/shared.ts
 * and web/src/lib/format.ts; rules the engine enforces win over the web's.
 */

/** Which body the request screen shows. */
enum class SessionView {
    Pinging,
    DirectWaiting,
    Shortlisting,
    Eliminating,
    Matched,
    Completed,
    NobodyFree,
    DirectEnded,
    Cancelled,
}

fun sessionView(session: MatchSession): SessionView = when (session.state) {
    SessionState.Pinging -> if (session.direct != null) SessionView.DirectWaiting else SessionView.Pinging
    SessionState.Shortlisting -> SessionView.Shortlisting
    SessionState.Eliminating -> SessionView.Eliminating
    SessionState.Matched -> SessionView.Matched
    SessionState.Completed -> SessionView.Completed
    SessionState.NoProviders -> if (session.direct != null) SessionView.DirectEnded else SessionView.NobodyFree
    SessionState.Cancelled -> SessionView.Cancelled
}

/** The screen's title, also announced to screen readers when the state changes. */
fun stateLabel(state: SessionState): String = when (state) {
    SessionState.Pinging -> "Finding people near you"
    SessionState.Shortlisting -> "Building your shortlist"
    SessionState.Eliminating -> "Narrowing it down"
    SessionState.Matched -> "Matched"
    SessionState.Completed -> "Done"
    SessionState.Cancelled -> "Cancelled"
    SessionState.NoProviders -> "Nobody free yet"
}

/** States in which the engine is still working on its own (the screen shows a live dot). */
fun isLive(state: SessionState): Boolean =
    state == SessionState.Pinging || state == SessionState.Shortlisting || state == SessionState.Eliminating

/**
 * The engine accepts a new budget only while the search is open
 * (engine core/matching/state.ts `canUpdateBudget`). The web also offers it
 * when nobody was free, but the engine refuses it there, so the app does not.
 */
fun canEditBudget(state: SessionState): Boolean = isLive(state)

fun canCancel(state: SessionState): Boolean = state != SessionState.Completed && state != SessionState.Cancelled

/**
 * How often to ask the engine again while the live connection is down (web:
 * `pollInterval`). Null for states that no longer change.
 */
fun pollIntervalMs(state: SessionState): Long? = when (state) {
    SessionState.Completed, SessionState.Cancelled -> null
    SessionState.Pinging, SessionState.Shortlisting -> 4_000
    else -> 15_000
}

/** Seconds left until the engine builds the shortlist; null when it gave no deadline. */
fun shortlistSecondsLeft(session: MatchSession, now: Instant): Long? =
    session.shortlistDueAt?.let { Format.secondsUntil(it, now) }

/** 75 → "1:15"; at or past the deadline the engine is about to move on: "Any moment". */
fun countdownText(secondsLeft: Long): String {
    if (secondsLeft <= 0) return "Any moment"
    val seconds = (secondsLeft % 60).toString().padStart(2, '0')
    return "${secondsLeft / 60}:$seconds"
}

/** One ring of the widening search (engine core/matching/state.ts `WAVES`). */
data class Wave(val number: Int, val km: Int)

val Waves = listOf(Wave(1, 3), Wave(2, 6), Wave(3, 10))

/** The search circle: the engine's radius, or the wave's when it sent none. */
fun searchRadiusKm(stats: PingStats): Double {
    if (stats.radiusKm > 0) return stats.radiusKm
    val km = Waves.firstOrNull { it.number == stats.wave }?.km ?: Waves.first().km
    return km.toDouble()
}

/** 3.0 → "3", 7.5 → "7.5" */
fun kmNumber(km: Double): String =
    if (km == km.roundToLong().toDouble()) km.roundToLong().toString() else String.format(Locale.US, "%.1f", km)

fun pingingHeading(session: MatchSession): String = when {
    session.stats.available > 0 -> "People are saying yes"
    session.turnedDown > 0 -> "Finding new people for you"
    else -> "Asking people near you"
}

fun pingingSubtitle(session: MatchSession, plural: String): String = when {
    session.turnedDown > 0 ->
        "We're asking $plural we haven't shown you yet and skipping the ${session.turnedDown} you passed on."
    session.stats.available > 0 -> "As soon as three people say yes, we line them up for you."
    else -> "We ask the closest people first and widen the search every 30 seconds."
}

/** The reply-time line under the heading; null when the engine has neither number. */
fun replyTimeLine(session: MatchSession, plural: String): String? {
    val first = session.stats.firstReplySeconds
    val typical = session.stats.typicalReplySeconds
    return when {
        first != null -> "First reply in ${Format.duration(first.roundToLong())}"
        session.replies.isEmpty() && typical != null -> {
            val who = plural.replaceFirstChar { it.uppercase() }
            "$who here usually reply in about ${Format.duration(typical.roundToLong())}"
        }
        else -> null
    }
}

fun shortlistNowLabel(available: Int): String =
    if (available < 1) "Shortlist now (waiting for a yes)" else "Shortlist now with $available ${people(available)}"

fun people(count: Int): String = if (count == 1) "person" else "people"

/** ("Electrician", 2) → "electricians", ("Movers", 1) → "mover", ("House cleaning", 1) → "house cleaning pro". */
fun categoryNoun(nameEn: String, count: Int): String {
    val name = nameEn.trim().lowercase(Locale.ROOT)
    if (name.isEmpty()) return people(count)
    val oneWord = name.none(Char::isWhitespace)
    if (oneWord && name.endsWith("ers")) return if (count == 1) name.dropLast(1) else name
    val isPerson = oneWord && listOf("er", "or", "ian", "ic", "on").any(name::endsWith)
    val noun = if (isPerson) name else "$name pro"
    return if (count == 1) noun else "${noun}s"
}

/** "electrician" → "an electrician", "plumber" → "a plumber". */
fun withArticle(noun: String): String {
    val vowel = noun.firstOrNull()?.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u')
    return "${if (vowel) "an" else "a"} $noun"
}

/** ("Plumber") → "A plumber": how an anonymous person is introduced. */
fun anonymousLead(categoryName: String): String =
    withArticle(categoryNoun(categoryName, 1)).replaceFirstChar { it.uppercase() }

/** "c1" → "Candidate A". Stable across rounds because it comes from the id. */
fun candidateLabel(shortId: String): String = "Candidate ${candidateLetter(shortId)}"

fun candidateLetter(shortId: String): String {
    val number = shortId.filter(Char::isDigit).toIntOrNull()
    return if (number != null && number in 1..26) ('A' + (number - 1)).toString() else shortId.uppercase()
}

/** 120 → "Usually replies in 2 min"; null when unknown. */
fun replyLabel(seconds: Double?): String? =
    seconds?.takeIf { it.isFinite() }?.let { "Usually replies in ${Format.duration(it.roundToLong())}" }

fun ratingText(average: Double, count: Int): String {
    if (count == 0) return "New on Hafi Yanjye"
    val rating = String.format(Locale.US, "%.1f", average)
    return "$rating ($count ${if (count == 1) "review" else "reviews"})"
}

/** Where a price sits next to the budget. */
enum class VsBudget { Within, Above }

data class PriceInfo(val text: String, val vsBudget: VsBudget?)

/**
 * What someone asks for, next to the budget. Prices are bargained, so a
 * profile price is shown as where it starts, never as a ceiling; an hourly or
 * monthly rate is not compared with a job budget.
 */
fun priceInfo(quoteRwf: Int?, priceMinRwf: Int?, priceMaxRwf: Int?, unit: PriceUnit, budgetRwf: Int?): PriceInfo? {
    fun against(amount: Int) = budgetRwf?.let { if (amount <= it) VsBudget.Within else VsBudget.Above }
    if (quoteRwf != null) return PriceInfo("Quote ${Format.rwf(quoteRwf)}", against(quoteRwf))
    val start = priceMinRwf ?: priceMaxRwf ?: return null
    val vsBudget = if (unit == PriceUnit.Job) against(start) else null
    return PriceInfo("From ${Format.rwf(start)} ${unit.short}", vsBudget)
}

const val MinBudgetRwf = 1_000
const val MaxBudgetRwf = 10_000_000

/** What the budget sheet accepts: the engine takes 1,000 to 10,000,000 RWF. */
fun parseBudget(typed: String): Int? =
    typed.filter(Char::isDigit).take(9).toIntOrNull()?.takeIf { it in MinBudgetRwf..MaxBudgetRwf }

val StarWords = listOf("", "Poor", "Okay", "Good", "Great", "Excellent")

/** Reasons offered when moving on from a match (the web's chips). */
val RejectReasons = listOf("Not answering", "Price too high", "Not available when I need", "Didn't feel right")

/**
 * Whether turning the pick down shows someone else straight away: the engine
 * takes the next one from the backups and everyone else who said yes.
 */
fun hasNextInLine(session: MatchSession): Boolean =
    session.backups.isNotEmpty() || session.alternatives.isNotEmpty()

/** The one line under "Keep looking". */
fun keepLookingSummary(others: Int, waiting: Int): String = when {
    waiting > 0 -> "Asking $waiting more ${people(waiting)}… $others said yes so far"
    others == 0 -> "Nobody else has said yes yet · ask more people"
    else -> "$others ${if (others == 1) "other person" else "other people"} said yes · tap one to compare or choose"
}

// ── People on the live map: never at a real spot ──────────────────────────

/** How someone on the map relates to this request. */
enum class PersonState { Pinged, Available, Declined, Chosen }

/** Someone drawn on the map; [distanceKm] is null when the engine gave none. */
data class LivePerson(
    val id: String,
    val state: PersonState,
    val distanceKm: Double?,
    val label: String? = null,
)

/**
 * Everyone pinged so far as anonymous people: those who said yes first (index
 * = position in `replies`), then "not now", then people who haven't answered.
 */
fun peopleItems(pinged: Int, declined: Int, replies: List<Reply>): List<LivePerson> {
    val total = max(pinged, replies.size + declined)
    return List(total) { index ->
        val reply = replies.getOrNull(index)
        val state = when {
            reply != null -> PersonState.Available
            index < replies.size + declined -> PersonState.Declined
            else -> PersonState.Pinged
        }
        LivePerson("p-$index", state, reply?.distanceKm)
    }
}

/** A radius that fits the pick and everyone else who said yes, so nobody bunches on the pin. */
fun fitRadiusKm(distancesKm: List<Double>): Double = max(2.0, ceil((distancesKm + 1.0).max() * 1.25))

/**
 * A spot for each person around [center]: at their reported distance (kept
 * inside the circle), else somewhere inside it, in a direction that comes from
 * [seed] and their place in the list. The same inputs always give the same
 * spots, so markers do not jump between refreshes, and no spot is real.
 */
fun scatterAround(seed: Int, center: GeoPoint, radiusKm: Double, people: List<LivePerson>): List<GeoPoint> {
    val circle = if (radiusKm > 0) radiusKm else 1.0
    return people.mapIndexed { index, person ->
        val noise = unitNoise(seed, index)
        val bearing = (index * GoldenAngle + noise * 40.0) * PI / 180.0
        val km = person.distanceKm?.coerceIn(0.15, circle * 0.95) ?: (circle * (0.3 + 0.6 * noise))
        val dLat = km / KmPerDegree * cos(bearing)
        val dLng = km / (KmPerDegree * cos(center.lat * PI / 180.0)) * sin(bearing)
        GeoPoint(center.lat + dLat, center.lng + dLng)
    }
}

/** A zoom that fits a circle of this radius in a card-sized map. */
fun zoomFor(radiusKm: Double): Float {
    if (radiusKm <= 0) return 15f
    return (14.5 - ln(radiusKm) / ln(2.0)).roundToInt().toFloat().coerceIn(10f, 16f)
}

private const val GoldenAngle = 137.508
private const val KmPerDegree = 111.32

/** 0 ≤ n < 1, fixed for a (seed, index) pair. */
private fun unitNoise(seed: Int, index: Int): Double {
    var x = seed * 374_761_393 + index * 668_265_263
    x = (x xor (x ushr 13)) * 1_274_126_177
    x = x xor (x ushr 16)
    return (x.toLong() and 0xFFFF) / 65_536.0
}
