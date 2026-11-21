package com.hafiyanjye.app.data.pings

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.Ping

/** The job seeker's home: pings, the work profile, and the numbers around them. */
class PingsApi(private val client: ApiClient) {

    /** Which pings to list: the open ones (and jobs this person was chosen for), or past ones. */
    enum class Scope(val wire: String) {
        Active("active"),
        History("history"),
    }

    suspend fun pings(scope: Scope): List<Ping> =
        client.get("/api/v1/pings", mapOf("scope" to scope.wire))

    /** Opening a ping tells the job provider it was seen. */
    suspend fun markSeen(pingId: Int): Ping = client.put("/api/v1/pings/$pingId/seen")

    /** "I'm available", with an optional quote and note. A blank note is not sent. */
    suspend fun answerAvailable(pingId: Int, quoteRwf: Int?, note: String?): Ping = client.put(
        "/api/v1/pings/$pingId/answer",
        PingAnswerInput(
            available = true,
            quoteRwf = quoteRwf,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
        ),
    )

    suspend fun answerNotNow(pingId: Int): Ping =
        client.put("/api/v1/pings/$pingId/answer", PingAnswerInput(available = false))

    /** Take back an "available" answer; the engine refuses once the job provider has chosen. */
    suspend fun withdraw(pingId: Int): Ping = client.put("/api/v1/pings/$pingId/withdraw")

    /** Null when the account has no work profile yet. */
    suspend fun profile(): WorkProfile? = client.get("/api/v1/providers/me")

    suspend fun setAvailability(availability: Availability): WorkProfile =
        client.put("/api/v1/providers/me/availability", AvailabilityInput(availability))

    suspend fun stats(): WorkStats = client.get("/api/v1/providers/me/stats")

    suspend fun ranking(): RankingBreakdown = client.get("/api/v1/providers/me/ranking")

    /** Public reviews of a work profile, newest first. */
    suspend fun reviews(profileId: Int): List<Review> =
        client.get("/api/v1/providers/$profileId/reviews")

    /** Every service, so a ping outside this person's own services still gets its icon. */
    suspend fun categories(): List<WorkCategory> = client.get("/api/v1/categories")
}
