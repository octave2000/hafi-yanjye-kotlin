package com.hafiyanjye.app.ui.location

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow

private const val KmPerDegLat = 110.574
private const val EquatorKm = 40_075.0

/**
 * Turns map points into offsets on a flat card and back. Fine at city scale;
 * it only has to put things in the right order and roughly the right distance.
 */
internal class FlatProjection(
    private val centre: GeoPoint,
    private val kmPerPx: Double,
    private val widthPx: Float,
    private val heightPx: Float,
) {
    private val kmPerDegLng = 111.32 * cos(Math.toRadians(centre.lat))

    fun toOffset(point: GeoPoint): Offset = Offset(
        x = widthPx / 2 + ((point.lng - centre.lng) * kmPerDegLng / kmPerPx).toFloat(),
        y = heightPx / 2 - ((point.lat - centre.lat) * KmPerDegLat / kmPerPx).toFloat(),
    )

    fun toPoint(offset: Offset): GeoPoint = GeoPoint(
        lat = centre.lat - (offset.y - heightPx / 2) * kmPerPx / KmPerDegLat,
        lng = centre.lng + (offset.x - widthPx / 2) * kmPerPx / kmPerDegLng,
    )

    fun km(distanceKm: Double): Float = (distanceKm / kmPerPx).toFloat()

    companion object {
        /**
         * Frames everything (`fit`) or shows `center` at about the scale a live
         * map would have at `zoom`. `pxPerDp` turns web-map pixels into ours.
         */
        fun frame(
            center: GeoPoint,
            zoom: Float,
            content: List<GeoPoint>,
            fit: Boolean,
            widthPx: Float,
            heightPx: Float,
            paddingPx: Float,
            pxPerDp: Float,
        ): FlatProjection {
            val atZoom = EquatorKm * cos(Math.toRadians(center.lat)) / (256.0 * 2.0.pow(zoom.toDouble())) / pxPerDp
            if (!fit || content.size < 2) {
                return FlatProjection(if (fit) content.firstOrNull() ?: center else center, atZoom, widthPx, heightPx)
            }
            val south = content.minOf { it.lat }
            val north = content.maxOf { it.lat }
            val west = content.minOf { it.lng }
            val east = content.maxOf { it.lng }
            val middle = GeoPoint((south + north) / 2, (west + east) / 2)
            val kmPerDegLng = 111.32 * cos(Math.toRadians(middle.lat))
            val spanX = abs(east - west) * kmPerDegLng
            val spanY = abs(north - south) * KmPerDegLat
            val usableX = max(widthPx - paddingPx * 2, 1f)
            val usableY = max(heightPx - paddingPx * 2, 1f)
            val scale = max(spanX / usableX, spanY / usableY).takeIf { it > 0 } ?: atZoom
            return FlatProjection(middle, scale, widthPx, heightPx)
        }
    }
}

/**
 * The calm stand-in for the live map (no key, no Play services, tiles that never
 * came): a plain green grid with the same circles, lines and square markers at
 * their relative places, like the web's static map. Markers stay tappable.
 */
@Composable
internal fun StaticMap(
    modifier: Modifier,
    center: GeoPoint,
    zoom: Float,
    markers: List<MapMarker>,
    lines: List<MapLine>,
    circles: List<MapCircle>,
    fitToContent: Boolean,
    interactive: Boolean,
    summary: String,
    onMarkerClick: ((String) -> Unit)?,
    onMapClick: ((GeoPoint) -> Unit)?,
) {
    BoxWithConstraints(modifier.background(Hafi.Surface2)) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val content = remember(markers, lines, circles) { contentPoints(markers, lines, circles) }
        val projection = remember(center, zoom, content, fitToContent, widthPx, heightPx, density) {
            FlatProjection.frame(
                center = center,
                zoom = zoom,
                content = content,
                fit = fitToContent || circles.isNotEmpty(),
                widthPx = widthPx,
                heightPx = heightPx,
                paddingPx = with(density) { 40.dp.toPx() },
                pxPerDp = density.density,
            )
        }
        val tapModifier = if (interactive && onMapClick != null) {
            Modifier.pointerInput(projection) { detectTapGestures { onMapClick(projection.toPoint(it)) } }
        } else {
            Modifier
        }
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .then(tapModifier)
                .semantics { contentDescription = summary },
        ) {
            drawGrid(gridStep = 24.dp.toPx())
            circles.forEach { circle ->
                val centre = projection.toOffset(circle.center)
                val radius = projection.km(circle.radiusKm)
                drawCircle(Hafi.Accent.copy(alpha = 0.07f), radius, centre)
                drawCircle(
                    color = Hafi.AccentStrong.copy(alpha = 0.5f),
                    radius = radius,
                    center = centre,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                )
            }
            lines.filter { it.points.size >= 2 }.forEach { line ->
                val width = 4.dp.toPx()
                val dash = if (line.dashed) PathEffect.dashPathEffect(floatArrayOf(width * 2, width * 1.5f)) else null
                line.points.zipWithNext().forEach { (from, to) ->
                    val start = projection.toOffset(from)
                    val end = projection.toOffset(to)
                    if (!line.dashed) drawLine(Hafi.Surface, start, end, width * 2, StrokeCap.Round)
                    drawLine(Hafi.Accent, start, end, width, StrokeCap.Round, dash)
                }
            }
        }
        markers.sortedBy { markerLayer(it) }.forEach { marker ->
            StaticMarker(
                marker = marker,
                offset = projection.toOffset(marker.position),
                onClick = onMarkerClick?.let { click -> { click(marker.id) } },
            )
        }
        Text(
            text = formatCoordinates(center),
            style = HafiType.Caption,
            color = Hafi.FgMuted,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Hafi.Surface)
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .clearAndSetSemantics { },
        )
    }
}

private fun DrawScope.drawGrid(gridStep: Float) {
    val color = Hafi.Accent.copy(alpha = 0.12f)
    var x = 0f
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f)
        x += gridStep
    }
    var y = 0f
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), 1f)
        y += gridStep
    }
}

/** Draw order: plain markers first, the selected ones over them, the person's own dot on top. */
internal fun markerLayer(marker: MapMarker): Int = when {
    marker.kind == MarkerKind.Me -> 2
    marker.selected -> 1
    else -> 0
}

@Composable
private fun StaticMarker(marker: MapMarker, offset: Offset, onClick: (() -> Unit)?) {
    val density = LocalDensity.current
    val side: Dp = markerSideDp(marker.kind, marker.selected).dp
    val title = marker.title ?: defaultTitle(marker.kind)
    val position = with(density) {
        Modifier.offset(x = offset.x.toDp() - side / 2, y = offset.y.toDp() - side / 2)
    }
    val semantics = if (onClick != null) {
        Modifier
            .clickable(onClickLabel = "See details", role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = title
                role = Role.Button
            }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    if (marker.kind == MarkerKind.Me) {
        Box(
            modifier = position
                .size(side)
                .background(Hafi.Surface, CircleShape)
                .padding(3.dp)
                .background(Hafi.Accent, CircleShape)
                .then(semantics),
        )
        return
    }
    val colors = markerColors(marker.selected, marker.muted)
    Box(
        modifier = position
            .size(side)
            .background(colors.fill)
            .border(if (marker.selected) 2.dp else 1.5.dp, colors.edge)
            .then(semantics),
        contentAlignment = Alignment.Center,
    ) {
        HIcon(iconFor(marker.kind), size = if (marker.selected) 22.dp else 18.dp, tint = colors.icon)
        marker.heading?.let { heading ->
            HIcon(
                id = R.drawable.ic_arrow_up,
                size = 12.dp,
                tint = colors.icon,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .rotate(heading),
            )
        }
    }
}
