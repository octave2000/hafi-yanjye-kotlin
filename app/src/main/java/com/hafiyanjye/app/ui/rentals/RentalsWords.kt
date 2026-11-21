package com.hafiyanjye.app.ui.rentals

import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.data.rentals.CarBody
import com.hafiyanjye.app.data.rentals.FuelType
import com.hafiyanjye.app.data.rentals.HomeAttributes
import com.hafiyanjye.app.data.rentals.HomeType
import com.hafiyanjye.app.data.rentals.ListingAttributes
import com.hafiyanjye.app.data.rentals.ListingKind
import com.hafiyanjye.app.data.rentals.ListingFilters
import com.hafiyanjye.app.data.rentals.PricePeriod
import com.hafiyanjye.app.data.rentals.Transmission
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// The words shown for filters and listing facts; the web's lib/rentals.ts is the source.

internal fun HomeType.label(): String = when (this) {
    HomeType.Apartment -> "Apartment"
    HomeType.House -> "House"
    HomeType.Room -> "Room"
}

internal fun CarBody.label(): String = when (this) {
    CarBody.Sedan -> "Sedan"
    CarBody.Suv -> "SUV"
    CarBody.Hatchback -> "Hatchback"
    CarBody.Van -> "Van or minibus"
    CarBody.Pickup -> "Pickup"
}

internal fun FuelType.label(): String = when (this) {
    FuelType.Petrol -> "Petrol"
    FuelType.Diesel -> "Diesel"
    FuelType.Hybrid -> "Hybrid"
    FuelType.Electric -> "Electric"
}

internal fun Transmission.label(): String = when (this) {
    Transmission.Automatic -> "Automatic"
    Transmission.Manual -> "Manual"
}

internal fun PricePeriod.word(): String = when (this) {
    PricePeriod.Day -> "day"
    PricePeriod.Night -> "night"
    PricePeriod.Month -> "month"
}

/** "car" / "cars" and "home" / "homes". */
internal fun ListingKind.noun(count: Int = 2): String = when (this) {
    ListingKind.Car -> if (count == 1) "car" else "cars"
    ListingKind.Home -> if (count == 1) "home" else "homes"
}

internal fun ListingKind.other(): ListingKind = if (this == ListingKind.Car) ListingKind.Home else ListingKind.Car

/** "45,000 RWF / day", as on the cards. */
internal fun priceLine(priceRwf: Int, period: PricePeriod): String = "${Format.rwf(priceRwf)} / ${period.word()}"

/** "Room", "Studio" or "2 bed". */
private fun bedsShort(home: HomeAttributes): String = when {
    home.homeType == HomeType.Room -> "Room"
    home.bedrooms == 0 -> "Studio"
    else -> "${home.bedrooms} bed"
}

/** "5 seats · Automatic · With driver" / "2 bed · 1 bath · Furnished". */
internal fun detailLine(attributes: ListingAttributes): String {
    attributes.car?.let { car ->
        return listOf(
            "${car.seats} seats",
            car.transmission.label(),
            if (car.withDriver) "With driver" else "Self drive",
        ).joinToString(" · ")
    }
    val home = attributes.home ?: return ""
    return listOf(
        bedsShort(home),
        "${home.bathrooms} bath",
        if (home.furnished) "Furnished" else "Unfurnished",
    ).joinToString(" · ")
}

/** The small line above a card's title: "SUV · 2019" / "Apartment". */
internal fun kicker(attributes: ListingAttributes): String {
    attributes.car?.let { return "${it.body.label()} · ${it.year}" }
    return attributes.home?.homeType?.label().orEmpty()
}

/** The bedrooms fact on the detail page: "1 room", "Studio" or "3". */
internal fun bedroomsFact(home: HomeAttributes): String = when {
    home.homeType == HomeType.Room -> "1 room"
    home.bedrooms == 0 -> "Studio"
    else -> home.bedrooms.toString()
}

/** "November 2026", for "On Hafi Yanjye since …". */
internal fun monthYear(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK).withZone(zone).format(at)

/** One filter a person can remove on its own. */
enum class FilterKey {
    MaxPrice,
    MinBedrooms,
    HomeType,
    Furnished,
    Make,
    Body,
    MinSeats,
    Transmission,
    WithDriver,
}

/** A removable chip: which filter, and how it reads. */
internal data class FilterChipText(val key: FilterKey, val label: String)

/** One chip per filter that is set, in the web's order; only the current kind's filters count. */
internal fun filterChips(filters: ListingFilters): List<FilterChipText> {
    val chips = mutableListOf<FilterChipText>()
    val per = if (filters.kind == ListingKind.Car) "day" else "month"
    filters.maxPriceRwf?.let { chips += FilterChipText(FilterKey.MaxPrice, "Under ${Format.rwf(it)} a $per") }
    if (filters.kind == ListingKind.Home) {
        filters.minBedrooms?.let { chips += FilterChipText(FilterKey.MinBedrooms, "$it+ bedrooms") }
        filters.homeType?.let { chips += FilterChipText(FilterKey.HomeType, it.label()) }
        filters.furnished?.let {
            chips += FilterChipText(FilterKey.Furnished, if (it) "Furnished" else "Unfurnished")
        }
    } else {
        filters.make?.let { chips += FilterChipText(FilterKey.Make, it) }
        filters.body?.let { chips += FilterChipText(FilterKey.Body, it.label()) }
        filters.minSeats?.let { chips += FilterChipText(FilterKey.MinSeats, "$it+ seats") }
        filters.transmission?.let { chips += FilterChipText(FilterKey.Transmission, it.label()) }
        filters.withDriver?.let {
            chips += FilterChipText(FilterKey.WithDriver, if (it) "With driver" else "Self drive")
        }
    }
    return chips
}

/** The same filters with one removed. */
internal fun ListingFilters.without(key: FilterKey): ListingFilters = when (key) {
    FilterKey.MaxPrice -> copy(maxPriceRwf = null)
    FilterKey.MinBedrooms -> copy(minBedrooms = null)
    FilterKey.HomeType -> copy(homeType = null)
    FilterKey.Furnished -> copy(furnished = null)
    FilterKey.Make -> copy(make = null)
    FilterKey.Body -> copy(body = null)
    FilterKey.MinSeats -> copy(minSeats = null)
    FilterKey.Transmission -> copy(transmission = null)
    FilterKey.WithDriver -> copy(withDriver = null)
}

/**
 * The filters as the engine reads them: zero or blank values dropped and the
 * other kind's fields cleared, so the chips, the count and the query agree.
 */
internal fun ListingFilters.cleaned(): ListingFilters {
    val price = maxPriceRwf?.takeIf { it > 0 }
    return when (kind) {
        ListingKind.Home -> ListingFilters(
            kind = kind,
            maxPriceRwf = price,
            minBedrooms = minBedrooms?.takeIf { it > 0 },
            furnished = furnished,
            homeType = homeType,
        )
        ListingKind.Car -> ListingFilters(
            kind = kind,
            maxPriceRwf = price,
            minSeats = minSeats?.takeIf { it > 0 },
            transmission = transmission,
            withDriver = withDriver,
            body = body,
            make = make?.trim()?.take(MaxMakeLength)?.ifEmpty { null },
        )
    }
}

/** The engine compares the make in full; the web caps it at 30 characters, like the listing form. */
internal const val MaxMakeLength = 30
