package com.hafiyanjye.app.ui.cvstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.hafiyanjye.app.data.talent.TalentLanguageLevel
import com.hafiyanjye.app.ui.kit.RemoteImage

// Inside a CV page, LocalDensity maps 1.dp and 1.sp to one page unit (a CSS
// px of the web's 794-wide A4 page), so the sizes below are the web's own.

/** The document's typeface, set once per page from the CV style. */
internal val LocalCvFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

private val CssLeading = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

/** A text style in page units; [leading] is the CSS line-height multiplier (1.5 when the web sets none). */
@Composable
internal fun cvStyle(
    size: Float,
    color: Color,
    leading: Float = 1.5f,
    weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0f,
    italic: Boolean = false,
    tabular: Boolean = false,
): TextStyle = TextStyle(
    fontFamily = LocalCvFont.current,
    fontSize = size.sp,
    lineHeight = (size * leading).sp,
    lineHeightStyle = CssLeading,
    fontWeight = weight,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    letterSpacing = tracking.em,
    fontFeatureSettings = if (tabular) "tnum" else null,
    color = color,
)

/** One run of CV text; one line with an ellipsis unless [maxLines] says more (web `truncate` / `line-clamp`). */
@Composable
internal fun CvText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    align: TextAlign? = null,
) {
    Text(
        text = text,
        style = if (align != null) style.copy(textAlign = align) else style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Same as [CvText] for text with mixed weights or colours. */
@Composable
internal fun CvText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    align: TextAlign? = null,
) {
    Text(
        text = text,
        style = if (align != null) style.copy(textAlign = align) else style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private fun TalentLanguageLevel.bars(): Int = ordinal + 1

/** Four thin bars for a language level, filled [on] and hairline [off]; read out as the level's name. */
@Composable
internal fun LevelBars(level: TalentLanguageLevel, on: Color, off: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { contentDescription = level.label },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(4) { index ->
            Box(
                Modifier
                    .size(width = 11.dp, height = 3.dp)
                    .background(if (index < level.bars()) on else off),
            )
        }
    }
}

/** Achievement lines as hanging-indent bullets with a small square marker (at most [max], two lines each). */
@Composable
internal fun Bullets(details: String, marker: Color, max: Int = 5) {
    val lines = detailLines(details).take(max)
    if (lines.isEmpty()) return
    val style = cvStyle(11.5f, CvInk.Body, leading = 1.5f)
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        lines.forEach { line ->
            Box {
                Box(
                    Modifier
                        .padding(start = 2.dp, top = 7.dp)
                        .size(3.dp)
                        .background(marker),
                )
                CvText(line, style, Modifier.padding(start = 14.dp), maxLines = 2)
            }
        }
    }
}

/** The person's photo as a square; [ring] draws the web's thin outline around it. */
@Composable
internal fun CvPhoto(path: String, size: Dp, ring: Color? = null, ringWidth: Dp = 1.dp, modifier: Modifier = Modifier) {
    val framed = if (ring != null) modifier.background(ring).padding(ringWidth) else modifier
    Box(framed) {
        RemoteImage(path, contentDescription = null, modifier = Modifier.size(size))
    }
}

/** A hairline across the bottom (or top) of a block, drawn inside it so nothing shifts. */
internal fun Modifier.rule(color: Color, top: Boolean = false): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    val y = if (top) stroke / 2 else size.height - stroke / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
}

/**
 * Collects where blocks of the laid-out CV end, in page units: the places a
 * page may be cut without splitting a job or a school in half.
 */
internal class CvBreaks {
    private var root: LayoutCoordinates? = null
    private val blocks = HashMap<Any, LayoutCoordinates>()

    /** Block bottoms, in page units from the top of the content. */
    var bottoms by mutableStateOf<List<Float>>(emptyList())
        private set

    /** Height of the whole laid-out CV, in page units. */
    var height by mutableStateOf(0f)
        private set

    fun onRoot(coordinates: LayoutCoordinates, unit: Float) {
        root = coordinates
        update(unit)
    }

    fun onBlock(key: Any, coordinates: LayoutCoordinates, unit: Float) {
        blocks[key] = coordinates
        update(unit)
    }

    fun remove(key: Any) {
        blocks.remove(key)
    }

    // Children and the root report in no fixed order; whichever reports last sees everything.
    private fun update(unit: Float) {
        val content = root?.takeIf { it.isAttached } ?: return
        if (unit <= 0f) return
        val next = blocks.values
            .filter { it.isAttached }
            .map { content.localPositionOf(it, Offset(0f, it.size.height.toFloat())).y / unit }
            .sorted()
        if (next != bottoms) bottoms = next
        val total = content.size.height / unit
        if (total != height) height = total
    }
}

/** Set on the first page only: the copy whose layout decides where pages break. */
internal val LocalCvBreaks = staticCompositionLocalOf<CvBreaks?> { null }

/** Marks the bottom of this block as a good place to start a new page. */
@Composable
internal fun Modifier.cvBlock(): Modifier {
    val breaks = LocalCvBreaks.current ?: return this
    val unit = LocalDensity.current.density
    val key = remember { Any() }
    DisposableEffect(breaks, key) { onDispose { breaks.remove(key) } }
    return onGloballyPositioned { breaks.onBlock(key, it, unit) }
}
