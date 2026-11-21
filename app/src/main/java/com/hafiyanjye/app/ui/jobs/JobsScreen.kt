package com.hafiyanjye.app.ui.jobs

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.core.jobStatusView
import com.hafiyanjye.app.data.api.HafiApi
import com.hafiyanjye.app.data.api.JobListItem
import com.hafiyanjye.app.data.realtime.RealtimeEvent
import com.hafiyanjye.app.data.realtime.RealtimeHub
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.Loader
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.Badge
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.EmptyState
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.kit.PageHeader
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.kit.categoryIcon
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import kotlinx.coroutines.flow.filter

/** "My requests": the job provider's requests, kept fresh by the live events that change a request's state. */
class JobsViewModel(api: HafiApi, hub: RealtimeHub) : ViewModel() {
    val requests = Loader(viewModelScope) { api.myRequests() }
        .refreshOn(hub.resyncs)
        .refreshOn(hub.events.filter { it.type in RequestEvents })

    /** Coming back to the tab (from a request, or from another app): show what changed meanwhile. */
    fun onShown() {
        val state = requests.state.value
        if (state is Load.Ready && !state.refreshing) requests.refresh()
    }

    private companion object {
        val RequestEvents = setOf(RealtimeEvent.SessionUpdated, RealtimeEvent.MatchFound)
    }
}

/** "My requests": everything the job provider has asked for, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(onAskForHelp: () -> Unit, modifier: Modifier = Modifier, onRequest: (Int) -> Unit = {}) {
    val viewModel = containerViewModel { JobsViewModel(it.api, it.hub) }
    val state by viewModel.requests.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onShown()
        onPauseOrDispose { }
    }

    PullToRefreshBox(
        isRefreshing = (state as? Load.Ready)?.refreshing == true,
        onRefresh = viewModel.requests::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Header(hasRequests = (state as? Load.Ready)?.value?.isNotEmpty() == true, onAskForHelp = onAskForHelp)
            }
            when (val current = state) {
                Load.Loading -> items(3, key = { "skeleton-$it" }) { RequestSkeleton() }
                is Load.Failed -> item(key = "error") {
                    ErrorPanel("We couldn't load your requests. ${current.message}", viewModel.requests::refresh)
                }
                is Load.Ready -> {
                    current.staleError?.let { message ->
                        item(key = "stale") {
                            InfoBanner(
                                "Couldn't refresh. $message",
                                tone = Tone.Warn,
                                icon = R.drawable.ic_wifi_off,
                                action = {
                                    HafiButton("Try again", viewModel.requests::refresh, kind = ButtonKind.Outline)
                                },
                            )
                        }
                    }
                    if (current.value.isEmpty()) {
                        item(key = "empty") { NoRequests(onAskForHelp) }
                    } else {
                        items(current.value, key = { it.id }) { RequestRow(it, onRequest) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(hasRequests: Boolean, onAskForHelp: () -> Unit) {
    Column(
        modifier = Modifier.padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(
            eyebrow = "Job provider",
            title = "My requests",
            description = "Everything you've asked for, newest first.",
        )
        if (hasRequests) {
            HafiButton("New request", onAskForHelp, icon = R.drawable.ic_plus)
        }
    }
}

@Composable
private fun NoRequests(onAskForHelp: () -> Unit) {
    EmptyState(
        icon = R.drawable.ic_search,
        title = "No requests yet",
        description = "Tell us what you need, like a plumber in Kicukiro or a tailor in Nyamirambo, and we'll " +
            "ping people nearby.",
        action = { HafiButton("Ask for help", onAskForHelp) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestRow(job: JobListItem, onRequest: (Int) -> Unit) {
    val status = jobStatusView(job.status, job.state)
    val spoken = listOfNotNull(
        job.summary,
        status.label,
        Format.budget(job.budgetRwf),
        job.categoryName,
        job.addressLabel,
        Format.timeAgo(job.createdAt),
    ).joinToString(", ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .clickable(onClickLabel = "Open request", role = Role.Button) { onRequest(job.id) }
            .padding(16.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HIcon(categoryIcon(job.categoryIcon), size = 24.dp, tint = Hafi.AccentStrong)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    job.summary,
                    style = HafiType.BodyStrong,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Badge(status.label, status.tone, live = status.live)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Format.budget(job.budgetRwf),
                    style = HafiType.Caption.copy(
                        fontWeight = HafiType.BodyStrong.fontWeight,
                        fontFeatureSettings = "tnum",
                    ),
                )
                Meta(job.categoryName)
                job.addressLabel?.let { place ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        HIcon(R.drawable.ic_map_pin, size = 12.dp, tint = Hafi.FgMuted)
                        Meta(place)
                    }
                }
                Meta(Format.timeAgo(job.createdAt))
            }
        }
        HIcon(R.drawable.ic_chevron_right, size = 16.dp, tint = Hafi.FgSubtle)
    }
}

@Composable
private fun Meta(text: String) {
    Text(text, style = HafiType.Caption, color = Hafi.FgMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun RequestSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Skeleton(Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Skeleton(
                Modifier
                    .fillMaxWidth(0.75f)
                    .height(16.dp),
            )
            Skeleton(
                Modifier
                    .width(96.dp)
                    .height(14.dp),
            )
            Skeleton(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp),
            )
        }
    }
}
