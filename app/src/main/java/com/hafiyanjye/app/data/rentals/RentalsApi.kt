package com.hafiyanjye.app.data.rentals

import com.hafiyanjye.app.data.api.ApiClient
import kotlinx.serialization.Serializable

/** The engine's public rental endpoints: search, one listing, and reading the search box. */
class RentalsApi(private val client: ApiClient) {

    /** Listings nearest first from [lat]/[lng] (Kigali centre when absent or outside Rwanda), 24 a page. */
    suspend fun search(filters: ListingFilters, lat: Double?, lng: Double?, page: Int = 0): ListingSearchDto =
        client.get(
            "/api/v1/listings/search",
            filters.toQuery() + mapOf("lat" to lat, "lng" to lng, "page" to page.takeIf { it > 0 }),
        )

    /** One listing; [lat]/[lng] only change the distance it reports. */
    suspend fun listing(id: Int, lat: Double? = null, lng: Double? = null): ListingDto =
        client.get("/api/v1/listings/$id", mapOf("lat" to lat, "lng" to lng))

    /** Reads what someone typed: a rental (with filters) or a job for someone nearby. */
    suspend fun ask(text: String): ListingAskDto =
        client.post<AskBody, ListingAskDto>("/api/v1/listings/ask", AskBody(text))

    @Serializable
    private data class AskBody(val text: String)
}
