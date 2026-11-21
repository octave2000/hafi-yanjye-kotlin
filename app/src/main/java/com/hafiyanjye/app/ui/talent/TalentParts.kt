package com.hafiyanjye.app.ui.talent

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.talent.TalentLanguageLevel
import com.hafiyanjye.app.data.talent.TalentLimits
import com.hafiyanjye.app.data.talent.TalentRules
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.ProgressBar
import com.hafiyanjye.app.ui.kit.RemoteImage
import com.hafiyanjye.app.ui.kit.focusRing
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion

/** Small capitals above a title. */
@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) HIcon(icon, size = 12.dp, tint = Hafi.AccentStrong)
        Text(text.uppercase(), style = HafiType.Micro, color = Hafi.AccentStrong)
    }
}

/** A white block with a green edge: the one thing to look at next. */
@Composable
internal fun AccentCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .drawBehind { drawRect(Hafi.Accent, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 19.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** The icon in a hairline square that opens a card or a notice. */
@Composable
internal fun IconMark(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .border(1.dp, Hafi.LineStrong),
        contentAlignment = Alignment.Center,
    ) {
        HIcon(icon, size = 20.dp, tint = Hafi.Fg)
    }
}

/** "Open to work": a green square beside the words, so it is not colour alone. */
@Composable
internal fun StatusPill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .border(1.dp, Hafi.Line)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(Hafi.Accent),
        )
        Text("Open to work", style = HafiType.Caption, color = Hafi.Fg)
    }
}

/** The person's photo, or their initials on white. */
@Composable
internal fun Portrait(name: String, photo: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            RemoteImage(photo, contentDescription = null, modifier = Modifier.matchParentSize())
        } else if (name.isNotBlank()) {
            Text(
                text = initialsOf(name),
                style = if (size >= 64.dp) HafiType.Title else HafiType.Subheading,
                color = Hafi.FgMuted,
            )
        } else {
            HIcon(R.drawable.ic_user, size = size / 3, tint = Hafi.FgSubtle)
        }
    }
}

/** "Aline Uwase" → "AU": shown when there is no photo. */
internal fun initialsOf(name: String): String = name
    .trim()
    .split(Regex("\\s+"))
    .filter { it.isNotEmpty() }
    .take(2)
    .joinToString("") { it.take(1).uppercase() }

/** Four bars, filled up to the level. Decorative: the level is always written beside it. */
@Composable
internal fun LevelBars(level: TalentLanguageLevel, modifier: Modifier = Modifier) {
    Row(modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        TalentLanguageLevel.entries.forEach { step ->
            Box(
                Modifier
                    .size(width = 14.dp, height = 5.dp)
                    .background(if (step <= level) Hafi.Fg else Hafi.Line),
            )
        }
    }
}

/** A chip that only shows a word (a skill, a wanted job). */
@Composable
internal fun PlainChip(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    Row(
        modifier = modifier
            .border(1.dp, Hafi.LineStrong)
            .background(Hafi.Surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) HIcon(icon, size = 14.dp, tint = Hafi.FgMuted)
        Text(text, style = HafiType.Label, color = Hafi.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Read-only chips that wrap onto new lines (skills, wanted jobs). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipFlow(items: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { PlainChip(it) }
    }
}

/** A chip that does one thing when tapped: remove itself ([icon] x) or add itself ([icon] plus). */
@Composable
internal fun ActionChip(
    text: String,
    actionLabel: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconFirst: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else 0.5f)
            .heightIn(min = 40.dp)
            .background(if (pressed) Hafi.Surface2 else Hafi.Surface)
            .border(1.dp, Hafi.LineStrong)
            .focusRing(focused)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = actionLabel,
                onClick = onClick,
            )
            .semantics { contentDescription = "$actionLabel $text" }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconFirst) HIcon(icon, size = 14.dp, tint = Hafi.FgMuted)
        Text(
            text = text,
            style = HafiType.Label,
            color = Hafi.Fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (!iconFirst) HIcon(icon, size = 14.dp, tint = Hafi.FgMuted)
    }
}

/** Type a word, press Done or Add, and it joins the list; tap a chip to remove it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipInput(
    label: String,
    value: List<String>,
    onChange: (List<String>) -> Unit,
    max: Int,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    hint: String? = null,
    enabled: Boolean = true,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val full = value.size >= max
    val add = {
        onChange(TalentRules.addChip(value, draft, max))
        draft = ""
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            HafiField(
                label = "$label (${value.size}/$max)",
                value = draft,
                // A comma ends a word, like on the web.
                onValueChange = { typed ->
                    if (typed.endsWith(",")) {
                        draft = typed.dropLast(1)
                        add()
                    } else {
                        draft = typed.take(TalentLimits.Chip)
                    }
                },
                placeholder = if (full) "That's the most you can add" else placeholder,
                enabled = enabled && !full,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            HafiIconButton(
                R.drawable.ic_plus,
                "Add",
                onClick = add,
                enabled = enabled && !full && draft.isNotBlank(),
                modifier = Modifier
                    .padding(bottom = 2.dp)
                    .border(1.dp, Hafi.LineStrong),
            )
        }
        if (hint != null) Text(hint, style = HafiType.Caption, color = Hafi.FgMuted)
        if (value.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                value.forEach { item ->
                    ActionChip(
                        text = item,
                        actionLabel = "Remove",
                        icon = R.drawable.ic_x,
                        onClick = { onChange(value - item) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

/** A suggestion to add with one tap (a skill, a wanted job). */
@Composable
internal fun SuggestChip(text: String, enabled: Boolean = true, onAdd: () -> Unit) {
    ActionChip(
        text = text,
        actionLabel = "Add",
        icon = R.drawable.ic_plus,
        onClick = onAdd,
        iconFirst = true,
        enabled = enabled,
    )
}

/** A 1dp rule along the top edge, like the web's sheet footer. */
internal fun Modifier.topRule(): Modifier = drawBehind {
    drawRect(Hafi.Line, size = Size(size.width, 1.dp.toPx()))
}

/** A year, or a year and month: "2023" or "2023-05". The dash is typed for the person. */
@Composable
internal fun DateField(
    label: String,
    value: String?,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
) {
    HafiField(
        label = label,
        value = value.orEmpty(),
        onValueChange = { onChange(TalentRules.dateInput(it)) },
        placeholder = if (enabled) "2023-05" else "Now",
        enabled = enabled,
        error = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

/** One item of a list being edited (a job, a school, a link) with its move and remove tools. */
@Composable
internal fun RowCard(
    index: Int,
    count: Int,
    heading: String,
    placeholder: String,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Hafi.Surface2)
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "%02d  %s".format(index + 1, heading.ifBlank { placeholder }),
                style = HafiType.Label,
                color = if (heading.isBlank()) Hafi.FgMuted else Hafi.Fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            HafiIconButton(R.drawable.ic_arrow_up, "Move up", { onMove(index, index - 1) }, enabled = index > 0)
            HafiIconButton(
                icon = R.drawable.ic_arrow_down,
                label = "Move down",
                onClick = { onMove(index, index + 1) },
                enabled = index < count - 1,
            )
            HafiIconButton(R.drawable.ic_trash_2, "Remove", onRemove, tint = Hafi.Danger)
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** The list with one item moved; out-of-range moves change nothing. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** The dashed "add one more" row under a list. */
@Composable
internal fun AddRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    HafiButton(
        text = label,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        kind = ButtonKind.Outline,
        icon = R.drawable.ic_plus,
        enabled = enabled,
    )
}

/** What the AI suggests, to accept or dismiss. Nothing changes until Accept. */
@Composable
internal fun Suggestion(onUse: () -> Unit, onDrop: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AccentCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Eyebrow("Suggested", icon = R.drawable.ic_sparkles)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HafiButton("Accept", onUse, kind = ButtonKind.Secondary, icon = R.drawable.ic_check)
            HafiButton("Dismiss", onDrop, kind = ButtonKind.Ghost)
        }
    }
}

/** A list of steps ticking through while the engine works; the bar is how far along. */
@Composable
internal fun StepList(steps: List<String>, current: Int, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ProgressBar(if (current >= steps.size) 1f else (current + 0.6f) / steps.size)
        Column(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            steps.forEachIndexed { i, label ->
                val state = when {
                    i < current -> "done"
                    i == current -> "in progress"
                    else -> "not started"
                }
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) { stateDescription = state },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(16.dp), contentAlignment = Alignment.Center) {
                        when {
                            i < current -> HIcon(R.drawable.ic_check, size = 16.dp, tint = Hafi.Fg)
                            i == current -> Spinner(reduced)
                            else -> Box(
                                Modifier
                                    .size(6.dp)
                                    .background(Hafi.LineStrong),
                            )
                        }
                    }
                    Text(label, style = HafiType.Body, color = if (i > current) Hafi.FgMuted else Hafi.Fg)
                }
            }
        }
    }
}

@Composable
private fun Spinner(reduced: Boolean) {
    if (reduced) {
        HIcon(R.drawable.ic_loader_circle, size = 16.dp, tint = Hafi.Fg)
        return
    }
    val turn by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "spinner turn",
    )
    HIcon(R.drawable.ic_loader_circle, size = 16.dp, tint = Hafi.Fg, modifier = Modifier.rotate(turn))
}

/** A page outline with a green line passing over it, while a CV is being read. Decorative. */
@Composable
internal fun ScanPage(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val sweep = if (reduced) {
        null
    } else {
        rememberInfiniteTransition(label = "scan").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "scan line",
        )
    }
    Column(
        modifier = modifier
            .size(width = 104.dp, height = 136.dp)
            .background(Hafi.Surface)
            .border(1.dp, Hafi.LineStrong)
            .drawBehind {
                val at = sweep?.value ?: return@drawBehind
                drawRect(Hafi.Accent, topLeft = Offset(0f, size.height * at), size = Size(size.width, 1.dp.toPx()))
            }
            .padding(12.dp)
            .clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(20.dp)
                    .border(1.dp, Hafi.LineStrong),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .size(width = 44.dp, height = 6.dp)
                        .background(Hafi.FgMuted),
                )
                Box(
                    Modifier
                        .size(width = 28.dp, height = 4.dp)
                        .background(Hafi.LineStrong),
                )
            }
        }
        listOf(1f, 0.86f, 0.94f, 0.62f, 1f, 0.78f, 0.9f, 0.54f).forEach { width ->
            Box(
                Modifier
                    .fillMaxWidth(width)
                    .height(4.dp)
                    .background(Hafi.Line),
            )
        }
    }
}
