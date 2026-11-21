package com.hafiyanjye.app.ui.trip

import com.hafiyanjye.app.data.api.Role
import com.hafiyanjye.app.data.trip.RouteProgress
import com.hafiyanjye.app.data.trip.Trip
import com.hafiyanjye.app.data.trip.TripRoutes
import com.hafiyanjye.app.data.trip.TripStatus
import com.hafiyanjye.app.ui.location.GeoPoint
import com.hafiyanjye.app.ui.location.distanceKm
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The words of the live trip, kept apart from the screen so they can be
 * tested. They follow the web's live-trip-map.tsx, with the role words fixed:
 * the hirer is "the job provider", never "the client".
 */
internal object TripText {
    /**
     * Phone locations are rarely better than 20–100 m indoors: below this (or
     * the accuracy the phone reports, if larger) say "very close" instead of a
     * distance that is not true.
     */
    private const val CloseMeters = 80.0

    /** Accuracy reports above this are a phone without a fix; ignore them. */
    const val MaxTrustedAccuracyMeters = 300.0

    /** Below this straight-line distance a road route says nothing useful (web `ROUTE_MIN_M`). */
    const val RouteMinMeters = 250.0

    fun firstName(name: String?): String? = name?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.ifEmpty { null }

    /** The hired person's first name. */
    fun worker(trip: Trip): String = firstName(trip.worker.name) ?: trip.worker.name

    /** The job provider's first name, or plain words when the engine has none. */
    fun hirer(trip: Trip): String = firstName(trip.hirer.name) ?: "the job provider"

    fun destination(trip: Trip) = GeoPoint(trip.destination.lat, trip.destination.lng)

    /** Straight-line kilometres from the shared position to the work, or null before any is shared. */
    fun straightKm(trip: Trip): Double? = trip.position?.let { distanceKm(GeoPoint(it.lat, it.lng), destination(trip)) }

    /** Within GPS error of the place: too close to give a distance honestly. */
    fun isVeryClose(km: Double, accuracyMeters: Double?): Boolean =
        km * 1000 <= max(CloseMeters, trusted(accuracyMeters) ?: 0.0)

    /** "within about 40 m": how sure the phone is, rounded, never below 20 m. */
    fun withinText(accuracyMeters: Double?): String {
        val meters = max(20, ((trusted(accuracyMeters) ?: CloseMeters) / 10).roundToInt() * 10)
        return "within about $meters m"
    }

    /** The reported accuracy, or null when it is too large to come from a real fix. */
    private fun trusted(accuracyMeters: Double?): Double? = accuracyMeters?.takeIf { it <= MaxTrustedAccuracyMeters }

    /** A straight-line distance as the web words it: "350 m", "2.4 km". */
    fun straightDistanceText(km: Double): String =
        if (km < 0.95) {
            "${max(100, (km * 20).roundToInt() * 50)} m"
        } else {
            String.format(Locale.US, "%.1f km", km)
        }

    /**
     * The one line under "Live map". Time is quoted only from the road route
     * (Google's travel time); a straight line gets a distance and no guess.
     */
    fun headline(trip: Trip, progress: RouteProgress?): String {
        val watching = trip.role == Role.Hirer
        val worker = worker(trip)
        val subject = if (watching) "$worker is" else "You're"
        val km = straightKm(trip)
        val accuracy = trip.position?.accuracy
        return when {
            !trip.live -> "The job is finished."
            trip.status == TripStatus.Arrived ->
                if (watching) "$worker has arrived." else "You've arrived. ${capitalized(hirer(trip))} has been told."
            trip.status == TripStatus.OnTheWay -> when {
                km == null -> if (watching) "$worker is on the way." else "Sharing your location…"
                isVeryClose(km, accuracy) ->
                    if (watching) {
                        "$worker is very close · ${withinText(accuracy)}"
                    } else {
                        "You're very close. Tap I've arrived when you're there."
                    }
                progress != null ->
                    "$subject ${TripRoutes.roadDistanceText(progress.remainingMeters)} away · " +
                        TripRoutes.etaText(progress.remainingSeconds)
                else -> "$subject ${straightDistanceText(km)} away in a straight line"
            }
            watching -> "$worker hasn't set off yet. You'll see them move here once they start the trip."
            else -> "Tap Start trip when you leave, so ${hirer(trip)} can follow you on the map."
        }
    }

    /** The "running late" line, or null when there is none to show. */
    fun delayLine(trip: Trip): String? {
        val delay = trip.delay ?: return null
        if (trip.status == TripStatus.Arrived) return null
        val who = if (trip.role == Role.Hirer) "${worker(trip)} is" else "You are"
        val note = delay.note?.let { ": “$it”" }.orEmpty()
        return "$who running about ${delay.minutes} min late · ${delay.reason.label}$note"
    }

    fun capitalized(text: String): String = text.replaceFirstChar { it.uppercase() }
}
