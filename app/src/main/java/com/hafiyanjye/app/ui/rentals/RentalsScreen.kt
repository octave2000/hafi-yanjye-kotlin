package com.hafiyanjye.app.ui.rentals

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.location.Geocoding
import com.hafiyanjye.app.data.rentals.ListingKind
import com.hafiyanjye.app.data.rentals.RentalsApi
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.EmptyState
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.HafiSheet
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.kit.PageHeader
import com.hafiyanjye.app.ui.kit.SegmentedControl
import com.hafiyanjye.app.ui.location.LocationPicker
import com.hafiyanjye.app.ui.location.LocationSource
import com.hafiyanjye.app.ui.location.LocationStatus
import com.hafiyanjye.app.ui.location.PickedLocation
import com.hafiyanjye.app.ui.location.rememberCurrentLocation
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/**
 * Cars and homes to rent, nearest first (web: /rentals): the ask box, filters,
 * where distances are measured from, and the results as a list or on a map.
 * `showHeader` is true on the tab; the pushed search has its own top bar.
 */
@Composable
fun RentalsScreen(
    start: RentalStart,
    viewModelKey: String,
    onOpenListing: (Int) -> Unit,
    onAskForHelp: (String) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
) {
    val vm = containerViewModel(key = viewModelKey) {
        RentalsViewModel(RentalsApi(it.client), Geocoding(it.http), it.hub, start)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val query = state.query
    val myLocation = rememberCurrentLocation(auto = query.place == null && query.near == null)
    LaunchedEffect(myLocation.location) {
        myLocation.location?.let(vm::useMyLocation)
    }
    var mapView by rememberSaveable { mutableStateOf(false) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var placeOpen by rememberSaveable { mutableStateOf(false) }
    val actions = SearchActions(
        onText = vm::setText,
        onSubmit = vm::submit,
        onKind = vm::setKind,
        onRemoveFilter = vm::removeFilter,
        onClearFilters = vm::clearFilters,
        onOpenFilters = { filtersOpen = true },
        onOpenPlace = { placeOpen = true },
        onAskForHelp = onAskForHelp,
        onToggleMap = { mapView = it },
    )
    val placeLabel = placeLabel(state, myLocation.status == LocationStatus.Locating)

    if (mapView) {
        RentalsMapView(
            state = state,
            placeLabel = placeLabel,
            actions = actions,
            onOpenListing = onOpenListing,
            onRetry = vm::refresh,
            modifier = modifier,
        )
    } else {
        RentalsListView(
            state = state,
            placeLabel = placeLabel,
            showHeader = showHeader,
            actions = actions,
            onOpenListing = onOpenListing,
            onRefresh = vm::refresh,
            onLoadMore = vm::loadMore,
            modifier = modifier,
        )
    }

    if (filtersOpen) {
        RentalFiltersSheet(
            filters = query.filters,
            onApply = {
                vm.applyFilters(it)
                filtersOpen = false
            },
            onDismiss = { filtersOpen = false },
        )
    }
    if (placeOpen) {
        PlaceSheet(
            place = query.place,
            onPick = {
                vm.setPlace(it)
                placeOpen = false
            },
            onDismiss = { placeOpen = false },
        )
    }
}

/** What the search controls can do, gathered so the parts stay short. */
internal class SearchActions(
    val onText: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onKind: (ListingKind) -> Unit,
    val onRemoveFilter: (FilterKey) -> Unit,
    val onClearFilters: () -> Unit,
    val onOpenFilters: () -> Unit,
    val onOpenPlace: () -> Unit,
    val onAskForHelp: (String) -> Unit,
    val onToggleMap: (Boolean) -> Unit,
)

/** The "Near" value: the place, or what is being found, or Kigali centre (web: the Near pill). */
private fun placeLabel(state: RentalsUiState, locating: Boolean): String {
    val query = state.query
    return when {
        query.near != null -> "Finding ${query.near}…"
        query.place != null -> query.place.label ?: "The place you chose"
        locating -> "Finding you…"
        else -> "Kigali centre"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RentalsListView(
    state: RentalsUiState,
    placeLabel: String,
    showHeader: Boolean,
    actions: SearchActions,
    onOpenListing: (Int) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val results = state.results
    PullToRefreshBox(
        isRefreshing = (results as? Load.Ready)?.refreshing == true,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showHeader) {
                item(key = "header") {
                    PageHeader(
                        title = "Rentals",
                        eyebrow = "Cars and homes",
                        description = "Cars by the day, with or without a driver. " +
                            "Apartments, houses and rooms, by the night or month.",
                    )
                }
            }
            item(key = "search") {
                SearchPanel(state = state, placeLabel = placeLabel, mapView = false, actions = actions)
            }
            item(key = "heading") {
                ResultsHeading(state)
            }
            listResults(state, actions, onOpenListing, onRefresh, onLoadMore)
        }
    }
}

private fun LazyListScope.listResults(
    state: RentalsUiState,
    actions: SearchActions,
    onOpenListing: (Int) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    when (val results = state.results) {
        Load.Loading -> items(4) { ListingCardSkeleton() }
        is Load.Failed -> item(key = "failed") {
            ErrorPanel(results.message, onRetry)
        }
        is Load.Ready -> {
            results.staleError?.let { message ->
                item(key = "stale") { ErrorPanel(message, onRetry) }
            }
            if (results.value.items.isEmpty()) {
                item(key = "empty") { NoResults(state, actions) }
            } else {
                items(results.value.items, key = { it.id }) { listing ->
                    ListingCard(listing, onOpenListing)
                }
                if (results.value.hasMore) {
                    item(key = "more") {
                        LoadMore(state, onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadMore(state: RentalsUiState, onLoadMore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormAlert(state.moreError)
        HafiButton(
            text = "Show more ${state.query.filters.kind.noun()}",
            onClick = onLoadMore,
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.Outline,
            loading = state.loadingMore,
            loadingText = "Loading…",
        )
    }
}

/** Nothing found: drop a filter, or try the other kind when there were none (web wording). */
@Composable
private fun NoResults(state: RentalsUiState, actions: SearchActions) {
    val kind = state.query.filters.kind
    val filtered = filterChips(state.query.filters).isNotEmpty()
    EmptyState(
        title = "Nothing matches all of this yet.",
        description = if (filtered) {
            "Remove a filter above to see more."
        } else {
            "No ${kind.noun()} are listed yet. Try ${kind.other().noun()}, or check back soon."
        },
        icon = R.drawable.ic_search_x,
        action = {
            if (filtered) {
                HafiButton("Show all ${kind.noun()}", actions.onClearFilters, kind = ButtonKind.Outline)
            } else {
                HafiButton("Show ${kind.other().noun()}", { actions.onKind(kind.other()) }, kind = ButtonKind.Outline)
            }
        },
    )
}

/** "12 homes to rent", and where they are measured from. Announced when it changes. */
@Composable
internal fun ResultsHeading(state: RentalsUiState) {
    val kind = state.query.filters.kind
    val ready = state.results as? Load.Ready
    val total = ready?.value?.total ?: 0
    val title = when {
        ready == null -> "Looking for ${kind.noun()}…"
        total == 0 -> "No ${kind.noun()} found"
        else -> "$total ${kind.noun(total)} to rent"
    }
    val place = state.query.place
    val from = when {
        place?.label != null -> "Nearest first from ${place.label}"
        place != null -> "Nearest first"
        else -> "Nearest first from Kigali centre"
    }
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = HafiType.Heading,
            color = Hafi.Fg,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(from, style = HafiType.Caption, color = Hafi.FgMuted)
    }
}

/** The search box, kind switch, where, filters and the list/map switch (web: the search form and chips). */
@Composable
internal fun SearchPanel(
    state: RentalsUiState,
    placeLabel: String,
    mapView: Boolean,
    actions: SearchActions,
    modifier: Modifier = Modifier,
) {
    val kind = state.query.filters.kind
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedControl(
            options = listOf("Cars", "Homes"),
            selectedIndex = if (kind == ListingKind.Car) 0 else 1,
            onSelect = { actions.onKind(if (it == 0) ListingKind.Car else ListingKind.Home) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!mapView) AskBox(state, actions)
        ControlsRow(state, placeLabel, mapView, actions)
        ActiveFilters(state, actions)
    }
}

@Composable
private fun AskBox(state: RentalsUiState, actions: SearchActions) {
    val focus = LocalFocusManager.current
    val submit = {
        focus.clearFocus()
        actions.onSubmit()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HafiField(
            label = "Describe what you want to rent",
            value = state.text,
            onValueChange = actions.onText,
            modifier = Modifier.fillMaxWidth(),
            placeholder = if (state.query.filters.kind == ListingKind.Car) {
                "e.g. SUV with driver for 3 days, under 60k a day"
            } else {
                "e.g. 2 bedroom furnished near Kimironko under 400k"
            },
            leadingIcon = R.drawable.ic_search,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
        )
        HafiButton(
            text = "Search",
            onClick = submit,
            modifier = Modifier.fillMaxWidth(),
            loading = state.asking,
            loadingText = "Reading…",
            icon = R.drawable.ic_arrow_right,
        )
        FormAlert(state.askError)
        state.serviceHint?.let { hint ->
            InfoBanner(
                text = "That sounds like a job for someone nearby, not a rental.",
                tone = Tone.Accent,
                icon = R.drawable.ic_hand_helping,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                action = {
                    HafiButton("Find help for it", { actions.onAskForHelp(hint) })
                },
            )
        }
        state.summary?.let { summary ->
            Text(
                text = summary,
                style = HafiType.Caption,
                color = Hafi.FgMuted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun ControlsRow(state: RentalsUiState, placeLabel: String, mapView: Boolean, actions: SearchActions) {
    val count = filterChips(state.query.filters).size
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NearButton(placeLabel, empty = state.query.place == null, onClick = actions.onOpenPlace)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HafiButton(
                text = if (count > 0) "Filters ($count)" else "Filters",
                onClick = actions.onOpenFilters,
                modifier = Modifier.weight(1f),
                kind = if (count > 0) ButtonKind.Secondary else ButtonKind.Outline,
                icon = R.drawable.ic_sliders_horizontal,
            )
            HafiButton(
                text = if (mapView) "List" else "Map",
                onClick = { actions.onToggleMap(!mapView) },
                modifier = Modifier.weight(1f),
                kind = ButtonKind.Outline,
                icon = if (mapView) R.drawable.ic_list else R.drawable.ic_map,
            )
        }
    }
}

/** "Near: Kigali centre", opening the place picker (web: the Near pill). */
@Composable
private fun NearButton(label: String, empty: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .border(1.dp, Hafi.LineStrong)
            .clickable(role = Role.Button, onClickLabel = "Change where distances are measured from", onClick = onClick)
            .clearAndSetSemantics { contentDescription = "Near: $label. Change" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HIcon(R.drawable.ic_map_pin, size = 18.dp, tint = Hafi.AccentStrong)
        Column(Modifier.weight(1f)) {
            Text("Near", style = HafiType.Caption, color = Hafi.FgMuted)
            Text(label, style = HafiType.Label, color = if (empty) Hafi.FgMuted else Hafi.Fg)
        }
        Text("Change", style = HafiType.Label, color = Hafi.AccentStrong)
    }
}

/** One removable chip per filter, "Clear all" when there are several, and the note about the place. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilters(state: RentalsUiState, actions: SearchActions) {
    val chips = filterChips(state.query.filters)
    if (chips.isEmpty() && state.placeNote == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (chips.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { chip ->
                    RemovableChip(chip.label) { actions.onRemoveFilter(chip.key) }
                }
                if (chips.size > 1) {
                    HafiButton("Clear all", actions.onClearFilters, kind = ButtonKind.Ghost)
                }
            }
        }
        state.placeNote?.let { Text(it, style = HafiType.Caption, color = Hafi.FgMuted) }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .border(1.dp, Hafi.AccentStrong)
            .clickable(role = Role.Button, onClick = onRemove)
            .clearAndSetSemantics { contentDescription = "$label. Remove this filter" }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = HafiType.Label, color = Hafi.Fg)
        HIcon(R.drawable.ic_x, size = 14.dp, tint = Hafi.FgMuted)
    }
}

/** Where distances are measured from: the shared place picker in a sheet. */
@Composable
private fun PlaceSheet(place: RentalPlace?, onPick: (PickedLocation) -> Unit, onDismiss: () -> Unit) {
    val current = remember(place) {
        place?.let { PickedLocation(it.lat, it.lng, it.label, LocationSource.Typed) }
    }
    HafiSheet(onDismiss = onDismiss, title = "Near") {
        LocationPicker(
            value = current,
            onChange = onPick,
            label = "Measure distances from",
            hint = "Results are listed nearest first from here.",
        )
    }
}
