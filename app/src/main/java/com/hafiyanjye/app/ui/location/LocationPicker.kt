package com.hafiyanjye.app.ui.location

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.location.Geocoding
import com.hafiyanjye.app.data.location.MapsKey
import com.hafiyanjye.app.data.location.PlaceSuggestion
import com.hafiyanjye.app.ui.LocalContainer
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.ButtonSize
import com.hafiyanjye.app.ui.kit.Divider
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiChip
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** The line under a chosen place saying where it came from (web: `SOURCE_HINT`). */
internal fun sourceHint(value: PickedLocation): String = when {
    value.approximate -> "Not found on the map: approximate (central Kigali)"
    value.source == LocationSource.Gps -> "From your phone's GPS"
    value.source == LocationSource.Places -> "From Google Maps"
    value.source == LocationSource.Typed -> "From the place you typed"
    else -> "Pin on the map"
}

/**
 * A form field: shows the chosen place; offers "Use my location", typing a place,
 * and moving a pin on a full-screen map.
 */
@Composable
fun LocationPicker(
    value: PickedLocation?,
    onChange: (PickedLocation) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Where",
    hint: String? = null,
    error: String? = null,
    enabled: Boolean = true,
) {
    var editing by rememberSaveable { mutableStateOf(value == null) }
    val pick: (PickedLocation) -> Unit = {
        onChange(it)
        editing = false
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = HafiType.Label, color = Hafi.Fg)
        if (value != null && !editing) {
            ChosenPlace(value = value, error = error != null, enabled = enabled, onChange = { editing = true })
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hafi.Surface)
                    .border(1.dp, if (error != null) Hafi.Danger else Hafi.Line)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PickerBody(start = value, enabled = enabled, onPick = pick)
                if (value != null) {
                    HafiButton(
                        text = "Keep “${value.label ?: "pinned location"}”",
                        onClick = { editing = false },
                        modifier = Modifier.fillMaxWidth(),
                        kind = ButtonKind.Ghost,
                    )
                }
            }
        }
        if (error != null) {
            Row(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HIcon(R.drawable.ic_triangle_alert, size = 16.dp, tint = Hafi.Danger)
                Text(text = error, style = HafiType.Caption, color = Hafi.Danger)
            }
        } else if (hint != null) {
            Text(text = hint, style = HafiType.Caption, color = Hafi.FgMuted)
        }
    }
}

@Composable
private fun ChosenPlace(value: PickedLocation, error: Boolean, enabled: Boolean, onChange: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, if (error) Hafi.Danger else Hafi.Accent)
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HIcon(R.drawable.ic_map_pin, tint = Hafi.AccentStrong)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
        ) {
            Text(
                text = value.label ?: "Pinned location",
                style = HafiType.BodyStrong,
                color = Hafi.Fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = sourceHint(value), style = HafiType.Caption, color = Hafi.FgMuted)
        }
        HafiButton(text = "Change", onClick = onChange, kind = ButtonKind.Ghost, enabled = enabled)
    }
}

/** "Use my location", the typed field with Google suggestions and area chips, and the pin map. */
@Composable
private fun ColumnScope.PickerBody(start: PickedLocation?, enabled: Boolean, onPick: (PickedLocation) -> Unit) {
    val context = LocalContext.current
    val http = LocalContainer.current.http
    val geocoding = remember(http) { Geocoding(http) }
    val current = rememberCurrentLocation()
    var askedGps by rememberSaveable { mutableStateOf(false) }
    var mapOpen by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { MapsKey.attach(context) }
    LaunchedEffect(current.fixes) {
        val found = current.location
        if (!askedGps || current.status == LocationStatus.Locating || current.status == LocationStatus.Asking) {
            return@LaunchedEffect
        }
        askedGps = false
        if (current.status == LocationStatus.Ready && found != null) onPick(found)
    }

    val locating = current.status == LocationStatus.Asking || current.status == LocationStatus.Locating
    HafiButton(
        text = "Use my location",
        onClick = {
            askedGps = true
            current.request()
        },
        modifier = Modifier.fillMaxWidth(),
        kind = ButtonKind.Outline,
        enabled = enabled && !busy,
        loading = locating,
        loadingText = "Finding you…",
        icon = R.drawable.ic_crosshair,
    )
    current.message?.let { message ->
        InfoBanner(
            text = message,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            tone = Tone.Warn,
            icon = R.drawable.ic_triangle_alert,
            action = if (current.status == LocationStatus.Denied) {
                { HafiButton(text = "Open settings", onClick = current::openSettings, kind = ButtonKind.Ghost) }
            } else {
                null
            },
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Divider(Modifier.weight(1f))
        Text(text = "OR TYPE YOUR AREA", style = HafiType.Micro, color = Hafi.FgSubtle)
        Divider(Modifier.weight(1f))
    }

    TypedPlace(
        geocoding = geocoding,
        enabled = enabled && !locating,
        busy = busy,
        onBusy = { busy = it },
        onPick = onPick,
    )

    // Without a key there is no map to put a pin on.
    if (MapsKey.present) {
        HafiButton(
            text = "Choose on the map",
            onClick = { mapOpen = true },
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.Secondary,
            enabled = enabled && !busy,
            icon = R.drawable.ic_map,
        )
    }
    if (mapOpen) {
        val known = start?.point ?: current.location?.point
        PinMapDialog(
            start = known ?: Kigali.Center,
            startKnown = known != null,
            onDismiss = { mapOpen = false },
            onPick = {
                mapOpen = false
                onPick(it)
            },
        )
    }
}

/**
 * The typed field. Google suggestions appear as the person types (when the key
 * works); Find looks the words up once on OpenStreetMap, then in known areas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.TypedPlace(
    geocoding: Geocoding,
    enabled: Boolean,
    busy: Boolean,
    onBusy: (Boolean) -> Unit,
    onPick: (PickedLocation) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    /** Set when the typed place was found nowhere; the person can refine it or use it anyway. */
    var notFound by remember { mutableStateOf<PickedLocation?>(null) }
    var suggestions by remember { mutableStateOf(emptyList<PlaceSuggestion>()) }
    /** The words the current suggestions answer, so "no exact place" waits for Google. */
    var answered by remember { mutableStateOf("") }
    /** Google refused once (API off, offline): stop asking on every letter, like the web. */
    var placesOff by remember { mutableStateOf(!geocoding.suggests) }
    /** Google bills place search by session: one token from the first letter to the choice. */
    var session by remember { mutableStateOf(UUID.randomUUID().toString()) }

    LaunchedEffect(text, busy, enabled, placesOff) {
        suggestions = emptyList()
        if (!enabled || busy || placesOff || text.trim().length < 2) return@LaunchedEffect
        delay(SuggestDelayMs)
        val found = geocoding.suggest(text, session)
        if (found == null) {
            placesOff = true
        } else {
            suggestions = found
            answered = text.trim()
        }
    }

    fun finish(found: PickedLocation) {
        onBusy(false)
        if (found.approximate) notFound = found else onPick(found)
    }

    val submit = {
        val typed = text.trim()
        if (enabled && typed.isNotEmpty() && !busy) {
            onBusy(true)
            notFound = null
            scope.launch { finish(geocoding.findTypedPlace(typed)) }
        }
    }

    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HafiField(
            label = "A place, street or area",
            value = text,
            onValueChange = {
                text = it
                notFound = null
            },
            modifier = Modifier.weight(1f),
            placeholder = "e.g. Kimironko market, KG 11 Ave",
            enabled = enabled && !busy,
            leadingIcon = R.drawable.ic_search,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
        )
        HafiButton(
            text = "Find",
            onClick = { submit() },
            enabled = enabled && text.isNotBlank(),
            loading = busy,
            loadingText = "Finding",
        )
    }

    if (suggestions.isNotEmpty()) {
        SuggestionList(suggestions = suggestions, enabled = enabled && !busy) { suggestion ->
            onBusy(true)
            suggestions = emptyList()
            val token = session
            session = UUID.randomUUID().toString()
            scope.launch { finish(geocoding.place(suggestion, token)) }
        }
    } else if (!placesOff && !busy && notFound == null && text.trim().length >= 2 && answered == text.trim()) {
        Text(
            text = "No exact place found. Tap Find to look up “${text.trim()}”.",
            style = HafiType.Caption,
            color = Hafi.FgMuted,
        )
    }

    notFound?.let { guess ->
        InfoBanner(
            text = "We couldn't find “${guess.label}” on the map. Try a nearby landmark or street, like " +
                "“Kimironko market” or “KG 11 Ave”.",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            tone = Tone.Warn,
            icon = R.drawable.ic_triangle_alert,
            action = {
                HafiButton(
                    text = "Use “${guess.label}” anyway (central Kigali)",
                    onClick = { onPick(guess) },
                    enabled = enabled && !busy,
                    kind = ButtonKind.Ghost,
                )
            },
        )
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Neighbourhoods.suggestions.forEach { area ->
            HafiChip(
                text = area,
                selected = false,
                onClick = { onPick(Geocoding.areaGuess(area)) },
                enabled = enabled && !busy,
            )
        }
    }
    Text(
        text = "Type a landmark, street or area and we'll find it on the map.",
        style = HafiType.Caption,
        color = Hafi.FgMuted,
    )
}

private const val SuggestDelayMs = 250L

@Composable
private fun SuggestionList(
    suggestions: List<PlaceSuggestion>,
    enabled: Boolean,
    onChoose: (PlaceSuggestion) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hafi.Line)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        suggestions.forEachIndexed { index, suggestion ->
            if (index > 0) Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(enabled = enabled, role = Role.Button) { onChoose(suggestion) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HIcon(R.drawable.ic_map_pin, size = 16.dp, tint = Hafi.AccentStrong)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = suggestion.main,
                        style = HafiType.Label,
                        color = Hafi.Fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (suggestion.secondary.isNotEmpty()) {
                        Text(
                            text = suggestion.secondary,
                            style = HafiType.Caption,
                            color = Hafi.FgMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** The full-screen map: tap to put the pin down, then confirm. It is our own map, inside the app. */
@Composable
private fun PinMapDialog(
    start: GeoPoint,
    startKnown: Boolean,
    onDismiss: () -> Unit,
    onPick: (PickedLocation) -> Unit,
) {
    var lat by rememberSaveable { mutableStateOf(start.lat) }
    var lng by rememberSaveable { mutableStateOf(start.lng) }
    var moved by rememberSaveable { mutableStateOf(false) }
    val pin = GeoPoint(lat, lng)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Hafi.Surface),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Choose the spot",
                    style = HafiType.Heading,
                    color = Hafi.Fg,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                HafiButton(text = "Cancel", onClick = onDismiss, kind = ButtonKind.Ghost)
            }
            HafiMap(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                center = start,
                zoom = 15f,
                markers = listOf(MapMarker("pin", pin, MarkerKind.Pin, title = "Your pin", selected = true)),
                onMapClick = {
                    lat = it.lat
                    lng = it.lng
                    moved = true
                },
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(
                        text = if (moved) {
                            "Pin at ${formatCoordinates(pin)}. Tap elsewhere to move it."
                        } else {
                            "Tap the map where you are to put the pin there."
                        },
                        style = HafiType.Caption,
                        color = Hafi.FgMuted,
                    )
                }
                HafiButton(
                    text = "Use this spot",
                    // Central Kigali is only a starting view, never a choice nobody made.
                    enabled = moved || startKnown,
                    onClick = { onPick(PickedLocation(lat, lng, "Pinned location", LocationSource.Pin)) },
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Large,
                )
            }
        }
    }
}
