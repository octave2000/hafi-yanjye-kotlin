package com.hafiyanjye.app.data.find

import com.hafiyanjye.app.ui.location.LocationSource
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** A service people can ask for (engine `CategoryDto`). `listed` ones are the tiles; others are found by searching. */
@Serializable
data class FindCategory(
    val id: Int,
    val slug: String,
    val nameEn: String,
    val nameRw: String = "",
    val nameFr: String = "",
    val icon: String = "",
    val listed: Boolean = true,
)

/** Body of `POST /jobs/create`. A null budget or service is left out of the JSON, as the engine expects. */
@Serializable
data class CreateRequestInput(
    val text: String,
    val lat: Double,
    val lng: Double,
    val locationSource: LocationSource,
    val addressLabel: String? = null,
    val budgetRwf: Int? = null,
    val categorySlug: String? = null,
)

/** What the engine did with a new request (engine `CreateJobResult`). */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
sealed interface CreateRequestResult {
    /** Matching has started: open the request. */
    @Serializable
    @SerialName("created")
    data class Created(val jobId: Int, val sessionId: Int) : CreateRequestResult

    /** The text is about renting a car or a home: search the listings instead. */
    @Serializable
    @SerialName("rental")
    data object Rental : CreateRequestResult

    /** The service is unclear; [uncovered] means nobody on Hafi Yanjye offers this work yet. */
    @Serializable
    @SerialName("pick_category")
    data class PickCategory(
        val suggestions: List<FindCategory> = emptyList(),
        val summary: String = "",
        val uncovered: Boolean = false,
    ) : CreateRequestResult
}

/** Listing filters the engine read from a rental search; only the kind is used by the rentals hand-off route. */
@Serializable
data class RentalFilters(
    val kind: String = "car",
    val maxPriceRwf: Int? = null,
    val minBedrooms: Int? = null,
)

/** `POST /listings/ask`: whether free text is a rental search rather than a job (engine `ListingAskDto`). */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
sealed interface ListingAsk {
    /** A job for people nearby. */
    @Serializable
    @SerialName("service")
    data object Service : ListingAsk

    /** A car or home search, with the filters and place the engine understood. */
    @Serializable
    @SerialName("rental")
    data class Rental(
        val filters: RentalFilters = RentalFilters(),
        val place: String? = null,
        val summary: String = "",
    ) : ListingAsk
}

/** `GET /providers/nearby`: how many people are online near a point. Counts only, never names or positions. */
@Serializable
data class Nearby(
    val online: Int,
    val radiusKm: Double,
    val category: FindCategory? = null,
    val typicalReplySeconds: Int? = null,
)
