package com.hafiyanjye.app.ui.rentals

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.rentals.ListingDto
import com.hafiyanjye.app.data.rentals.ListingKind
import com.hafiyanjye.app.data.rentals.ListingStatus
import com.hafiyanjye.app.data.rentals.RentalsApi
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.Avatar
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.ButtonSize
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.EmptyState
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.location.GeoPoint
import com.hafiyanjye.app.ui.location.HafiMap
import com.hafiyanjye.app.ui.location.MapCircle
import com.hafiyanjye.app.ui.location.MapMarker
import com.hafiyanjye.app.ui.location.zoomForRadius
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** The public pin is rounded to about 100 m; the circle says "somewhere around here". */
private const val ApproximateRadiusKm = 0.4

/** One car or home to rent (web: /rentals/:id), with Chat and Call for the owner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalDetailScreen(
    listingId: Int,
    onBack: () -> Unit,
    onChat: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onBrowse: () -> Unit,
) {
    val vm = containerViewModel(key = "rental-$listingId") {
        RentalDetailViewModel(RentalsApi(it.client), it.hub, listingId)
    }
    val state by vm.listing.state.collectAsStateWithLifecycle()
    val listing = (state as? Load.Ready)?.value
    DetailScaffold(
        title = when (listing?.kind) {
            ListingKind.Car -> "Car to rent"
            ListingKind.Home -> "Home to rent"
            null -> "Rental"
        },
        onBack = onBack,
        bottomBar = listing?.let { shown -> { ContactBar(shown, onChat) } },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = (state as? Load.Ready)?.refreshing == true,
            onRefresh = vm.listing::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val current = state) {
                Load.Loading -> DetailSkeleton()
                is Load.Failed -> ErrorPanel(current.message, vm.listing::refresh, Modifier.padding(16.dp))
                is Load.Ready -> {
                    val shown = current.value
                    if (shown == null) {
                        Unavailable(onBrowse)
                    } else {
                        ListingDetail(shown, current.staleError, vm.listing::refresh, onEdit)
                    }
                }
            }
        }
    }
}

@Composable
private fun Unavailable(onBrowse: () -> Unit) {
    EmptyState(
        title = "This listing isn't available",
        description = "The owner may have rented it out or paused it.",
        icon = R.drawable.ic_search_x,
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        action = { HafiButton("See other rentals", onBrowse, kind = ButtonKind.Outline) },
    )
}

@Composable
private fun DetailSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Skeleton(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f),
        )
        Skeleton(
            Modifier
                .fillMaxWidth(0.7f)
                .height(28.dp),
        )
        Skeleton(
            Modifier
                .fillMaxWidth()
                .height(120.dp),
        )
    }
}

@Composable
private fun ListingDetail(listing: ListingDto, staleError: String?, onRetry: () -> Unit, onEdit: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        staleError?.let { ErrorPanel(it, onRetry) }
        if (listing.mine) MineBanner(listing, onEdit)
        ListingGallery(listing)
        TitleBlock(listing)
        Facts(listing.attributes)
        if (listing.description.isNotBlank()) {
            Section(if (listing.kind == ListingKind.Car) "About this car" else "About this home") {
                Text(listing.description, style = HafiType.Body, color = Hafi.FgMuted)
            }
        }
        WhereItIs(listing)
        OwnerBlock(listing)
    }
}

@Composable
private fun MineBanner(listing: ListingDto, onEdit: (Int) -> Unit) {
    val paused = listing.status == ListingStatus.Paused
    InfoBanner(
        text = if (paused) "This is your listing, and it is paused: people can't find it." else "This is your listing.",
        tone = if (paused) Tone.Warn else Tone.Accent,
        icon = R.drawable.ic_pencil,
        action = { HafiButton("Edit", { onEdit(listing.id) }) },
    )
}

@Composable
private fun TitleBlock(listing: ListingDto) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = (if (listing.kind == ListingKind.Car) "Car to rent" else "Home to rent").uppercase(),
            style = HafiType.Micro,
            color = Hafi.AccentStrong,
        )
        Text(
            text = listing.title,
            style = HafiType.Title,
            color = Hafi.Fg,
            modifier = Modifier.semantics { heading() },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HIcon(R.drawable.ic_map_pin, size = 16.dp, tint = Hafi.FgMuted)
            Text(listing.areaLabel ?: "Kigali", style = HafiType.Body, color = Hafi.FgMuted)
        }
        Text(
            text = priceLine(listing.priceRwf, listing.pricePeriod),
            style = HafiType.Heading,
            color = Hafi.Fg,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = HafiType.Heading,
            color = Hafi.Fg,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun WhereItIs(listing: ListingDto) {
    val point = GeoPoint(listing.lat, listing.lng)
    Section("Where it is") {
        Text(
            text = "Approximate area. The owner shares the exact place in the chat.",
            style = HafiType.Body,
            color = Hafi.FgMuted,
        )
        HafiMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            center = point,
            zoom = zoomForRadius(ApproximateRadiusKm),
            markers = listOf(MapMarker("listing", point, title = "Approximate location")),
            circles = listOf(MapCircle(point, ApproximateRadiusKm)),
            interactive = false,
        )
    }
}

/** Who rents it out: picture with an online dot, name, and how long they have been here. */
@Composable
private fun OwnerBlock(listing: ListingDto) {
    val owner = listing.owner
    Section("About the owner") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                Avatar(owner.name, owner.picture, size = 48.dp)
                if (owner.online) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .border(2.dp, Hafi.Surface, CircleShape)
                            .padding(2.dp)
                            .background(Hafi.Accent, CircleShape),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(owner.name, style = HafiType.BodyStrong, color = Hafi.Fg)
                Text(
                    text = "${if (owner.online) "Online now" else "Offline"} · " +
                        "On Hafi Yanjye since ${monthYear(owner.memberSince)}",
                    style = HafiType.Caption,
                    color = if (owner.online) Hafi.AccentStrong else Hafi.FgMuted,
                )
            }
        }
    }
}

/** Price, then Chat and Call side by side; the owner sees a note instead (web: the sticky aside). */
@Composable
private fun ContactBar(listing: ListingDto, onChat: (Int) -> Unit) {
    val context = LocalContext.current
    var callError by rememberSaveable(listing.id) { mutableStateOf<String?>(null) }
    val phone = listing.owner.phone
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (listing.mine) {
            Text("People contact you here with Chat or Call.", style = HafiType.Body, color = Hafi.FgMuted)
            return@Column
        }
        FormAlert(callError)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HafiButton(
                text = "Chat",
                onClick = { onChat(listing.id) },
                modifier = Modifier.weight(1f),
                size = ButtonSize.Large,
                icon = R.drawable.ic_messages_square,
            )
            if (phone != null) {
                HafiButton(
                    text = "Call",
                    onClick = {
                        callError = try {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
                            null
                        } catch (_: ActivityNotFoundException) {
                            "This phone can't make calls. Chat with the owner instead."
                        }
                    },
                    modifier = Modifier.weight(1f),
                    kind = ButtonKind.Outline,
                    size = ButtonSize.Large,
                    icon = R.drawable.ic_phone,
                )
            }
        }
    }
}
