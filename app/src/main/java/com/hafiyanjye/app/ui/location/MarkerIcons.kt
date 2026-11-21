package com.hafiyanjye.app.ui.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.hafiyanjye.app.R
import com.hafiyanjye.app.ui.theme.Hafi
import kotlin.math.roundToInt

/** Headings are drawn in 16 directions, so a moving car needs 16 bitmaps and not 360. */
internal const val HeadingSteps = 16

internal fun headingStep(heading: Float): Int =
    (heading.mod(360f) / (360f / HeadingSteps)).roundToInt() % HeadingSteps

@DrawableRes
internal fun iconFor(kind: MarkerKind): Int = when (kind) {
    MarkerKind.Pin -> R.drawable.ic_map_pin
    MarkerKind.Me -> R.drawable.ic_map_pin
    MarkerKind.Person -> R.drawable.ic_user_round
    MarkerKind.Car -> R.drawable.ic_car
    MarkerKind.Home -> R.drawable.ic_house
    MarkerKind.Destination -> R.drawable.ic_flag
}

/** What a marker says to screen readers when the caller gave it no title. */
internal fun defaultTitle(kind: MarkerKind): String = when (kind) {
    MarkerKind.Pin -> "Pinned place"
    MarkerKind.Me -> "You are here"
    MarkerKind.Person -> "Someone nearby"
    MarkerKind.Car -> "Car"
    MarkerKind.Home -> "Home"
    MarkerKind.Destination -> "Destination"
}

/** Fill, edge and icon colours of a square marker; the same on the live and the static map. */
internal data class MarkerColors(val fill: Color, val edge: Color, val icon: Color)

internal fun markerColors(selected: Boolean, muted: Boolean): MarkerColors = when {
    selected -> MarkerColors(fill = Hafi.Accent, edge = Hafi.Surface, icon = Hafi.Surface)
    muted -> MarkerColors(fill = Hafi.Surface2, edge = Hafi.LineStrong, icon = Hafi.FgSubtle)
    else -> MarkerColors(fill = Hafi.Surface, edge = Hafi.Accent, icon = Hafi.Accent)
}

/** Side of a marker's square in dp: the selected one is larger so it reads without colour. */
internal fun markerSideDp(kind: MarkerKind, selected: Boolean): Float = when {
    kind == MarkerKind.Me -> 22f
    selected -> 40f
    else -> 32f
}

/**
 * Markers are drawn here and not by Google: Me is a green dot with a white ring,
 * everything else a square in brand colours (white with a green edge; filled
 * green and larger when selected; grey when muted). A heading adds an arrow.
 */
internal fun markerIcon(
    context: Context,
    kind: MarkerKind,
    selected: Boolean,
    muted: Boolean,
    headingStep: Int?,
): BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    val arrow = 7f * density
    val body = markerSideDp(kind, selected) * density
    val side = (body + arrow * 2).roundToInt()
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val mid = side / 2f
    val half = body / 2f

    if (headingStep != null) {
        drawHeadingArrow(canvas, paint, mid, half, arrow, density, headingStep)
    }
    if (kind == MarkerKind.Me) {
        drawMeDot(canvas, paint, mid, half, arrow, density)
    } else {
        val colors = markerColors(selected, muted)
        val stroke = (if (selected) 2f else 1.5f) * density
        paint.style = Paint.Style.FILL
        paint.color = colors.fill.toArgb()
        canvas.drawRect(mid - half, mid - half, mid + half, mid + half, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = colors.edge.toArgb()
        val inset = stroke / 2
        canvas.drawRect(mid - half + inset, mid - half + inset, mid + half - inset, mid + half - inset, paint)
        ContextCompat.getDrawable(context, iconFor(kind))?.mutate()?.let { drawable ->
            val iconHalf = ((if (selected) 22f else 18f) * density / 2).roundToInt()
            val centre = mid.roundToInt()
            drawable.setTint(colors.icon.toArgb())
            drawable.setBounds(centre - iconHalf, centre - iconHalf, centre + iconHalf, centre + iconHalf)
            drawable.draw(canvas)
        }
    }
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun drawHeadingArrow(
    canvas: Canvas,
    paint: Paint,
    mid: Float,
    half: Float,
    arrow: Float,
    density: Float,
    step: Int,
) {
    canvas.save()
    canvas.rotate(step * (360f / HeadingSteps), mid, mid)
    val path = Path().apply {
        moveTo(mid, mid - half - arrow + density)
        lineTo(mid - arrow * 0.8f, mid - half + density)
        lineTo(mid + arrow * 0.8f, mid - half + density)
        close()
    }
    paint.style = Paint.Style.FILL
    paint.color = Hafi.Accent.toArgb()
    canvas.drawPath(path, paint)
    canvas.restore()
}

private fun drawMeDot(canvas: Canvas, paint: Paint, mid: Float, half: Float, arrow: Float, density: Float) {
    paint.style = Paint.Style.FILL
    paint.color = Hafi.Accent.copy(alpha = 0.22f).toArgb()
    canvas.drawCircle(mid, mid, half + arrow * 0.7f, paint)
    paint.color = Hafi.Surface.toArgb()
    canvas.drawCircle(mid, mid, half, paint)
    paint.color = Hafi.Accent.toArgb()
    canvas.drawCircle(mid, mid, half - 3f * density, paint)
}
