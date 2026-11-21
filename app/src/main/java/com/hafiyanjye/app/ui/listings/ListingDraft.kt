package com.hafiyanjye.app.ui.listings

import com.hafiyanjye.app.data.listings.SaveListingInput
import com.hafiyanjye.app.data.rentals.CarAttributes
import com.hafiyanjye.app.data.rentals.CarBody
import com.hafiyanjye.app.data.rentals.FuelType
import com.hafiyanjye.app.data.rentals.HomeAttributes
import com.hafiyanjye.app.data.rentals.HomeType
import com.hafiyanjye.app.data.rentals.ListingDto
import com.hafiyanjye.app.data.rentals.ListingKind
import com.hafiyanjye.app.data.rentals.PricePeriod
import com.hafiyanjye.app.data.rentals.Transmission
import com.hafiyanjye.app.ui.location.LocationSource
import com.hafiyanjye.app.ui.location.PickedLocation
import java.time.Year

// Limits from the engine's core/listings/validate.ts; the engine still checks
// every one and its message is shown when it refuses.
const val MAX_LISTING_PHOTOS = 8
const val MIN_PRICE_RWF = 1_000
const val MAX_PRICE_RWF = 50_000_000
const val MAX_TITLE = 80
const val MAX_DESCRIPTION = 2_000
const val MAX_MAKE = 30
const val MAX_MODEL = 40
const val MAX_ROOMS = 20

/** The digits a price field keeps: 50,000,000 has eight. */
private const val MAX_PRICE_DIGITS = 8

private val RwandanMobile = Regex("^(\\+?250|0)?7[2389]\\d{7}$")
private val PhoneSeparators = Regex("[\\s-]")
private val Spaces = Regex("\\s+")

/** A form field that can carry a message; the order is the form's reading order, for focusing the first problem. */
enum class DraftField { Kind, Make, Model, Year, Seats, Title, Description, Price, Photos, Location, Phone }

/** The car part of the form, as typed. */
data class CarDraft(
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val seats: String = "5",
    val body: CarBody = CarBody.Suv,
    val transmission: Transmission = Transmission.Automatic,
    val fuel: FuelType = FuelType.Petrol,
    val withDriver: Boolean = false,
)

/** The home part of the form. Bedrooms 0 is a studio. */
data class HomeDraft(
    val homeType: HomeType = HomeType.Apartment,
    val bedrooms: Int = 2,
    val bathrooms: Int = 1,
    val furnished: Boolean = false,
)

/** Everything the listing form holds, as typed (web: `Form` in listing-form.tsx). */
data class ListingDraft(
    val kind: ListingKind? = null,
    val title: String = "",
    val description: String = "",
    /** Digits only. */
    val price: String = "",
    /** Homes only; cars are always per day. */
    val homePeriod: PricePeriod = PricePeriod.Month,
    val location: PickedLocation? = null,
    /** Uploaded photos ("/api/media/<id>"), the cover first. */
    val photos: List<String> = emptyList(),
    val car: CarDraft = CarDraft(),
    val home: HomeDraft = HomeDraft(),
    /** Asked only when the account has no phone yet. */
    val phone: String = "",
) {
    val period: PricePeriod
        get() = if (kind == ListingKind.Car) PricePeriod.Day else homePeriod

    /** What the engine calls the listing when the title is left empty. */
    val titlePlaceholder: String
        get() = when {
            kind == ListingKind.Car -> listOf(
                car.make.trim().ifEmpty { "Toyota" },
                car.model.trim().ifEmpty { "RAV4" },
                car.year.ifEmpty { "2019" },
            ).joinToString(" ")
            home.homeType == HomeType.Room -> "Room"
            home.bedrooms == 0 -> "Studio ${homeWord(home.homeType)}"
            else -> "${home.bedrooms}-bedroom ${homeWord(home.homeType)}"
        }

    val pricePlaceholder: String
        get() = when {
            kind == ListingKind.Car -> "45000"
            homePeriod == PricePeriod.Night -> "30000"
            else -> "350000"
        }

    /** Each field's first problem, in the form's words. Empty when the engine should accept it. */
    fun problems(needsPhone: Boolean, thisYear: Int = Year.now().value): Map<DraftField, String> = buildMap {
        if (kind == null) put(DraftField.Kind, "Choose a car or a home.")
        if (kind == ListingKind.Car) putAll(carProblems(thisYear))
        val title = oneLine(title)
        if (title.isNotEmpty() && title.length !in 3..MAX_TITLE) {
            put(DraftField.Title, "Use 3 to $MAX_TITLE characters, or leave it empty.")
        }
        if (description.trim().length > MAX_DESCRIPTION) {
            put(DraftField.Description, "Use at most 2,000 characters.")
        }
        val amount = price.toLongOrNull()
        when {
            amount == null || amount < MIN_PRICE_RWF -> {
                put(DraftField.Price, "Enter the price in RWF (at least 1,000).")
            }
            amount > MAX_PRICE_RWF -> put(DraftField.Price, "The price can be at most 50,000,000 RWF.")
        }
        if (photos.size > MAX_LISTING_PHOTOS) put(DraftField.Photos, "At most $MAX_LISTING_PHOTOS photos.")
        val place = location
        when {
            place == null -> put(DraftField.Location, "Set where it is.")
            !isInRwanda(place.lat, place.lng) -> put(DraftField.Location, "Put the pin somewhere in Rwanda.")
        }
        if (needsPhone && !RwandanMobile.matches(cleanPhone(phone))) {
            put(DraftField.Phone, "Enter a Rwandan mobile number, e.g. 078 123 4567.")
        }
    }

    private fun carProblems(thisYear: Int): Map<DraftField, String> = buildMap {
        val make = oneLine(car.make)
        if (make.length !in 2..MAX_MAKE) put(DraftField.Make, "Enter the make, e.g. Toyota.")
        val model = oneLine(car.model)
        if (model.length !in 1..MAX_MODEL) put(DraftField.Model, "Enter the model, e.g. RAV4.")
        if (car.year.toIntOrNull() !in 1980..thisYear + 1) {
            put(DraftField.Year, "Enter the year, between 1980 and ${thisYear + 1}.")
        }
        if (car.seats.toIntOrNull() !in 2..60) put(DraftField.Seats, "Between 2 and 60 seats.")
    }

    /** The save request, or null while [problems] is not empty. */
    fun toInput(id: Int?, thisYear: Int = Year.now().value): SaveListingInput? {
        val kind = kind ?: return null
        val place = location ?: return null
        val priceRwf = price.toIntOrNull() ?: return null
        if (problems(needsPhone = false, thisYear = thisYear).isNotEmpty()) return null
        return SaveListingInput(
            id = id,
            kind = kind,
            title = oneLine(title).ifEmpty { null },
            description = description.trim(),
            priceRwf = priceRwf,
            pricePeriod = period,
            lat = place.lat,
            lng = place.lng,
            addressLabel = place.label,
            photos = photos,
            car = if (kind == ListingKind.Car) carAttributes() else null,
            home = if (kind == ListingKind.Home) homeAttributes() else null,
        )
    }

    private fun carAttributes(): CarAttributes? {
        val year = car.year.toIntOrNull() ?: return null
        val seats = car.seats.toIntOrNull() ?: return null
        return CarAttributes(
            make = oneLine(car.make),
            model = oneLine(car.model),
            year = year,
            seats = seats,
            body = car.body,
            transmission = car.transmission,
            fuel = car.fuel,
            withDriver = car.withDriver,
        )
    }

    // A room is one bedroom by definition; the engine stores it that way too.
    private fun homeAttributes(): HomeAttributes = HomeAttributes(
        homeType = home.homeType,
        bedrooms = if (home.homeType == HomeType.Room) 1 else home.bedrooms,
        bathrooms = home.bathrooms,
        furnished = home.furnished,
    )

    companion object {
        /** The form filled from a saved listing; the exact pin is only sent to its owner. */
        fun from(listing: ListingDto): ListingDraft {
            val car = listing.attributes.car
            val home = listing.attributes.home
            return ListingDraft(
                kind = listing.kind,
                title = listing.title,
                description = listing.description,
                price = listing.priceRwf.toString(),
                homePeriod = if (listing.pricePeriod == PricePeriod.Night) PricePeriod.Night else PricePeriod.Month,
                location = listing.exact?.let {
                    PickedLocation(it.lat, it.lng, it.addressLabel, LocationSource.Pin)
                },
                photos = listing.photos,
                car = car?.let {
                    CarDraft(
                        make = it.make,
                        model = it.model,
                        year = it.year.toString(),
                        seats = it.seats.toString(),
                        body = it.body,
                        transmission = it.transmission,
                        fuel = it.fuel,
                        withDriver = it.withDriver,
                    )
                } ?: CarDraft(),
                home = home?.let {
                    HomeDraft(it.homeType, it.bedrooms, it.bathrooms, it.furnished)
                } ?: HomeDraft(),
            )
        }
    }
}

/** What a price field keeps of what was typed or pasted: digits, at most eight. */
fun priceDigits(typed: String): String = typed.filter(Char::isDigit).take(MAX_PRICE_DIGITS)

/** The phone as the engine expects it: no spaces or dashes. */
fun cleanPhone(phone: String): String = phone.replace(PhoneSeparators, "")

/** The engine's bounds for a pin (core/matching/geo.ts). */
fun isInRwanda(lat: Double, lng: Double): Boolean = lat in -3.0..-1.0 && lng in 28.8..31.0

private fun oneLine(text: String): String = text.replace(Spaces, " ").trim()

private fun homeWord(type: HomeType): String = when (type) {
    HomeType.Apartment -> "apartment"
    HomeType.House -> "house"
    HomeType.Room -> "room"
}

/** Moves the photo at [from] one place earlier or later; the first one is the cover. */
fun List<String>.moved(from: Int, by: Int): List<String> {
    val to = from + by
    if (from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** Puts [photo] first, as the cover. */
fun List<String>.withCover(photo: String): List<String> = listOf(photo) + filter { it != photo }
