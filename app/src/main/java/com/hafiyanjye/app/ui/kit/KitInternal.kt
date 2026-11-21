package com.hafiyanjye.app.ui.kit

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.ui.theme.Hafi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The kit's one focus treatment (same as HafiButton), so keyboard users see where they are. */
internal fun Modifier.focusRing(focused: Boolean): Modifier =
    if (focused) border(3.dp, Hafi.Accent.copy(alpha = 0.45f)) else this

/** No movement at all when the person turned animations off. */
internal fun <T> motion(reduced: Boolean, millis: Int = 150): AnimationSpec<T> =
    if (reduced) snap() else tween(millis)

/** A 1dp rule on one edge only; `border` would draw all four. */
internal fun Modifier.bottomLine(color: Color = Hafi.Line): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawRect(color, topLeft = Offset(0f, size.height - stroke), size = Size(size.width, stroke))
}

internal fun Modifier.topLine(color: Color = Hafi.Line): Modifier = drawBehind {
    drawRect(color, size = Size(size.width, 1.dp.toPx()))
}

/** A solid five-point star: the Lucide star is an outline and cannot show a half fill. */
internal fun starPath(size: Size): Path {
    val outer = min(size.width, size.height) / 2f
    val inner = outer * 0.42f
    val cx = size.width / 2f
    val cy = size.height / 2f + outer * 0.05f
    return Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = -PI / 2 + i * PI / 5
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
