package com.hafiyanjye.app.ui.find

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.find.FindCategory
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiChip
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.kit.categoryIcon
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/** A section title, read as a heading. */
@Composable
internal fun FindHeading(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = HafiType.Heading, color = Hafi.Fg, modifier = modifier.semantics { heading() })
}

/** A problem under a field, announced at once. */
@Composable
internal fun FieldProblem(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HIcon(R.drawable.ic_triangle_alert, size = 16.dp, tint = Hafi.Danger, modifier = Modifier.padding(top = 1.dp))
        Text(text = message, style = HafiType.Caption, color = Hafi.Danger)
    }
}

/**
 * The big "What do you need?" box (web: SearchBox). Several lines so long
 * descriptions stay readable; the keyboard's search key sends. While empty its
 * placeholder cycles through real service examples, unless motion is reduced.
 */
@Composable
internal fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    examples: List<String> = emptyList(),
    label: String = "What do you need?",
    invalid: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    // Text set from outside (a service picked) puts the cursor at the end, ready for details.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val remaining = MaxRequestLength - value.length

    BasicTextField(
        value = field,
        onValueChange = { next ->
            val capped = if (next.text.length > MaxRequestLength) {
                next.copy(text = next.text.take(MaxRequestLength))
            } else {
                next
            }
            field = capped
            if (capped.text != value) onValueChange(capped.text)
        },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics {
                contentDescription = label
                if (invalid) error("Check what you need")
            },
        textStyle = HafiType.Body.copy(color = Hafi.Fg),
        cursorBrush = SolidColor(Hafi.Accent),
        minLines = 2,
        maxLines = 6,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        interactionSource = interaction,
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hafi.Surface)
                    .border(
                        width = if (focused || invalid) 2.dp else 1.dp,
                        color = when {
                            invalid -> Hafi.Danger
                            focused -> Hafi.Accent
                            else -> Hafi.LineStrong
                        },
                    )
                    .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HIcon(R.drawable.ic_search, tint = Hafi.AccentStrong, modifier = Modifier.padding(top = 2.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box {
                        if (value.isEmpty()) RotatingPlaceholder(examples)
                        inner()
                    }
                    if (remaining < 80) {
                        Text(
                            text = "$remaining",
                            style = HafiType.Caption,
                            color = Hafi.FgSubtle,
                            modifier = Modifier
                                .align(Alignment.End)
                                .semantics { contentDescription = "$remaining characters left" },
                        )
                    }
                }
            }
        },
    )
}

/** "What do you need help with?", then each example in turn; silent for screen readers, which read the label. */
@Composable
private fun RotatingPlaceholder(examples: List<String>) {
    val reduced = rememberReducedMotion()
    val lines = remember(examples) { listOf("What do you need help with?") + examples.map { "e.g. $it" } }
    var index by remember(lines) { mutableIntStateOf(0) }
    LaunchedEffect(lines, reduced) {
        if (reduced || lines.size < 2) return@LaunchedEffect
        while (true) {
            delay(ExampleMillis)
            index = (index + 1) % lines.size
        }
    }
    Crossfade(
        targetState = lines[index % lines.size],
        animationSpec = tween(250),
        label = "search example",
        modifier = Modifier.clearAndSetSemantics { },
    ) { line ->
        Text(text = line, style = HafiType.Body, color = Hafi.FgSubtle, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private const val ExampleMillis = 3_500L

/** "Where · Kimironko": opens the place picker below (web: ContextPill). */
@Composable
internal fun WherePill(
    value: String,
    empty: Boolean,
    open: Boolean,
    attention: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val turn by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(if (rememberReducedMotion()) 0 else 200),
        label = "where chevron",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(
                when {
                    attention -> Hafi.DangerSoft
                    pressed -> Hafi.Surface3
                    else -> Hafi.Surface2
                },
            )
            .border(
                width = if (focused || open || attention) 2.dp else 1.dp,
                color = when {
                    attention -> Hafi.Danger
                    open || focused -> Hafi.Accent
                    else -> Hafi.Line
                },
            )
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                HIcon(R.drawable.ic_map_pin, size = 14.dp, tint = if (empty) Hafi.FgMuted else Hafi.AccentStrong)
                Text(text = "WHERE", style = HafiType.Micro, color = if (empty) Hafi.FgMuted else Hafi.AccentStrong)
            }
            Text(
                text = value,
                style = if (empty) HafiType.Label else HafiType.BodyStrong,
                color = if (empty) Hafi.FgMuted else Hafi.Fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HIcon(R.drawable.ic_chevron_down, size = 18.dp, tint = Hafi.FgMuted, modifier = Modifier.rotate(turn))
    }
}

/** "2 plumbers online within 10 km · usually reply in about 3 min" (web: NearbyLine). Counts only. */
@Composable
internal fun NearbyLineView(line: NearbyLine, modifier: Modifier = Modifier) {
    val frame = modifier
        .fillMaxWidth()
        .heightIn(min = 24.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }
    when (line) {
        NearbyLine.Hidden -> Unit
        NearbyLine.Checking -> Row(
            modifier = frame.semantics(mergeDescendants = true) {
                contentDescription = "Checking who is online nearby…"
            },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Skeleton(Modifier.size(8.dp))
            Skeleton(
                Modifier
                    .fillMaxWidth(0.7f)
                    .heightIn(min = 12.dp),
            )
        }
        is NearbyLine.Shown -> Row(
            modifier = frame,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val someone = line.data.online > 0
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .then(
                        if (someone) {
                            Modifier.background(Hafi.Accent, CircleShape)
                        } else {
                            Modifier.border(1.dp, Hafi.FgSubtle, CircleShape)
                        },
                    ),
            )
            Text(
                text = nearbySentence(line.data, line.hasLocation),
                style = if (someone) HafiType.Label else HafiType.Caption,
                color = if (someone) Hafi.Fg else Hafi.FgMuted,
            )
        }
    }
}

/** One service tile: icon, name and what it covers; picked ones get a tick as well as the green border. */
@Composable
internal fun ServiceCard(
    service: FindCategory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = modifier
            .heightIn(min = 104.dp)
            .background(if (pressed) Hafi.Surface2 else Hafi.Surface)
            .border(
                width = if (selected || focused) 2.dp else 1.dp,
                color = if (selected || focused) Hafi.Accent else Hafi.Line,
            )
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HIcon(categoryIcon(service.icon), size = 22.dp, tint = Hafi.AccentStrong)
            Box(modifier = Modifier.weight(1f))
            if (selected) HIcon(R.drawable.ic_check, size = 18.dp, tint = Hafi.AccentStrong)
        }
        Text(text = service.nameEn, style = HafiType.BodyStrong, color = Hafi.Fg, maxLines = 2)
        Text(text = serviceExample(service.slug), style = HafiType.Caption, color = Hafi.FgMuted, maxLines = 2)
    }
}

/** The green "View more" tile that ends the popular services. */
@Composable
internal fun MoreServicesCard(moreCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = modifier
            .heightIn(min = 104.dp)
            .background(if (pressed) Hafi.AccentStrong else Hafi.Accent)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "View more", style = HafiType.BodyStrong, color = Hafi.AccentFg)
            HIcon(R.drawable.ic_arrow_right, size = 16.dp, tint = Hafi.AccentFg)
        }
        Text(
            text = when {
                moreCount <= 0 -> "All services"
                moreCount == 1 -> "1 more service"
                else -> "$moreCount more services"
            },
            style = HafiType.Caption,
            color = Hafi.AccentSoft,
        )
    }
}

/** A plain tile into the rentals search ("Rent a car"). */
@Composable
internal fun RentalCard(title: String, description: String, icon: Int, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = modifier
            .heightIn(min = 104.dp)
            .background(if (pressed) Hafi.Surface2 else Hafi.Surface)
            .border(if (focused) 2.dp else 1.dp, if (focused) Hafi.Accent else Hafi.Line)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HIcon(icon, size = 22.dp, tint = Hafi.AccentStrong)
        Text(text = title, style = HafiType.BodyStrong, color = Hafi.Fg)
        Text(text = description, style = HafiType.Caption, color = Hafi.FgMuted)
    }
}

/** Budget in RWF with quick picks (web: BudgetInput). Optional; grouped as it is typed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BudgetInput(
    value: Int?,
    onChange: (Int?) -> Unit,
    fromRequest: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HafiField(
            label = "Your budget (RWF)",
            value = value?.let(::groupedAmount).orEmpty(),
            onValueChange = { onChange(budgetFromInput(it)) },
            modifier = Modifier.focusRequester(focusRequester),
            placeholder = "Leave empty if you're not sure",
            hint = if (fromRequest) {
                "Read from what you wrote. Change it if that's not right."
            } else {
                "Between 1,000 and 10,000,000 RWF."
            },
            error = error,
            optional = true,
            leadingIcon = R.drawable.ic_banknote,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        )
        FlowRow(
            modifier = Modifier.semantics { contentDescription = "Quick budget" },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BudgetPresets.forEach { preset ->
                HafiChip(
                    text = groupedAmount(preset),
                    selected = value == preset,
                    onClick = { onChange(if (value == preset) null else preset) },
                )
            }
        }
    }
}
