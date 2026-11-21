package com.hafiyanjye.app.data.rentals

import com.hafiyanjye.app.data.api.Timestamp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/** What a listing rents out. */
@Serializable
enum class ListingKind {
    @SerialName("car") Car,
    @SerialName("home") Home,
}

/** Paused listings are hidden from search and seen only by their owner. */
@Serializable
enum class ListingStatus {
    @SerialName("active") Active,
    @SerialName("paused") Paused,
}

/** Cars per day; homes per night (short stays) or per month. */
@Serializable
enum class PricePeriod {
    @SerialName("day") Day,
    @SerialName("night") Night,
    @SerialName("month") Month,
}

/** A car's gearbox. */
@Serializable
enum class Transmission {
    @SerialName("automatic") Automatic,
    @SerialName("manual") Manual,
}

/** What a car runs on. */
@Serializable
enum class FuelType {
    @SerialName("petrol") Petrol,
    @SerialName("diesel") Diesel,
    @SerialName("hybrid") Hybrid,
    @SerialName("electric") Electric,
}

/** A car's body shape. */
@Serializable
enum class CarBody {
    @SerialName("sedan") Sedan,
    @SerialName("suv") Suv,
    @SerialName("hatchback") Hatchback,
    @SerialName("van") Van,
    @SerialName("pickup") Pickup,
}

/** What kind of home. A room always counts as one bedroom. */
@Serializable
enum class HomeType {
    @SerialName("apartment") Apartment,
    @SerialName("house") House,
    @SerialName("room") Room,
}

/** The engine's word for an enum value (its `@SerialName`), for query strings. */
inline fun <reified E : Enum<E>> E.wireName(): String = serializer<E>().descriptor.getElementName(ordinal)

/** The facts of a car listing. */
@Serializable
data class CarAttributes(
    val make: String,
    val model: String,
    val year: Int,
    val seats: Int,
    val body: CarBody,
    val transmission: Transmission,
    val fuel: FuelType,
    val withDriver: Boolean,
)

/** The facts of a home listing. `bedrooms` 0 is a studio. */
@Serializable
data class HomeAttributes(
    val homeType: HomeType,
    val bedrooms: Int,
    val bathrooms: Int,
    val furnished: Boolean,
)

/** Exactly one side is set, matching the listing's kind. */
@Serializable
data class ListingAttributes(
    val car: CarAttributes? = null,
    val home: HomeAttributes? = null,
)

/** What a rental search asks for. Every field but kind is optional; each kind reads only its own fields. */
@Serializable
data class ListingFilters(
    val kind: ListingKind,
    val maxPriceRwf: Int? = null,
    val minBedrooms: Int? = null,
    val furnished: Boolean? = null,
    val homeType: HomeType? = null,
    val minSeats: Int? = null,
    val transmission: Transmission? = null,
    val withDriver: Boolean? = null,
    val body: CarBody? = null,
    val make: String? = null,
) {
    /** The query string the engine's search reads; filters of the other kind are left out, as on the web. */
    fun toQuery(): Map<String, Any?> {
        val shared = mapOf("kind" to kind.wireName(), "maxPriceRwf" to maxPriceRwf)
        val specific = when (kind) {
            ListingKind.Home -> mapOf(
                "minBedrooms" to minBedrooms,
                "furnished" to furnished,
                "homeType" to homeType?.wireName(),
            )
            ListingKind.Car -> mapOf(
                "minSeats" to minSeats,
                "transmission" to transmission?.wireName(),
                "withDriver" to withDriver,
                "body" to body?.wireName(),
                "make" to make?.trim()?.ifEmpty { null },
            )
        }
        return shared + specific
    }
}

/** A point on the map as the engine sends it. */
@Serializable
data class ListingPoint(val lat: Double, val lng: Double)

/** A listing in search results. The pin is rounded to about 100 m. */
@Serializable
data class ListingCardDto(
    val id: Int,
    val kind: ListingKind,
    val title: String,
    val priceRwf: Int,
    val pricePeriod: PricePeriod,
    val photo: String? = null,
    val photoCount: Int = 0,
    val lat: Double,
    val lng: Double,
    val areaLabel: String? = null,
    val distanceKm: Double,
    val attributes: ListingAttributes,
)

/** One page of search results, nearest first. */
@Serializable
data class ListingSearchDto(
    val items: List<ListingCardDto>,
    /** Distances are measured from here. */
    val center: ListingPoint,
    val total: Int,
    val hasMore: Boolean,
)

/** The person renting a listing out. */
@Serializable
data class ListingOwnerDto(
    val userId: Int,
    val name: String,
    val picture: String? = null,
    /** Only for signed-in callers. */
    val phone: String? = null,
    val memberSince: Timestamp,
    val activeListings: Int,
    /** Has the app open right now. */
    val online: Boolean,
)

/** The exact pin and address, sent only to the owner. */
@Serializable
data class ListingExact(val lat: Double, val lng: Double, val addressLabel: String? = null)

/** One listing in full: the card's fields plus what the detail page shows. */
@Serializable
data class ListingDto(
    val id: Int,
    val kind: ListingKind,
    val title: String,
    val priceRwf: Int,
    val pricePeriod: PricePeriod,
    val photo: String? = null,
    val photoCount: Int = 0,
    val lat: Double,
    val lng: Double,
    val areaLabel: String? = null,
    val distanceKm: Double,
    val attributes: ListingAttributes,
    val description: String = "",
    val photos: List<String> = emptyList(),
    val status: ListingStatus,
    val owner: ListingOwnerDto,
    /** The caller owns it (can edit). */
    val mine: Boolean = false,
    val exact: ListingExact? = null,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
)

/** Whether the search box text is about a rental or a job for someone nearby. */
@Serializable
enum class ListingAskKind {
    @SerialName("service") Service,
    @SerialName("rental") Rental,
}

/**
 * What the search box text is about. For `Rental`, `filters` and `summary` are
 * set and `place` names a place to look up ("Kimironko"); for `Service` all are null.
 */
@Serializable
data class ListingAskDto(
    val kind: ListingAskKind,
    val filters: ListingFilters? = null,
    val place: String? = null,
    val summary: String? = null,
)
