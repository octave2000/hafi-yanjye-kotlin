package com.hafiyanjye.app.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A rating to read: five stars in half steps, then the number, so the stars
 * are never the only carrier. Read out as one phrase.
 */
@Composable
fun StarRating(rating: Double, modifier: Modifier = Modifier, size: Dp = 16.dp, count: Int? = null) {
    val safe = if (rating.isNaN()) 0.0 else rating.coerceIn(0.0, 5.0)
    val halves = (safe * 2).roundToInt()
    val number = String.format(Locale.US, "%.1f", safe)
    val spoken = buildString {
        append("Rated $number out of 5")
        if (count != null) append(if (count == 1) ", 1 review" else ", $count reviews")
    }
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            for (star in 0 until 5) {
                // 1f = full, 0.5f = half, 0f = empty.
                val fill = ((halves - star * 2).coerceIn(0, 2)) / 2f
                Canvas(Modifier.size(size)) {
                    val path = starPath(this.size)
                    drawPath(path, Hafi.Surface3)
                    if (fill > 0f) {
                        clipRect(right = this.size.width * fill) { drawPath(path, Hafi.Star) }
                    }
                }
            }
        }
        Text(number, style = HafiType.Label, color = Hafi.Fg)
        if (count != null) Text("($count)", style = HafiType.Caption, color = Hafi.FgMuted)
    }
}

/**
 * Choosing a rating: five large stars. Chosen ones are filled, the rest are
 * outlines, so the difference is shape as well as colour. 0 means none yet.
 */
@Composable
fun StarInput(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier = modifier.alpha(if (enabled) 1f else 0.5f).selectableGroup()) {
        for (n in 1..5) {
            val filled = n <= value
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val focused by interaction.collectIsFocusedAsState()
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(if (pressed) Hafi.Surface2 else Color.Transparent)
                    .focusRing(focused)
                    .selectable(
                        selected = n == value,
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onChange(n) },
                    )
                    .semantics { contentDescription = if (n == 1) "1 star" else "$n stars" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.padding(8.dp).size(32.dp)) {
                    val path = starPath(size)
                    if (filled) drawPath(path, Hafi.Star)
                    // The yellow alone is too faint on white; the outline keeps the shape clear.
                    drawPath(path, if (filled) Hafi.Warn else Hafi.FgSubtle, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}
