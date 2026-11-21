package com.hafiyanjye.app.ui.pings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.data.api.Ping
import com.hafiyanjye.app.data.api.PingMatch
import com.hafiyanjye.app.data.api.SessionState
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.ButtonSize
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.location.GeoPoint
import com.hafiyanjye.app.ui.location.HafiMap
import com.hafiyanjye.app.ui.location.MapMarker
import com.hafiyanjye.app.ui.location.MarkerKind
import com.hafiyanjye.app.ui.nav.Routes
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/**
 * "You were chosen!" / "You're hired!": the job provider's contact, where to
 * go, and the way to chat or start the trip. Nothing when the ping has no match.
 */
@Composable
internal fun MatchedPing(
    ping: Ping,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val match = ping.match ?: return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Accent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Hafi.AccentSoft)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HIcon(R.drawable.ic_party_popper, size = 28.dp, tint = Hafi.AccentStrong)
            Column(Modifier.weight(1f)) {
                Text(
                    if (match.hired) "You're hired!" else "You were chosen!",
                    style = HafiType.Heading,
                    color = Hafi.AccentStrong,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "${ping.categoryName} · ${ping.summary}",
                    style = HafiType.Caption,
                    color = Hafi.FgMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HirerContact(ping, match)
            ContactButtons(ping.jobId, match.hirerPhone, onNavigate)
            JobPlace(ping, match)
            // After the hire the trip screen takes over: start it, share the way, say if late.
            if (match.hired && match.state == SessionState.Matched) {
                HafiButton(
                    "Open the trip",
                    onClick = { onNavigate(Routes.trip(match.sessionId)) },
                    modifier = Modifier.fillMaxWidth(),
                    kind = ButtonKind.Secondary,
                    size = ButtonSize.Large,
                    icon = R.drawable.ic_route,
                )
            }
        }
    }
}

@Composable
private fun HirerContact(ping: Ping, match: PingMatch) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("YOUR JOB PROVIDER", style = HafiType.Micro, color = Hafi.FgMuted)
        Text(match.hirerName ?: "The person who chose you", style = HafiType.Heading)
        val phone = match.hirerPhone
        if (phone != null) {
            Text(Format.phone(phone), style = HafiType.Subheading)
        } else {
            Text(
                "No phone shared yet. Check back in a moment.",
                style = HafiType.Caption,
                color = Hafi.FgMuted,
            )
        }
        ping.quoteRwf?.let {
            Text("Your quote: ${Format.rwf(it)}", style = HafiType.Caption, color = Hafi.FgMuted)
        }
    }
}

/** Chat stays in the app; "Call" opens the dialler with the number filled in. */
@Composable
private fun ContactButtons(jobId: Int, phone: String?, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HafiButton(
            "Chat",
            onClick = { onNavigate(Routes.chatWith(jobId = jobId)) },
            modifier = Modifier.weight(1f),
            size = ButtonSize.Large,
            icon = R.drawable.ic_message_circle,
        )
        if (phone != null) {
            HafiButton(
                "Call",
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))
                    } catch (_: ActivityNotFoundException) {
                        // A tablet without a dialler: the number is on screen to copy.
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

/** The exact place, known only after the match, on our own small map. */
@Composable
private fun JobPlace(ping: Ping, match: PingMatch) {
    val place = match.addressLabel ?: ping.areaLabel ?: "Location pinned on the map"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HIcon(
                R.drawable.ic_map_pin,
                size = 16.dp,
                tint = Hafi.AccentStrong,
                modifier = Modifier.padding(top = 3.dp),
            )
            Text(place, style = HafiType.Body, modifier = Modifier.weight(1f))
        }
        val destination = GeoPoint(match.lat, match.lng)
        HafiMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            center = destination,
            markers = listOf(MapMarker("job", destination, MarkerKind.Destination, "Job: $place")),
            interactive = false,
        )
    }
}
