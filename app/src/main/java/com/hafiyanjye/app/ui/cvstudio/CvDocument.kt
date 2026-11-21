package com.hafiyanjye.app.ui.cvstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import com.hafiyanjye.app.data.cvstudio.CvStyles
import com.hafiyanjye.app.data.talent.CvStyle
import com.hafiyanjye.app.data.talent.CvTemplate
import com.hafiyanjye.app.data.talent.TalentProfileFields
import kotlin.math.roundToInt

/** The chosen template, laid out in page units at the full page width. */
@Composable
private fun CvTemplateBody(content: CvContent, style: CvStyle) {
    val accent = accentColor(style.accent)
    val onAccent = onAccentColor(style.accent)
    CompositionLocalProvider(LocalCvFont provides cvFontFamily(style.font)) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = CvPageLayout.HEIGHT.dp),
        ) {
            when (style.template) {
                CvTemplate.Bold -> TemplateBold(content, accent, onAccent)
                CvTemplate.Classic -> TemplateClassic(content, accent)
                CvTemplate.Sidebar -> TemplateSidebar(content, accent, onAccent)
                CvTemplate.Minimal -> TemplateMinimal(content, accent)
            }
        }
    }
}

/**
 * One A4 sheet [width] wide showing [slice] of the laid-out CV. Inside it,
 * one dp is one page unit, so the templates use the web's numbers as they are.
 */
@Composable
internal fun CvPageFrame(
    content: CvContent,
    style: CvStyle,
    slice: PageSlice,
    first: Boolean,
    width: Dp,
    modifier: Modifier = Modifier,
    breaks: CvBreaks? = null,
) {
    val unit = with(LocalDensity.current) { width.toPx() } / CvPageLayout.WIDTH
    Box(
        modifier
            .size(width, width * (CvPageLayout.HEIGHT / CvPageLayout.WIDTH))
            .clipToBounds()
            .background(CvInk.Paper),
    ) {
        if (unit > 0f) {
            CompositionLocalProvider(
                LocalDensity provides Density(unit, fontScale = 1f),
                LocalCvBreaks provides breaks,
            ) {
                if (style.template == CvTemplate.Sidebar) {
                    Box(
                        Modifier
                            .width(SIDEBAR_WIDTH.dp)
                            .fillMaxHeight()
                            .background(accentColor(style.accent)),
                    )
                }
                val top = if (first) 0f else CvPageLayout.CONTINUED_TOP
                Box(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .offset { IntOffset(0, ((top - slice.start) * unit).roundToInt()) }
                        .drawWithContent {
                            clipRect(top = slice.start * unit, bottom = slice.end * unit) {
                                this@drawWithContent.drawContent()
                            }
                        }
                        .then(
                            if (breaks != null) Modifier.onGloballyPositioned { breaks.onRoot(it, unit) } else Modifier,
                        ),
                ) {
                    CvTemplateBody(content, style)
                }
            }
        }
    }
}

/**
 * The whole CV as A4 pages [pageWidth] wide, one under the other. The first
 * page's layout reports where blocks end; the CV is cut there into pages.
 */
@Composable
internal fun CvPages(
    fields: TalentProfileFields,
    style: CvStyle,
    pageWidth: Dp,
    modifier: Modifier = Modifier,
    gap: Dp = 16.dp,
    breaks: CvBreaks = remember { CvBreaks() },
) {
    val safeStyle = remember(style) { CvStyles.normalized(style) }
    val content = remember(fields, safeStyle) { cvContent(fields, safeStyle) }
    val slices = CvPageLayout.paginate(breaks.height.coerceAtLeast(CvPageLayout.HEIGHT), breaks.bottoms)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        slices.forEachIndexed { index, slice ->
            CvPageFrame(
                content = content,
                style = safeStyle,
                slice = slice,
                first = index == 0,
                width = pageWidth,
                breaks = if (index == 0) breaks else null,
            )
        }
    }
}

/** How many pages the CV takes, from what its layout last reported. */
internal fun CvBreaks.pageCount(): Int =
    CvPageLayout.paginate(height.coerceAtLeast(CvPageLayout.HEIGHT), bottoms).size

/**
 * A template tile: the first page of the person's own CV in [style], drawn
 * by the same composables as the full page, only smaller.
 */
@Composable
internal fun CvThumb(fields: TalentProfileFields, style: CvStyle, width: Dp, modifier: Modifier = Modifier) {
    val safeStyle = remember(style) { CvStyles.normalized(style) }
    val content = remember(fields, safeStyle) { cvContent(fields, safeStyle) }
    Box(modifier.clearAndSetSemantics { }) {
        CvPageFrame(
            content = content,
            style = safeStyle,
            slice = PageSlice(0f, CvPageLayout.HEIGHT),
            first = true,
            width = width,
        )
    }
}
