package com.hafiyanjye.app.data.find

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.JobListItem
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

@Serializable
private data class AskInput(val text: String)

/** The engine calls behind asking for help: services, own requests, the nearby count, and sending a request. */
class FindApi(private val client: ApiClient) {
    /** Every service in display order (the retired "Other services" is already left out). */
    suspend fun categories(): List<FindCategory> = client.get("/api/v1/categories")

    /** The signed-in job provider's requests, newest first. */
    suspend fun requests(): List<JobListItem> = client.get("/api/v1/jobs")

    /** Sends a request; see [CreateRequestResult] for the three outcomes. */
    suspend fun create(input: CreateRequestInput): CreateRequestResult = client.post("/api/v1/jobs/create", input)

    /** Asks whether [text] is a rental search, so it can go to the listings instead of becoming a job. */
    suspend fun ask(text: String): ListingAsk = client.post("/api/v1/listings/ask", AskInput(text))

    /** Online people near a point. The position is rounded to about 100 m: a teaser needs no exact spot. */
    suspend fun nearby(lat: Double, lng: Double, text: String?, categorySlug: String?): Nearby =
        client.get(
            "/api/v1/providers/nearby",
            mapOf(
                "lat" to roundTo3(lat),
                "lng" to roundTo3(lng),
                "text" to text,
                "categorySlug" to categorySlug,
            ),
        )

    private fun roundTo3(value: Double): Double = (value * 1000).roundToLong() / 1000.0
}
