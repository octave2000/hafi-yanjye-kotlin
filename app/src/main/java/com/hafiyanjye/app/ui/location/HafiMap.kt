package com.hafiyanjye.app.ui.location

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.data.location.MapsKey
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.min

/** Tiles that never arrive (a refused key, no network) end in the static map after this long. */
private const val LoadTimeoutMs = 15_000L
private const val CameraMoveMs = 300

/**
 * The app's own map: brand-styled, square markers, never a hand-off to another
 * app. Give it a size through `modifier` (it is at least 200dp tall otherwise).
 * `interactive = false` is a still preview that lets the page scroll over it.
 * Without a key, without Google Play services, or when the map does not load,
 * it draws the same content on a plain static map instead.
 */
@Composable
fun HafiMap(
    modifier: Modifier = Modifier,
    center: GeoPoint = Kigali.Center,
    zoom: Float = 14f,
    markers: List<MapMarker> = emptyList(),
    lines: List<MapLine> = emptyList(),
    circles: List<MapCircle> = emptyList(),
    fitToContent: Boolean = false,
    interactive: Boolean = true,
    onMarkerClick: ((String) -> Unit)? = null,
    onMapClick: ((GeoPoint) -> Unit)? = null,
) {
    val context = LocalContext.current
    val summary = remember(center, markers, lines, circles) { describeMap(center, markers, lines, circles) }
    val frame = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 200.dp)
        .clipToBounds()
        .border(1.dp, Hafi.Line)
    val preview = LocalInspectionMode.current
    val canShow = remember {
        !preview &&
            MapsKey.present &&
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }
    // Survives rotation, so a map that failed does not spend another 15 s loading for nothing.
    var timedOut by rememberSaveable { mutableStateOf(false) }

    if (!canShow || timedOut) {
        Box(frame) {
            StaticMap(
                modifier = Modifier.matchParentSize(),
                center = center,
                zoom = zoom,
                markers = markers,
                lines = lines,
                circles = circles,
                fitToContent = fitToContent,
                interactive = interactive,
                summary = summary,
                onMarkerClick = onMarkerClick,
                onMapClick = onMapClick,
            )
            if (timedOut) {
                HafiButton(
                    text = "Load live map",
                    onClick = { timedOut = false },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    kind = ButtonKind.Secondary,
                    icon = R.drawable.ic_refresh_cw,
                )
            }
        }
        return
    }
    LiveMap(
        frame = frame,
        center = center,
        zoom = zoom,
        markers = markers,
        lines = lines,
        circles = circles,
        fitToContent = fitToContent,
        interactive = interactive,
        summary = summary,
        onMarkerClick = onMarkerClick,
        onMapClick = onMapClick,
        onTimeout = { timedOut = true },
    )
}

@Composable
private fun LiveMap(
    frame: Modifier,
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
    onTimeout: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val reducedMotion = rememberReducedMotion()
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        MapsKey.attach(context)
        delay(LoadTimeoutMs)
        if (!loaded) onTimeout()
    }

    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center.latLng(), zoom)
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val style = remember { runCatching { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style) }.getOrNull() }
    val properties = remember(style) { MapProperties(mapStyleOptions = style, maxZoomPreference = 19f) }
    val uiSettings = remember(interactive) { uiSettingsFor(interactive) }

    // Follow the centre when the caller moves it; fitting takes over when asked for.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(center, zoom, fitToContent, loaded) {
        if (fitToContent || !loaded) return@LaunchedEffect
        val update = CameraUpdateFactory.newLatLngZoom(center.latLng(), zoom)
        if (!placed || reducedMotion) camera.move(update) else runCatching { camera.animate(update, CameraMoveMs) }
        placed = true
    }
    val content = remember(markers, lines, circles) { contentPoints(markers, lines, circles) }
    var fits by remember { mutableIntStateOf(0) }
    LaunchedEffect(fitToContent, content, size, loaded) {
        if (!loaded || !fitToContent || content.isEmpty() || size.width == 0 || size.height == 0) {
            return@LaunchedEffect
        }
        val padding = min(with(density) { 48.dp.roundToPx() }, min(size.width, size.height) / 4)
        val update = fitUpdate(content, zoom, size, padding)
        // The first fit is where the map starts, not a change to explain.
        if (fits == 0 || reducedMotion) camera.move(update) else runCatching { camera.animate(update, CameraMoveMs) }
        fits += 1
    }

    val markerClick by rememberUpdatedState(onMarkerClick)
    val mapClick by rememberUpdatedState(onMapClick)
    Box(frame.onSizeChanged { size = it }) {
        GoogleMap(
            modifier = Modifier.matchParentSize(),
            cameraPositionState = camera,
            contentDescription = summary,
            properties = properties,
            uiSettings = uiSettings,
            mapColorScheme = ComposeMapColorScheme.LIGHT,
            onMapClick = { point -> mapClick?.invoke(GeoPoint(point.latitude, point.longitude)) },
            onMapLoaded = { loaded = true },
            onPOIClick = {},
        ) {
            MapShapes(circles = circles, lines = lines)
            MapMarkers(markers = markers, onClick = { id -> markerClick?.invoke(id) })
        }
        if (!interactive) {
            // Catches touches before the map view can claim them, so the page around it still scrolls.
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {}
                    .clearAndSetSemantics { },
            )
        }
        if (!loaded) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Hafi.Surface),
            ) {
                Skeleton(Modifier.matchParentSize())
                Text(
                    text = "Loading map…",
                    style = HafiType.Label,
                    color = Hafi.Fg,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

private fun uiSettingsFor(interactive: Boolean) = MapUiSettings(
    compassEnabled = false,
    indoorLevelPickerEnabled = false,
    // The toolbar's buttons open Google Maps: we stay in the app.
    mapToolbarEnabled = false,
    myLocationButtonEnabled = false,
    rotationGesturesEnabled = false,
    scrollGesturesEnabled = interactive,
    scrollGesturesEnabledDuringRotateOrZoom = interactive,
    tiltGesturesEnabled = false,
    // Buttons for people who cannot pinch, as the web map has.
    zoomControlsEnabled = interactive,
    zoomGesturesEnabled = interactive,
)

private fun fitUpdate(content: List<GeoPoint>, zoom: Float, size: IntSize, padding: Int) =
    if (content.all { it == content.first() }) {
        CameraUpdateFactory.newLatLngZoom(content.first().latLng(), zoom)
    } else {
        val bounds = LatLngBounds.builder()
            .apply { content.forEach { include(it.latLng()) } }
            .build()
        CameraUpdateFactory.newLatLngBounds(bounds, size.width, size.height, padding)
    }

@Composable
@GoogleMapComposable
private fun MapShapes(circles: List<MapCircle>, lines: List<MapLine>) {
    val density = LocalDensity.current
    val strokePx = with(density) { 1.5.dp.toPx() }
    val linePx = with(density) { 4.dp.toPx() }
    circles.forEach { circle ->
        Circle(
            center = circle.center.latLng(),
            radius = circle.radiusKm * 1000,
            fillColor = Hafi.Accent.copy(alpha = 0.08f),
            strokeColor = Hafi.Accent.copy(alpha = 0.7f),
            strokeWidth = strokePx,
        )
    }
    lines.filter { it.points.size >= 2 }.forEach { line ->
        val points = line.points.map { it.latLng() }
        if (!line.dashed) {
            // A white casing under the road route so it reads on any part of the map, as on the web.
            Polyline(
                points = points,
                color = Hafi.Surface,
                width = linePx * 1.8f,
                jointType = JointType.ROUND,
                startCap = RoundCap(),
                endCap = RoundCap(),
                zIndex = 1f,
            )
        }
        Polyline(
            points = points,
            color = Hafi.Accent,
            width = linePx,
            jointType = JointType.ROUND,
            startCap = RoundCap(),
            endCap = RoundCap(),
            pattern = if (line.dashed) listOf(Dash(linePx * 2), Gap(linePx * 1.5f)) else null,
            zIndex = 2f,
        )
    }
}

@Composable
@GoogleMapComposable
private fun MapMarkers(markers: List<MapMarker>, onClick: (String) -> Unit) {
    val context = LocalContext.current
    markers.forEach { marker ->
        key(marker.id) {
            val step = marker.heading?.let(::headingStep)
            val icon = remember(marker.kind, marker.selected, marker.muted, step) {
                markerIcon(context, marker.kind, marker.selected, marker.muted, step)
            }
            Marker(
                state = rememberUpdatedMarkerState(marker.position.latLng()),
                contentDescription = marker.title ?: defaultTitle(marker.kind),
                anchor = Offset(0.5f, 0.5f),
                icon = icon,
                zIndex = markerLayer(marker) * 5f + 3f,
                // Consumed: no Google info bubble, and the camera stays where the person put it.
                onClick = {
                    onClick(marker.id)
                    true
                },
            )
        }
    }
}

private fun GeoPoint.latLng() = LatLng(lat, lng)

internal fun formatCoordinates(point: GeoPoint): String =
    String.format(Locale.US, "%.4f, %.4f", point.lat, point.lng)

/** Every point the camera must include when fitting: markers, line points and each circle's edges. */
internal fun contentPoints(markers: List<MapMarker>, lines: List<MapLine>, circles: List<MapCircle>): List<GeoPoint> =
    markers.map { it.position } +
        lines.flatMap { it.points } +
        circles.flatMap { circle ->
            listOf(0.0, 90.0, 180.0, 270.0).map { placeAround(circle.center, circle.radiusKm, it) }
        }

/** What the map shows, for screen readers: "Map around Kimironko market: 3 places marked, a route, …". */
internal fun describeMap(
    center: GeoPoint,
    markers: List<MapMarker>,
    lines: List<MapLine>,
    circles: List<MapCircle>,
): String {
    val named = (markers.firstOrNull { it.selected } ?: markers.firstOrNull())?.title
    val place = named ?: if (distanceKm(center, Kigali.Center) < 15) "Kigali" else formatCoordinates(center)
    val parts = buildList {
        when (markers.size) {
            0 -> Unit
            1 -> add("1 place marked")
            else -> add("${markers.size} places marked")
        }
        if (markers.any { it.kind == MarkerKind.Me }) add("your position")
        when {
            lines.any { !it.dashed } -> add("a road route")
            lines.any { it.dashed } -> add("a straight line, as no road route is known")
        }
        circles.maxByOrNull { it.radiusKm }?.let { add("an area of ${Format.km(it.radiusKm)} around the centre") }
    }
    return if (parts.isEmpty()) "Map around $place" else "Map around $place: ${parts.joinToString(", ")}"
}
