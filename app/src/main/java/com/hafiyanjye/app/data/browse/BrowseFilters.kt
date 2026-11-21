package com.hafiyanjye.app.data.browse

import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.ui.location.PickedLocation
import java.util.Locale

/** One choice in a filter group; `value == null` is "Any". */
data class FilterOption<T>(val value: T?, val label: String)

/** Lowest level to show (the engine's `minLevel`; "new" is not a filter). */
enum class MinLevel(val wire: String) { Bronze("bronze"), Silver("silver"), Gold("gold") }

/** The choices the web offers for each filter (web: components/browse/filters.ts). */
object FilterOptions {
    const val PageSize = 24
    const val WidestKm = 20

    val Distances: List<FilterOption<Int>> = listOf(
        FilterOption(null, "Any"),
        FilterOption(2, "Within 2 km"),
        FilterOption(5, "Within 5 km"),
        FilterOption(10, "Within 10 km"),
        FilterOption(WidestKm, "Within 20 km"),
    )
    val Ratings: List<FilterOption<Double>> = listOf(
        FilterOption(null, "Any"),
        FilterOption(4.0, "4.0+"),
        FilterOption(4.5, "4.5+"),
    )
    val Prices: List<FilterOption<Int>> = listOf(
        FilterOption(null, "Any"),
        FilterOption(5_000, "5,000"),
        FilterOption(10_000, "10,000"),
        FilterOption(20_000, "20,000"),
        FilterOption(50_000, "50,000"),
    )
    val Levels: List<FilterOption<MinLevel>> = listOf(
        FilterOption(null, "Any"),
        FilterOption(MinLevel.Bronze, "Bronze+"),
        FilterOption(MinLevel.Silver, "Silver+"),
        FilterOption(MinLevel.Gold, "Gold"),
    )
    val Languages: List<FilterOption<String>> = listOf(
        FilterOption(null, "Any"),
        FilterOption("rw", "Kinyarwanda"),
        FilterOption("en", "English"),
        FilterOption("fr", "French"),
        FilterOption("sw", "Swahili"),
    )
}

/** Which filter an active-filter chip removes. */
enum class FilterKey { Distance, Online, Rating, Price, Level, Checked, Language }

/** A removable chip above the results, e.g. "Within 5 km". */
data class FilterChip(val key: FilterKey, val label: String)

/**
 * Everything the services list filters on. [service] is a service slug; null
 * [sort] means the default (closest first with a location, else top rated).
 */
data class BrowseFilters(
    val service: String? = null,
    val q: String = "",
    val sort: SearchSort? = null,
    val km: Int? = null,
    val online: Boolean = false,
    val rating: Double? = null,
    val price: Int? = null,
    val level: MinLevel? = null,
    val checked: Boolean = false,
    val language: String? = null,
) {
    /** Filters set in the sheet (not the service, the search text or the sort). */
    val activeCount: Int
        get() = listOf(km != null, online, rating != null, price != null, level != null, checked, language != null)
            .count { it }

    /** The sheet's filters back to "Any", keeping the service, the search and the sort. */
    fun cleared(): BrowseFilters = BrowseFilters(service = service, q = q, sort = sort)

    fun without(key: FilterKey): BrowseFilters = when (key) {
        FilterKey.Distance -> copy(km = null)
        FilterKey.Online -> copy(online = false)
        FilterKey.Rating -> copy(rating = null)
        FilterKey.Price -> copy(price = null)
        FilterKey.Level -> copy(level = null)
        FilterKey.Checked -> copy(checked = false)
        FilterKey.Language -> copy(language = null)
    }

    /** Short labels for the active filters; distance only counts with a location. */
    fun chips(located: Boolean): List<FilterChip> = buildList {
        if (km != null && located) add(FilterChip(FilterKey.Distance, "Within $km km"))
        if (online) add(FilterChip(FilterKey.Online, "Available now"))
        if (rating != null) add(FilterChip(FilterKey.Rating, "Rated ${String.format(Locale.US, "%.1f", rating)}+"))
        if (price != null) add(FilterChip(FilterKey.Price, "Up to ${Format.rwf(price)}"))
        if (level != null) {
            val label = FilterOptions.Levels.firstOrNull { it.value == level }?.label ?: level.wire
            add(FilterChip(FilterKey.Level, label))
        }
        if (checked) add(FilterChip(FilterKey.Checked, "Checked CV"))
        if (language != null) {
            val name = FilterOptions.Languages.firstOrNull { it.value == language }?.label ?: language
            add(FilterChip(FilterKey.Language, "Speaks $name"))
        }
    }

    /**
     * The engine query. Defaults are left out like the web does; distance
     * filters need a location, and the engine never stores or echoes it.
     */
    fun toQuery(location: PickedLocation?, offset: Int = 0): Map<String, Any?> = mapOf(
        "lat" to location?.lat,
        "lng" to location?.lng,
        "category" to service,
        "q" to q.trim().take(MaxQueryLength).ifEmpty { null },
        "sort" to sort?.wire,
        "maxKm" to km.takeIf { location != null },
        "onlineOnly" to online.takeIf { it },
        "minRating" to rating,
        "maxPriceRwf" to price,
        "minLevel" to level?.wire,
        "checkedOnly" to checked.takeIf { it },
        "language" to language,
        "limit" to FilterOptions.PageSize,
        "offset" to offset.takeIf { it > 0 },
    )

    companion object {
        /** The engine reads at most 80 characters of search text. */
        const val MaxQueryLength = 80
    }
}
