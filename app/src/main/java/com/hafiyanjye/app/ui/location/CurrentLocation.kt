package com.hafiyanjye.app.ui.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.hafiyanjye.app.ui.kit.HafiDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

/** Where asking for the phone's position stands. */
enum class LocationStatus { Idle, Asking, Locating, Ready, Denied, Unavailable }

/** The phone's position for a screen: read `location` and `status`, call `request()` from a button. */
@Stable
class CurrentLocationState internal constructor(
    location: PickedLocation? = null,
    status: LocationStatus = LocationStatus.Idle,
) {
    var location: PickedLocation? by mutableStateOf(location)
        internal set
    var status: LocationStatus by mutableStateOf(status)
        internal set

    /** Counts finished requests, so a screen can react to each new fix even when the spot is the same. */
    var fixes: Int by mutableStateOf(0)
        internal set

    /** What to tell the person when it did not work, with what to do next; null otherwise. */
    val message: String?
        get() = when (status) {
            LocationStatus.Denied ->
                "Location access is off for Hafi Yanjye. Allow it in your phone's settings, or type your area instead."
            LocationStatus.Unavailable ->
                "We couldn't find your position. Check that Location is switched on, move closer to a window, " +
                    "or type your area instead."
            else -> null
        }

    internal var onRequest: () -> Unit = {}
    internal var onOpenSettings: () -> Unit = {}

    /** Reads the position, asking for the permission first when it is needed. */
    fun request() = onRequest()

    /** Opens this app's page in the phone's settings, the only way back after "Don't allow" twice. */
    fun openSettings() = onOpenSettings()

    internal companion object {
        /** Keeps the found place and the state across rotation; a lookup cut short is resumed. */
        val Saver: Saver<CurrentLocationState, List<String>> = Saver(
            save = { state ->
                listOf(
                    state.status.name,
                    state.location?.let { Json.encodeToString(PickedLocation.serializer(), it) }.orEmpty(),
                )
            },
            restore = { saved ->
                CurrentLocationState(
                    location = saved[1].takeIf { it.isNotEmpty() }?.let {
                        Json.decodeFromString(PickedLocation.serializer(), it)
                    },
                    status = LocationStatus.valueOf(saved[0]),
                )
            },
        )
    }
}

private val LocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun hasLocationPermission(context: Context): Boolean = LocationPermissions.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun hasPreciseLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * `auto = true` reads the position straight away, but only when the permission
 * was already given: like the web, it never opens a permission prompt nobody
 * asked for. `request()` is what asks.
 */
@Composable
fun rememberCurrentLocation(auto: Boolean = false): CurrentLocationState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val state = rememberSaveable(saver = CurrentLocationState.Saver) { CurrentLocationState() }
    var explaining by rememberSaveable { mutableStateOf(false) }

    val locate = {
        state.status = LocationStatus.Locating
        scope.launch {
            val fix = currentFix(context)
            if (fix != null) {
                state.location = PickedLocation(
                    lat = fix.latitude,
                    lng = fix.longitude,
                    // With only "approximate" allowed, Android blurs the fix to about 2 km: say so.
                    label = if (hasPreciseLocation(context)) "Your current location" else "Near you (approximate)",
                    source = LocationSource.Gps,
                )
                state.status = LocationStatus.Ready
            } else {
                state.status = LocationStatus.Unavailable
            }
            state.fixes += 1
        }
        Unit
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) {
            locate()
        } else {
            state.status = LocationStatus.Denied
            state.fixes += 1
        }
    }

    state.onRequest = request@{
        if (state.status == LocationStatus.Asking || state.status == LocationStatus.Locating) return@request
        val declinedBefore = activity != null && LocationPermissions.any {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
        when {
            hasLocationPermission(context) -> locate()
            // Someone who said no once gets our reason in plain words before the system asks again.
            declinedBefore -> explaining = true
            else -> {
                state.status = LocationStatus.Asking
                launcher.launch(LocationPermissions)
            }
        }
    }
    state.onOpenSettings = {
        val appPage = Uri.fromParts("package", context.packageName, null)
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appPage)
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    LaunchedEffect(auto) {
        val idle = state.location == null && state.status == LocationStatus.Idle
        if (auto && idle && hasLocationPermission(context)) locate()
    }
    LaunchedEffect(Unit) {
        // A rotation cut the lookup short: start it again in the new screen.
        if (state.status == LocationStatus.Locating) locate()
    }

    if (explaining) {
        HafiDialog(
            title = "Share your location?",
            text = "Hafi Yanjye uses your location to find people and places near you and to show how far away " +
                "they are. It is only read while the app is open. You can type your area instead.",
            confirmLabel = "Continue",
            dismissLabel = "Not now",
            onConfirm = {
                explaining = false
                state.status = LocationStatus.Asking
                launcher.launch(LocationPermissions)
            },
            onDismiss = { explaining = false },
        )
    }
    return state
}

private const val FixTimeoutMs = 12_000L
private const val LastFixTimeoutMs = 2_000L
private const val MaxFixAgeNanos = 60_000_000_000L

/** One fresh fix from the fused provider, or a last known one under a minute old; null when neither comes. */
@SuppressLint("MissingPermission") // Callers check hasLocationPermission first.
private suspend fun currentFix(context: Context): Location? = try {
    val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
    val request = CurrentLocationRequest.Builder()
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .setMaxUpdateAgeMillis(60_000)
        .setDurationMillis(10_000)
        .build()
    val fresh = withTimeoutOrNull(FixTimeoutMs) {
        suspendCancellableCoroutine<Location?> { cont ->
            val cancel = CancellationTokenSource()
            client.getCurrentLocation(request, cancel.token)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            cont.invokeOnCancellation { cancel.cancel() }
        }
    }
    val fix = fresh ?: withTimeoutOrNull(LastFixTimeoutMs) {
        suspendCancellableCoroutine<Location?> { cont ->
            client.lastLocation
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
    }
    // Last-known fixes may predate this visit by hours.
    fix?.takeIf { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos in 0..MaxFixAgeNanos }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: SecurityException) {
    null
} catch (_: RuntimeException) {
    // No Google Play services on this phone.
    null
}
