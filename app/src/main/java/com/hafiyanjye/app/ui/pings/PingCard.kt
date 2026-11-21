package com.hafiyanjye.app.ui.pings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.api.Importance
import com.hafiyanjye.app.data.api.JobRequirement
import com.hafiyanjye.app.data.api.Ping
import com.hafiyanjye.app.ui.kit.Badge
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.categoryIcon
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import java.time.Instant

/** Whole seconds until [expiresAt], counted down on the phone once a second until it reaches zero. */
@Composable
internal fun rememberSecondsLeft(expiresAt: Instant): Long {
    var left by remember(expiresAt) { mutableLongStateOf(Format.secondsUntil(expiresAt)) }
    LaunchedEffect(expiresAt) {
        while (true) {
            left = Format.secondsUntil(expiresAt)
            if (left <= 0) break
            delay(1_000)
        }
    }
    return left
}

/** The badges of a ping: asked for you, where it stands, how soon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PingBadges(ping: Ping, view: PingView, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (ping.direct) Badge("Direct hire request", Tone.Accent)
        Badge(view.label, view.tone, live = view == PingView.New)
        Badge(urgencyLabel(ping.urgency), urgencyTone(ping.urgency))
    }
}

/** "Must: …" stands out by weight and border, not by colour alone. */
@Composable
internal fun RequirementChip(requirement: JobRequirement) {
    val must = requirement.importance == Importance.Must
    Text(
        text = (if (must) "Must: " else "Nice: ") + requirement.label,
        style = HafiType.Caption.copy(fontWeight = if (must) FontWeight.SemiBold else FontWeight.Normal),
        color = if (must) Hafi.Fg else Hafi.FgMuted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(if (must) Hafi.Surface2 else Hafi.Surface)
            .border(1.dp, if (must) Hafi.LineStrong else Hafi.Line)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * Tappable summary of a job ping. Opening it is the screen's job (and marks it
 * seen). [fresh] tints a ping that just arrived for a moment.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PingCard(
    ping: Ping,
    icon: String?,
    onOpen: (Ping) -> Unit,
    modifier: Modifier = Modifier,
    fresh: Boolean = false,
) {
    val left = rememberSecondsLeft(ping.expiresAt)
    val view = pingView(ping, left)
    val lastMinute = isLastMinute(view, left)
    val reducedMotion = rememberReducedMotion()
    val fill by animateColorAsState(
        targetValue = if (fresh) Hafi.AccentSoft else Hafi.Surface,
        animationSpec = tween(if (reducedMotion) 0 else 180),
        label = "new ping",
    )
    val border = when {
        fresh || view == PingView.Chosen || (ping.direct && view.answerable) -> Hafi.Accent
        view == PingView.New -> Hafi.Accent.copy(alpha = 0.6f)
        else -> Hafi.Line
    }
    val strong = view == PingView.New || view == PingView.Chosen

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(fill)
            .border(1.dp, border)
            .clickable(onClickLabel = "Open this ping", role = Role.Button) { onOpen(ping) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            HIcon(
                categoryIcon(icon),
                size = 24.dp,
                tint = if (strong) Hafi.AccentStrong else Hafi.FgMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PingBadges(ping, view)
                Text(ping.categoryName.uppercase(), style = HafiType.Micro, color = Hafi.FgMuted)
                Text(ping.summary, style = HafiType.Subheading, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            HIcon(
                R.drawable.ic_chevron_right,
                size = 20.dp,
                tint = Hafi.FgSubtle,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        if (ping.requirements.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ping.requirements.take(4).forEach { RequirementChip(it) }
                if (ping.requirements.size > 4) {
                    Text(
                        "+${ping.requirements.size - 4} more",
                        style = HafiType.Caption,
                        color = Hafi.FgMuted,
                        modifier = Modifier.padding(vertical = 5.dp),
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val budget = Format.budget(ping.budgetRwf)
            Fact(R.drawable.ic_wallet, budget, strong = true, spoken = "Budget: $budget")
            Fact(R.drawable.ic_navigation, "${Format.km(ping.distanceKm)} away")
            Fact(R.drawable.ic_map_pin, ping.areaLabel ?: "Kigali")
            if (view.answerable) {
                Fact(
                    R.drawable.ic_clock,
                    "${formatCountdown(left)} left",
                    strong = lastMinute,
                    danger = lastMinute,
                    spoken = "${spokenCountdown(left)} left to answer",
                )
            }
            val quote = ping.quoteRwf
            if (view == PingView.Waiting && quote != null) {
                Text("You quoted ${Format.rwf(quote)}", style = HafiType.Caption, color = Hafi.FgMuted)
            }
        }
    }
}

@Composable
private fun Fact(icon: Int, text: String, strong: Boolean = false, danger: Boolean = false, spoken: String? = null) {
    val ink = when {
        danger -> Hafi.Danger
        strong -> Hafi.Fg
        else -> Hafi.FgMuted
    }
    Row(
        modifier = if (spoken == null) {
            Modifier
        } else {
            Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIcon(icon, size = 16.dp, tint = ink)
        Text(
            text,
            style = HafiType.Caption.copy(fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
