package com.hafiyanjye.app.ui.cvstudio

import kotlin.math.max
import kotlin.math.min

/** The part of the laid-out CV, in page units, that one A4 page shows. */
internal data class PageSlice(val start: Float, val end: Float) {
    val height: Float get() = end - start
}

/**
 * A4 page maths. A CV is laid out once at the web's page width in page
 * units (CSS px at 96 dpi), cut into pages at block edges, and scaled to fit.
 */
internal object CvPageLayout {
    const val WIDTH = 794f
    const val HEIGHT = 1123f

    /** Room kept free above content that continues from the previous page. */
    const val CONTINUED_TOP = 56f

    /** Room kept free under the last block of a page that isn't the last one. */
    const val BOTTOM_MARGIN = 48f

    /** Pages never cut closer than this to their top, so one huge block can't make empty pages. */
    private const val MIN_FILL = 0.5f

    /**
     * Where each page starts and ends. [breaks] are the bottoms of blocks
     * (jobs, schools, sections) where a cut reads well; when no break fits a
     * page, the page is cut at its edge.
     */
    fun paginate(contentHeight: Float, breaks: Collection<Float>): List<PageSlice> {
        if (contentHeight <= HEIGHT) return listOf(PageSlice(0f, max(contentHeight, 0f)))
        val sorted = breaks.filter { it > 0f && it < contentHeight }.sorted()
        val slices = mutableListOf<PageSlice>()
        var start = 0f
        while (start < contentHeight) {
            val top = if (slices.isEmpty()) 0f else CONTINUED_TOP
            val room = HEIGHT - top
            if (contentHeight - start <= room) {
                slices += PageSlice(start, contentHeight)
                break
            }
            val limit = start + room - BOTTOM_MARGIN
            val cut = sorted.lastOrNull { it <= limit && it >= start + room * MIN_FILL } ?: (start + room)
            slices += PageSlice(start, cut)
            start = cut
        }
        return slices
    }

    /** Screen pixels per page unit so a page fits [availableWidth] (and [availableHeight] when given). */
    fun fitScale(availableWidth: Float, availableHeight: Float? = null): Float {
        if (availableWidth <= 0f) return 0f
        val byWidth = availableWidth / WIDTH
        return if (availableHeight == null || availableHeight <= 0f) byWidth else min(byWidth, availableHeight / HEIGHT)
    }

    /**
     * Keeps a zoomed document in view: when it is smaller than the viewport
     * along an axis it is centred (or kept at the start), otherwise its edges can't come inside.
     */
    fun clampOffset(offset: Float, contentSize: Float, viewportSize: Float, centre: Boolean = true): Float = when {
        contentSize > viewportSize -> offset.coerceIn(viewportSize - contentSize, 0f)
        centre -> (viewportSize - contentSize) / 2f
        else -> 0f
    }

    /** The offset that keeps the point under [focus] still while zooming by [factor]. */
    fun zoomAround(offset: Float, focus: Float, factor: Float): Float = focus - (focus - offset) * factor
}
