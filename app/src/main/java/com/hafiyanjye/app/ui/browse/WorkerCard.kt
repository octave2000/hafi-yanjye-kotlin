package com.hafiyanjye.app.ui.browse

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.browse.Availability
import com.hafiyanjye.app.data.browse.WorkerResult
import com.hafiyanjye.app.ui.kit.Avatar
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.Divider
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.PulseDot
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.kit.StarRating
import com.hafiyanjye.app.ui.match.LevelBadge
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/**
 * One person in the services list (web: provider-result.tsx). The top opens
 * their profile; Chat and Hire sit underneath. Offline people can't be hired.
 */
@Composable
internal fun WorkerCard(
    person: WorkerResult,
    onOpen: () -> Unit,
    onChat: () -> Unit,
    onHire: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val online = person.availability == Availability.Online
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Open profile", role = Role.Button, onClick = onOpen)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(person)
            if (person.bioSnippet.isNotBlank()) {
                Text(
                    person.bioSnippet,
                    style = HafiType.Body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Divider()
            CardFacts(person, online)
        }
        Divider()
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HafiButton(
                "Chat",
                onChat,
                Modifier.weight(1f),
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_message_circle,
            )
            HafiButton(
                if (online) "Hire ${firstNameOf(person.displayName)}" else "Offline",
                onHire,
                Modifier.weight(1f),
                enabled = online,
                icon = R.drawable.ic_user_check,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardHeader(person: WorkerResult) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Avatar(person.displayName, person.picture, size = 64.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    person.displayName,
                    style = HafiType.Subheading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                person.distanceKm?.let { km -> Distance(km) }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (person.ratingCount > 0) {
                    StarRating(person.ratingAvg, size = 14.dp, count = person.ratingCount)
                } else {
                    Text("No reviews yet", style = HafiType.Caption, color = Hafi.FgMuted)
                }
                LevelBadge(person.level)
            }
            if (person.categories.isNotEmpty()) {
                Text(
                    servicesLine(person.categories),
                    style = HafiType.Caption,
                    color = Hafi.FgMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Distance(km: Double) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        HIcon(R.drawable.ic_navigation, size = 14.dp, tint = Hafi.AccentStrong)
        Text("${distanceLabel(km)} away", style = HafiType.Label, color = Hafi.Fg)
    }
}

/** Availability, area, price, jobs and the checked CV, in one wrapping line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardFacts(person: WorkerResult, online: Boolean) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        AvailabilityFact(online)
        person.addressLabel?.let { Fact(R.drawable.ic_map_pin, it) }
        Fact(R.drawable.ic_wallet, startingPrice(person.priceMinRwf, person.priceUnit))
        Fact(R.drawable.ic_briefcase, jobsCount(person.completedJobs))
        if (person.hasCheckedCredential) Fact(R.drawable.ic_badge_check, "Checked CV", strong = true)
        if (person.coversYou == false) {
            Text(
                "Usually works closer to home; ask if they can come.",
                style = HafiType.Caption,
                color = Hafi.FgMuted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AvailabilityFact(online: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (online) {
            PulseDot(Hafi.Accent)
        } else {
            Box(
                Modifier
                    .size(6.dp)
                    .background(Hafi.FgSubtle, CircleShape),
            )
        }
        Text(
            if (online) "Available now" else "Offline",
            style = HafiType.Caption,
            color = if (online) Hafi.AccentStrong else Hafi.FgMuted,
        )
    }
}

/** An icon and a short caption; [strong] makes both green. */
@Composable
internal fun Fact(@DrawableRes icon: Int, text: String, strong: Boolean = false) {
    val ink = if (strong) Hafi.AccentStrong else Hafi.FgMuted
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        HIcon(icon, size = 14.dp, tint = ink)
        Text(text, style = HafiType.Caption, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A card-shaped placeholder while results load. */
@Composable
internal fun WorkerCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Skeleton(Modifier.size(64.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skeleton(
                    Modifier
                        .width(160.dp)
                        .height(16.dp),
                )
                Skeleton(
                    Modifier
                        .width(110.dp)
                        .height(12.dp),
                )
                Skeleton(
                    Modifier
                        .width(80.dp)
                        .height(12.dp),
                )
            }
        }
        Skeleton(
            Modifier
                .fillMaxWidth()
                .height(12.dp),
        )
        Skeleton(
            Modifier
                .fillMaxWidth(0.8f)
                .height(12.dp),
        )
    }
}
