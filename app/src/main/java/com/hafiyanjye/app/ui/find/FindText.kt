package com.hafiyanjye.app.ui.find

import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.data.find.FindCategory
import com.hafiyanjye.app.data.find.Nearby
import java.text.NumberFormat
import java.util.Locale

/** Longest request the engine takes, and the web search box's limit. */
const val MaxRequestLength = 500

/** Smallest and largest budget the engine accepts, in RWF. */
const val MinBudgetRwf = 1_000
const val MaxBudgetRwf = 10_000_000

/** One-tap budgets under the budget field (web: BUDGET_PRESETS). */
val BudgetPresets = listOf(5_000, 10_000, 20_000, 50_000)

/** How many services "Popular right now" shows before "View more" (web: POPULAR_SERVICE_COUNT). */
const val PopularServiceCount = 6

/** One line of typical jobs per service, so a card says what it covers (web: category-examples.ts). */
private val ServiceExamples = mapOf(
    "electrician" to "Sockets, wiring, lights, breakers",
    "plumber" to "Leaks, taps, blocked drains, tanks",
    "cleaner" to "Homes, offices, move-in cleans",
    "mason" to "Walls, floors, small repairs",
    "carpenter" to "Doors, furniture, shelves",
    "painter" to "Rooms, walls, gates",
    "mechanic" to "Cars and motos, at home",
    "tailor" to "Repairs, made-to-measure",
    "hair" to "Hair, braids, nails at home",
    "tutor" to "Lessons for school and exams",
    "it_repair" to "Laptops, Wi-Fi, software",
    "phone_repair" to "Screens, batteries, charging",
    "moto_delivery" to "Parcels, documents, shopping runs",
    "welder" to "Gates, windows, metal repairs",
    "gardener" to "Lawns, hedges, compound care",
    "cook_caterer" to "Events, parties, daily meals",
    "photographer" to "Events, portraits, products",
    "tiler" to "Floors, bathrooms, kitchens",
    "ac_fridge_repair" to "ACs, fridges, freezers",
    "laundry" to "Wash, iron, pick-up and drop-off",
    "mover" to "Houses, offices, single items",
)

/** What a service covers, for its card. */
fun serviceExample(slug: String): String = ServiceExamples[slug] ?: "Ask someone nearby"

/**
 * Placeholder lines for the search box, one per service the engine lists, each
 * a real request someone could type ("Plumber: leaks, taps…"). Never invented
 * services: only ones present in [categories] with a known example.
 */
fun searchExamples(categories: List<FindCategory>): List<String> =
    tileServices(categories)
        .mapNotNull { service -> ServiceExamples[service.slug]?.let { "${service.nameEn}: ${it.lowercase()}" } }

/** Shown while the service list loads or when it fails, so the screen is never empty (mirrors the seed). */
val FallbackCategories = listOf(
    FindCategory(1, "electrician", "Electrician", icon = "Zap"),
    FindCategory(2, "plumber", "Plumber", icon = "Droplets"),
    FindCategory(3, "cleaner", "Cleaner", icon = "Sparkles"),
    FindCategory(4, "mason", "Mason", icon = "BrickWall"),
    FindCategory(5, "carpenter", "Carpenter", icon = "Hammer"),
    FindCategory(6, "painter", "Painter", icon = "Paintbrush"),
)

/** Approved services only (registered ones are found by searching), keeping [keep] when it was picked. */
fun tileServices(list: List<FindCategory>, keep: String? = null): List<FindCategory> =
    list.filter { it.slug != "other" && (it.listed || it.slug == keep) }

/** "Electrician: sockets spark" → "sockets spark"; "Electrician: " → "". */
fun detailsOf(text: String, categoryName: String?): String {
    val trimmed = text.trim()
    if (categoryName == null) return trimmed
    val prefix = "$categoryName:"
    return if (trimmed.startsWith(prefix, ignoreCase = true)) trimmed.drop(prefix.length).trim() else trimmed
}

/** The text sent to the engine: the details under the service name, or "I need an electrician" when none. */
fun requestText(text: String, category: FindCategory?): String {
    val details = detailsOf(text, category?.nameEn)
    return when {
        category == null -> details
        details.length >= 3 -> "${category.nameEn}: $details"
        else -> "I need ${withArticle(categoryNoun(category.nameEn, 1))}"
    }
}

/** "Electrician" → "electrician" / "electricians"; "Hair & beauty" → "hair & beauty pro(s)". */
fun categoryNoun(nameEn: String, count: Int): String {
    val name = nameEn.trim().lowercase()
    if (name.isEmpty()) return if (count == 1) "person" else "people"
    val oneWord = !name.contains(Regex("\\s"))
    // Already a plural person noun ("Movers"): the singular drops the "s".
    if (oneWord && name.endsWith("ers")) return if (count == 1) name.dropLast(1) else name
    val isPerson = oneWord && Regex("(er|or|ian|ic|on)$").containsMatchIn(name)
    val noun = if (isPerson) name else "$name pro"
    return if (count == 1) noun else "${noun}s"
}

/** "electrician" → "an electrician", "plumber" → "a plumber". */
fun withArticle(noun: String): String {
    val vowel = noun.firstOrNull()?.lowercaseChar()?.let { it in "aeiou" } == true
    return if (vowel) "an $noun" else "a $noun"
}

/** The request text sent with the nearby count: no "Service: " prefix, at most 200 characters, null when short. */
fun nearbyQuery(text: String): String? =
    text.replace(Regex("^[^:]{1,40}:\\s*"), "").trim().take(200).takeIf { it.length >= 3 }

/** "2 plumbers online within 10 km · usually reply in about 3 min", only from the engine's counts. */
fun nearbySentence(data: Nearby, hasLocation: Boolean): String {
    val near = if (hasLocation) "near you" else "in Kigali"
    val where = if (hasLocation) "within ${Format.km(data.radiusKm)}" else "in Kigali"
    val name = data.category?.nameEn
    if (data.online == 0) {
        return if (name != null) {
            "No ${categoryNoun(name, 2)} online $near right now. " +
                "Post your request and we'll tell you the moment one is."
        } else {
            "No one is online $near right now. Post your request and we'll tell you the moment someone is."
        }
    }
    val who = if (name != null) {
        "${data.online} ${categoryNoun(name, data.online)} online $where"
    } else {
        "${data.online} ${if (data.online == 1) "person" else "people"} online $near"
    }
    val reply = data.typicalReplySeconds ?: return who
    val verb = if (data.online == 1) "usually replies" else "usually reply"
    return "$who · $verb in about ${Format.duration(reply.toLong())}"
}

/** A budget is optional: empty is fine, a given amount must be in the engine's range. */
fun budgetError(amount: Int?): String? = when {
    amount == null -> null
    amount < MinBudgetRwf -> "The smallest budget is 1,000 RWF."
    amount > MaxBudgetRwf -> "That's above 10,000,000 RWF. Try a smaller amount."
    else -> null
}

/** What the budget box holds after typing: digits only, at most 8, null when empty. */
fun budgetFromInput(typed: String): Int? = typed.filter(Char::isDigit).take(8).toIntOrNull()

/** 15000 → "15,000" for the budget box and its quick picks. */
fun groupedAmount(amount: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(amount)

private const val Number = "(\\d{1,3}(?:[ ,.]\\d{3})+|\\d+)"
private const val Currency = "(?:rwf|frw|rf|francs?|amafaranga|amfr)"
private const val Thousand = "(k|thousand|ibihumbi)"
private const val Cue = "(?:budget|pay|afford|up to|max(?:imum)?|around|about|for|nzishyura|ingengo)"

/** Same patterns, same order as the web's budget-from-text.ts. */
private val BudgetPatterns = listOf(
    "$Number\\s*$Thousand?\\s*$Currency\\b",
    "\\b$Currency\\.?\\s*$Number\\s*$Thousand?",
    "\\b$Cue\\b[^\\d\\n]{0,15}$Number\\s*$Thousand?",
    "$Number\\s*$Thousand?\\s*(?:as\\s+(?:my\\s+)?)?budget\\b",
    "\\b$Number\\s*(k)\\b",
).map { Regex(it, RegexOption.IGNORE_CASE) }

/**
 * The budget someone wrote in their own words ("budget 18000", "about 20k",
 * "frw 15 000"), or null. Only numbers tied to money count, so "2 bedrooms"
 * or "KG 11 Ave" are never read as a budget.
 */
fun budgetFromText(text: String): Int? {
    for (pattern in BudgetPatterns) {
        val match = pattern.find(text) ?: continue
        val digits = match.groupValues[1].replace(Regex("[ ,.]"), "").toLongOrNull() ?: continue
        val amount = if (match.groupValues.getOrNull(2).isNullOrEmpty()) digits else digits * 1000
        if (amount in MinBudgetRwf..MaxBudgetRwf) return amount.toInt()
    }
    return null
}
