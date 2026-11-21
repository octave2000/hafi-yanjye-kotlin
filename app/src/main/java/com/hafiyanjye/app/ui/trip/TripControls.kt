package com.hafiyanjye.app.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.trip.Trip
import com.hafiyanjye.app.data.trip.TripAction
import com.hafiyanjye.app.data.trip.TripStatus
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** What the hired person's controls need to know beyond the trip. */
internal data class SharingState(
    /** This phone is sending positions for this trip right now. */
    val sharingHere: Boolean,
    /** Why positions are not getting through, from the sharing service. */
    val problem: String?,
    /** Only approximate location is allowed. */
    val approximate: Boolean,
    /** Location access was refused on the last ask: Declined can be asked again, Blocked only in settings. */
    val refused: LocationAnswer?,
)

/** The hired person's actions, as the web's ProviderControls: start, arrived, stop sharing, running late. */
@Composable
internal fun WorkerControls(
    trip: Trip,
    sharing: SharingState,
    busy: TripAction?,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onArrived: () -> Unit,
    onStop: () -> Unit,
    onLate: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hirer = TripText.hirer(trip)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LocationNotices(trip, sharing, hirer, onOpenSettings)
        when (trip.status) {
            TripStatus.NotStarted -> HafiButton(
                text = "Start trip",
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = busy == null,
                loading = busy == TripAction.Start,
                icon = R.drawable.ic_navigation,
            )
            TripStatus.OnTheWay -> {
                SharingNote(sharing.sharingHere, hirer, busy == null, onResume)
                HafiButton(
                    text = "I've arrived",
                    onClick = onArrived,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = busy == null,
                    loading = busy == TripAction.Arrived,
                    icon = R.drawable.ic_check_circle_2,
                )
                HafiButton(
                    text = "Stop sharing",
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    kind = ButtonKind.Outline,
                    enabled = busy == null,
                    loading = busy == TripAction.Stop,
                    icon = R.drawable.ic_square,
                )
            }
            TripStatus.Arrived -> Unit
        }
        if (trip.status != TripStatus.Arrived) {
            HafiButton(
                text = "Running late",
                onClick = onLate,
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_clock,
            )
        }
    }
}

@Composable
private fun LocationNotices(trip: Trip, sharing: SharingState, hirer: String, onOpenSettings: () -> Unit) {
    when (sharing.refused) {
        LocationAnswer.Blocked -> InfoBanner(
            text = "Location access is off for Hafi Yanjye, so the trip didn't start. Allow it in settings, " +
                "then tap Start trip again. ${TripText.capitalized(hirer)} can follow you only with it.",
            tone = Tone.Warn,
            icon = R.drawable.ic_map_pin_off,
            action = {
                HafiButton(
                    text = "Open settings",
                    onClick = onOpenSettings,
                    kind = ButtonKind.Outline,
                    icon = R.drawable.ic_settings,
                )
            },
        )
        LocationAnswer.Declined -> InfoBanner(
            text = "Without your location the trip can't start, because $hirer couldn't follow you. " +
                "Tap Start trip to be asked again.",
            tone = Tone.Warn,
            icon = R.drawable.ic_map_pin_off,
        )
        LocationAnswer.Granted, null -> Unit
    }
    if (trip.status == TripStatus.OnTheWay) {
        FormAlert(sharing.problem)
        if (sharing.approximate && sharing.sharingHere) {
            Text(
                text = "Only your approximate location is allowed, so $hirer sees roughly where you are " +
                    "(within about 2 km). Allow precise location in settings for a closer position.",
                style = HafiType.Caption,
                color = Hafi.FgMuted,
            )
        }
    }
}

/** Says plainly whether this phone is sending positions; resuming is a tap, never automatic. */
@Composable
private fun SharingNote(sharingHere: Boolean, hirer: String, enabled: Boolean, onResume: () -> Unit) {
    if (sharingHere) {
        Text(
            text = "Your location goes to $hirer every few seconds, also with the screen off. " +
                "It stops when you tap I've arrived or Stop sharing.",
            style = HafiType.Caption,
            color = Hafi.FgMuted,
        )
    } else {
        InfoBanner(
            text = "Your location isn't being shared from this phone right now, so $hirer can't see where you are.",
            tone = Tone.Warn,
            icon = R.drawable.ic_map_pin_off,
            action = {
                HafiButton(
                    text = "Share my location",
                    onClick = onResume,
                    kind = ButtonKind.Secondary,
                    enabled = enabled,
                    icon = R.drawable.ic_navigation,
                )
            },
        )
    }
}
