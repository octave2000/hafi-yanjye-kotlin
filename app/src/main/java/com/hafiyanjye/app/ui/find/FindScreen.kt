package com.hafiyanjye.app.ui.find

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.core.jobStatusView
import com.hafiyanjye.app.data.api.JobListItem
import com.hafiyanjye.app.data.find.FindApi
import com.hafiyanjye.app.data.find.FindCategory
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.Badge
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.ButtonSize
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.PulseDot
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.kit.categoryIcon
import com.hafiyanjye.app.ui.location.LocationPicker
import com.hafiyanjye.app.ui.location.LocationStatus
import com.hafiyanjye.app.ui.location.PickedLocation
import com.hafiyanjye.app.ui.location.rememberCurrentLocation
import com.hafiyanjye.app.ui.nav.LocalMe
import com.hafiyanjye.app.ui.nav.Routes
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion

private const val RecentCount = 3

/**
 * The job provider's first screen (web: /find): one search box, the request that
 * is running, recent requests to ask again, popular services and rentals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindScreen(nav: NavHostController, saved: SavedStateHandle) {
    val vm = containerViewModel { FindViewModel(FindApi(it.client), it.hub, saved) }
    val search by vm.state.collectAsStateWithLifecycle()
    val categories by vm.categories.state.collectAsStateWithLifecycle()
    val requests by vm.requests.state.collectAsStateWithLifecycle()
    val nearby by vm.nearby.line.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val searchFocus = remember { FocusRequester() }
    var handledFocus by rememberSaveable { mutableIntStateOf(search.focusRequests) }

    // Location already allowed: fill "Where" quietly. Never asks for the permission here.
    val here = rememberCurrentLocation(auto = search.location == null)
    LaunchedEffect(here.location) { here.location?.let { vm.location(it, fromPanel = false) } }
    LaunchedEffect(Unit) { vm.navigation.collect { route -> nav.navigate(route) } }
    LaunchedEffect(search.focusRequests) {
        if (search.focusRequests == handledFocus) return@LaunchedEffect
        handledFocus = search.focusRequests
        list.animateScrollToItem(0)
        searchFocus.requestFocus()
    }

    val services = (categories as? Load.Ready)?.value ?: FallbackCategories
    PullToRefreshBox(
        isRefreshing = (requests as? Load.Ready)?.refreshing == true,
        onRefresh = vm::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "search") {
                SearchSection(
                    search = search,
                    picked = vm.category(),
                    examples = searchExamples(services),
                    nearby = nearby,
                    finding = here.status == LocationStatus.Locating,
                    focusRequester = searchFocus,
                    onText = vm::text,
                    onSubmit = vm::submit,
                    onToggleWhere = vm::toggleWhere,
                    onPlace = { vm.location(it) },
                    onSeePeople = { nav.navigate(Routes.services(categoryId = it.id)) },
                )
            }
            requestItems(
                requests = requests,
                onOpen = { nav.navigate(Routes.request(it)) },
                onSeeAll = { nav.navigate(Routes.Jobs) },
                onAskAgain = { vm.askAgain(it.categoryName) },
                onRetry = vm.requests::refresh,
            )
            serviceItems(
                services = tileServices(services, keep = search.categorySlug),
                selectedSlug = search.categorySlug,
                staleError = (categories as? Load.Failed)?.message,
                onPick = vm::pickCategory,
                onMore = { nav.navigate(Routes.services()) },
                onRetry = vm.categories::refresh,
            )
            item(key = "rentals") {
                RentalSection(onKind = { kind -> nav.navigate(Routes.rentalSearch(kind = kind)) })
            }
        }
    }
}

@Composable
private fun SearchSection(
    search: SearchState,
    picked: FindCategory?,
    examples: List<String>,
    nearby: NearbyLine,
    finding: Boolean,
    focusRequester: FocusRequester,
    onText: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleWhere: () -> Unit,
    onPlace: (PickedLocation) -> Unit,
    onSeePeople: (FindCategory) -> Unit,
) {
    val firstName = LocalMe.current.name.orEmpty().trim().split(Regex("\\s+")).firstOrNull().orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (firstName.isEmpty()) "What do you need?" else "What do you need, $firstName?",
                style = HafiType.Title,
                color = Hafi.Fg,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Say it in your own words. We ask people nearby and bring you the best one, " +
                    "usually within minutes.",
                style = HafiType.Body,
                color = Hafi.FgMuted,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Hafi.Surface)
                .border(1.dp, Hafi.Line)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SearchBox(
                value = search.text,
                onValueChange = onText,
                onSubmit = onSubmit,
                examples = examples,
                invalid = search.error?.field == SearchField.Need,
                focusRequester = focusRequester,
            )
            WherePill(
                value = search.location?.label ?: if (finding) "Finding you…" else "Add area",
                empty = search.location == null,
                open = search.whereOpen,
                attention = search.error?.field == SearchField.Where,
                onClick = onToggleWhere,
            )
            WherePanel(open = search.whereOpen, onPlace = onPlace)
            HafiButton(
                text = "Find help",
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                size = ButtonSize.Large,
                loading = search.asking,
                loadingText = "Reading your request…",
                icon = R.drawable.ic_arrow_right,
            )
            search.error?.let { FieldProblem(it.message) }
        }
        picked?.let { service ->
            HafiButton(
                text = "See people offering ${service.nameEn.lowercase()}",
                onClick = { onSeePeople(service) },
                kind = ButtonKind.Ghost,
                icon = R.drawable.ic_list,
            )
        }
        NearbyLineView(nearby)
    }
}

/** The place picker under the Where pill; it opens downwards from the pill. */
@Composable
private fun WherePanel(open: Boolean, onPlace: (PickedLocation) -> Unit) {
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible = open,
        enter = if (reduced) fadeIn() else fadeIn() + expandVertically(),
        exit = if (reduced) fadeOut() else fadeOut() + shrinkVertically(),
    ) {
        // Always empty: the panel is for choosing a new place; the pill shows the current one.
        LocationPicker(
            value = null,
            onChange = onPlace,
            label = "Where should they come?",
            hint = "We only share the exact spot with the person you hire.",
        )
    }
}

private fun LazyListScope.requestItems(
    requests: Load<List<JobListItem>>,
    onOpen: (Int) -> Unit,
    onSeeAll: () -> Unit,
    onAskAgain: (JobListItem) -> Unit,
    onRetry: () -> Unit,
) {
    when (requests) {
        Load.Loading -> {
            item(key = "recent-title") { RecentTitle(onSeeAll) }
            items(2, key = { "recent-skeleton-$it" }) {
                Skeleton(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                )
            }
        }
        is Load.Failed -> item(key = "recent-error") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RecentTitle(onSeeAll)
                ErrorPanel(requests.message, onRetry)
            }
        }
        is Load.Ready -> {
            // The newest request still running gets its own card on top.
            val live = requests.value.firstOrNull { jobStatusView(it.status, it.state).live }
            val recent = requests.value.filter { it.id != live?.id }.take(RecentCount)
            if (live != null) item(key = "live-${live.id}") { LiveRequestCard(live, onOpen = { onOpen(live.id) }) }
            requests.staleError?.let { message -> item(key = "recent-stale") { ErrorPanel(message, onRetry) } }
            if (recent.isNotEmpty()) {
                item(key = "recent-title") { RecentTitle(onSeeAll) }
                items(recent, key = { "recent-${it.id}" }) { job ->
                    RecentRequestRow(job, onOpen = { onOpen(job.id) }, onAskAgain = { onAskAgain(job) })
                }
            }
        }
    }
}

@Composable
private fun RecentTitle(onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FindHeading("Your recent requests", modifier = Modifier.weight(1f))
        HafiButton(text = "See all", onClick = onSeeAll, kind = ButtonKind.Ghost)
    }
}

/** "Your plumber request is running": the request in progress, one tap from its live screen. */
@Composable
private fun LiveRequestCard(job: JobListItem, onOpen: () -> Unit) {
    val status = jobStatusView(job.status, job.state)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(Hafi.Surface)
            .border(if (focused) 3.dp else 1.dp, if (focused) Hafi.Accent else Hafi.Accent.copy(alpha = 0.4f))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HIcon(categoryIcon(job.categoryIcon), size = 24.dp, tint = Hafi.AccentStrong)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulseDot(Hafi.Accent)
                    Text(text = status.label.uppercase(), style = HafiType.Micro, color = Hafi.AccentStrong)
                }
                Text(
                    text = "Your ${job.categoryName.lowercase()} request is running",
                    style = HafiType.BodyStrong,
                    color = Hafi.Fg,
                )
                Text(
                    text = "Started ${Format.timeAgo(job.createdAt)}" + job.addressLabel?.let { " · $it" }.orEmpty(),
                    style = HafiType.Caption,
                    color = Hafi.FgMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(Hafi.Accent),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Open", style = HafiType.Button, color = Hafi.AccentFg)
            HIcon(R.drawable.ic_arrow_right, size = 16.dp, tint = Hafi.AccentFg)
        }
    }
}

@Composable
private fun RecentRequestRow(job: JobListItem, onOpen: () -> Unit, onAskAgain: () -> Unit) {
    val status = jobStatusView(job.status, job.state)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIcon(categoryIcon(job.categoryIcon), size = 22.dp, tint = Hafi.AccentStrong)
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = job.summary,
                style = HafiType.BodyStrong,
                color = Hafi.Fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge(status.label, status.tone, live = status.live)
                Text(
                    text = "${job.categoryName} · ${Format.timeAgo(job.createdAt)}",
                    style = HafiType.Caption,
                    color = Hafi.FgMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HafiButton(
            text = "Ask again",
            onClick = onAskAgain,
            modifier = Modifier.semantics { contentDescription = "Ask again for ${job.categoryName}" },
            kind = ButtonKind.Outline,
            icon = R.drawable.ic_rotate_ccw,
        )
    }
}

private fun LazyListScope.serviceItems(
    services: List<FindCategory>,
    selectedSlug: String?,
    staleError: String?,
    onPick: (FindCategory) -> Unit,
    onMore: () -> Unit,
    onRetry: () -> Unit,
) {
    item(key = "popular-title") {
        Column(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FindHeading("Popular right now")
            Text("One tap is enough. You can add details after.", style = HafiType.Body, color = Hafi.FgMuted)
        }
    }
    // The fallback tiles keep the screen useful; the retry says the real list is missing.
    staleError?.let { message -> item(key = "popular-error") { ErrorPanel(message, onRetry) } }
    val popular = services.take(PopularServiceCount)
    val moreCount = services.size - popular.size
    val cells: List<FindCategory?> = popular + listOf(null)
    items(cells.chunked(2), key = { row -> "popular-" + row.joinToString { it?.slug ?: "more" } }) { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.forEach { service ->
                val cell = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                if (service == null) {
                    MoreServicesCard(moreCount = moreCount, onClick = onMore, modifier = cell)
                } else {
                    ServiceCard(service, selected = service.slug == selectedSlug, onClick = { onPick(service) }, cell)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RentalSection(onKind: (String) -> Unit) {
    Column(
        modifier = Modifier.padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FindHeading("Renting instead?")
            Text("Cars and homes near you, nearest first.", style = HafiType.Body, color = Hafi.FgMuted)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val cell = Modifier
                .weight(1f)
                .fillMaxHeight()
            RentalCard(
                title = "Rent a car",
                description = "Cars by the day, with or without a driver",
                icon = R.drawable.ic_car,
                onClick = { onKind("car") },
                modifier = cell,
            )
            RentalCard(
                title = "Find a home",
                description = "Apartments, houses and rooms, by the night or month",
                icon = R.drawable.ic_house,
                onClick = { onKind("home") },
                modifier = cell,
            )
        }
    }
}
