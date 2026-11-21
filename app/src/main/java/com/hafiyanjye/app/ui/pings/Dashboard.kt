package com.hafiyanjye.app.ui.pings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.data.pings.Availability
import com.hafiyanjye.app.data.pings.LevelRequirement
import com.hafiyanjye.app.data.pings.RankingBreakdown
import com.hafiyanjye.app.data.pings.RankingPart
import com.hafiyanjye.app.data.pings.Review
import com.hafiyanjye.app.data.pings.StarterPath
import com.hafiyanjye.app.data.pings.WeekStats
import com.hafiyanjye.app.data.pings.WorkLevel
import com.hafiyanjye.app.data.pings.WorkProfile
import com.hafiyanjye.app.data.pings.WorkStats
import com.hafiyanjye.app.ui.kit.Avatar
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.Divider
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiSwitchRow
import com.hafiyanjye.app.ui.kit.ProgressBar
import com.hafiyanjye.app.ui.kit.StarRating
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import kotlin.math.roundToInt

/* The work dashboard around the pings: every number is the engine's own. */

/** A white bordered block with a small upper-case heading. */
@Composable
internal fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) HIcon(icon, size = 16.dp, tint = Hafi.AccentStrong)
            Text(
                title.uppercase(),
                style = HafiType.Micro,
                color = Hafi.FgMuted,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            trailing?.invoke()
        }
        content()
    }
}

// ── Online / offline ────────────────────────────────────────────────────────

/** One switch (no "busy"). The state is in the icon and the words, not only the switch. */
@Composable
internal fun AvailabilityControl(
    value: Availability,
    pending: Boolean,
    onChange: (Availability) -> Unit,
    modifier: Modifier = Modifier,
) {
    val online = value == Availability.Online
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, if (online) Hafi.Accent else Hafi.LineStrong)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIcon(
            if (online) R.drawable.ic_radio else R.drawable.ic_moon,
            size = 24.dp,
            tint = if (online) Hafi.AccentStrong else Hafi.FgMuted,
        )
        HafiSwitchRow(
            title = when {
                pending && online -> "Going online…"
                pending -> "Going offline…"
                online -> "You're online"
                else -> "You're offline"
            },
            checked = online,
            onCheckedChange = { onChange(if (it) Availability.Online else Availability.Offline) },
            modifier = Modifier.weight(1f),
            description = if (online) "You'll get job pings from people nearby."
            else "No pings: nobody can send you a job until you go online.",
            enabled = !pending,
        )
    }
}

/** Shown while offline: paused after missed pings, or jobs that went out meanwhile. */
@Composable
internal fun HabitBannerCard(banner: HabitBanner, missed: Int, pending: Boolean, onGoOnline: () -> Unit) {
    val title = if (banner == HabitBanner.Paused) "You're paused" else missedTitle(missed)
    val body = if (banner == HabitBanner.Paused) {
        "We paused you after 3 missed requests in a row so your level stays safe. Go online whenever you're ready."
    } else {
        "Go online when you're free and we'll ping you for the next one."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.AccentSoft)
            .border(1.dp, Hafi.Accent.copy(alpha = 0.3f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HIcon(
                if (banner == HabitBanner.Paused) R.drawable.ic_circle_pause else R.drawable.ic_map_pin,
                size = 24.dp,
                tint = Hafi.AccentStrong,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = HafiType.Subheading, modifier = Modifier.semantics { heading() })
                Text(body, style = HafiType.Caption, color = Hafi.FgMuted)
            }
        }
        HafiButton("Go online", onGoOnline, loading = pending, loadingText = "Going online…")
    }
}

// ── Numbers ─────────────────────────────────────────────────────────────────

@Composable
private fun Tile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    icon: Int? = null,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) HIcon(icon, size = 16.dp, tint = Hafi.AccentStrong)
            Text(label, style = HafiType.Caption, color = Hafi.FgMuted)
        }
        Text(value, style = HafiType.Heading)
        if (hint != null) Text(hint, style = HafiType.Caption, color = Hafi.FgMuted)
    }
}

/** Jobs done, how far they go, and their starting price. */
@Composable
internal fun ServiceCard(profile: WorkProfile) {
    Panel("Your service") {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Jobs done", profile.completedJobs.toString(), Modifier.weight(1f))
            Tile("Radius", "${points(profile.serviceRadiusKm)} km", Modifier.weight(1f))
            Tile(
                "From",
                profile.priceMinRwf?.let { "${Format.rwf(it)} ${profile.priceUnit.short}" } ?: "—",
                Modifier.weight(1f),
            )
        }
        profile.addressLabel?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HIcon(R.drawable.ic_map_pin, size = 16.dp, tint = Hafi.FgMuted, modifier = Modifier.padding(top = 2.dp))
                Text("Based in $it", style = HafiType.Caption, color = Hafi.FgMuted)
            }
        }
    }
}

/** Reply time · answer rate · fast streak, over the last 30 counted pings. */
@Composable
internal fun StatsRow(stats: WorkStats) {
    val reply = stats.replyMedianSec
    val rate = stats.answerRate
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(
            "Reply time",
            reply?.let { Format.duration(it.roundToInt().toLong()) } ?: "—",
            Modifier.weight(1f),
            hint = if (reply != null) "typical" else "no replies yet",
            icon = R.drawable.ic_message_circle_reply,
        )
        Tile(
            "Answer rate",
            rate?.let { "${(it * 100).roundToInt()}%" } ?: "—",
            Modifier.weight(1f),
            hint = "last 30 pings",
            icon = R.drawable.ic_gauge,
        )
        Tile(
            "Fast streak",
            stats.fastStreak.toString(),
            Modifier.weight(1f),
            hint = "best ${stats.bestStreak}",
            icon = R.drawable.ic_zap,
        )
    }
}

/** Level as icon + word, never colour alone. */
@Composable
internal fun LevelBadge(level: WorkLevel, modifier: Modifier = Modifier) {
    val icon = when (level) {
        WorkLevel.Gold -> R.drawable.ic_crown
        WorkLevel.Silver -> R.drawable.ic_medal
        WorkLevel.Bronze -> R.drawable.ic_award
        WorkLevel.New -> R.drawable.ic_sprout
    }
    Row(
        modifier = modifier
            .background(Hafi.Accent)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${level.word} level" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIcon(icon, size = 14.dp, tint = Hafi.AccentFg)
        Text(level.word, style = HafiType.Caption.copy(fontWeight = FontWeight.SemiBold), color = Hafi.AccentFg)
    }
}

@Composable
private fun CheckRow(
    done: Boolean,
    label: String,
    trailing: String? = null,
    current: Boolean = false,
    detail: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            stateDescription = if (done) "done" else if (current) "next" else "not yet"
        },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HIcon(
            if (done) R.drawable.ic_check_circle_2 else R.drawable.ic_circle,
            size = 18.dp,
            tint = if (done || current) Hafi.AccentStrong else Hafi.FgSubtle,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = HafiType.Label.copy(
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    textDecoration = if (done && detail != null) TextDecoration.LineThrough else null,
                ),
                color = if (done && detail != null) Hafi.FgMuted else Hafi.Fg,
            )
            if (detail != null) Text(detail, style = HafiType.Caption, color = Hafi.FgMuted)
        }
        if (trailing != null) Text(trailing, style = HafiType.Caption, color = Hafi.FgMuted, textAlign = TextAlign.End)
    }
}

/** Current level, why it matters, and what is left for the next one. */
@Composable
internal fun LevelCard(level: WorkLevel, nextLevel: WorkLevel?, checklist: List<LevelRequirement>) {
    Panel("Your level", trailing = { LevelBadge(level) }) {
        Text("Higher levels are pinged first for nearby jobs.", style = HafiType.Caption, color = Hafi.FgMuted)
        if (level == WorkLevel.New) {
            Text(
                "Answer your first 10 requests to get your level.",
                style = HafiType.Label,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hafi.Surface2)
                    .padding(12.dp),
            )
        }
        if (nextLevel == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hafi.AccentSoft)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HIcon(R.drawable.ic_trophy, size = 16.dp, tint = Hafi.AccentStrong)
                Text("You're at the top level", style = HafiType.Label, color = Hafi.AccentStrong)
            }
        } else {
            Text("To reach ${nextLevel.word}", style = HafiType.BodyStrong)
            checklist.forEach { CheckRow(it.met, it.label, trailing = it.progress) }
        }
    }
}

/** This week's numbers. Earnings are this person's own quotes, not payouts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekCard(week: WeekStats) {
    Panel("This week", icon = R.drawable.ic_calendar_days) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(
                "Pings received" to week.pingsReceived,
                "Answered" to week.answered,
                "Chosen" to week.chosen,
                "Jobs done" to week.jobsDone,
            ).forEach { (label, value) ->
                Column(Modifier.widthIn(min = 96.dp).semantics(mergeDescendants = true) { }) {
                    Text(label, style = HafiType.Caption, color = Hafi.FgMuted)
                    Text(value.toString(), style = HafiType.Heading)
                }
            }
        }
        Divider()
        val earned = roundedEarnings(week.quotedEarningsRwf)
        Text(
            if (earned != null) "About ${Format.rwf(earned)} from your quotes"
            else "No finished jobs from your quotes yet this week.",
            style = HafiType.Caption,
            color = Hafi.FgMuted,
        )
    }
}

// ── Ranking ─────────────────────────────────────────────────────────────────

/** Step-by-step path for people with fewer than 10 jobs. */
@Composable
internal fun StarterPathCard(starter: StarterPath) {
    val done = starter.steps.count { it.done }
    val current = starter.steps.indexOfFirst { !it.done }
    Panel(
        "Your starter path",
        icon = R.drawable.ic_sprout,
        trailing = { Text("$done of ${starter.steps.size} steps", style = HafiType.Caption, color = Hafi.FgMuted) },
    ) {
        Text(
            text = if (starter.seatEligible) {
                "Starter seats are on. You're asked about nearby jobs even when others are closer. " +
                    "Boost: ${points(starter.boostPoints)} points, ${jobsToGo(starter.jobsLeft)}."
            } else {
                "Starter seats are off. Make your profile Ready to switch them on."
            },
            style = HafiType.Label,
            color = if (starter.seatEligible) Hafi.AccentStrong else Hafi.Fg,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (starter.seatEligible) Hafi.AccentSoft else Hafi.Surface2)
                .padding(12.dp),
        )
        starter.steps.forEachIndexed { index, step ->
            CheckRow(step.done, step.label, current = index == current, detail = step.unlocks)
        }
    }
}

@Composable
private fun PartRow(part: RankingPart) {
    val earned = part.points
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(part.label, style = HafiType.BodyStrong, modifier = Modifier.weight(1f))
            Text(
                if (earned == null) "up to ${points(part.max)}" else "${points(earned)} / ${points(part.max)}",
                style = HafiType.Label,
                color = Hafi.FgMuted,
            )
        }
        if (earned != null) ProgressBar(if (part.max > 0) (earned / part.max).toFloat() else 0f)
        if (part.detail.isNotBlank()) Text(part.detail, style = HafiType.Caption, color = Hafi.FgMuted)
        part.items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(item.label, style = HafiType.Caption, color = Hafi.FgMuted, modifier = Modifier.weight(1f))
                Text("${points(item.points)} / ${points(item.max)}", style = HafiType.Caption, color = Hafi.Fg)
            }
        }
    }
}

private val HOW_RANKING_WORKS = listOf(
    "Every job seeker gets one score out of 100, with the same rules for all. Nobody can pay to rank higher.",
    "Job fit (50) counts most: distance, your price against the budget, saying yes, " +
        "and speaking the job provider's language.",
    "New here? Your rating and reliability start at the platform average, " +
        "a starter boost helps for your first 10 jobs, " +
        "and some places in every round of requests are kept for starters.",
    "Been here long? Jobs done, good reviews and every active month keep adding up, so steady work keeps you ahead.",
    "The job provider always makes the final choice by answering a few quick questions.",
)

/** Fair Rank breakdown: each part, the next steps that raise it, and how it works. */
@Composable
internal fun RankingCard(ranking: RankingBreakdown) {
    var open by rememberSaveable { mutableStateOf(false) }
    Panel("Your ranking") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Points you've earned, the same rules for everyone.",
                style = HafiType.Caption,
                color = Hafi.FgMuted,
                modifier = Modifier.weight(1f),
            )
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) { },
                horizontalAlignment = Alignment.End,
            ) {
                Text("${points(ranking.standingTotal)} / 50", style = HafiType.Heading)
                Text("+ job fit, up to 50", style = HafiType.Caption, color = Hafi.FgMuted)
            }
        }
        // Job fit first: it is half the score and differs for every job.
        ranking.parts.filter { it.points == null }.forEach { PartRow(it) }
        Divider()
        ranking.parts.filter { it.points != null }.forEach { PartRow(it) }

        if (ranking.nextSteps.isNotEmpty()) {
            Divider()
            Text(
                "Your next steps to rank higher",
                style = HafiType.BodyStrong,
                modifier = Modifier.semantics { heading() },
            )
            ranking.nextSteps.forEach { step ->
                Row(
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HIcon(
                        R.drawable.ic_arrow_up_right,
                        size = 16.dp,
                        tint = Hafi.AccentStrong,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(step.label, style = HafiType.Label, modifier = Modifier.weight(1f))
                    Text(
                        "+${points(step.gain)}",
                        style = HafiType.Caption.copy(fontWeight = FontWeight.SemiBold),
                        color = Hafi.AccentStrong,
                        modifier = Modifier
                            .background(Hafi.AccentSoft)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .semantics { contentDescription = "plus ${points(step.gain)} points" },
                    )
                }
            }
        }

        Divider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = if (open) "Hide" else "Show") { open = !open }
                .semantics { stateDescription = if (open) "expanded" else "collapsed" },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HIcon(R.drawable.ic_scale, size = 16.dp, tint = Hafi.AccentStrong)
            Text("How ranking works", style = HafiType.BodyStrong, modifier = Modifier.weight(1f))
            HIcon(
                R.drawable.ic_chevron_down,
                size = 16.dp,
                tint = Hafi.FgMuted,
                modifier = Modifier.rotate(if (open) 180f else 0f),
            )
        }
        if (open) {
            HOW_RANKING_WORKS.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = HafiType.Caption, color = Hafi.FgSubtle)
                    Text(line, style = HafiType.Caption, color = Hafi.FgMuted, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Profile strength with up to three tips, and the way to act on them. */
@Composable
internal fun CompletenessCard(score: Double, tips: List<String>, onImprove: () -> Unit) {
    val clamped = score.roundToInt().coerceIn(0, 100)
    Panel("Complete profiles get picked more") {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$clamped%", style = HafiType.Heading)
            Text("· ${completenessWord(clamped)}", style = HafiType.Label, color = Hafi.FgMuted)
        }
        ProgressBar(clamped / 100f)
        Text("Ready from $READY_SCORE%, Strong from $STRONG_SCORE%.", style = HafiType.Caption, color = Hafi.FgSubtle)
        tips.take(3).forEach { tip ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HIcon(
                    R.drawable.ic_lightbulb,
                    size = 16.dp,
                    tint = Hafi.AccentStrong,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(tip, style = HafiType.Label, color = Hafi.FgMuted, modifier = Modifier.weight(1f))
            }
        }
        HafiButton("Improve my profile", onImprove, kind = ButtonKind.Outline)
    }
}

// ── Reviews ─────────────────────────────────────────────────────────────────

/** One review from a job provider, with who wrote it and when. */
@Composable
internal fun ReviewCard(review: Review) {
    val name = review.reviewerName ?: "A neighbour"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, null, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = HafiType.BodyStrong)
                StarRating(review.rating)
            }
            Text(Format.timeAgo(review.createdAt), style = HafiType.Caption, color = Hafi.FgMuted)
        }
        if (review.text.isNotBlank()) Text(review.text, style = HafiType.Body)
    }
}
