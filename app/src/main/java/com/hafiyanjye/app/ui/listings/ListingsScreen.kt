package com.hafiyanjye.app.ui.listings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.listings.ListingStatusChange
import com.hafiyanjye.app.data.listings.ListingsApi
import com.hafiyanjye.app.data.rentals.ListingDto
import com.hafiyanjye.app.data.rentals.ListingStatus
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.EmptyState
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiDialog
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.kit.LocalToaster
import com.hafiyanjye.app.ui.kit.PageHeader
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.rentals.ListingPhoto
import com.hafiyanjye.app.ui.rentals.detailLine
import com.hafiyanjye.app.ui.rentals.priceLine
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** The Listings tab: the owner's cars and homes, with pause, go live, edit and delete (web: /provider/listings). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingsScreen(
    onNew: () -> Unit,
    onEdit: (Int) -> Unit,
    onView: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = containerViewModel { ListingsViewModel(ListingsApi(it.client), it.hub) }
    val state by viewModel.listings.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    var deletingId by rememberSaveable { mutableStateOf<Int?>(null) }

    // Coming back from the form (or another screen) shows what was just saved.
    LaunchedEffect(viewModel) { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.oneOffs.collect { toaster.show(it.message, if (it.failed) Tone.Danger else Tone.Accent) }
    }

    PullToRefreshBox(
        isRefreshing = (state as? Load.Ready)?.refreshing == true,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Header(
                    summary = when (val current = state) {
                        is Load.Ready -> listingsSummary(current.value)
                        Load.Loading -> "Loading…"
                        is Load.Failed -> null
                    },
                    onNew = onNew,
                )
            }
            when (val current = state) {
                Load.Loading -> items(2, key = { "skeleton-$it" }) {
                    Skeleton(
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                    )
                }
                is Load.Failed -> item(key = "error") {
                    ErrorPanel("We couldn't load your listings. ${current.message}", viewModel::refresh)
                }
                is Load.Ready -> {
                    current.staleError?.let { message ->
                        item(key = "stale") {
                            InfoBanner(
                                "Couldn't refresh. $message",
                                tone = Tone.Warn,
                                icon = R.drawable.ic_wifi_off,
                                action = { HafiButton("Try again", viewModel::refresh, kind = ButtonKind.Outline) },
                            )
                        }
                    }
                    if (current.value.isEmpty()) {
                        item(key = "empty") { NoListings(onNew) }
                    } else {
                        items(current.value, key = { it.id }) { listing ->
                            ListingRow(
                                listing = listing,
                                busy = listing.id in busy,
                                onView = { onView(listing.id) },
                                onEdit = { onEdit(listing.id) },
                                onToggle = {
                                    val next = if (listing.status == ListingStatus.Paused) {
                                        ListingStatusChange.Active
                                    } else {
                                        ListingStatusChange.Paused
                                    }
                                    viewModel.change(listing, next)
                                },
                                onDelete = { deletingId = listing.id },
                            )
                        }
                    }
                }
            }
        }
    }

    val deleting = (state as? Load.Ready)?.value?.firstOrNull { it.id == deletingId }
    if (deleting != null) {
        HafiDialog(
            title = "Delete this listing?",
            text = "“${deleting.title}” and its photos are removed for good. To hide it for a while, pause it instead.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.change(deleting, ListingStatusChange.Deleted)
                deletingId = null
            },
            onDismiss = { deletingId = null },
            dismissLabel = "Keep",
            destructive = true,
        )
    }
}

@Composable
private fun Header(summary: String?, onNew: () -> Unit) {
    Column(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(title = "My listings", description = summary)
        HafiButton("New listing", onNew, icon = R.drawable.ic_plus)
    }
}

@Composable
private fun NoListings(onNew: () -> Unit) {
    EmptyState(
        title = "No listings yet",
        description = "Add your car (by the day) or your apartment, house or room (by the night or the month). " +
            "People nearby find it with the search box and contact you by chat or call.",
        icon = R.drawable.ic_house,
        action = { HafiButton("Add your first listing", onNew, icon = R.drawable.ic_plus) },
    )
}

/** One listing: photo and facts, then its actions. Paused ones are faded and say so in words. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListingRow(
    listing: ListingDto,
    busy: Boolean,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val paused = listing.status == ListingStatus.Paused
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ListingPhoto(
                photo = listing.photo,
                kind = listing.kind,
                contentDescription = null,
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(4f / 3f)
                    .alpha(if (paused) 0.6f else 1f),
            )
            ListingFacts(listing, paused, Modifier.weight(1f))
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HafiButton("View", onView, kind = ButtonKind.Outline, icon = R.drawable.ic_eye)
            HafiButton("Edit", onEdit, kind = ButtonKind.Outline, icon = R.drawable.ic_pencil, enabled = !busy)
            HafiButton(
                text = if (paused) "Go live" else "Pause",
                onClick = onToggle,
                kind = ButtonKind.Outline,
                icon = if (paused) R.drawable.ic_play else R.drawable.ic_pause,
                enabled = !busy,
                loading = busy,
            )
            HafiIconButton(
                icon = R.drawable.ic_trash_2,
                label = "Delete ${listing.title}",
                onClick = onDelete,
                tint = Hafi.Danger,
                enabled = !busy,
            )
        }
    }
}

@Composable
private fun ListingFacts(listing: ListingDto, paused: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = if (paused) "PAUSED" else "LIVE",
            style = HafiType.Micro,
            color = if (paused) Hafi.FgMuted else Hafi.AccentStrong,
        )
        Text(
            text = listing.title,
            style = HafiType.BodyStrong,
            color = Hafi.Fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Text(detailLine(listing.attributes), style = HafiType.Caption, color = Hafi.FgMuted)
        Text(priceLine(listing.priceRwf, listing.pricePeriod), style = HafiType.Label, color = Hafi.Fg)
        listing.areaLabel?.let {
            Text(it, style = HafiType.Caption, color = Hafi.FgMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
