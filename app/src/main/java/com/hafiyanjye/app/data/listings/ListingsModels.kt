package com.hafiyanjye.app.data.listings

import com.hafiyanjye.app.data.rentals.CarAttributes
import com.hafiyanjye.app.data.rentals.HomeAttributes
import com.hafiyanjye.app.data.rentals.ListingKind
import com.hafiyanjye.app.data.rentals.PricePeriod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The body of POST /listings/save. A new listing has no [id]: the engine reads
 * a present id (even null) as an edit, and `HafiJson` leaves nulls out.
 * An empty [title] is left out too, so the engine names it from the details.
 */
@Serializable
data class SaveListingInput(
    val id: Int? = null,
    val kind: ListingKind,
    val title: String? = null,
    val description: String,
    val priceRwf: Int,
    val pricePeriod: PricePeriod,
    val lat: Double,
    val lng: Double,
    val addressLabel: String? = null,
    val photos: List<String>,
    val car: CarAttributes? = null,
    val home: HomeAttributes? = null,
)

/** What an owner can do to a listing from their list. Deleting also removes its photos. */
@Serializable
enum class ListingStatusChange {
    @SerialName("active") Active,
    @SerialName("paused") Paused,
    @SerialName("deleted") Deleted,
}

/** The body of POST /listings/status. */
@Serializable
data class ListingStatusInput(val id: Int, val status: ListingStatusChange)

/** The engine's plain acknowledgement. */
@Serializable
data class ListingStatusResult(val ok: Boolean)

/** A stored photo; [url] ("/api/media/<id>") is what a listing keeps. */
@Serializable
data class UploadedPhoto(val id: String, val url: String)

/** The body of PUT /users/me/update when the listing form asks for a phone. */
@Serializable
data class PhoneInput(val phone: String)
