package com.hafiyanjye.app.data.trip

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether this phone is sharing its position for a trip right now, and what
 * went wrong if it cannot. The work itself is [TripSharingService]; screens
 * read this to show the right state and start or stop it.
 */
object TripSharing {
    private val _sessionId = MutableStateFlow<Int?>(null)

    /** The session whose trip this phone is sharing, or null. */
    val sessionId: StateFlow<Int?> = _sessionId.asStateFlow()

    private val _problem = MutableStateFlow<String?>(null)

    /** Why positions are not getting through (location off, no connection), in plain words; null when fine. */
    val problem: StateFlow<String?> = _problem.asStateFlow()

    /** True when the app may read the position while in use; background location is never asked for. */
    fun hasPermission(context: Context): Boolean = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /**
     * Starts sharing. Call only from a tap of the person on the way, with the
     * app on screen: Android lets a location service start only from there.
     * Returns false (with [problem] set) when it could not start.
     */
    fun start(context: Context, sessionId: Int, hirerName: String?, place: String?): Boolean {
        if (!hasPermission(context)) {
            _problem.value = "Location access is off for Hafi Yanjye, so your position can't be shared."
            return false
        }
        val intent = Intent(context, TripSharingService::class.java)
            .setAction(TripSharingService.ActionStart)
            .putExtra(TripSharingService.ExtraSessionId, sessionId)
            .putExtra(TripSharingService.ExtraHirerName, hirerName)
            .putExtra(TripSharingService.ExtraPlace, place)
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (refused: IllegalStateException) {
            // Android refuses a start from the background; the next tap on screen works.
            _problem.value = "Couldn't start sharing your location. Open the trip and try again."
            false
        }
    }

    /** Stops sharing on this phone only; the engine is told separately (or not at all, when the trip ended). */
    fun stop(context: Context) {
        context.stopService(Intent(context, TripSharingService::class.java))
        _sessionId.value = null
        _problem.value = null
    }

    internal fun markSharing(sessionId: Int?) {
        _sessionId.value = sessionId
    }

    internal fun report(problem: String?) {
        _problem.value = problem
    }
}
