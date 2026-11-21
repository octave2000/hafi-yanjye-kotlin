package com.hafiyanjye.app.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

enum class ButtonKind { Accent, Secondary, Outline, Ghost, Destructive }

enum class ButtonSize(val height: Dp, val padding: Dp) {
    Medium(48.dp, 18.dp),
    Large(54.dp, 24.dp),
}

private data class ButtonColors(val fill: Color, val pressed: Color, val text: Color, val border: Color)

private fun colorsFor(kind: ButtonKind) = when (kind) {
    ButtonKind.Accent -> ButtonColors(Hafi.Accent, Hafi.AccentStrong, Hafi.AccentFg, Color.Transparent)
    ButtonKind.Secondary -> ButtonColors(
        Hafi.AccentSoft,
        Hafi.Surface3,
        Hafi.AccentStrong,
        Hafi.Accent.copy(alpha = 0.25f),
    )
    ButtonKind.Outline -> ButtonColors(Hafi.Surface, Hafi.Surface2, Hafi.Fg, Hafi.LineStrong)
    ButtonKind.Ghost -> ButtonColors(Color.Transparent, Hafi.Surface2, Hafi.AccentStrong, Color.Transparent)
    // Solid red with white text: every Sign out button.
    ButtonKind.Destructive -> ButtonColors(Hafi.Danger, Hafi.DangerStrong, Hafi.AccentFg, Color.Transparent)
}

/**
 * The app's button: square, flat, green by default. While [loading] it keeps
 * its width, shows a spinner and ignores taps.
 */
@Composable
fun HafiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Accent,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String? = null,
    @DrawableRes icon: Int? = null,
) {
    val colors = colorsFor(kind)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val fill by animateColorAsState(
        targetValue = if (pressed) colors.pressed else colors.fill,
        animationSpec = tween(150),
        label = "button fill",
    )
    val active = enabled && !loading

    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = size.height)
            .alpha(if (enabled) 1f else 0.5f)
            .background(fill)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Hafi.Accent.copy(alpha = 0.45f) else colors.border,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = active,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { if (loading) stateDescription = "Loading" }
            .padding(horizontal = size.padding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.text,
                trackColor = Color.Transparent,
                strokeWidth = 2.dp,
            )
        } else if (icon != null) {
            HIcon(icon, size = 18.dp, tint = colors.text)
        }
        Text(
            text = if (loading && loadingText != null) loadingText else text,
            style = HafiType.Button,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
