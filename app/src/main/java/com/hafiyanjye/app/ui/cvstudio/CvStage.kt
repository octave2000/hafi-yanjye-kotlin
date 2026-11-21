package com.hafiyanjye.app.ui.cvstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.hafiyanjye.app.data.talent.CvStyle
import com.hafiyanjye.app.data.talent.TalentProfileFields
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import kotlin.math.roundToInt

private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * The CV's pages on a dotted canvas: fitted to the width (to the height on
 * wide screens), pinch or double-tap to zoom, drag to pan between pages.
 */
@Composable
internal fun CvStage(fields: TalentProfileFields, style: CvStyle, modifier: Modifier = Modifier) {
    val dot = Hafi.LineStrong
    BoxWithConstraints(
        modifier
            .clipToBounds()
            .background(Hafi.Surface)
            .drawBehind {
                val step = 18.dp.toPx()
                val radius = 1.dp.toPx()
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) {
                        drawCircle(dot, radius, Offset(x, y))
                        x += step
                    }
                    y += step
                }
            },
    ) {
        val padding = 16.dp
        val byWidth = (maxWidth - padding * 2).coerceAtLeast(0.dp)
        val pageWidth = if (maxWidth > maxHeight) {
            min(byWidth, (maxHeight - padding * 2) * (CvPageLayout.WIDTH / CvPageLayout.HEIGHT))
        } else {
            byWidth
        }.coerceAtLeast(0.dp)
        val viewport = Offset(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val breaks = remember { CvBreaks() }
        var zoom by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var contentSize by remember { mutableStateOf(IntSize.Zero) }

        fun clamp(raw: Offset, scale: Float) = Offset(
            CvPageLayout.clampOffset(raw.x, contentSize.width * scale, viewport.x),
            CvPageLayout.clampOffset(raw.y, contentSize.height * scale, viewport.y, centre = false),
        )

        fun zoomTo(target: Float, focus: Offset) {
            val next = target.coerceIn(1f, MAX_ZOOM)
            val factor = next / zoom
            val shown = clamp(offset, zoom)
            offset = clamp(
                Offset(
                    CvPageLayout.zoomAround(shown.x, focus.x, factor),
                    CvPageLayout.zoomAround(shown.y, focus.y, factor),
                ),
                next,
            )
            zoom = next
        }

        val pages = breaks.pageCount()
        val percent = (pageWidth.value / CvPageLayout.WIDTH * zoom * 100).roundToInt()
        Box(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "CV preview, ${if (pages == 1) "1 page" else "$pages pages"}, $percent%"
                    customActions = listOf(
                        CustomAccessibilityAction("Zoom in") {
                            zoomTo(zoom * 1.5f, viewport / 2f)
                            true
                        },
                        CustomAccessibilityAction("Fit page") {
                            zoomTo(1f, Offset.Zero)
                            true
                        },
                    )
                }
                .pointerInput(contentSize, viewport) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        zoomTo(zoom * gestureZoom, centroid)
                        offset = clamp(offset + pan, zoom)
                    }
                }
                .pointerInput(contentSize, viewport) {
                    detectTapGestures(onDoubleTap = { tap -> zoomTo(if (zoom > 1.01f) 1f else DOUBLE_TAP_ZOOM, tap) })
                },
        ) {
            CvPages(
                fields = fields,
                style = style,
                pageWidth = pageWidth,
                breaks = breaks,
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .onSizeChanged { contentSize = it }
                    .graphicsLayer {
                        val shown = clamp(offset, zoom)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = zoom
                        scaleY = zoom
                        translationX = shown.x
                        translationY = shown.y
                    }
                    .padding(padding),
            )
        }
        ZoomMark(
            percent = percent,
            zoomed = zoom > 1.01f,
            onFit = { zoomTo(1f, Offset.Zero) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
        )
    }
}

/** "Fit · 45%" while the page fits; a button back to the fitted page once zoomed. */
@Composable
private fun ZoomMark(percent: Int, zoomed: Boolean, onFit: () -> Unit, modifier: Modifier = Modifier) {
    val base = modifier
        .background(Hafi.Surface)
        .border(1.dp, Hafi.Line)
    val shaped = if (zoomed) {
        base
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Fit page", onClick = onFit)
    } else {
        base
    }
    Box(shaped.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            text = if (zoomed) "$percent% · Fit page" else "Fit · $percent%",
            style = HafiType.Caption,
            color = Hafi.FgMuted,
        )
    }
}
