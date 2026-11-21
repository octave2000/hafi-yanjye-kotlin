package com.hafiyanjye.app.ui.rentals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.data.rentals.ListingCardDto
import com.hafiyanjye.app.data.rentals.ListingKind
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.RemoteImage
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** Cover photo, or a calm placeholder that still says what it is, with the photo count when there are several. */
@Composable
internal fun ListingPhoto(
    photo: String?,
    kind: ListingKind,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    Box(modifier = modifier.background(Hafi.Surface2)) {
        if (photo != null) {
            RemoteImage(photo, contentDescription, Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HIcon(kindIcon(kind), size = 28.dp, tint = Hafi.FgSubtle)
                Text("No photo yet", style = HafiType.Caption, color = Hafi.FgMuted)
            }
        }
        if (count != null && count > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Hafi.Ink.copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clearAndSetSemantics { },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HIcon(R.drawable.ic_image, size = 12.dp, tint = Hafi.InkFg)
                Text(count.toString(), style = HafiType.Micro, color = Hafi.InkFg)
            }
        }
    }
}

/** The icon for cars or homes. */
internal fun kindIcon(kind: ListingKind): Int =
    if (kind == ListingKind.Car) R.drawable.ic_car_front else R.drawable.ic_house

/** What a screen reader says for a card or a map pin: everything the card shows, in reading order. */
internal fun listingSummary(listing: ListingCardDto): String = listOfNotNull(
    kicker(listing.attributes),
    listing.title,
    detailLine(listing.attributes),
    priceLine(listing.priceRwf, listing.pricePeriod),
    "${Format.km(listing.distanceKm)} away",
    listing.areaLabel,
    if (listing.photoCount > 1) "${listing.photoCount} photos" else null,
).filter { it.isNotBlank() }.joinToString(", ")

/** One result: photo left, details right (web: ListingCardView). Tapping opens the listing. */
@Composable
internal fun ListingCard(
    listing: ListingCardDto,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, if (selected) Hafi.AccentStrong else Hafi.Line)
            .clickable(role = Role.Button, onClickLabel = "Open listing") { onOpen(listing.id) }
            .clearAndSetSemantics {
                contentDescription = listingSummary(listing)
                this.selected = selected
            }
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ListingPhoto(
            photo = listing.photo,
            kind = listing.kind,
            contentDescription = null,
            count = listing.photoCount,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(4f / 3f),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = kicker(listing.attributes).uppercase(),
                style = HafiType.Micro,
                color = Hafi.AccentStrong,
            )
            Text(
                text = listing.title,
                style = HafiType.Subheading,
                color = Hafi.Fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(detailLine(listing.attributes), style = HafiType.Caption, color = Hafi.FgMuted)
            Text(
                text = priceLine(listing.priceRwf, listing.pricePeriod),
                style = HafiType.BodyStrong,
                color = Hafi.Fg,
                modifier = Modifier.padding(top = 4.dp),
            )
            DistanceLine(listing)
        }
    }
}

@Composable
private fun DistanceLine(listing: ListingCardDto) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HIcon(R.drawable.ic_map_pin, size = 14.dp, tint = Hafi.FgMuted)
        val area = listing.areaLabel?.let { " · $it" }.orEmpty()
        Text(
            text = "${Format.km(listing.distanceKm)} away$area",
            style = HafiType.Caption,
            color = Hafi.FgMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A card-shaped placeholder while results load. */
@Composable
internal fun ListingCardSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Hafi.Line)
            .background(Hafi.Surface)
            .padding(8.dp)
            .semantics { contentDescription = "Loading" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Skeleton(
            Modifier
                .width(120.dp)
                .aspectRatio(4f / 3f),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Skeleton(
                Modifier
                    .width(64.dp)
                    .height(10.dp),
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
            Skeleton(
                Modifier
                    .fillMaxWidth(0.35f)
                    .height(18.dp),
            )
        }
    }
}
