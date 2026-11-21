package com.hafiyanjye.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.ProgressBar
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** Numbered steps (web: StepIndicator): done steps show a check, the current one is named. */
@Composable
internal fun StepIndicator(
    current: SetupStep,
    canJumpTo: (SetupStep) -> Boolean,
    onStepClick: (SetupStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "STEP ${current.ordinal + 1} OF ${SetupStep.entries.size} · ${current.title.uppercase()}",
            style = HafiType.Micro,
            color = Hafi.FgMuted,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            SetupStep.entries.forEach { step ->
                StepDot(
                    step = step,
                    current = current,
                    jumpable = step != current && canJumpTo(step),
                    onClick = { onStepClick(step) },
                )
                if (step.next != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 2.dp, max = 2.dp)
                            .background(if (step < current) Hafi.Accent else Hafi.Line),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepDot(step: SetupStep, current: SetupStep, jumpable: Boolean, onClick: () -> Unit) {
    val done = step < current
    val active = step == current
    val state = when {
        done -> "done"
        active -> "current step"
        else -> "not started"
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(48.dp)
            .then(
                if (jumpable) {
                    Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = "Step ${step.ordinal + 1}: ${step.title}"
                stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    when {
                        done -> Hafi.Accent
                        active -> Hafi.AccentSoft
                        else -> Hafi.Surface
                    },
                )
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (done || active || focused) Hafi.Accent else Hafi.LineStrong,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                HIcon(R.drawable.ic_check, size = 16.dp, tint = Hafi.AccentFg)
            } else {
                Text(
                    text = "${step.ordinal + 1}",
                    style = HafiType.Label,
                    color = if (active) Hafi.AccentStrong else Hafi.FgMuted,
                )
            }
        }
    }
}

/** Profile strength bar with its stage in words and, on the last step, what would raise it. */
@Composable
internal fun CompletenessMeter(completeness: Completeness, showTips: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Profile strength", style = HafiType.BodyStrong, modifier = Modifier.weight(1f))
            Text("${completeness.score}%", style = HafiType.Subheading)
            Text(" · ${completeness.stage}", style = HafiType.Caption, color = Hafi.FgMuted)
        }
        ProgressBar(fraction = completeness.score / 100f)
        Text(
            text = "Ready from ${Completeness.READY_SCORE}% · Strong from ${Completeness.STRONG_SCORE}%",
            style = HafiType.Caption,
            color = Hafi.FgSubtle,
        )
        if (showTips) {
            completeness.tips.take(MAX_TIPS).forEach { tip ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HIcon(R.drawable.ic_lightbulb, size = 16.dp, tint = Hafi.AccentStrong)
                    Text(tip, style = HafiType.Caption, color = Hafi.FgMuted)
                }
            }
        }
    }
}

/** A labelled group (a fieldset on the web): a heading and an optional quieter note. */
@Composable
internal fun GroupTitle(title: String, note: String? = null) {
    Row(modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(title, style = HafiType.BodyStrong)
        if (note != null) Text(" $note", style = HafiType.Body, color = Hafi.FgMuted)
    }
}

/**
 * A square toggle tile with a title and a quieter second line: services and
 * languages. Selection shows as a check and a thicker border, never colour alone.
 */
@Composable
internal fun ToggleTile(
    title: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Int? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .heightIn(min = if (subtitle != null) 64.dp else 48.dp)
            .background(if (selected) Hafi.AccentSoft else Hafi.Surface)
            .border(
                width = if (selected || focused) 2.dp else 1.dp,
                color = when {
                    selected || focused -> Hafi.Accent
                    else -> Hafi.Line
                },
            )
            .toggleable(
                value = selected,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shownIcon = if (selected) R.drawable.ic_check else icon
        if (shownIcon != null) {
            HIcon(
                id = shownIcon,
                size = 20.dp,
                tint = when {
                    !enabled -> Hafi.FgSubtle
                    selected -> Hafi.AccentStrong
                    else -> Hafi.FgMuted
                },
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = HafiType.Label.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) Hafi.Fg else Hafi.FgSubtle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = HafiType.Caption,
                    color = if (enabled) Hafi.FgMuted else Hafi.FgSubtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Two tiles per row, as the web's grid on a phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TileGrid(content: @Composable (Modifier) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        content(Modifier.weight(1f))
    }
}

/** One line of the last step's summary, with a 48dp "Edit" that jumps back to its step. */
@Composable
internal fun SummaryRow(label: String, value: String, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = HafiType.Caption, color = Hafi.FgMuted, modifier = Modifier.width(104.dp))
        Text(
            text = value,
            style = HafiType.Label,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        HafiIconButton(
            icon = R.drawable.ic_pencil,
            label = "Edit ${label.lowercase()}",
            onClick = onEdit,
            tint = Hafi.FgMuted,
        )
    }
}

private const val MAX_TIPS = 3
