package com.hafiyanjye.app.data.profile

import kotlin.math.max
import kotlin.math.min

/**
 * How a photo sits in the square crop frame. [x] and [y] are how far its
 * centre is from the frame's centre, as a fraction of the frame's side, so the
 * same view draws the preview and the saved picture. [turn] is quarter turns
 * clockwise. Mirrors web/src/components/provider/photo-crop-dialog.tsx.
 */
data class CropView(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f, val turn: Int = 0) {

    /** Frame sides per photo pixel: at zoom 1 the photo's short side fills the frame. */
    fun scale(width: Int, height: Int): Float = zoom / min(width, height).coerceAtLeast(1)

    /** How far the photo may move each way before the frame would show a gap. */
    fun maxOffset(width: Int, height: Int): Pair<Float, Float> {
        val sideways = turn % 2 != 0
        val w = if (sideways) height else width
        val h = if (sideways) width else height
        val scale = scale(width, height)
        return max(0f, (w * scale - 1f) / 2f) to max(0f, (h * scale - 1f) / 2f)
    }

    /** The same view, kept inside the zoom range and covering the whole frame. */
    fun clamped(width: Int, height: Int): CropView {
        val zoomed = copy(zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM), turn = ((turn % 4) + 4) % 4)
        val (maxX, maxY) = zoomed.maxOffset(width, height)
        return zoomed.copy(x = x.coerceIn(-maxX, maxX), y = y.coerceIn(-maxY, maxY))
    }

    fun turned(by: Int): CropView = copy(turn = (((turn + by) % 4) + 4) % 4, x = 0f, y = 0f)

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 4f

        /** The saved picture's side, in pixels. */
        const val OUTPUT = 512
    }
}
