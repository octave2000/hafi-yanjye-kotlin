package com.hafiyanjye.app.ui.rentals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.rentals.ListingCardDto
import com.hafiyanjye.app.data.rentals.ListingKind
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.EmptyState
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.location.GeoPoint
import com.hafiyanjye.app.ui.location.HafiMap
import com.hafiyanjye.app.ui.location.MapMarker
import com.hafiyanjye.app.ui.location.MarkerKind
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

private const val CenterMarkerId = "center"

/**
 * The results on our own map (web: RentalsMap): a pin per listing and one for
 * where distances are measured from. Tapping a pin shows that listing's card.
 */
@Composable
internal fun RentalsMapView(
    state: RentalsUiState,
    placeLabel: String,
    actions: SearchActions,
    onOpenListing: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SearchPanel(
            state = state,
            placeLabel = placeLabel,
            mapView = true,
            actions = actions,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        Box(Modifier.padding(horizontal = 16.dp)) {
            ResultsHeading(state)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            when (val results = state.results) {
                Load.Loading -> Skeleton(Modifier.fillMaxSize())
                is Load.Failed -> ErrorPanel(
                    message = results.message,
                    onRetry = onRetry,
                    modifier = Modifier.padding(16.dp),
                )
                is Load.Ready -> if (results.value.items.isEmpty()) {
                    EmptyState(
                        title = "Nothing to show on the map.",
                        description = "Remove a filter or try another place.",
                        icon = R.drawable.ic_search_x,
                        modifier = Modifier.padding(16.dp),
                        action = {
                            HafiButton("Show the list", { actions.onToggleMap(false) }, kind = ButtonKind.Outline)
                        },
                    )
                } else {
                    ResultsMap(results.value, state.query.filters.kind, onOpenListing)
                }
            }
        }
    }
}

@Composable
private fun ResultsMap(results: RentalResults, kind: ListingKind, onOpenListing: (Int) -> Unit) {
    var selectedId by rememberSaveable(results.items.map { it.id }) { mutableStateOf<Int?>(null) }
    val selected = results.items.firstOrNull { it.id == selectedId }
    val center = GeoPoint(results.center.lat, results.center.lng)
    val markers = remember(results, selectedId) { mapMarkers(results, selectedId) }
    Box(Modifier.fillMaxSize()) {
        HafiMap(
            modifier = Modifier.fillMaxSize(),
            center = center,
            zoom = 13f,
            markers = markers,
            fitToContent = true,
            onMarkerClick = { id -> id.toIntOrNull()?.let { selectedId = it } },
            onMapClick = { selectedId = null },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selected != null) {
                SelectedCard(selected, onOpenListing, onClose = { selectedId = null })
            } else {
                MapHint(results, kind)
            }
        }
    }
}

/** Listing pins carry the listing id; the centre pin has its own id so a tap on it selects nothing. */
private fun mapMarkers(results: RentalResults, selectedId: Int?): List<MapMarker> {
    val center = MapMarker(
        id = CenterMarkerId,
        position = GeoPoint(results.center.lat, results.center.lng),
        kind = MarkerKind.Me,
        title = "Where distances are measured from",
    )
    return listOf(center) + results.items.map { listing ->
        MapMarker(
            id = listing.id.toString(),
            position = GeoPoint(listing.lat, listing.lng),
            kind = if (listing.kind == ListingKind.Car) MarkerKind.Car else MarkerKind.Home,
            title = listingSummary(listing),
            selected = listing.id == selectedId,
        )
    }
}

@Composable
private fun SelectedCard(listing: ListingCardDto, onOpenListing: (Int) -> Unit, onClose: () -> Unit) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        HafiIconButton(
            icon = R.drawable.ic_x,
            label = "Close this listing",
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.End)
                .background(Hafi.Surface),
        )
        ListingCard(listing, onOpenListing, selected = true)
    }
}

/** How to use the map, and an honest count when not every result is on it yet. */
@Composable
private fun MapHint(results: RentalResults, kind: ListingKind) {
    val shown = results.items.size
    val text = if (results.hasMore) {
        "Tap a pin to see the listing. Showing the nearest $shown of ${results.total} ${kind.noun(results.total)}; " +
            "the list loads more."
    } else {
        "Tap a pin to see the listing."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, style = HafiType.Caption, color = Hafi.Fg)
    }
}
