package com.hafiyanjye.app.ui.browse

import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.data.browse.FilterOptions
import com.hafiyanjye.app.data.match.JobCategory
import com.hafiyanjye.app.data.match.PriceUnit
import com.hafiyanjye.app.ui.match.categoryNoun
import java.util.Locale

/** "Jean Bosco Habimana" → "Jean": buttons and messages use the first name. */
internal fun firstNameOf(displayName: String): String =
    displayName.trim().split(Regex("\\s+")).firstOrNull()?.ifEmpty { null } ?: displayName

/** Prices are bargained: where they start, never a ceiling. "From 10,000 RWF / job", or "Ask for a quote". */
internal fun startingPrice(amount: Int?, unit: PriceUnit): String =
    amount?.let { "From ${Format.rwf(it)} ${unit.short}" } ?: "Ask for a quote"

/** The engine rounds anything close to 0.5 km, so that reads "Under 1 km". */
internal fun distanceLabel(km: Double): String = when {
    km < 1 -> "Under 1 km"
    km % 1.0 == 0.0 -> "${km.toInt()} km"
    else -> String.format(Locale.US, "%.1f km", km)
}

/** ["rw", "en"] → "Kinyarwanda, English"; unknown codes are kept as they are, after the known ones. */
internal fun languageNames(codes: List<String>): String {
    val known = FilterOptions.Languages.filter { it.value != null && it.value in codes }.map { it.label }
    val others = codes.filter { code -> FilterOptions.Languages.none { it.value == code } }
    return (known + others).joinToString(", ")
}

/** "Electrician · Mason · +1": at most two services, then how many more. */
internal fun servicesLine(categories: List<JobCategory>): String {
    val names = categories.map { it.nameEn }
    val shown = names.take(2).joinToString(" · ")
    return if (names.size > 2) "$shown · +${names.size - 2}" else shown
}

/** "Electricians near you" or "Find help near you". */
internal fun servicesTitle(selected: JobCategory?): String =
    selected?.let { "${categoryNoun(it.nameEn, 2).replaceFirstChar(Char::uppercase)} near you" } ?: "Find help near you"

/** Plural noun for the people listed: "electricians", "people". */
internal fun peopleNoun(selected: JobCategory?): String = selected?.let { categoryNoun(it.nameEn, 2) } ?: "people"

/** "13 electricians near Kimironko", "1 person". */
internal fun resultsCount(total: Int, selected: JobCategory?, near: String?): String {
    val noun = when {
        total != 1 -> peopleNoun(selected)
        selected != null -> categoryNoun(selected.nameEn, 1)
        else -> "person"
    }
    return "$total $noun" + (near?.let { " near $it" } ?: "")
}

/** "7 yrs", "1 yr" or "New". */
internal fun experienceLabel(years: Int): String = when {
    years <= 0 -> "New"
    years == 1 -> "1 yr"
    else -> "$years yrs"
}

/** 95 → "Usually replies in 2 min"; null when unknown. */
internal fun replyText(seconds: Int?): String? = seconds?.let { "Usually replies in ${Format.duration(it.toLong())}" }

internal fun jobsCount(count: Int): String = if (count == 1) "1 job" else "$count jobs"

/** "Show 13 results" on the filter sheet's button. */
internal fun showResultsLabel(total: Int?): String = when (total) {
    null -> "Show results"
    1 -> "Show 1 result"
    else -> "Show $total results"
}
