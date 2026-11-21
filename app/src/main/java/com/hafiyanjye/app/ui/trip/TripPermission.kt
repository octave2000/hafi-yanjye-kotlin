package com.hafiyanjye.app.ui.trip

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hafiyanjye.app.data.trip.TripSharing
import com.hafiyanjye.app.ui.kit.HafiDialog

private val LocationPermissions = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** What came of asking for location access. */
internal enum class LocationAnswer { Granted, Declined, Blocked }

/**
 * Asks for location access the way people can follow: our reason in plain
 * words first, then Android's prompt. Only while-in-use access is asked; the
 * notification permission rides along on Android 13+, so the ongoing "sharing"
 * notice (with its Stop button) can be seen. Returns the function that asks.
 */
@Composable
internal fun rememberTripLocationAsk(hirer: String, onAnswer: (LocationAnswer) -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val answer by rememberUpdatedState(onAnswer)
    var explaining by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val granted = LocationPermissions.any { grants[it] == true }
        // After a no, Android stops showing its prompt once the person chose "don't ask again".
        val canAskAgain = activity != null &&
            LocationPermissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        answer(
            when {
                granted -> LocationAnswer.Granted
                canAskAgain -> LocationAnswer.Declined
                else -> LocationAnswer.Blocked
            },
        )
    }

    if (explaining) {
        HafiDialog(
            title = "Share your location on the way?",
            text = "While you're on the way, Hafi Yanjye sends your location to $hirer every few seconds, " +
                "so they can follow you on the map. It keeps going with the screen off and shows a notice " +
                "while it does. It stops when you tap I've arrived or Stop sharing, and never runs at other times.",
            confirmLabel = "Continue",
            dismissLabel = "Not now",
            onConfirm = {
                explaining = false
                val notifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                val asked = if (notifications) {
                    LocationPermissions + Manifest.permission.POST_NOTIFICATIONS
                } else {
                    LocationPermissions
                }
                launcher.launch(asked.toTypedArray())
            },
            onDismiss = { explaining = false },
        )
    }

    return {
        if (TripSharing.hasPermission(context)) answer(LocationAnswer.Granted) else explaining = true
    }
}

/** Only "approximate" was allowed: Android blurs it to about 2 km, and the job provider should know. */
@Composable
internal fun isApproximateOnly(): Boolean {
    val context = LocalContext.current
    return TripSharing.hasPermission(context) &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
}

/** Opens this app's page in Android settings, where a blocked permission can be allowed. */
@Composable
internal fun rememberOpenAppSettings(): () -> Unit {
    val context = LocalContext.current
    return {
        val appPage = Uri.fromParts("package", context.packageName, null)
        val page = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appPage)
        runCatching { context.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
