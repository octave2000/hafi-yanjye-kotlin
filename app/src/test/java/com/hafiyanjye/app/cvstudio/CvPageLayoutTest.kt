package com.hafiyanjye.app.cvstudio

import com.hafiyanjye.app.ui.cvstudio.CvPageLayout
import com.hafiyanjye.app.ui.cvstudio.PageSlice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CvPageLayoutTest {
    private val page = CvPageLayout.HEIGHT

    @Test
    fun `the page is A4 at 96 dpi like the web`() {
        assertEquals(794f, CvPageLayout.WIDTH)
        assertEquals(1123f, CvPageLayout.HEIGHT)
        assertEquals(210.0 / 297.0, (CvPageLayout.WIDTH / CvPageLayout.HEIGHT).toDouble(), 0.002)
    }

    @Test
    fun `a CV that fits is one page`() {
        assertEquals(listOf(PageSlice(0f, page)), CvPageLayout.paginate(page, listOf(300f, 900f)))
    }

    @Test
    fun `a long CV is cut at the last block that fits above the bottom margin`() {
        val slices = CvPageLayout.paginate(1800f, listOf(400f, 700f, 1050f, 1090f, 1500f))
        // 1090 is inside the bottom margin (1123 - 48), so the cut is at 1050.
        assertEquals(PageSlice(0f, 1050f), slices[0])
        assertEquals(PageSlice(1050f, 1800f), slices[1])
        assertEquals(2, slices.size)
    }

    @Test
    fun `continued pages leave room at the top`() {
        val slices = CvPageLayout.paginate(3000f, emptyList())
        assertEquals(PageSlice(0f, page), slices[0])
        assertEquals(page - CvPageLayout.CONTINUED_TOP, slices[1].height)
        assertEquals(3000f, slices.last().end)
        slices.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
    }

    @Test
    fun `without a usable break the page is cut at its edge, never left half empty`() {
        val slices = CvPageLayout.paginate(2000f, listOf(100f))
        assertEquals(PageSlice(0f, page), slices[0])
    }

    @Test
    fun `every slice fits its page`() {
        val slices = CvPageLayout.paginate(5200f, listOf(500f, 1000f, 1600f, 2100f, 2700f, 3300f, 3900f, 4500f))
        slices.forEachIndexed { index, slice ->
            val top = if (index == 0) 0f else CvPageLayout.CONTINUED_TOP
            assertTrue("page $index too tall", slice.height + top <= page)
        }
    }

    @Test
    fun `fit scale uses the width, and the height when given`() {
        assertEquals(0.5f, CvPageLayout.fitScale(397f), 0.0001f)
        assertEquals(400f / 1123f, CvPageLayout.fitScale(794f, 400f), 0.0001f)
        assertEquals(0f, CvPageLayout.fitScale(0f))
    }

    @Test
    fun `zoomed pages stay in view`() {
        // Bigger than the viewport: edges can't come inside.
        assertEquals(0f, CvPageLayout.clampOffset(50f, 800f, 400f))
        assertEquals(-400f, CvPageLayout.clampOffset(-900f, 800f, 400f))
        // Smaller: centred across, kept at the top down.
        assertEquals(100f, CvPageLayout.clampOffset(-30f, 200f, 400f))
        assertEquals(0f, CvPageLayout.clampOffset(-30f, 200f, 400f, centre = false))
    }

    @Test
    fun `zooming keeps the pinched point still`() {
        val focus = 120f
        val before = -40f
        val after = CvPageLayout.zoomAround(before, focus, 2f)
        // The content point under the finger: (focus - offset) / zoom stays the same.
        assertEquals((focus - before) / 1f, (focus - after) / 2f, 0.0001f)
    }
}
