package com.hafiyanjye.app.data.trip

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.hafiyanjye.app.HafiApp
import com.hafiyanjye.app.MainActivity
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.Role
import com.hafiyanjye.app.data.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * Sends the hired person's position to the engine while they are on the way,
 * also with the screen off or another app open. It runs as a visible
 * foreground service of type `location`, started only by their tap, with an
 * ongoing notification that says what is shared and with whom, and a Stop
 * action. It stops itself when the trip is no longer under way.
 */
class TripSharingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as HafiApp).container
    private val api by lazy { TripApi(container.client) }
    private lateinit var locations: FusedLocationProviderClient

    private var sessionId: Int? = null
    private var lastSentAt = 0L
    private var watchdog: Job? = null
    private var stopping = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::send)
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            TripSharing.report(if (availability.isLocationAvailable) null else NoFix)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locations = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionStart -> begin(intent)
            ActionStop -> stopFromNotification()
            // Restarted by the system with no tap behind it: never share without one.
            else -> finish()
        }
        return START_NOT_STICKY
    }

    private fun begin(intent: Intent) {
        val session = intent.getIntExtra(ExtraSessionId, -1)
        val notification = notification(
            session,
            intent.getStringExtra(ExtraHirerName),
            intent.getStringExtra(ExtraPlace),
        )
        // startForeground must come first: Android ends an app that asked for a service and never showed it.
        val shown = try {
            ServiceCompat.startForeground(
                this,
                NotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            true
        } catch (refused: RuntimeException) {
            // SecurityException without location access, or a start Android no longer allows.
            false
        }
        if (!shown || session < 0 || !TripSharing.hasPermission(this)) {
            TripSharing.report("Location access is off for Hafi Yanjye, so your position can't be shared.")
            finish()
            return
        }
        if (sessionId != session) lastSentAt = 0L
        sessionId = session
        stopping = false
        TripSharing.markSharing(session)
        TripSharing.report(null)
        requestUpdates()
        watchdog?.cancel()
        watchdog = scope.launch { watch(session) }
    }

    @SuppressLint("MissingPermission") // Checked in begin(); a revoked permission ends in the catch below.
    private fun requestUpdates() {
        locations.removeLocationUpdates(callback)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, SendEveryMs)
            .setMinUpdateIntervalMillis(SendEveryMs)
            .setMaxUpdateAgeMillis(MaxFixAgeMs)
            .build()
        try {
            locations.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener {
                    TripSharing.report(NoFix)
                }
        } catch (revoked: SecurityException) {
            TripSharing.report("Location access was turned off, so sharing stopped.")
            finish()
        }
    }

    /** Same cadence as the web: at most one position every 4 s. */
    private fun send(location: Location) {
        val session = sessionId ?: return
        if (stopping) return
        val now = SystemClock.elapsedRealtime()
        if (lastSentAt != 0L && now - lastSentAt < SendEveryMs) return
        lastSentAt = now
        scope.launch {
            try {
                api.location(
                    sessionId = session,
                    lat = location.latitude,
                    lng = location.longitude,
                    heading = if (location.hasBearing()) location.bearing.toDouble() else null,
                    accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                    speedKmh = if (location.hasSpeed()) location.speed * MetresPerSecondToKmh else null,
                )
                TripSharing.report(null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ApiException) {
                onRefused(session, error)
            } catch (offline: IOException) {
                TripSharing.report("No connection. Your position is sent again as soon as you're back online.")
            }
        }
    }

    private suspend fun onRefused(session: Int, error: ApiException) {
        when (error.status) {
            // Signed out, no longer the hired person, or the trip is gone.
            401, 403, 404 -> finish()
            // "This job is already finished" or "That location is outside Rwanda": the trip decides which.
            else -> if (!stillOnTheWay(session)) finish() else TripSharing.report(error.message)
        }
    }

    /**
     * Ends sharing when the trip is no longer under way, also when that was
     * decided elsewhere (the web, or the job closing): a position sent after
     * "arrived" or "stop" would put the trip back on the way.
     */
    private suspend fun watch(session: Int) = coroutineScope {
        launch {
            container.session.state.first { it is SessionState.SignedOut }
            finish()
        }
        while (isActive) {
            delay(CheckEveryMs)
            if (!stillOnTheWay(session)) finish()
        }
    }

    private suspend fun stillOnTheWay(session: Int): Boolean = try {
        val trip = api.trip(session)
        trip.live && trip.role == Role.Worker && trip.status == TripStatus.OnTheWay
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: ApiException) {
        // Refused or gone ends it; a server hiccup (5xx, 429) does not.
        error.status !in 400..499 || error.status == 429
    } catch (offline: IOException) {
        // Can't tell offline; keep going and ask again later.
        true
    }

    /** The notification's Stop: same as Stop sharing in the app, the job provider is told. */
    private fun stopFromNotification() {
        val session = sessionId ?: return finish()
        stopping = true
        locations.removeLocationUpdates(callback)
        scope.launch {
            withTimeoutOrNull(StopTimeoutMs) {
                runCatching { api.action(session, TripAction.Stop) }
            }
            finish()
        }
    }

    private fun finish() {
        locations.removeLocationUpdates(callback)
        watchdog?.cancel()
        sessionId = null
        TripSharing.markSharing(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locations.removeLocationUpdates(callback)
        scope.cancel()
        TripSharing.markSharing(null)
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            ChannelId,
            "Trip location sharing",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = "Shown while you share your location with the person who hired you."
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(session: Int, hirerName: String?, place: String?): Notification {
        val who = hirerName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
            ?: "the job provider"
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_URL, "/trip/$session"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, TripSharingService::class.java).setAction(ActionStop),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val where = place?.takeIf { it.isNotBlank() }?.let { " to $it" }.orEmpty()
        return NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sharing your location with $who")
            .setContentText("They follow you on the map until you arrive$where.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Your position goes to $who every few seconds while you're on the way$where, " +
                        "also with the screen off. It stops when you arrive or tap Stop sharing.",
                ),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(R.drawable.ic_square, "Stop sharing", stop)
            .build()
    }

    internal companion object {
        const val ActionStart = "com.hafiyanjye.app.trip.START"
        const val ActionStop = "com.hafiyanjye.app.trip.STOP"
        const val ExtraSessionId = "sessionId"
        const val ExtraHirerName = "hirerName"
        const val ExtraPlace = "place"

        private const val NoFix = "Your phone can't find its location. Check that Location is on."
        private const val ChannelId = "trip_sharing"
        private const val NotificationId = 4_201
        private const val SendEveryMs = 4_000L
        private const val MaxFixAgeMs = 5_000L
        private const val CheckEveryMs = 30_000L
        private const val StopTimeoutMs = 10_000L
        private const val MetresPerSecondToKmh = 3.6
    }
}
