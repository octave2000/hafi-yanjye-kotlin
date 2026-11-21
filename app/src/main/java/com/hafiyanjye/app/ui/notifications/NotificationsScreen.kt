package com.hafiyanjye.app.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.Badges
import com.hafiyanjye.app.data.profile.Notification
import com.hafiyanjye.app.data.profile.Notifications
import com.hafiyanjye.app.data.profile.ProfileApi
import com.hafiyanjye.app.data.realtime.RealtimeEvent
import com.hafiyanjye.app.data.realtime.RealtimeHub
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.Loader
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.EmptyState
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.nav.Routes
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The inbox. As on the web, seeing it marks everything read; the ones that
 * were unread stay marked "New" for this visit so the person can tell them apart.
 */
class NotificationsViewModel(
    private val api: ProfileApi,
    hub: RealtimeHub,
    private val badges: Badges,
) : ViewModel() {

    val inbox = Loader(viewModelScope) { api.notifications() }
        .refreshOn(hub.resyncs)
        .refreshOn(hub.events.filter { it.type == RealtimeEvent.Notification })

    private val _fresh = MutableStateFlow<Set<Int>>(emptySet())

    /** Ids that were unread when they were shown on this visit. */
    val fresh: StateFlow<Set<Int>> = _fresh.asStateFlow()

    private var marking = false

    /** Called while the screen is showing [shown]: the engine and the bell learn they have been seen. */
    fun seen(shown: Notifications) {
        val unread = shown.items.filter { it.unread }.map { it.id }
        _fresh.update { it + unread }
        badges.notificationsUnread.value = shown.unread
        if (shown.unread == 0 || marking) return
        marking = true
        viewModelScope.launch {
            try {
                val read = api.markRead()
                badges.notificationsUnread.value = read.unread
                inbox.mutate { read }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Like the web: nothing to tell the person; they stay unread and the next load tries again.
            } finally {
                marking = false
            }
        }
    }
}

/** Job updates, matches and messages, newest first (web: the bell's sheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(nav: NavHostController) {
    val viewModel = containerViewModel { NotificationsViewModel(ProfileApi(it.client), it.hub, it.badges) }
    val state by viewModel.inbox.state.collectAsStateWithLifecycle()
    val fresh by viewModel.fresh.collectAsStateWithLifecycle()

    // This composable only runs while the inbox is on screen, so "shown" really means seen.
    val shown = (state as? Load.Ready)?.value
    LaunchedEffect(shown) {
        if (shown != null) viewModel.seen(shown)
    }

    val open: (Notification) -> Unit = { item ->
        val route = Routes.fromWebPath(item.url)
        when {
            route == null -> Unit
            route in Routes.Tabs -> nav.navigate(route) {
                // The same move as pressing that tab: the inbox does not stay underneath.
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            else -> nav.navigate(route) { launchSingleTop = true }
        }
    }

    DetailScaffold(title = "Notifications", onBack = { nav.popBackStack() }) { padding ->
        PullToRefreshBox(
            isRefreshing = (state as? Load.Ready)?.refreshing == true,
            onRefresh = viewModel.inbox::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "intro") {
                    Text(
                        "Job updates, matches and messages.",
                        style = HafiType.Body,
                        color = Hafi.FgMuted,
                    )
                }
                when (val load = state) {
                    Load.Loading -> items(4, key = { "skeleton-$it" }) {
                        Skeleton(
                            Modifier
                                .fillMaxWidth()
                                .height(84.dp),
                        )
                    }

                    is Load.Failed -> item(key = "error") {
                        ErrorPanel(
                            "Couldn't load notifications. ${load.message}",
                            onRetry = viewModel.inbox::refresh,
                        )
                    }

                    is Load.Ready -> inboxItems(
                        load = load,
                        fresh = fresh,
                        onOpen = open,
                        onRefresh = viewModel.inbox::refresh,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.inboxItems(
    load: Load.Ready<Notifications>,
    fresh: Set<Int>,
    onOpen: (Notification) -> Unit,
    onRefresh: () -> Unit,
) {
    load.staleError?.let { message ->
        item(key = "stale") { InfoBanner("Showing what we had. $message", tone = Tone.Warn) }
    }
    val items = load.value.items
    if (items.isEmpty()) {
        item(key = "empty") {
            EmptyState(
                title = "No notifications yet",
                description = "We'll let you know when someone responds or a job comes up nearby.",
                icon = R.drawable.ic_bell_off,
                action = { HafiButton("Check again", onRefresh, kind = ButtonKind.Outline) },
            )
        }
        return
    }
    items(items, key = { it.id }) { item ->
        val reducedMotion = rememberReducedMotion()
        NotificationRow(
            item = item,
            isNew = item.unread || item.id in fresh,
            onOpen = { onOpen(item) },
            modifier = if (reducedMotion) Modifier else Modifier.animateItem(),
        )
    }
}

@Composable
private fun NotificationRow(
    item: Notification,
    isNew: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only engine paths the app has a screen for are links; the rest are plain rows, as on the web.
    val leads = Routes.fromWebPath(item.url) != null
    val clickable = if (leads) {
        Modifier.clickable(role = Role.Button, onClickLabel = "Open", onClick = onOpen)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isNew) Hafi.AccentSoft else Hafi.Surface)
            .border(1.dp, if (isNew) Hafi.Accent else Hafi.Line)
            .then(clickable)
            .semantics(mergeDescendants = true) {}
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A second cue next to the weight and the word "New".
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(8.dp)
                .background(if (isNew) Hafi.Accent else Hafi.Surface, CircleShape),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                item.title,
                style = if (isNew) HafiType.BodyStrong else HafiType.Label,
                color = Hafi.Fg,
            )
            if (item.body.isNotBlank()) {
                Text(
                    item.body,
                    style = HafiType.Caption,
                    color = Hafi.FgMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                (if (isNew) "New · " else "") + Format.timeAgo(item.createdAt),
                style = HafiType.Caption,
                color = Hafi.FgSubtle,
            )
        }
        if (leads) {
            HIcon(
                R.drawable.ic_chevron_right,
                size = 18.dp,
                tint = Hafi.FgSubtle,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
