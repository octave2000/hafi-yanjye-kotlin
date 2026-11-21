package com.hafiyanjye.app.data.trip

import com.hafiyanjye.app.data.api.ApiClient

/** The trip endpoints of a hired session: `ENGINE/api/v1/match/[sessionId]/trip` and its action, location and delay. */
class TripApi(private val client: ApiClient) {

    suspend fun trip(sessionId: Int): Trip = client.get(path(sessionId))

    suspend fun action(sessionId: Int, action: TripAction): TripOk =
        client.post("${path(sessionId)}/action", TripActionInput(action))

    /** One position from the person on the way; the engine drops anything faster than one per 1.5 s. */
    suspend fun location(
        sessionId: Int,
        lat: Double,
        lng: Double,
        heading: Double?,
        accuracy: Double?,
        speedKmh: Double?,
    ): TripOk = client.post(
        "${path(sessionId)}/location",
        TripLocationInput(lat, lng, heading, accuracy, speedKmh),
    )

    suspend fun delay(sessionId: Int, minutes: Int, reason: DelayReason, note: String?): TripOk =
        client.post("${path(sessionId)}/delay", TripDelayInput(minutes, reason, note))

    private fun path(sessionId: Int) = "/api/v1/match/$sessionId/trip"

    companion object {
        /** The delay lengths the web offers; the engine accepts 5 to 180 minutes. */
        val DelayMinutes = listOf(10, 15, 30, 45, 60)

        /** Longest note the engine keeps. */
        const val DelayNoteMax = 200
    }
}
