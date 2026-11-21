package com.hafiyanjye.app.data.profile

import java.text.Normalizer

/**
 * A country people can have a phone number from. [dial] is the calling code
 * without "+"; [example] is a local number without the trunk 0, when known.
 * Mirrors web/src/lib/countries.ts.
 */
data class Country(val iso: String, val name: String, val dial: String, val example: String? = null) {
    /** The flag as an emoji: the two letters as regional indicator symbols. */
    val flag: String
        get() = iso.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

object Countries {
    const val DEFAULT_ISO = "RW"

    val all: List<Country> = COUNTRY_ROWS

    private val byIso: Map<String, Country> = all.associateBy { it.iso }

    val default: Country = byIso.getValue(DEFAULT_ISO)

    /** Rwanda and its neighbours, shown first when nothing is searched. */
    val nearby: List<Country> = NEARBY_ISO.mapNotNull { byIso[it] }

    fun byIso(iso: String): Country = byIso[iso.uppercase()] ?: default

    /**
     * Countries matching a search: by name ("ken"), code ("KE") or calling
     * code ("254", "+254"). Names that start with the search come first.
     */
    fun search(query: String): List<Country> {
        val q = fold(query.trim()).removePrefix("+")
        if (q.isEmpty()) return all
        val starts = mutableListOf<Country>()
        val contains = mutableListOf<Country>()
        for (country in all) {
            val name = fold(country.name)
            when {
                name.startsWith(q) || country.iso.lowercase() == q || country.dial.startsWith(q) -> starts += country
                name.contains(q) -> contains += country
            }
        }
        return starts + contains
    }

    /** Several countries share a calling code; a stored number opens on the largest of them. */
    private val mainForDial = mapOf(
        "1" to "US", "7" to "RU", "39" to "IT", "44" to "GB", "47" to "NO", "61" to "AU",
        "212" to "MA", "262" to "RE", "358" to "FI", "590" to "GP", "599" to "CW",
    )

    /**
     * A stored number ("+250781234567") as the country and the local part the
     * form shows. A number we cannot place stays whole, under the default country
     * (it is sent back as typed because it starts with "+").
     */
    fun split(e164: String?): Pair<Country, String> {
        val text = e164?.trim().orEmpty()
        if (text.isEmpty()) return default to ""
        if (!text.startsWith("+")) return default to text
        val digits = text.drop(1).filter(Char::isDigit)
        val dial = (4 downTo 1).map { digits.take(it) }.firstOrNull { code -> all.any { it.dial == code } }
            ?: return default to text
        val country = mainForDial[dial]?.let { byIso[it] }?.takeIf { it.dial == dial } ?: all.first { it.dial == dial }
        return country to digits.drop(dial.length)
    }

    private fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
}

object PhoneRules {
    /** The web profile form's rule for Rwandan mobiles. */
    private val RW_MOBILE = Regex("^(\\+?250|0)?7[2389]\\d{7}$")

    /** E.164 as the engine reads other countries: a country code (never 0), at most 15 digits. */
    private val INTERNATIONAL = Regex("^[1-9]\\d{7,14}$")

    private val SEPARATORS = Regex("[\\s\\-.()]")

    /**
     * The full number to send: "+<dial><national digits>". A leading trunk 0
     * ("078…") is dropped; a number typed with its own "+" or "00" is kept as
     * typed. Empty when nothing was typed.
     */
    fun full(country: Country, national: String): String {
        val typed = national.trim()
        if (typed.isEmpty()) return ""
        // "00" is an international prefix only when a real number follows it ("000" is nothing).
        val international = typed.startsWith("+") || (typed.startsWith("00") && typed.any { it in '1'..'9' })
        if (international) return typed
        val digits = typed.replace(SEPARATORS, "").trimStart('0')
        return if (digits.isEmpty()) "" else "+${country.dial}$digits"
    }

    /** What is wrong with the typed number, or null when it can be saved (empty is fine: the phone is optional). */
    fun problem(country: Country, national: String): String? {
        if (national.isBlank()) return null
        val compact = full(country, national).replace(SEPARATORS, "")
        val international = when {
            compact.startsWith("+") -> compact.drop(1)
            compact.startsWith("00") -> compact.drop(2)
            else -> compact
        }
        return when {
            international.startsWith("250") ->
                if (RW_MOBILE.matches(international)) null else "Enter a Rwandan mobile number, e.g. 078 123 4567."
            INTERNATIONAL.matches(international) -> null
            national.trim().startsWith("+") || national.trim().startsWith("00") ->
                "Enter a valid mobile number, like 078 123 4567 or +254 712 345 678."
            else -> "Enter a valid mobile number for ${country.name}."
        }
    }
}
