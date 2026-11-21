package com.hafiyanjye.app.ui.match

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.data.match.Candidate
import com.hafiyanjye.app.data.match.JobWithSession
import com.hafiyanjye.app.data.match.MatchSession
import com.hafiyanjye.app.data.match.Reply
import com.hafiyanjye.app.data.match.Round
import com.hafiyanjye.app.data.match.RoundOption
import com.hafiyanjye.app.data.match.RoundSource
import com.hafiyanjye.app.data.match.WorkerLevel
import com.hafiyanjye.app.ui.kit.Avatar
import com.hafiyanjye.app.ui.kit.ButtonSize
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.PulseDot
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.kit.Tag
import com.hafiyanjye.app.ui.location.GeoPoint
import com.hafiyanjye.app.ui.location.HafiMap
import com.hafiyanjye.app.ui.location.MapCircle
import com.hafiyanjye.app.ui.location.MapMarker
import com.hafiyanjye.app.ui.location.MarkerKind
import com.hafiyanjye.app.ui.match.RequestViewModel.Actions
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion
import java.util.Locale

// ─── pinging ─────────────────────────────────────────────────────────────────

/** People nearby are being asked: the live map, the counts, the replies and "Shortlist now". */
@Composable
internal fun PingingView(data: JobWithSession, viewModel: RequestViewModel, busy: String?) {
    val (job, session) = data
    val stats = session.stats
    val radiusKm = searchRadiusKm(stats)
    val people = remember(stats.pinged, stats.declined, session.replies) {
        peopleItems(stats.pinged, stats.declined, session.replies)
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Hafi.Line),
        ) {
            PeopleMap(
                seed = session.id,
                center = GeoPoint(job.lat, job.lng),
                radiusKm = radiusKm,
                people = people,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                onPersonClick = { person ->
                    val index = person.id.removePrefix("p-").toIntOrNull()
                    val reply = index?.takeIf { person.state == PersonState.Available }?.let(session.replies::getOrNull)
                    viewModel.open(Overlay.Person(PersonDetails.Anonymous(person.state, reply)))
                },
            )
            MapOverlayChips(session)
        }
        LiveProgress(data, radiusKm, busy, onShortlistNow = { viewModel.shortlistNow(session.id) })
    }
}

/** "Live" and the countdown to the shortlist, over the map. */
@Composable
private fun MapOverlayChips(session: MatchSession) {
    val now by rememberNow()
    val remaining = shortlistSecondsLeft(session, now)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MapChip {
            PulseDot(Hafi.Accent)
            Text("Live", style = HafiType.Caption.copy(fontWeight = HafiType.BodyStrong.fontWeight))
        }
        if (remaining != null) {
            val spoken = if (remaining > 0) "Shortlist in ${Format.duration(remaining)}" else "Shortlist any moment"
            MapChip(Modifier.clearAndSetSemantics { contentDescription = spoken }) {
                HIcon(R.drawable.ic_clock, size = 14.dp, tint = Hafi.FgMuted)
                Text(
                    countdownText(remaining),
                    style = HafiType.Caption.copy(
                        fontWeight = HafiType.BodyStrong.fontWeight,
                        fontFeatureSettings = "tnum",
                    ),
                )
            }
        }
    }
}

@Composable
private fun LiveProgress(data: JobWithSession, radiusKm: Double, busy: String?, onShortlistNow: () -> Unit) {
    val (job, session) = data
    val stats = session.stats
    val plural = categoryNoun(job.category.nameEn, 2)
    Panel {
        PanelHeading(pingingHeading(session))
        Text(
            pingingSubtitle(session, plural),
            style = HafiType.Body,
            color = Hafi.FgMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        replyTimeLine(session, plural)?.let { line -> ReplyTimeNote(line, Modifier.padding(top = 12.dp)) }
        Row(
            modifier = Modifier
                .padding(top = 20.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Counter(R.drawable.ic_radio, stats.pinged, "nearby pinged", Modifier.weight(1f))
            Counter(R.drawable.ic_check, stats.available, "available", Modifier.weight(1f), highlight = true)
            Counter(R.drawable.ic_eye, stats.seen, "viewing", Modifier.weight(1f))
        }
        if (session.replies.isNotEmpty()) {
            ReplyFeed(session.replies, job.category.nameEn, Modifier.padding(top = 20.dp))
        }
        WaveIndicator(stats.wave, radiusKm, Modifier.padding(top = 20.dp))
        HafiButton(
            shortlistNowLabel(stats.available),
            onClick = onShortlistNow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            size = ButtonSize.Large,
            enabled = stats.available >= 1 && busy == null,
            loading = busy == Actions.Shortlist,
            icon = R.drawable.ic_list_checks,
        )
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HIcon(R.drawable.ic_users, size = 14.dp, tint = Hafi.FgMuted, modifier = Modifier.padding(top = 2.dp))
            Text(
                "Nobody sees your number until you choose them. You can leave this screen; we will " +
                    "notify you when your shortlist is ready.",
                style = HafiType.Caption,
                color = Hafi.FgMuted,
            )
        }
    }
}

@Composable
private fun ReplyTimeNote(line: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Hafi.AccentSoft)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HIcon(R.drawable.ic_clock, size = 14.dp, tint = Hafi.AccentStrong, modifier = Modifier.padding(top = 2.dp))
        Text(
            line,
            style = HafiType.Caption.copy(fontWeight = HafiType.BodyStrong.fontWeight),
            color = Hafi.AccentStrong,
        )
    }
}

/**
 * People around the request's pin at roughly their distance, never at a real
 * spot. Those who said yes, and the chosen one, are the highlighted markers.
 */
@Composable
internal fun PeopleMap(
    seed: Int,
    center: GeoPoint,
    radiusKm: Double,
    people: List<LivePerson>,
    modifier: Modifier = Modifier,
    onPersonClick: (LivePerson) -> Unit,
) {
    val markers = remember(seed, center, radiusKm, people) {
        val spots = scatterAround(seed, center, radiusKm, people)
        val others = people.zip(spots) { person, spot ->
            MapMarker(
                id = person.id,
                position = spot,
                kind = MarkerKind.Person,
                title = person.label ?: personStateWords(person.state),
                selected = person.state == PersonState.Available || person.state == PersonState.Chosen,
            )
        }
        listOf(MapMarker("me", center, MarkerKind.Me, title = "Your request")) + others
    }
    HafiMap(
        modifier = modifier,
        center = center,
        zoom = zoomFor(radiusKm),
        markers = markers,
        circles = listOf(MapCircle(center, radiusKm)),
        onMarkerClick = { id -> people.firstOrNull { it.id == id }?.let(onPersonClick) },
    )
}

internal fun personStateWords(state: PersonState): String = when (state) {
    PersonState.Available -> "Said yes to your request"
    PersonState.Declined -> "Said not now"
    PersonState.Pinged -> "Got your request, hasn't answered yet"
    PersonState.Chosen -> "Your match"
}

/** A small white label over a map. */
@Composable
internal fun MapChip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun Counter(
    @DrawableRes icon: Int,
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val on = highlight && value > 0
    Column(
        modifier = modifier
            .background(if (on) Hafi.AccentSoft else Hafi.Surface2)
            .padding(12.dp)
            .clearAndSetSemantics { contentDescription = "$value $label" },
    ) {
        LiveNumber(value, color = if (on) Hafi.AccentStrong else Hafi.Fg)
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HIcon(icon, size = 14.dp, tint = Hafi.FgMuted)
            Text(label, style = HafiType.Caption, color = Hafi.FgMuted)
        }
    }
}

/** A number that rises into place when it changes. */
@Composable
private fun LiveNumber(value: Int, color: Color) {
    val reduced = rememberReducedMotion()
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            if (reduced) {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            } else {
                (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 3 }) togetherWith fadeOut(tween(100))
            }
        },
        label = "live number",
    ) { shown ->
        Text(shown.toString(), style = HafiType.Title.copy(fontFeatureSettings = "tnum"), color = color)
    }
}

/** Anonymous "raised hand" lines, oldest first, newest at the bottom. */
@Composable
private fun ReplyFeed(replies: List<Reply>, categoryName: String, modifier: Modifier = Modifier) {
    val lead = anonymousLead(categoryName)
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        replies.forEach { reply ->
            val quote = reply.quoteRwf?.let(Format::rwf) ?: "quote on visit"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hafi.AccentSoft)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HIcon(
                    R.drawable.ic_hand,
                    size = 16.dp,
                    tint = Hafi.AccentStrong,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    "$lead ${Format.km(reply.distanceKm)} away raised their hand · $quote",
                    style = HafiType.Label,
                    modifier = Modifier.weight(1f),
                )
                if (reply.level == WorkerLevel.Silver || reply.level == WorkerLevel.Gold) LevelBadge(reply.level)
            }
        }
    }
}

/** The three rings of the search: done, now, next. */
@Composable
private fun WaveIndicator(wave: Int, radiusKm: Double, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Looking within ${kmNumber(radiusKm)} km", style = HafiType.Label)
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Waves.forEachIndexed { index, step ->
                val done = wave > step.number
                WaveStep(step.km, done = done, current = wave == step.number)
                if (index < Waves.lastIndex) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (done) Hafi.Accent else Hafi.Line),
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveStep(km: Int, done: Boolean, current: Boolean) {
    val fill = when {
        current -> Hafi.Accent
        done -> Hafi.AccentSoft
        else -> Hafi.Surface2
    }
    val ink = when {
        current -> Hafi.AccentFg
        done -> Hafi.AccentStrong
        else -> Hafi.FgMuted
    }
    val spoken = "$km km, ${if (current) "now" else if (done) "done" else "next"}"
    Row(
        modifier = Modifier
            .background(fill)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (done) HIcon(R.drawable.ic_check, size = 12.dp, tint = ink)
        Text("$km km", style = HafiType.Caption.copy(fontWeight = HafiType.BodyStrong.fontWeight), color = ink)
    }
}

// ─── a direct hire request, waiting ──────────────────────────────────────────

/** A hire request sent to one person from Find help, waiting for their answer. */
@Composable
internal fun DirectWaitingView(data: JobWithSession) {
    val direct = data.session.direct ?: return
    val service = data.job.category.nameEn.lowercase(Locale.ROOT)
    Panel(centered = true, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Box {
            Avatar(
                direct.displayName,
                direct.picture,
                Modifier.border(4.dp, Hafi.Lime, CircleShape),
                size = 96.dp,
            )
            PulseDot(
                Hafi.Accent,
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
            )
        }
        Eyebrow("Hire request sent", Modifier.padding(top = 20.dp), icon = R.drawable.ic_user_check)
        Text(
            "Waiting for ${direct.firstName} to accept",
            style = HafiType.Heading,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 4.dp)
                .semantics { heading() },
        )
        Text(
            "We sent ${direct.displayName} your request for $service. You're hired together as soon as " +
                "${direct.firstName} accepts, and then you can chat or call.",
            style = HafiType.Body,
            color = Hafi.FgMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        IconLine(
            R.drawable.ic_clock,
            "The request waits up to an hour. You can cancel it any time.",
            Modifier.padding(top = 16.dp),
        )
    }
}

// ─── shortlisting ────────────────────────────────────────────────────────────

/** The engine is ranking the people who said yes (usually a few seconds). */
@Composable
internal fun ShortlistingView(session: MatchSession) {
    val available = session.stats.available
    Panel(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HIcon(R.drawable.ic_list_checks, size = 32.dp, tint = Hafi.AccentStrong)
            Column {
                PanelHeading("Weighing up your options…")
                Text(
                    "Comparing $available ${people(available)} on distance, reviews, experience and price.",
                    style = HafiType.Body,
                    color = Hafi.FgMuted,
                )
            }
        }
        Column(
            modifier = Modifier
                .padding(top = 20.dp)
                .clearAndSetSemantics { },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(available.coerceIn(2, 3)) { ShortlistPlaceholder() }
        }
        Text(
            "This usually takes under 15 seconds.",
            style = HafiType.Caption,
            color = Hafi.FgMuted,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun ShortlistPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hafi.Line)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Skeleton(Modifier.size(44.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Skeleton(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(16.dp),
            )
            Skeleton(
                Modifier
                    .fillMaxWidth(0.75f)
                    .height(12.dp),
            )
        }
        Skeleton(
            Modifier
                .width(56.dp)
                .height(28.dp),
        )
    }
}

// ─── eliminating: "which matters more to you?" ───────────────────────────────

/** A few quick questions narrow the shortlist; the people still in the running are shown under them. */
@Composable
internal fun EliminationView(data: JobWithSession, viewModel: RequestViewModel, busy: String?) {
    val (job, session) = data
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        val round = session.round
        if (round != null) {
            RoundCard(session, round, busy, onAnswer = { viewModel.answer(session.id, round.roundNo, it) })
        } else {
            Panel {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Hafi.Accent, strokeWidth = 2.dp)
                    Text("Preparing the next question…", style = HafiType.Body, color = Hafi.FgMuted)
                }
            }
        }
        CandidateStrip(session.candidates, job.budgetRwf)
    }
}

@Composable
private fun RoundCard(session: MatchSession, round: Round, busy: String?, onAnswer: (String) -> Unit) {
    val total = session.roundsPlayed + round.maxRounds
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Question ${session.roundsPlayed + 1} of up to $total", Modifier.weight(1f))
            Row(
                modifier = Modifier.clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(total) { index ->
                    RoundDot(answered = index < session.roundsPlayed, now = index == session.roundsPlayed)
                }
            }
        }
        Text(
            round.question,
            style = HafiType.Title,
            modifier = Modifier
                .padding(top = 12.dp)
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
        )
        Text(
            "Tap what matters more. We keep the people who fit best.",
            style = HafiType.Body,
            color = Hafi.FgMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        Column(
            Modifier
                .padding(top = 20.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            round.options.forEach { option ->
                RoundOptionButton(
                    option = option,
                    chosen = busy == Actions.answer(option.id),
                    dimmed = Actions.isAnswer(busy) && busy != Actions.answer(option.id),
                    enabled = busy == null,
                    onClick = { onAnswer(option.id) },
                )
            }
        }
        Text(
            if (round.source == RoundSource.Ai) "Question written by AI from your shortlist" else "Standard question",
            style = HafiType.Caption,
            color = Hafi.FgSubtle,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** One answer; while it is being sent it shows a spinner and the others fade. */
@Composable
private fun RoundOptionButton(
    option: RoundOption,
    chosen: Boolean,
    dimmed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.5f else 1f)
            .border(2.dp, if (chosen) Hafi.Accent else Hafi.Line)
            .selectable(selected = chosen, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .defaultMinSize(minHeight = 80.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .border(2.dp, if (chosen) Hafi.Accent else Hafi.LineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (chosen) CircularProgressIndicator(Modifier.size(12.dp), color = Hafi.Accent, strokeWidth = 2.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(option.label, style = HafiType.Subheading)
            if (option.description.isNotBlank()) {
                Text(
                    option.description,
                    style = HafiType.Label,
                    color = Hafi.FgMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Tag(
                "${option.keepCount} ${people(option.keepCount)}",
                Modifier.padding(top = 8.dp),
                icon = R.drawable.ic_users,
            )
        }
    }
}

/** Answered questions are dots; the current one is a short bar (square, like everything else). */
@Composable
private fun RoundDot(answered: Boolean, now: Boolean) {
    val shape = if (now) RectangleShape else CircleShape
    val color = when {
        now -> Hafi.AccentStrong
        answered -> Hafi.Accent
        else -> Hafi.LineStrong
    }
    Box(
        Modifier
            .size(width = if (now) 20.dp else 8.dp, height = 8.dp)
            .background(color, shape),
    )
}

/** The remaining candidates, side by side. Someone who drops out fades away rather than vanishing. */
@Composable
private fun CandidateStrip(candidates: List<Candidate>, budgetRwf: Int?) {
    val reduced = rememberReducedMotion()
    Column {
        Text(
            "Still in the running (${candidates.size})",
            style = HafiType.Label.copy(fontWeight = HafiType.BodyStrong.fontWeight),
            modifier = Modifier
                .padding(bottom = 8.dp)
                .semantics { heading() },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
            items(candidates, key = { it.shortId }) { candidate ->
                val motion = if (reduced) {
                    Modifier
                } else {
                    Modifier.animateItem(fadeInSpec = tween(200), fadeOutSpec = tween(200), placementSpec = tween(240))
                }
                CandidateCard(candidate, budgetRwf, motion)
            }
        }
    }
}

/** An anonymous candidate: a letter instead of a name, and the facts that matter for choosing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateCard(candidate: Candidate, budgetRwf: Int?, modifier: Modifier = Modifier) {
    val price = with(candidate) { priceInfo(quoteRwf, priceMinRwf, priceMaxRwf, priceUnit, budgetRwf) }
    Column(
        modifier = modifier
            .width(240.dp)
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CandidateTitle(candidate)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CandidateRating(candidate)
            val years = candidate.yearsExperience
            IconLine(R.drawable.ic_briefcase, "$years ${if (years == 1) "yr" else "yrs"}")
        }
        replyLabel(candidate.replyMedianSec)?.let { IconLine(R.drawable.ic_timer, it) }
        when {
            candidate.hasCertificate -> IconLine(R.drawable.ic_award, "Certified", strong = true)
            candidate.missingCredential -> IconLine(R.drawable.ic_award, "No certificate uploaded")
            candidate.hasCv -> IconLine(R.drawable.ic_file_text, "CV added")
        }
        if (price != null) {
            val versus = when (price.vsBudget) {
                VsBudget.Within -> " · within budget"
                VsBudget.Above -> " · above budget"
                null -> ""
            }
            Text(price.text + versus, style = HafiType.Caption.copy(fontWeight = HafiType.Label.fontWeight))
        }
        if (candidate.reasons.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                candidate.reasons.take(3).forEach { Tag(it) }
            }
        }
    }
}

@Composable
private fun CandidateTitle(candidate: Candidate) {
    val strong = HafiType.Label.copy(fontWeight = HafiType.BodyStrong.fontWeight)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(36.dp)
                .background(Hafi.Surface2)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(candidateLetter(candidate.shortId), style = strong)
        }
        Column(Modifier.weight(1f)) {
            Text(
                candidateLabel(candidate.shortId),
                style = strong,
                modifier = Modifier.semantics { heading() },
            )
            Text("${Format.km(candidate.distanceKm)} away", style = HafiType.Caption, color = Hafi.FgMuted)
        }
        LevelBadge(candidate.level)
    }
}

@Composable
private fun CandidateRating(candidate: Candidate) {
    val rated = candidate.ratingCount > 0
    val average = String.format(Locale.US, "%.1f", candidate.ratingAvg)
    val spoken = if (rated) "Rated $average from ${candidate.ratingCount} reviews" else "No reviews yet"
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HIcon(R.drawable.ic_star, size = 14.dp, tint = Hafi.Star)
        Text(if (rated) "$average (${candidate.ratingCount})" else "New", style = HafiType.Caption)
    }
}
