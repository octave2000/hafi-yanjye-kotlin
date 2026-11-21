package com.hafiyanjye.app.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion
import kotlin.math.roundToInt

/** A white bordered block that groups one topic on a page. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
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
        if (title != null || action != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title.orEmpty(),
                    style = HafiType.Subheading,
                    modifier = Modifier.weight(1f).semantics { if (title != null) heading() },
                )
                action?.invoke()
            }
        }
        content()
    }
}

/**
 * One line in a list or menu. With [onClick] it is a button and shows a
 * chevron (unless [trailing] is given); with null it only displays.
 */
@Composable
fun ListRow(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(if (pressed) Hafi.Surface2 else Color.Transparent)
            .focusRing(focused)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) HIcon(icon, size = 20.dp, tint = Hafi.AccentStrong)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = HafiType.Label, color = Hafi.Fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = HafiType.Caption,
                    color = Hafi.FgMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> HIcon(R.drawable.ic_chevron_right, size = 18.dp, tint = Hafi.FgSubtle)
        }
    }
}

/** One number that matters, with what it counts. Read as a single phrase. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(
        modifier = modifier
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label.uppercase(),
            style = HafiType.Micro,
            color = Hafi.FgMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(value, style = HafiType.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (hint != null) Text(hint, style = HafiType.Caption, color = Hafi.FgMuted)
    }
}

/** How far along something is, 0 to 1. The percentage is written when there is a [label]. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, label: String? = null) {
    val target = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, motion(rememberReducedMotion(), 300), label = "progress")
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, style = HafiType.Caption, color = Hafi.FgMuted, modifier = Modifier.weight(1f))
                Text("${(target * 100).roundToInt()}%", style = HafiType.Caption, color = Hafi.Fg)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Hafi.Surface3)
                .progressSemantics(target),
        ) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(Hafi.Accent))
        }
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Hafi.Line))
}
