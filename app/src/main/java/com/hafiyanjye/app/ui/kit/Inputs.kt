package com.hafiyanjye.app.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion

@Composable
private fun FieldLabel(label: String, optional: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HafiType.Label, color = Hafi.Fg)
        if (optional) {
            Text("Optional", style = HafiType.Caption, color = Hafi.FgSubtle, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun FieldError(error: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIcon(R.drawable.ic_circle_alert, size = 14.dp, tint = Hafi.Danger)
        Text(error, style = HafiType.Caption, color = Hafi.Danger)
    }
}

/** HafiField for longer writing. With [maxLength] it counts and never takes more. */
@Composable
fun HafiTextArea(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    hint: String? = null,
    error: String? = null,
    optional: Boolean = false,
    enabled: Boolean = true,
    minLines: Int = 3,
    maxLines: Int = 8,
    maxLength: Int? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = when {
            error != null -> Hafi.Danger
            focused -> Hafi.Accent
            else -> Hafi.LineStrong
        },
        animationSpec = motion(rememberReducedMotion()),
        label = "textarea border",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label, optional)

        BasicTextField(
            value = value,
            // A paste longer than the limit keeps what fits rather than being refused whole.
            onValueChange = { next -> onValueChange(if (maxLength != null) next.take(maxLength) else next) },
            enabled = enabled,
            singleLine = false,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = HafiType.Body.copy(color = Hafi.Fg),
            cursorBrush = SolidColor(Hafi.Accent),
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                    if (error != null) error(error)
                },
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (enabled) Hafi.Surface else Hafi.Surface2)
                        .border(if (focused || error != null) 2.dp else 1.dp, borderColor)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = HafiType.Body, color = Hafi.FgSubtle)
                    }
                    inner()
                }
            },
        )

        if (error != null || hint != null || maxLength != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    when {
                        error != null -> FieldError(error)
                        hint != null -> Text(hint, style = HafiType.Caption, color = Hafi.FgMuted)
                    }
                }
                if (maxLength != null) {
                    Text(
                        text = "${value.length} / $maxLength",
                        style = HafiType.Caption,
                        color = if (value.length >= maxLength) Hafi.Fg else Hafi.FgMuted,
                        modifier = Modifier.semantics {
                            contentDescription = "${value.length} of $maxLength characters"
                        },
                    )
                }
            }
        }
    }
}

/** A setting that is on or off. Tapping anywhere on the row flips it. */
@Composable
fun HafiSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val reduced = rememberReducedMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val track by animateColorAsState(
        if (checked) Hafi.Accent else Hafi.LineStrong,
        motion(reduced),
        label = "switch track",
    )
    val thumbX by animateDpAsState(if (checked) 23.dp else 3.dp, motion(reduced), label = "switch thumb")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .background(if (pressed) Hafi.Surface2 else Color.Transparent)
            .focusRing(focused)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = HafiType.Label, color = Hafi.Fg)
            if (description != null) Text(description, style = HafiType.Caption, color = Hafi.FgMuted)
        }
        // Square track, round thumb; the tick and the thumb's side say "on" without colour.
        Box(Modifier.size(width = 48.dp, height = 28.dp).background(track)) {
            Box(
                modifier = Modifier
                    .offset(x = thumbX, y = 3.dp)
                    .size(22.dp)
                    .background(Hafi.Surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) HIcon(R.drawable.ic_check, size = 14.dp, tint = Hafi.Accent)
            }
        }
    }
}

/** Two to four choices side by side, one always chosen. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reduced = rememberReducedMotion()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .background(Hafi.Surface)
            .border(1.dp, Hafi.LineStrong)
            .padding(1.dp)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val focused by interaction.collectIsFocusedAsState()
            val fill by animateColorAsState(
                targetValue = when {
                    selected && pressed -> Hafi.AccentStrong
                    selected -> Hafi.Accent
                    pressed -> Hafi.Surface2
                    else -> Hafi.Surface
                },
                animationSpec = motion(reduced),
                label = "segment fill",
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .background(fill)
                    .focusRing(focused)
                    .selectable(
                        selected = selected,
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(index) },
                    )
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) HIcon(R.drawable.ic_check, size = 14.dp, tint = Hafi.AccentFg)
                Text(
                    text = option,
                    style = HafiType.Label.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (selected) Hafi.AccentFg else Hafi.FgMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One choice from a list: a field-shaped button that opens the options in a sheet. */
@Composable
fun HafiSelect(
    label: String,
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Choose",
    error: String? = null,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val chosen = selectedIndex?.let { options.getOrNull(it) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .background(if (pressed) Hafi.Surface2 else Hafi.Surface)
                .border(
                    width = if (focused || error != null) 2.dp else 1.dp,
                    color = when {
                        error != null -> Hafi.Danger
                        focused -> Hafi.Accent
                        else -> Hafi.LineStrong
                    },
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.DropdownList,
                    onClick = { open = true },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    stateDescription = chosen ?: "Nothing chosen"
                    if (error != null) error(error)
                }
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = chosen ?: placeholder,
                style = HafiType.Body,
                color = if (chosen != null) Hafi.Fg else Hafi.FgSubtle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clearAndSetSemantics { },
            )
            HIcon(R.drawable.ic_chevron_down, size = 18.dp, tint = Hafi.FgMuted)
        }
        if (error != null) FieldError(error)
    }

    if (open) {
        HafiSheet(onDismiss = { open = false }, title = label) {
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                options.forEachIndexed { index, option ->
                    val selected = index == selectedIndex
                    val rowInteraction = remember { MutableInteractionSource() }
                    val rowPressed by rowInteraction.collectIsPressedAsState()
                    val rowFocused by rowInteraction.collectIsFocusedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .background(
                                when {
                                    rowPressed -> Hafi.Surface2
                                    selected -> Hafi.AccentSoft
                                    else -> Color.Transparent
                                },
                            )
                            .focusRing(rowFocused)
                            .selectable(
                                selected = selected,
                                interactionSource = rowInteraction,
                                indication = null,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(index)
                                    open = false
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option,
                            style = if (selected) HafiType.BodyStrong else HafiType.Body,
                            color = if (selected) Hafi.AccentStrong else Hafi.Fg,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) HIcon(R.drawable.ic_check, size = 18.dp, tint = Hafi.AccentStrong)
                    }
                }
            }
        }
    }
}

/** A value on a scale (a distance, a price), with its name and current reading above the track. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HafiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    label: String? = null,
    valueText: String? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f

    Column(modifier = modifier.alpha(if (enabled) 1f else 0.5f)) {
        if (label != null || valueText != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label.orEmpty(), style = HafiType.Label, color = Hafi.Fg, modifier = Modifier.weight(1f))
                if (valueText != null) Text(valueText, style = HafiType.Label, color = Hafi.AccentStrong)
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics {
                    if (label != null) contentDescription = label
                    if (valueText != null) stateDescription = valueText
                },
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            interactionSource = interaction,
            thumb = { _ ->
                Box(
                    Modifier
                        .size(width = 20.dp, height = 28.dp)
                        .background(if (pressed) Hafi.AccentStrong else Hafi.Accent)
                        .border(2.dp, Hafi.Surface)
                        .focusRing(focused),
                )
            },
            track = { _ ->
                Box(Modifier.fillMaxWidth().height(6.dp).background(Hafi.Surface3)) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Hafi.Accent))
                }
            },
        )
    }
}
