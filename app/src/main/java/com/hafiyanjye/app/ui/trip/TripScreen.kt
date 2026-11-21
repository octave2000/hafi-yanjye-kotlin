package com.hafiyanjye.app.ui.trip

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.api.Role
import com.hafiyanjye.app.data.trip.Trip
import com.hafiyanjye.app.data.trip.TripRoutes
import com.hafiyanjye.app.data.trip.TripSharing
import com.hafiyanjye.app.data.trip.TripStatus
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.kit.LocalToaster
import com.hafiyanjye.app.ui.kit.PulseDot
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

private val MapHeight = 320.dp

/**
 * The live trip after a hire, for both people: the hirer watches the person
 * they hired come, the hired person starts the trip, shares their way and
 * says when they're late or there. Web: components/trip/live-trip-map.tsx.
 */
@Composable
fun TripScreen(sessionId: Int, nav: NavHostController) {
    val vm = containerViewModel(key = "trip-$sessionId") { TripViewModel(sessionId, it) }
    val state by vm.loader.state.collectAsStateWithLifecycle()
    var full by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = full) { full = false }
    val toaster = LocalToaster.current
    LaunchedEffect(vm) {
        vm.messages.collect { toaster.show(it.text, if (it.failed) Tone.Danger else Tone.Accent) }
    }

    DetailScaffold(title = "Live trip", onBack = { if (full) full = false else nav.popBackStack() }) { padding ->
        when (val load = state) {
            Load.Loading -> TripSkeleton(padding)
            is Load.Failed -> Box(Modifier.padding(padding).padding(16.dp)) {
                ErrorPanel(load.message, vm.loader::refresh)
            }
            is Load.Ready -> TripContent(
                trip = load.value,
                staleError = load.staleError,
                vm = vm,
                full = full,
                onToggleFull = { full = !full },
                padding = padding,
            )
        }
    }
}

@Composable
private fun TripSkeleton(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Skeleton(Modifier.fillMaxWidth().height(56.dp))
        Skeleton(Modifier.fillMaxWidth().height(MapHeight))
        Skeleton(Modifier.fillMaxWidth().height(48.dp))
    }
}

@Composable
private fun TripContent(
    trip: Trip,
    staleError: String?,
    vm: TripViewModel,
    full: Boolean,
    onToggleFull: () -> Unit,
    padding: PaddingValues,
) {
    val tripNav by vm.nav.collectAsStateWithLifecycle()
    val worker = trip.role == Role.Worker && trip.live
    val column = Modifier
        .padding(padding)
        .fillMaxSize()
    if (full) {
        // Full screen: the map takes what the status and controls leave.
        Column(column) {
            TripHeader(trip, tripNav)
            TripMap(
                trip = trip,
                nav = tripNav,
                full = true,
                onToggleFull = onToggleFull,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            if (worker) WorkerPanel(trip, vm, Modifier.padding(12.dp))
        }
    } else {
        Column(column.verticalScroll(rememberScrollState())) {
            staleError?.let { ErrorPanel(it, vm.loader::refresh, Modifier.padding(16.dp)) }
            TripHeader(trip, tripNav)
            TripMap(
                trip = trip,
                nav = tripNav,
                full = false,
                onToggleFull = onToggleFull,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MapHeight),
            )
            PlaceLines(trip)
            if (worker) WorkerPanel(trip, vm, Modifier.padding(16.dp))
        }
    }
}

/** The status, the next turn for the person on the way, and any delay notice. */
@Composable
private fun TripHeader(trip: Trip, tripNav: TripNav?) {
    StatusLine(trip, tripNav)
    if (trip.role == Role.Worker) NextTurnBanner(trip, tripNav)
    TripText.delayLine(trip)?.let {
        InfoBanner(text = it, tone = Tone.Warn, icon = R.drawable.ic_triangle_alert)
    }
}

@Composable
private fun StatusLine(trip: Trip, tripNav: TripNav?) {
    val onTheWay = trip.live && trip.status == TripStatus.OnTheWay
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            onTheWay -> PulseDot(Hafi.Accent, Modifier.size(10.dp))
            else -> Box(
                Modifier
                    .size(10.dp)
                    .background(if (trip.status == TripStatus.Arrived) Hafi.Accent else Hafi.FgSubtle, CircleShape),
            )
        }
        Column {
            Text(
                text = "LIVE MAP",
                style = HafiType.Micro,
                color = Hafi.AccentStrong,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = TripText.headline(trip, tripNav?.progress),
                style = HafiType.BodyStrong,
                color = Hafi.Fg,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** For the person on the way: the next turn, like a satnav, inside the app. */
@Composable
private fun NextTurnBanner(trip: Trip, tripNav: TripNav?) {
    val position = trip.position ?: return
    if (tripNav == null || !trip.live || trip.status != TripStatus.OnTheWay) return
    val km = TripText.straightKm(trip) ?: return
    if (TripText.isVeryClose(km, position.accuracy)) return
    val next = tripNav.progress.next
    val distance = TripRoutes.roadDistanceText(next?.inMeters ?: tripNav.progress.remainingMeters)
    val instruction = next?.step?.instructions ?: "Arrive at ${trip.destination.label ?: "the place"}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.AccentStrong)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIcon(maneuverIcon(next?.step?.maneuver), size = 28.dp, tint = Hafi.AccentFg)
        Column {
            Text(text = "In $distance", style = HafiType.Heading, color = Hafi.AccentFg)
            Text(
                text = instruction,
                style = HafiType.Label,
                color = Hafi.AccentFg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The web's MANEUVER_ICON, with a flag for the arrival. */
private fun maneuverIcon(maneuver: String?): Int = when (maneuver) {
    null -> R.drawable.ic_flag
    "TURN_LEFT", "TURN_SHARP_LEFT" -> R.drawable.ic_corner_up_left
    "TURN_SLIGHT_LEFT", "FORK_LEFT", "RAMP_LEFT" -> R.drawable.ic_arrow_up_left
    "TURN_RIGHT", "TURN_SHARP_RIGHT" -> R.drawable.ic_corner_up_right
    "TURN_SLIGHT_RIGHT", "FORK_RIGHT", "RAMP_RIGHT", "MERGE" -> R.drawable.ic_arrow_up_right
    "UTURN_LEFT", "ROUNDABOUT_LEFT" -> R.drawable.ic_rotate_ccw
    "UTURN_RIGHT", "ROUNDABOUT_RIGHT" -> R.drawable.ic_rotate_cw
    else -> R.drawable.ic_arrow_up
}

/** Where the work is and how fresh the shared position is, in words, for anyone who can't read the map. */
@Composable
private fun PlaceLines(trip: Trip) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Where the work is: ${trip.destination.label ?: "the pin on the map"}",
            style = HafiType.Body,
            color = Hafi.Fg,
        )
        trip.position?.let {
            Text(
                text = "Position shared ${Format.timeAgo(it.at)}",
                style = HafiType.Caption,
                color = Hafi.FgMuted,
            )
        }
    }
}

/** The hired person's controls, with the location ask and the delay sheet. */
@Composable
private fun WorkerPanel(trip: Trip, vm: TripViewModel, modifier: Modifier) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val sharingSession by TripSharing.sessionId.collectAsStateWithLifecycle()
    val problem by TripSharing.problem.collectAsStateWithLifecycle()
    val delaySending by vm.delaySending.collectAsStateWithLifecycle()
    val delayError by vm.delayError.collectAsStateWithLifecycle()
    val delaysSent by vm.delaysSent.collectAsStateWithLifecycle()
    var refused by rememberSaveable { mutableStateOf<LocationAnswer?>(null) }
    var resuming by rememberSaveable { mutableStateOf(false) }
    var delayOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(delaysSent) { delayOpen = false }

    val hirer = TripText.hirer(trip)
    val ask = rememberTripLocationAsk(hirer) { answer ->
        refused = answer.takeIf { it != LocationAnswer.Granted }
        if (answer == LocationAnswer.Granted) {
            if (resuming) vm.resumeSharing() else vm.start()
        }
    }
    WorkerControls(
        trip = trip,
        sharing = SharingState(
            sharingHere = sharingSession == trip.sessionId,
            problem = problem,
            approximate = isApproximateOnly(),
            refused = refused,
        ),
        busy = busy,
        onStart = {
            resuming = false
            ask()
        },
        onResume = {
            resuming = true
            ask()
        },
        onArrived = vm::arrived,
        onStop = vm::stopSharing,
        onLate = {
            vm.clearDelayError()
            delayOpen = true
        },
        onOpenSettings = rememberOpenAppSettings(),
        modifier = modifier,
    )
    if (delayOpen && trip.status != TripStatus.Arrived) {
        DelaySheet(
            hirer = hirer,
            sending = delaySending,
            error = delayError,
            onSend = vm::sendDelay,
            onDismiss = { delayOpen = false },
        )
    }
}
