package com.hafiyanjye.app.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion

/**
 * A chip that is on or off (filters, skills, days). On shows a tick as well as
 * the green tint, so it never depends on colour.
 */
@Composable
fun HafiChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val fill by animateColorAsState(
        targetValue = when {
            pressed -> Hafi.Surface3
            selected -> Hafi.AccentSoft
            else -> Hafi.Surface
        },
        animationSpec = motion(rememberReducedMotion()),
        label = "chip fill",
    )
    val ink = if (selected) Hafi.AccentStrong else Hafi.Fg

    Row(
        modifier = modifier
            // 40dp to look at, 48dp to touch.
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else 0.5f)
            .heightIn(min = 40.dp)
            .background(fill)
            .border(if (selected) 2.dp else 1.dp, if (selected) Hafi.Accent else Hafi.LineStrong)
            .focusRing(focused)
            .toggleable(
                value = selected,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The tick takes the icon's place, so a chip with an icon keeps its width.
        if (selected) {
            HIcon(R.drawable.ic_check, size = 16.dp, tint = Hafi.AccentStrong)
        } else if (icon != null) {
            HIcon(icon, size = 16.dp, tint = Hafi.FgMuted)
        }
        Text(
            text = text,
            style = HafiType.Label.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A small label that only informs (a skill, a distance). Not a button. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    Row(
        modifier = modifier
            .background(Hafi.Surface2)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) HIcon(icon, size = 14.dp, tint = Hafi.FgMuted)
        Text(text, style = HafiType.Caption, color = Hafi.FgMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
