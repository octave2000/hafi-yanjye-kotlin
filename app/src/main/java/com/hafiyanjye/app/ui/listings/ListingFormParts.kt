package com.hafiyanjye.app.ui.listings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** One titled block of the listing form (web: `Section`). */
@Composable
internal fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = HafiType.Heading,
                color = Hafi.Fg,
                modifier = Modifier.semantics { heading() },
            )
            if (description != null) Text(description, style = HafiType.Caption, color = Hafi.FgMuted)
        }
        content()
    }
}

/** A label over a control, with its error or hint below (web: `Field`). */
@Composable
internal fun LabelledField(
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    hint: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = HafiType.Label, color = Hafi.Fg)
        content()
        FieldMessage(error = error, hint = hint)
    }
}

/** An error says so with an icon and words, not only red; it is read out when it appears. */
@Composable
internal fun FieldMessage(error: String?, hint: String? = null) {
    if (error != null) {
        Row(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HIcon(R.drawable.ic_triangle_alert, size = 16.dp, tint = Hafi.Danger)
            Text(error, style = HafiType.Caption, color = Hafi.Danger)
        }
    } else if (hint != null) {
        Text(hint, style = HafiType.Caption, color = Hafi.FgMuted)
    }
}

/** One option of a [ChoiceRow]. */
internal data class Choice<T>(val value: T, val label: String)

/**
 * A row of square choice buttons with radio semantics (web: `Choice`). The
 * chosen one has a thicker green border and a check, so it is not told by colour alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceRow(
    label: String,
    value: T,
    options: List<Choice<T>>,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = modifier
            .semantics { contentDescription = label }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val selected = option.value == value
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .background(if (selected) Hafi.AccentSoft else Hafi.Surface)
                    .border(if (selected) 2.dp else 1.dp, if (selected) Hafi.Accent else Hafi.LineStrong)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onChange(option.value) },
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (selected) HIcon(R.drawable.ic_check, size = 16.dp, tint = Hafi.AccentStrong)
                Text(option.label, style = HafiType.Label, color = if (enabled) Hafi.Fg else Hafi.FgMuted)
            }
        }
    }
}

/** A large radio tile for choosing car or home on a new listing. */
@Composable
internal fun KindTile(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(if (selected) Hafi.AccentSoft else Hafi.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) Hafi.Accent else Hafi.LineStrong)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HIcon(icon, size = 24.dp, tint = Hafi.AccentStrong)
            Box(Modifier.weight(1f))
            if (selected) HIcon(R.drawable.ic_check, size = 20.dp, tint = Hafi.AccentStrong)
        }
        Text(title, style = HafiType.Subheading, color = Hafi.Fg)
        Text(description, style = HafiType.Caption, color = Hafi.FgMuted)
    }
}

/** − value + for a small count (web: `Stepper`). The value is read out as it changes. */
@Composable
internal fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    format: (Int) -> String = Int::toString,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.LineStrong),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HafiIconButton(
            icon = R.drawable.ic_minus,
            label = "Fewer ${label.lowercase()}",
            onClick = { onChange((value - 1).coerceIn(range)) },
            enabled = enabled && value > range.first,
        )
        Text(
            text = format(value),
            style = HafiType.BodyStrong,
            color = Hafi.Fg,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = "$label: ${format(value)}"
                    liveRegion = LiveRegionMode.Polite
                },
        )
        HafiIconButton(
            icon = R.drawable.ic_plus,
            label = "More ${label.lowercase()}",
            onClick = { onChange((value + 1).coerceIn(range)) },
            enabled = enabled && value < range.last,
        )
    }
}
