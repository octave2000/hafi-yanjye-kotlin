package com.hafiyanjye.app.ui.browse

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.browse.BrowseFilters
import com.hafiyanjye.app.data.browse.FilterOptions
import com.hafiyanjye.app.data.browse.SearchSort
import com.hafiyanjye.app.data.browse.WorkerResult
import com.hafiyanjye.app.data.match.JobCategory
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.EmptyState
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiChip
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.HafiSelect
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.categoryIcon
import com.hafiyanjye.app.ui.location.LocationPicker
import com.hafiyanjye.app.ui.location.LocationStatus
import com.hafiyanjye.app.ui.location.PickedLocation
import com.hafiyanjye.app.ui.location.rememberCurrentLocation
import com.hafiyanjye.app.ui.nav.Routes
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

/** Where the results start in the list: hero, service chips, then the toolbar. */
private const val ResultsIndex = 2

/** The services list (web: app/services/page.tsx), reached through [Routes.services]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServicesScreen(viewModel: ServicesViewModel, nav: NavHostController) {
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val hireForm by viewModel.hire.form.collectAsStateWithLifecycle()
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var locationOpen by rememberSaveable { mutableStateOf(false) }
    var pulled by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()

    // Like the web, the phone's position is used when it can be read without asking.
    val phone = rememberCurrentLocation(auto = true)
    LaunchedEffect(phone.location) { phone.location?.let(viewModel::foundLocation) }
    LaunchedEffect(state.loading) { if (!state.loading) pulled = false }
    LaunchedEffect(hireForm?.jobId) {
        val jobId = hireForm?.jobId ?: return@LaunchedEffect
        viewModel.hire.opened()
        nav.navigate(Routes.request(jobId))
    }

    val selected = state.categories.firstOrNull { it.slug == filters.service } ?: state.results?.category
    val scrollToResults: () -> Unit = {
        scope.launch {
            if (reduced) listState.scrollToItem(ResultsIndex) else listState.animateScrollToItem(ResultsIndex)
        }
    }
    val actions = ResultActions(
        open = { nav.navigate(Routes.person(it.id)) },
        chat = { nav.navigate(Routes.chatWith(providerId = it.id)) },
        hire = { person ->
            viewModel.hire.open(HireTarget(person.id, person.displayName, person.categories), location, filters.service)
        },
        ask = { nav.navigate(Routes.newRequest(categoryId = selected?.id)) },
    )

    DetailScaffold(servicesTitle(selected), onBack = { nav.popBackStack() }) { padding ->
        PullToRefreshBox(
            isRefreshing = pulled && state.loading,
            onRefresh = {
                pulled = true
                viewModel.load()
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "hero") {
                    SearchHero(
                        viewModel = viewModel,
                        selected = selected,
                        location = location,
                        locating = phone.status == LocationStatus.Locating,
                        locationOpen = locationOpen,
                        onLocationOpen = { locationOpen = it },
                        onSearch = scrollToResults,
                    )
                }
                item(key = "services") {
                    ServiceChips(state.services(filters.service), state, filters.service) { slug ->
                        viewModel.update { it.copy(service = slug) }
                    }
                }
                item(key = "toolbar") {
                    ResultsToolbar(
                        filters = filters,
                        state = state,
                        selected = selected,
                        location = location,
                        onChange = viewModel::update,
                        onFilters = { filtersOpen = true },
                        onNeedLocation = {
                            locationOpen = true
                            scope.launch { listState.scrollToItem(0) }
                        },
                    )
                }
                results(viewModel, state, filters, location != null, actions)
                item(key = "ask") { AskNearby(peopleNoun(selected), actions.ask) }
            }
        }
    }

    if (filtersOpen) {
        FiltersSheet(
            filters = filters,
            located = location != null,
            total = state.results?.total,
            onChange = viewModel::update,
            onDismiss = { filtersOpen = false },
        )
    }
    hireForm?.let { HireSheet(it, viewModel.hire) }
}

/** What a result card and the empty state can do. */
private class ResultActions(
    val open: (WorkerResult) -> Unit,
    val chat: (WorkerResult) -> Unit,
    val hire: (WorkerResult) -> Unit,
    val ask: () -> Unit,
)

/** Title, the search box and where to search from, on the green band. */
@Composable
private fun SearchHero(
    viewModel: ServicesViewModel,
    selected: JobCategory?,
    location: PickedLocation?,
    locating: Boolean,
    locationOpen: Boolean,
    onLocationOpen: (Boolean) -> Unit,
    onSearch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.HeroGreen)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            servicesTitle(selected),
            style = HafiType.Title,
            color = Hafi.AccentFg,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            if (location != null) {
                "Nearest first. Open anyone to see their work and reviews."
            } else {
                "Share where you are to see who's closest."
            },
            style = HafiType.Body,
            color = Hafi.InkFg,
        )
        SearchBox(viewModel, location, locating, locationOpen, onLocationOpen, onSearch)
    }
}

@Composable
private fun SearchBox(
    viewModel: ServicesViewModel,
    location: PickedLocation?,
    locating: Boolean,
    locationOpen: Boolean,
    onLocationOpen: (Boolean) -> Unit,
    onSearch: () -> Unit,
) {
    val typed by viewModel.typed.collectAsStateWithLifecycle()
    val search = {
        viewModel.applySearch()
        onSearch()
    }
    Column(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(Hafi.Surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            HafiField(
                label = "Search by name, area or skill",
                value = typed,
                onValueChange = viewModel::type,
                modifier = Modifier.weight(1f),
                placeholder = "e.g. plumber, Kimironko",
                leadingIcon = R.drawable.ic_search,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
            )
            if (typed.isNotEmpty()) HafiIconButton(R.drawable.ic_x, "Clear search", viewModel::clearSearch)
        }
        LocationButton(location, locating, locationOpen) { onLocationOpen(!locationOpen) }
        if (locationOpen) {
            LocationPicker(
                value = location,
                onChange = { place ->
                    viewModel.setLocation(place)
                    onLocationOpen(false)
                },
                label = "Search near",
            )
            if (location != null) {
                HafiButton(
                    "Stop using my location",
                    {
                        viewModel.stopUsingLocation()
                        onLocationOpen(false)
                    },
                    kind = ButtonKind.Ghost,
                )
            }
        }
        HafiButton("Search", search, Modifier.fillMaxWidth(), icon = R.drawable.ic_search)
    }
}

/** "Near · Kimironko" or "Your location · Set your location"; opens the place picker. */
@Composable
private fun LocationButton(location: PickedLocation?, locating: Boolean, open: Boolean, onClick: () -> Unit) {
    val place = when {
        location != null -> location.label ?: "Your location"
        locating -> "Finding you…"
        else -> "Set your location"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .border(1.dp, Hafi.LineStrong)
            .clickable(onClickLabel = if (open) "Close" else "Change", role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIcon(
            if (location != null) R.drawable.ic_map_pin else R.drawable.ic_locate_fixed,
            tint = Hafi.AccentStrong,
        )
        Column(Modifier.weight(1f)) {
            Text(
                if (location != null) "NEAR" else "YOUR LOCATION",
                style = HafiType.Micro,
                color = Hafi.FgMuted,
            )
            Text(place, style = HafiType.BodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HIcon(if (open) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down, size = 16.dp, tint = Hafi.FgMuted)
    }
}

/** Every service as a chip with how many people match it under the other filters. */
@Composable
private fun ServiceChips(
    services: List<JobCategory>,
    state: ServicesState,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val counts = state.results?.categoryCounts
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            HafiChip(
                text = withCount("All services", state.results?.allCount),
                selected = selected == null,
                onClick = { onSelect(null) },
                icon = R.drawable.ic_layout_grid,
            )
        }
        items(services, key = { it.slug }) { service ->
            HafiChip(
                text = withCount(service.nameEn, counts?.let { it[service.slug] ?: 0 }),
                selected = selected == service.slug,
                onClick = { onSelect(service.slug) },
                icon = categoryIcon(service.icon),
            )
        }
    }
}

private fun withCount(label: String, count: Int?): String = if (count == null) label else "$label ($count)"

/** The count, the active filters as removable chips, and the Filters and Sort controls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultsToolbar(
    filters: BrowseFilters,
    state: ServicesState,
    selected: JobCategory?,
    location: PickedLocation?,
    onChange: ((BrowseFilters) -> BrowseFilters) -> Unit,
    onFilters: () -> Unit,
    onNeedLocation: () -> Unit,
) {
    val chips = filters.chips(location != null)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            state.results?.let { resultsCount(it.total, selected, location?.label) } ?: "Looking for people…",
            style = HafiType.Subheading,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        if (chips.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { chip -> RemovableChip(chip.label) { onChange { it.without(chip.key) } } }
                if (chips.size > 1) HafiButton("Clear all", { onChange { it.cleared() } }, kind = ButtonKind.Ghost)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            val count = filters.activeCount
            HafiButton(
                if (count > 0) "Filters ($count)" else "Filters",
                onFilters,
                Modifier.weight(1f),
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_sliders_horizontal,
            )
            SortSelect(filters, state, location != null, onChange, onNeedLocation, Modifier.weight(1f))
        }
    }
}

/** "Closest first" needs a location: choosing it without one opens the place picker instead. */
@Composable
private fun SortSelect(
    filters: BrowseFilters,
    state: ServicesState,
    located: Boolean,
    onChange: ((BrowseFilters) -> BrowseFilters) -> Unit,
    onNeedLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorts = SearchSort.entries
    val effective = filters.sort ?: state.results?.sort ?: if (located) SearchSort.Distance else SearchSort.Rating
    HafiSelect(
        label = "Sort by",
        options = sorts.map { if (it == SearchSort.Distance && !located) "${it.label} (set location)" else it.label },
        selectedIndex = sorts.indexOf(effective),
        onSelect = { index ->
            val sort = sorts[index]
            if (sort == SearchSort.Distance && !located) onNeedLocation() else onChange { it.copy(sort = sort) }
        },
        modifier = modifier,
    )
}

/** An active filter; tapping it removes the filter. */
@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Accent)
            .clickable(onClickLabel = "Remove filter $label", role = Role.Button, onClick = onRemove)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = HafiType.Label)
        HIcon(R.drawable.ic_x, size = 14.dp, tint = Hafi.FgMuted)
    }
}

/** Skeletons, the error panel, the empty state, or the cards with "Show more". */
private fun LazyListScope.results(
    viewModel: ServicesViewModel,
    state: ServicesState,
    filters: BrowseFilters,
    located: Boolean,
    actions: ResultActions,
) {
    val page = state.results
    val error = state.error
    when {
        page == null && error != null -> item(key = "error") {
            LoadError(error, filters.service != null, viewModel::load) {
                viewModel.update { it.copy(service = null) }
            }
        }
        page == null -> items(4, key = { "skeleton-$it" }) { WorkerCardSkeleton(Modifier.padding(horizontal = 16.dp)) }
        page.total == 0 -> item(key = "empty") { NoMatches(viewModel, filters, located, actions.ask) }
        else -> {
            if (error != null) {
                item(key = "stale") {
                    InfoBanner(
                        "Showing earlier results. $error",
                        Modifier.padding(horizontal = 16.dp),
                        tone = Tone.Warn,
                        action = { HafiButton("Try again", viewModel::load, kind = ButtonKind.Outline) },
                    )
                }
            }
            items(page.items, key = { it.id }) { person ->
                WorkerCard(
                    person = person,
                    onOpen = { actions.open(person) },
                    onChat = { actions.chat(person) },
                    onHire = { actions.hire(person) },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .alpha(if (state.loading) 0.6f else 1f),
                )
            }
            moreResults(viewModel, state, page.total - page.items.size)
        }
    }
}

private fun LazyListScope.moreResults(viewModel: ServicesViewModel, state: ServicesState, left: Int) {
    if (state.moreLoading) {
        items(2, key = { "more-skeleton-$it" }) { WorkerCardSkeleton(Modifier.padding(horizontal = 16.dp)) }
        return
    }
    if (left <= 0) return
    item(key = "more") {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormAlert(state.moreError)
            HafiButton(
                "Show more ($left more)",
                viewModel::more,
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Outline,
                enabled = !state.loading,
            )
        }
    }
}

@Composable
private fun LoadError(message: String, serviceChosen: Boolean, onRetry: () -> Unit, onAllServices: () -> Unit) {
    EmptyState(
        title = "We couldn't load this list",
        description = message,
        modifier = Modifier.padding(horizontal = 16.dp),
        icon = R.drawable.ic_triangle_alert,
        action = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HafiButton("Try again", onRetry, icon = R.drawable.ic_refresh_cw)
                if (serviceChosen) HafiButton("Show all services", onAllServices, kind = ButtonKind.Outline)
            }
        },
    )
}

/** Nobody matches: widen, clear, drop the service, or let us ask the closest people. */
@Composable
private fun NoMatches(viewModel: ServicesViewModel, filters: BrowseFilters, located: Boolean, onAsk: () -> Unit) {
    val km = filters.km
    EmptyState(
        title = "No one matches yet",
        description = "Try fewer filters or a wider area. Or tell us what you need and we'll ask the people " +
            "closest to you.",
        modifier = Modifier.padding(horizontal = 16.dp),
        icon = R.drawable.ic_search_x,
        action = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (located && km != null && km < FilterOptions.WidestKm) {
                    HafiButton(
                        "Widen to ${FilterOptions.WidestKm} km",
                        { viewModel.update { it.copy(km = FilterOptions.WidestKm) } },
                        kind = ButtonKind.Outline,
                    )
                }
                if (filters.activeCount > 0) {
                    HafiButton("Clear filters", { viewModel.update { it.cleared() } }, kind = ButtonKind.Outline)
                } else if (filters.service != null) {
                    HafiButton(
                        "Show all services",
                        { viewModel.update { it.copy(service = null) } },
                        kind = ButtonKind.Outline,
                    )
                }
                HafiButton("Ask nearby people", onAsk, icon = R.drawable.ic_arrow_right)
            }
        },
    )
}

/** The other way to hire: say what you need once and let the engine ask the closest people. */
@Composable
private fun AskNearby(noun: String, onAsk: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .fillMaxWidth()
            .background(Hafi.Ink)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Rather not choose yourself?",
            style = HafiType.Heading,
            color = Hafi.InkFg,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Say what you need once. We ask the closest $noun who are free, and you pick from the ones who say yes.",
            style = HafiType.Body,
            color = Hafi.InkMuted,
        )
        HafiButton(
            "Ask nearby $noun",
            onAsk,
            Modifier.padding(top = 8.dp),
            icon = R.drawable.ic_arrow_right,
        )
    }
}
