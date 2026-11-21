package com.hafiyanjye.app.ui.onboarding

import com.hafiyanjye.app.data.pings.PriceUnit
import com.hafiyanjye.app.data.pings.WorkCategory
import com.hafiyanjye.app.data.pings.WorkProfile
import com.hafiyanjye.app.ui.location.LocationSource
import com.hafiyanjye.app.ui.location.PickedLocation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.Normalizer
import kotlin.math.roundToInt

/** The four setup steps, in order (web: STEPS). */
enum class SetupStep(val title: String) {
    WhatYouDo("What you do"),
    WhereYouWork("Where you work"),
    AboutYou("About you"),
    Done("Done"),
    ;

    val previous: SetupStep? get() = entries.getOrNull(ordinal - 1)
    val next: SetupStep? get() = entries.getOrNull(ordinal + 1)
}

/** A form field that can carry an error, plus the whole form. */
enum class FormField { Name, Services, Location, Years, Price, Bio, Phone, Photos, Form }

/**
 * Everything the job seeker types during setup, kept as typed text so nothing
 * is lost between steps, rotation or a closed app (saved as a draft until the
 * profile exists). Mirrors the web's `Form`.
 */
@Serializable
data class WorkForm(
    val name: String = "",
    val services: List<String> = emptyList(),
    val location: PickedLocation? = null,
    val radiusKm: Int = DEFAULT_RADIUS_KM,
    val years: String = "",
    val price: String = "",
    val priceUnit: PriceUnit = PriceUnit.Job,
    val bio: String = "",
    val phone: String = "",
    val photos: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
) {
    val yearsValue: Int? get() = years.trim().toIntOrNull()
    val priceValue: Int? get() = price.trim().toIntOrNull()

    /** The problems that stop [step] from being saved, by field (web: validate). */
    fun validate(step: SetupStep): Map<FormField, String> = buildMap {
        when (step) {
            SetupStep.WhatYouDo -> {
                if (name.trim().length !in NAME_LENGTH) {
                    put(FormField.Name, "Enter the name job providers will see (2–80 letters).")
                }
                if (services.isEmpty()) put(FormField.Services, "Pick at least one service you offer.")
            }
            SetupStep.WhereYouWork -> {
                if (location == null) put(FormField.Location, "Choose where you usually work.")
            }
            SetupStep.AboutYou -> {
                if (years.isNotBlank() && yearsValue?.let { it in YEARS_RANGE } != true) {
                    put(FormField.Years, "Years must be between 0 and 60.")
                }
                if ((priceValue ?: 0) > MAX_PRICE_RWF) put(FormField.Price, "Prices must be under 10,000,000 RWF.")
                if (bio.length > BIO_MAX) put(FormField.Bio, "Keep your bio under $BIO_MAX characters.")
                when {
                    phone.isBlank() -> put(FormField.Phone, "Add your phone — chosen clients need it to reach you.")
                    !isRwandaPhone(phone) -> put(FormField.Phone, "Use a Rwandan mobile number like 078 123 4567.")
                }
            }
            SetupStep.Done -> Unit
        }
    }

    /** The live 100-point checklist; [hasPicture] is the account's photo. */
    fun completeness(hasPicture: Boolean): Completeness = Completeness.of(this, hasPicture)

    companion object {
        const val BIO_MIN = 80
        const val BIO_MAX = 1000
        const val MAX_SERVICES = 3
        const val MAX_PHOTOS = 6
        const val FULL_PHOTOS = 3
        const val MAX_RADIUS_KM = 10
        const val DEFAULT_RADIUS_KM = 5
        const val MAX_PRICE_RWF = 10_000_000
        const val MAX_YEARS_DIGITS = 2
        const val MAX_PRICE_DIGITS = 8
        const val MAX_LABEL = 120
        val NAME_LENGTH = 2..80
        val YEARS_RANGE = 0..60

        private val rwandaPhone = Regex("^(\\+?250|0)7[2389]\\d{7}$")
        private val phoneSeparators = Regex("[\\s-]")

        fun isRwandaPhone(phone: String): Boolean = rwandaPhone.matches(phone.replace(phoneSeparators, ""))

        /** "078 123-4567" → "0781234567", as the engine stores it. */
        fun cleanPhone(phone: String): String = phone.replace(phoneSeparators, "")

        /** "+250781234567" → "0781234567", as the web shows it. */
        fun localPhone(phone: String?): String {
            if (phone.isNullOrBlank()) return ""
            val digits = phone.filter(Char::isDigit)
            return if (digits.startsWith("250") && digits.length == 12) "0" + digits.drop(3) else phone
        }

        /** Same number typed two ways ("078…" and "+25078…") counts as unchanged. */
        fun samePhone(typed: String, saved: String?): Boolean =
            typed.filter(Char::isDigit) == localPhone(saved).filter(Char::isDigit)

        /** The saved profile as a form ("Other services" is retired: they pick or add their real service). */
        fun fromProfile(profile: WorkProfile, phone: String?): WorkForm = WorkForm(
            name = profile.displayName,
            services = profile.categories.map { it.slug }.filter { it != "other" },
            location = PickedLocation(
                lat = profile.lat,
                lng = profile.lng,
                label = profile.addressLabel,
                source = LocationSource.Pin,
            ),
            radiusKm = profile.serviceRadiusKm.roundToInt().coerceIn(1, MAX_RADIUS_KM),
            years = profile.yearsExperience.takeIf { it > 0 }?.toString().orEmpty(),
            price = profile.priceMinRwf?.toString().orEmpty(),
            priceUnit = profile.priceUnit,
            bio = profile.bio,
            phone = localPhone(phone),
            photos = profile.photos,
            languages = profile.languages,
        )

        /** Digits only, at most [max] of them (the web's numeric inputs). */
        fun digits(typed: String, max: Int): String = typed.filter(Char::isDigit).take(max)
    }
}

/** Profile strength: points out of 100 and what would raise them. */
data class Completeness(val score: Int, val tips: List<String>) {
    val stage: String
        get() = when {
            score >= STRONG_SCORE -> "Strong"
            score >= READY_SCORE -> "Ready for jobs"
            else -> "Getting started"
        }

    companion object {
        /** From this score a profile is "Ready" (starter seats unlock). */
        const val READY_SCORE = 60

        /** From this score a profile is "Strong". */
        const val STRONG_SCORE = 85

        /**
         * Mirrors the engine's checklist (core/providers/profile.ts, completenessItems)
         * and the web's computeCompleteness, so the meter moves while typing.
         */
        fun of(form: WorkForm, hasPicture: Boolean): Completeness {
            val bio = form.bio.trim().length
            val photos = form.photos.size
            val missingPhotos = WorkForm.FULL_PHOTOS - photos
            val parts = listOf(
                Part(if (form.name.isNotBlank()) 5 else 0, 5, "Add your display name"),
                Part(if (hasPicture) 15 else 0, 15, "Add a profile photo so clients see who is coming"),
                Part(if (form.phone.isNotBlank()) 15 else 0, 15, "Add a phone number so matched clients can reach you"),
                Part(if (form.services.isNotEmpty()) 10 else 0, 10, "Pick at least one service"),
                Part(if (form.priceValue != null) 10 else 0, 10, "Set your starting price"),
                Part(
                    points = if (bio >= WorkForm.BIO_MIN) 15 else 15 * bio / WorkForm.BIO_MIN,
                    max = 15,
                    tip = if (bio == 0) {
                        "Add a bio describing your work"
                    } else {
                        "Write a longer bio (at least ${WorkForm.BIO_MIN} characters)"
                    },
                ),
                Part(if ((form.yearsValue ?: 0) > 0) 5 else 0, 5, "Add your years of experience"),
                Part(if (form.languages.isNotEmpty()) 5 else 0, 5, "Add the languages you speak"),
                Part(
                    points = if (!form.location?.label.isNullOrBlank()) 5 else 0,
                    max = 5,
                    tip = "Add your area name (e.g. Kimironko)",
                ),
                Part(
                    points = when {
                        photos >= WorkForm.FULL_PHOTOS -> 15
                        photos >= 1 -> 10
                        else -> 0
                    },
                    max = 15,
                    tip = if (photos == 0) {
                        "Add photos of your work"
                    } else {
                        "Add $missingPhotos more work photo${if (missingPhotos == 1) "" else "s"}"
                    },
                ),
            )
            return Completeness(
                score = parts.sumOf { it.points }.coerceAtMost(100),
                tips = parts.filter { it.points < it.max }.map { it.tip },
            )
        }
    }

    private data class Part(val points: Int, val max: Int, val tip: String)
}

/** The PUT /providers/me/update body for each step, as the web sends it. */
object WorkSetupBodies {

    /** Step 1 saves only when the profile exists; it is created on step 2, with a location. */
    fun whatYouDo(form: WorkForm): JsonObject = buildJsonObject {
        put("displayName", form.name.trim())
        put("categorySlugs", strings(form.services))
    }

    fun whereYouWork(form: WorkForm, location: PickedLocation): JsonObject = buildJsonObject {
        put("displayName", form.name.trim())
        put("categorySlugs", strings(form.services))
        put("lat", location.lat)
        put("lng", location.lng)
        location.label?.trim()?.takeIf { it.isNotEmpty() }?.let { put("addressLabel", it.take(WorkForm.MAX_LABEL)) }
        put("serviceRadiusKm", form.radiusKm)
    }

    fun aboutYou(form: WorkForm): JsonObject = buildJsonObject {
        put("yearsExperience", form.yearsValue ?: 0)
        put("priceMinRwf", form.priceValue?.let(::JsonPrimitive) ?: JsonNull)
        // Prices are bargained: only a starting price, which clears an old highest one.
        put("priceMaxRwf", JsonNull)
        put("priceUnit", form.priceUnit.wire)
        put("bio", form.bio.trim())
        put("photos", strings(form.photos))
        put("languages", strings(form.languages))
    }

    private fun strings(values: List<String>) = JsonArray(values.map(::JsonPrimitive))

    private val PriceUnit.wire: String
        get() = when (this) {
            PriceUnit.Hour -> "hour"
            PriceUnit.Job -> "job"
            PriceUnit.Month -> "month"
        }
}

/** Price unit choices as the web words them. */
val PriceUnit.label: String
    get() = when (this) {
        PriceUnit.Hour -> "Per hour"
        PriceUnit.Job -> "Per job"
        PriceUnit.Month -> "Per month"
    }

/** Service search and add-your-own (web: CategoryMultiSelect). */
object ServiceSearch {

    /** Accents, case and punctuation don't matter: "Électricien" finds "electricien". */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /** Approved tiles plus anything picked; a search also finds services others registered. */
    fun filter(categories: List<WorkCategory>, query: String, picked: List<String>): List<WorkCategory> {
        val all = categories.filter { it.slug != "other" }.distinctBy { it.slug }
        val wanted = normalize(query)
        if (wanted.isEmpty()) return all.filter { it.listed || it.slug in picked }
        return all.filter { category ->
            listOf(category.nameEn, category.nameRw, category.nameFr, category.slug)
                .any { normalize(it).contains(wanted) }
        }
    }

    /** The typed name, tidied the way the engine receives it. */
    fun typed(query: String): String = query.replace(Regex("\\s+"), " ").trim()

    /** "Add … as my service" shows for 2+ letters that no shown service is named exactly. */
    fun canAdd(query: String, shown: List<WorkCategory>): Boolean {
        val wanted = normalize(query)
        val exact = shown.any { category ->
            listOf(category.nameEn, category.nameRw, category.nameFr).any { normalize(it) == wanted }
        }
        return typed(query).length >= 2 && !exact
    }
}

/** Languages a job seeker speaks: known codes first, then ones they typed (web: LanguagePicker). */
object Languages {
    /** Engine PROVIDER_LANGUAGES, named as the web names them. */
    val known: List<Pair<String, String>> = listOf(
        "rw" to "Kinyarwanda",
        "en" to "English",
        "fr" to "Français",
        "sw" to "Kiswahili",
    )

    /** Engine MAX_OTHER_LANGUAGES. */
    const val MAX_OTHER = 5
    const val MAX_LENGTH = 30

    private val knownCodes = known.map { it.first }
    private val allowed = Regex("^\\p{L}[\\p{L}\\p{M} '’-]{0,29}$")

    fun others(languages: List<String>): List<String> = languages.filter { it !in knownCodes }

    /** "rw", "fr", "Lingala" → "Kinyarwanda, Français, Lingala". */
    fun names(languages: List<String>): String =
        (known.filter { it.first in languages }.map { it.second } + others(languages)).joinToString(", ")

    /** Known codes in their fixed order, then the typed ones in the order added. */
    fun toggle(languages: List<String>, code: String): List<String> {
        val chosen = if (code in languages) languages - code else languages + code
        return ordered(chosen)
    }

    /** Adds a typed language, or says why not. A typed "english" picks the known code instead. */
    fun add(languages: List<String>, typed: String): Result<List<String>> {
        val name = typed.replace(Regex("\\s+"), " ").trim()
        if (name.isEmpty()) return Result.success(languages)
        if (!allowed.matches(name)) {
            return Result.failure(IllegalArgumentException("Use letters only, up to 30 characters."))
        }
        val nice = name.replaceFirstChar { it.uppercase() }
        val knownMatch = known.firstOrNull { it.second.equals(nice, ignoreCase = true) }
        return Result.success(
            when {
                knownMatch != null -> ordered((languages + knownMatch.first).distinct())
                others(languages).any { it.equals(nice, ignoreCase = true) } -> languages
                else -> ordered(languages + nice)
            },
        )
    }

    private fun ordered(languages: List<String>): List<String> =
        knownCodes.filter { it in languages } + others(languages)
}
