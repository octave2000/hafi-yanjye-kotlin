package com.hafiyanjye.app.ui.pings

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.api.Ping
import com.hafiyanjye.app.data.pings.Availability
import com.hafiyanjye.app.data.pings.PingsApi
import com.hafiyanjye.app.data.pings.RankingBreakdown
import com.hafiyanjye.app.data.pings.Review
import com.hafiyanjye.app.data.pings.WorkProfile
import com.hafiyanjye.app.data.pings.WorkStats
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.Avatar
import com.hafiyanjye.app.ui.kit.ButtonSize
import com.hafiyanjye.app.ui.kit.EmptyState
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.LocalToaster
import com.hafiyanjye.app.ui.kit.PageHeader
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.kit.StarRating
import com.hafiyanjye.app.ui.kit.TabStrip
import com.hafiyanjye.app.ui.nav.Routes
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion

private val PagePadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)

private const val TAB_ACTIVE = 0
private const val TAB_HISTORY = 1

/** The job seeker's home: pings to answer, jobs they were chosen for, and their work dashboard. */
@Composable
fun PingsScreen(onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel = containerViewModel { PingsViewModel(PingsApi(it.client), it.hub) }
    val toaster = LocalToaster.current
    LaunchedEffect(viewModel) { viewModel.notices.collect { toaster.show(it.message, it.tone) } }
    // Coming back (from setup, a chat, the trip, another app): show what is true now, then keep polling.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshAll()
            viewModel.keepPingsFresh()
        }
    }

    val profile by viewModel.profile.state.collectAsStateWithLifecycle()
    when (val current = profile) {
        Load.Loading -> DashboardSkeleton(modifier)

        is Load.Failed -> Column(
            modifier = modifier
                .fillMaxSize()
                .padding(PagePadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PageHeader(title = "Your work")
            ErrorPanel("Couldn't load your job seeker profile. ${current.message}", viewModel.profile::refresh)
        }

        is Load.Ready -> {
            val workProfile = current.value
            if (workProfile == null) {
                SetupIntro(onSetUp = { onNavigate(Routes.WorkSetup) }, modifier = modifier)
            } else {
                Dashboard(workProfile, current.staleError, viewModel, onNavigate, modifier)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    profile: WorkProfile,
    profileError: String?,
    viewModel: PingsViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileLoad by viewModel.profile.state.collectAsStateWithLifecycle()
    val active by viewModel.active.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val stats by viewModel.stats.state.collectAsStateWithLifecycle()
    val ranking by viewModel.ranking.state.collectAsStateWithLifecycle()
    val reviews by viewModel.reviews.state.collectAsStateWithLifecycle()
    val allIcons by viewModel.categoryIcons.state.collectAsStateWithLifecycle()
    val pending by viewModel.pendingAvailability.collectAsStateWithLifecycle()
    val fresh by viewModel.fresh.collectAsStateWithLifecycle()
    val reducedMotion = rememberReducedMotion()
    var tab by rememberSaveable { mutableIntStateOf(TAB_ACTIVE) }
    LaunchedEffect(tab) { if (tab == TAB_HISTORY) viewModel.showHistory() }

    // The pull spinner shows for a pull only, not for every live update in the background.
    var pulled by remember { mutableStateOf(false) }
    val profileRefreshing = (profileLoad as? Load.Ready)?.refreshing == true
    val activeRefreshing = (active as? Load.Ready)?.refreshing == true
    LaunchedEffect(profileRefreshing, activeRefreshing) {
        if (!profileRefreshing && !activeRefreshing) pulled = false
    }

    val availability = pending ?: profile.availability
    val activePings = (active as? Load.Ready)?.value.orEmpty()
    val habit = (stats as? Load.Ready)?.value
    val banner = habitBanner(availability, habit)
    val icons = remember(profile.categories, allIcons) {
        (allIcons as? Load.Ready)?.value.orEmpty() + profile.categories.associate { it.nameEn to it.icon }
    }
    val goOnline = { viewModel.setAvailability(Availability.Online) }
    val pingList = PingList(icons, fresh, reducedMotion, viewModel::open)

    PullToRefreshBox(
        isRefreshing = pulled,
        onRefresh = {
            pulled = true
            viewModel.refreshAll()
        },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PagePadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            profileError?.let { message ->
                item(key = "profile-stale") {
                    ErrorPanel("Couldn't refresh your job seeker profile. $message", viewModel.profile::refresh)
                }
            }
            if (banner != null && habit != null) {
                item(key = "banner") {
                    HabitBannerCard(banner, habit.missedWhileOffline, pending != null, goOnline)
                }
            }
            item(key = "identity") {
                Identity(profile, availability, pending != null, viewModel::setAvailability, onNavigate)
            }

            // Jobs they were chosen for: the most important thing on screen.
            items(chosenPings(activePings), key = { "chosen-${it.id}" }) { ping ->
                MatchedPing(ping, onNavigate, listMotion(reducedMotion))
            }

            item(key = "tabs") {
                TabStrip(
                    tabs = listOf("New pings", "History"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    counts = listOf(unreadCount(activePings).takeIf { it > 0 }, null),
                )
            }
            if (tab == TAB_ACTIVE) {
                activeTab(active, profile, availability, pingList, viewModel.active::refresh, goOnline)
            } else {
                historyTab(history, pingList, viewModel::refreshHistory)
            }

            weekSection(stats, viewModel.stats::refresh)
            item(key = "service") { ServiceCard(profile) }
            habitSection(stats)
            rankingSection(ranking, viewModel.ranking::refresh)
            if (profile.completenessScore < STRONG_SCORE) {
                item(key = "completeness") {
                    CompletenessCard(profile.completenessScore, profile.completenessTips) {
                        onNavigate(Routes.WorkSetup)
                    }
                }
            }
            reviewsSection(profile, reviews, viewModel.reviews::refresh)
        }
    }

    AnswerSheetHost(viewModel, activePings, history, onNavigate)
}

/** The sheet over whichever list the ping came from; it keeps the last copy while lists reload. */
@Composable
private fun AnswerSheetHost(
    viewModel: PingsViewModel,
    activePings: List<Ping>,
    history: Load<List<Ping>>,
    onNavigate: (String) -> Unit,
) {
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val openedPing by viewModel.openedPing.collectAsStateWithLifecycle()
    val state = sheet ?: return
    val ping = activePings.firstOrNull { it.id == state.pingId }
        ?: (history as? Load.Ready)?.value?.firstOrNull { it.id == state.pingId }
        ?: openedPing?.takeIf { it.id == state.pingId }
        ?: return
    AnswerSheet(
        ping = ping,
        state = state,
        onAvailable = { quote, note -> viewModel.answerAvailable(ping.id, quote, note) },
        onNotNow = { viewModel.answerNotNow(ping.id) },
        onWithdraw = { viewModel.withdraw(ping.id) },
        onClearError = viewModel::clearSheetError,
        onNavigate = onNavigate,
        onDismiss = viewModel::closeSheet,
    )
}

/** Name, services, rating, the online switch, and the ways to edit or see the public profile. */
@Composable
private fun Identity(
    profile: WorkProfile,
    availability: Availability,
    pending: Boolean,
    onAvailability: (Availability) -> Unit,
    onNavigate: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Avatar(profile.displayName, profile.picture, size = 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PageHeader(
                    title = profile.displayName,
                    description = profile.categories.joinToString(" · ") { it.nameEn }.ifBlank { null },
                )
                if (profile.ratingCount > 0) {
                    StarRating(profile.ratingAvg, count = profile.ratingCount)
                } else {
                    Text("No ratings yet", style = HafiType.Caption, color = Hafi.FgMuted)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvailabilityControl(availability, pending, onAvailability, Modifier.weight(1f))
            HafiIconButton(
                R.drawable.ic_pencil,
                "Edit work profile",
                onClick = { onNavigate(Routes.WorkSetup) },
            )
            HafiIconButton(
                R.drawable.ic_external_link,
                "View my public profile",
                onClick = { onNavigate(Routes.person(profile.id)) },
            )
        }
    }
}

/** What every list of ping cards needs. */
private class PingList(
    val icons: Map<String, String>,
    val fresh: Set<Int>,
    val reducedMotion: Boolean,
    val onOpen: (Ping) -> Unit,
)

/** Items enter, leave and move briefly; nothing moves with reduced motion. */
private fun LazyItemScope.listMotion(reducedMotion: Boolean): Modifier = if (reducedMotion) {
    Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
} else {
    Modifier.animateItem(fadeInSpec = tween(180), placementSpec = tween(220), fadeOutSpec = tween(150))
}

private fun LazyListScope.pingCards(pings: List<Ping>, list: PingList, keyPrefix: String) {
    items(pings, key = { "$keyPrefix-${it.id}" }) { ping ->
        val isFresh = ping.id in list.fresh
        PingCard(
            ping = ping,
            icon = list.icons[ping.categoryName],
            onOpen = list.onOpen,
            // A ping that just arrived is announced as well as tinted.
            modifier = listMotion(list.reducedMotion)
                .semantics { if (isFresh) liveRegion = LiveRegionMode.Polite },
            fresh = isFresh,
        )
    }
}

private fun LazyListScope.activeTab(
    active: Load<List<Ping>>,
    profile: WorkProfile,
    availability: Availability,
    list: PingList,
    onRetry: () -> Unit,
    goOnline: () -> Unit,
) {
    when (active) {
        Load.Loading -> pingSkeletons()

        is Load.Failed -> item(key = "pings-error") {
            ErrorPanel("Couldn't load pings. ${active.message}", onRetry)
        }

        is Load.Ready -> {
            active.staleError?.let { message ->
                item(key = "pings-stale") { ErrorPanel("Couldn't refresh your pings. $message", onRetry) }
            }
            val open = otherPings(active.value)
            if (open.isNotEmpty()) {
                pingCards(open, list, "ping")
            } else {
                item(key = "pings-empty") { NoOpenPings(profile, availability, goOnline) }
            }
        }
    }
}

@Composable
private fun NoOpenPings(profile: WorkProfile, availability: Availability, goOnline: () -> Unit) {
    if (availability == Availability.Online) {
        val service = profile.categories.firstOrNull()?.nameEn ?: "service"
        EmptyState(
            icon = R.drawable.ic_radio,
            title = "You're online",
            description = "We'll ping you when someone nearby needs ${withArticle(service)}. " +
                "Keep this screen open or turn on notifications.",
        )
    } else {
        EmptyState(
            icon = R.drawable.ic_moon,
            title = "Go online to get jobs",
            description = "While you're offline, nobody nearby can ping you.",
            action = { HafiButton("Go online", goOnline) },
        )
    }
}

private fun LazyListScope.historyTab(history: Load<List<Ping>>, list: PingList, onRetry: () -> Unit) {
    when (history) {
        Load.Loading -> pingSkeletons()

        is Load.Failed -> item(key = "history-error") {
            ErrorPanel("Couldn't load past pings. ${history.message}", onRetry)
        }

        is Load.Ready -> {
            history.staleError?.let { message ->
                item(key = "history-stale") { ErrorPanel("Couldn't refresh past pings. $message", onRetry) }
            }
            if (history.value.isNotEmpty()) {
                pingCards(history.value, list, "past")
            } else {
                item(key = "history-empty") {
                    EmptyState(
                        icon = R.drawable.ic_inbox,
                        title = "No past pings yet",
                        description = "Jobs you answered, missed or won will show up here.",
                    )
                }
            }
        }
    }
}

private fun LazyListScope.weekSection(stats: Load<WorkStats>, onRetry: () -> Unit) {
    when (stats) {
        Load.Loading -> item(key = "week-skeleton") {
            Skeleton(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            )
        }

        is Load.Ready -> {
            stats.staleError?.let { message ->
                item(key = "stats-stale") { ErrorPanel("Couldn't refresh your work stats. $message", onRetry) }
            }
            item(key = "week") { WeekCard(stats.value.week) }
        }

        is Load.Failed -> item(key = "stats-error") {
            ErrorPanel("Couldn't load your work stats. ${stats.message}", onRetry)
        }
    }
}

/** Reply time, answer rate, streak and level; on failure the week section already offers a retry. */
private fun LazyListScope.habitSection(stats: Load<WorkStats>) {
    when (stats) {
        Load.Loading -> item(key = "stats-skeleton") {
            Skeleton(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            )
        }

        is Load.Ready -> {
            item(key = "stats") { StatsRow(stats.value) }
            item(key = "level") {
                LevelCard(stats.value.level, stats.value.nextLevel, stats.value.nextLevelChecklist)
            }
        }

        is Load.Failed -> Unit
    }
}

private fun LazyListScope.rankingSection(ranking: Load<RankingBreakdown>, onRetry: () -> Unit) {
    when (ranking) {
        Load.Loading -> item(key = "ranking-skeleton") {
            Skeleton(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            )
        }

        is Load.Ready -> {
            ranking.staleError?.let { message ->
                item(key = "ranking-stale") { ErrorPanel("Couldn't refresh your ranking. $message", onRetry) }
            }
            ranking.value.starter?.let { starter -> item(key = "starter") { StarterPathCard(starter) } }
            item(key = "ranking") { RankingCard(ranking.value) }
        }

        is Load.Failed -> item(key = "ranking-error") {
            ErrorPanel("Couldn't load your ranking. ${ranking.message}", onRetry)
        }
    }
}

private fun LazyListScope.reviewsSection(profile: WorkProfile, reviews: Load<List<Review>>, onRetry: () -> Unit) {
    item(key = "reviews-heading") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HIcon(R.drawable.ic_badge_check, size = 20.dp, tint = Hafi.AccentStrong)
            Text(
                "Reviews",
                style = HafiType.Heading,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (profile.ratingCount > 0) StarRating(profile.ratingAvg, count = profile.ratingCount)
        }
    }
    when (reviews) {
        Load.Loading -> items(2, key = { "review-skeleton-$it" }) {
            Skeleton(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            )
        }

        is Load.Failed -> item(key = "reviews-error") {
            ErrorPanel("Couldn't load your reviews. ${reviews.message}", onRetry)
        }

        is Load.Ready -> if (reviews.value.isEmpty()) {
            item(key = "reviews-empty") {
                EmptyState(
                    icon = R.drawable.ic_message_square_quote,
                    title = "No reviews yet",
                    description = "Finish a job well and your first review shows up here.",
                )
            }
        } else {
            items(reviews.value, key = { "review-${it.id}" }) { ReviewCard(it) }
        }
    }
}

private fun LazyListScope.pingSkeletons() {
    items(3, key = { "ping-skeleton-$it" }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Hafi.Surface)
                .border(1.dp, Hafi.Line)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Skeleton(
                Modifier
                    .width(110.dp)
                    .height(14.dp),
            )
            Skeleton(
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(16.dp),
            )
            Skeleton(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp),
            )
        }
    }
}

@Composable
private fun DashboardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PagePadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Skeleton(Modifier.size(56.dp))
            Skeleton(
                Modifier
                    .width(180.dp)
                    .height(28.dp),
            )
        }
        Skeleton(
            Modifier
                .fillMaxWidth()
                .height(64.dp),
        )
        repeat(3) {
            Skeleton(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )
        }
    }
}

private val SETUP_STEPS = listOf(
    "Pick what you do: up to 3 services",
    "Set where you work and how far you'll go",
    "Add your price, phone and a short bio",
)

/** No work profile yet: what it is for, and the one way forward. */
@Composable
private fun SetupIntro(onSetUp: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PagePadding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Hafi.Surface)
                .border(1.dp, Hafi.Line),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Hafi.AccentSoft)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HIcon(R.drawable.ic_briefcase, size = 32.dp, tint = Hafi.AccentStrong)
                PageHeader(
                    title = "Get paid jobs from your neighbours",
                    description = "People in Kimironko, Remera, Nyamirambo and across Kigali post what they need. " +
                        "Set up your work profile and we'll ping you when a job is close by.",
                )
            }
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SETUP_STEPS.forEachIndexed { index, step -> SetupStep(index + 1, step) }
                HafiButton(
                    "Set up my work profile",
                    onSetUp,
                    Modifier.fillMaxWidth(),
                    size = ButtonSize.Large,
                )
                Text(
                    "Takes about 3 minutes. Free to join.",
                    style = HafiType.Caption,
                    color = Hafi.FgMuted,
                )
            }
        }
    }
}

@Composable
private fun SetupStep(number: Int, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 28.dp, minHeight = 28.dp)
                .background(Hafi.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = HafiType.Caption, color = Hafi.Fg)
        }
        Text(text, style = HafiType.Body, modifier = Modifier.weight(1f))
    }
}
