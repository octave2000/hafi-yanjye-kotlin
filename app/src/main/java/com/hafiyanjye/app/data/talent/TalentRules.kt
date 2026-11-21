package com.hafiyanjye.app.data.talent

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Year

/**
 * Says "the talent profile was saved" to every screen showing it. The CV tab
 * reloads on it; any feature that saves the profile (the CV studio) calls [notifySaved].
 */
object TalentChanges {
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    fun notifySaved() {
        _saved.tryEmit(Unit)
    }
}

/** Max items per list and max characters per text field (engine TALENT_LIMITS). */
object TalentLimits {
    const val Skills = 30
    const val Languages = 10
    const val Experience = 15
    const val Education = 10
    const val Links = 8
    const val DesiredRoles = 5
    const val Text = 120
    const val Summary = 1200
    const val Details = 1500

    /** One skill or wanted job, as typed (web ChipInput). */
    const val Chip = 60

    /** A language typed by hand (web LanguagesEditor). */
    const val LanguageName = 30
    const val LinkUrl = 300
    const val LinkLabel = 40

    /** One interview answer: a one-line question takes 200, the others 2000 (engine INTERVIEW_LIMITS). */
    const val ShortAnswer = 200
    const val Answer = 2000
}

/** The parts of the profile a person edits, in page order. */
enum class TalentSection(val key: String, val title: String, val addLabel: String) {
    Basics("basics", "Your details", "Add your details"),
    Summary("summary", "About", "Add a short summary"),
    Experience("experience", "Experience", "Add experience"),
    Education("education", "Education", "Add education"),
    Skills("skills", "Skills", "Add skills"),
    Languages("languages", "Languages", "Add languages"),
    Links("links", "Links", "Add a link"),
    Goals("goals", "Looking for", "Add the jobs you want");

    /** True when the dashboard shows the dashed "add" row instead of the section. */
    fun isEmpty(profile: TalentProfileFields): Boolean = when (this) {
        Basics -> profile.fullName.isBlank() && profile.headline.isBlank()
        Summary -> profile.summary.isBlank()
        Experience -> profile.experience.isEmpty()
        Education -> profile.education.isEmpty()
        Skills -> profile.skills.isEmpty()
        Languages -> profile.languages.isEmpty()
        Links -> profile.links.isEmpty()
        Goals -> profile.desiredRoles.isEmpty()
    }

    /** Shown beside the title; null for sections that aren't counted. */
    fun count(profile: TalentProfileFields): Int? = when (this) {
        Experience -> profile.experience.size
        Education -> profile.education.size
        Skills -> profile.skills.size
        Links -> profile.links.size
        else -> null
    }

    companion object {
        fun fromKey(key: String?): TalentSection? = entries.firstOrNull { it.key == key }
    }
}

/** What to do about one `missing` key: the section that fills it and the line that asks. */
data class MissingNudge(val section: TalentSection, val line: String)

/** One line of the dashboard checklist (engine `talentCompleteness` keys). */
data class ChecklistItem(val key: String, val label: String, val section: TalentSection)

/** The engine's 0–100 score and unmet keys, heaviest first. */
data class Completeness(val score: Int, val missing: List<String>)

/** The web's and engine's talent rules, shared by the CV tab and the CV studio. */
object TalentRules {

    /** Below this profile strength, the next step is the AI interview. */
    const val AI_NUDGE_BELOW = 40

    private class Item(val key: String, val weight: Int, val met: (TalentProfileFields) -> Boolean)

    // Engine COMPLETENESS_ITEMS; weights sum to 100.
    private val items = listOf(
        Item("fullName", 10) { it.fullName.isNotBlank() },
        Item("headline", 10) { it.headline.isNotBlank() },
        Item("photo", 10) { !it.photo.isNullOrEmpty() },
        Item("summary", 15) { it.summary.isNotBlank() },
        Item("experience", 20) { it.experience.isNotEmpty() },
        Item("education", 10) { it.education.isNotEmpty() },
        Item("skills", 15) { it.skills.size >= 3 },
        Item("phone", 5) { it.phone.isNotBlank() || it.email.isNotBlank() },
        Item("languages", 5) { it.languages.isNotEmpty() },
    )

    /**
     * The engine's checklist score, for a profile not saved yet (the interview
     * draft). Saved profiles carry the engine's own `completeness` and `missing`.
     */
    fun completeness(fields: TalentProfileFields): Completeness {
        val unmet = items.filterNot { it.met(fields) }
        return Completeness(
            score = items.sumOf { it.weight } - unmet.sumOf { it.weight },
            // Stable sort: ties stay in checklist order, like the engine.
            missing = unmet.sortedByDescending { it.weight }.map { it.key },
        )
    }

    private val nudges = mapOf(
        "fullName" to MissingNudge(TalentSection.Basics, "Add your full name"),
        "headline" to MissingNudge(TalentSection.Basics, "Add a headline, e.g. your trade"),
        "photo" to MissingNudge(TalentSection.Basics, "Add a profile photo"),
        "phone" to MissingNudge(TalentSection.Basics, "Add a phone number or email"),
        "email" to MissingNudge(TalentSection.Basics, "Add a phone number or email"),
        "location" to MissingNudge(TalentSection.Basics, "Add your town"),
        "summary" to MissingNudge(TalentSection.Summary, "Write a short summary about you"),
        "experience" to MissingNudge(TalentSection.Experience, "Add your most recent job"),
        "education" to MissingNudge(TalentSection.Education, "Add your school or training"),
        "skills" to MissingNudge(TalentSection.Skills, "Add at least 3 skills"),
        "languages" to MissingNudge(TalentSection.Languages, "Add the languages you speak"),
        "links" to MissingNudge(TalentSection.Links, "Add a link"),
        "desiredRoles" to MissingNudge(TalentSection.Goals, "Pick the jobs you want"),
    )

    /** The one thing to do next, from the engine's `missing` list; null when nothing is missing. */
    fun nextStep(missing: List<String>): MissingNudge? = missing.firstOrNull()?.let(nudges::get)

    val checklist = listOf(
        ChecklistItem("fullName", "Full name", TalentSection.Basics),
        ChecklistItem("headline", "Headline", TalentSection.Basics),
        ChecklistItem("photo", "Photo", TalentSection.Basics),
        ChecklistItem("phone", "Phone or email", TalentSection.Basics),
        ChecklistItem("summary", "Summary", TalentSection.Summary),
        ChecklistItem("experience", "Experience", TalentSection.Experience),
        ChecklistItem("education", "Education", TalentSection.Education),
        ChecklistItem("skills", "3 or more skills", TalentSection.Skills),
        ChecklistItem("languages", "Languages", TalentSection.Languages),
    )

    /** Enough on the profile to be worth sending as a CV (engine rule too). */
    fun hasStudioCv(profile: TalentProfileFields?): Boolean = profile != null &&
        (
            profile.summary.isNotBlank() ||
                profile.experience.isNotEmpty() ||
                profile.education.isNotEmpty() ||
                profile.skills.isNotEmpty()
            )

    // ── Dates ───────────────────────────────────────────────────────────────

    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val dateRe = Regex("""^\d{4}(-(0[1-9]|1[0-2]))?$""")

    /** "2021-03" → "Mar 2021", "2021" → "2021", null → "Now". */
    fun formatCvDate(value: String?): String {
        if (value.isNullOrEmpty()) return "Now"
        val parts = value.split("-")
        val month = parts.getOrNull(1)?.toIntOrNull()?.let { months.getOrNull(it - 1) }
        return if (month != null) "$month ${parts[0]}" else parts[0]
    }

    /** "Mar 2021 – Now"; empty when there is no start. */
    fun formatCvRange(start: String, end: String?): String =
        if (start.isEmpty()) end?.takeIf { it.isNotEmpty() }?.let(::formatCvDate).orEmpty()
        else "${formatCvDate(start)} – ${formatCvDate(end)}"

    /** Digits typed into a date box → "YYYY" or "YYYY-MM" as the person types. */
    fun dateInput(typed: String): String {
        val digits = typed.filter(Char::isDigit).take(6)
        return if (digits.length <= 4) digits else digits.take(4) + "-" + digits.drop(4)
    }

    /** What is wrong with a typed date, or null. Empty is fine: dates are optional. */
    fun dateError(value: String?, thisYear: Int = Year.now().value): String? {
        if (value.isNullOrEmpty()) return null
        if (!dateRe.matches(value)) return "Use a year or a year and month, like 2023 or 2023-05."
        val year = value.take(4).toInt()
        if (year < 1950 || year > thisYear + 10) return "Check the year."
        return null
    }

    /** An end before its start, when both are valid dates. */
    fun rangeError(start: String, end: String?): String? {
        if (end.isNullOrEmpty() || start.isEmpty()) return null
        if (dateError(start) != null || dateError(end) != null) return null
        // "2021" spans the whole year, so compare on what both give.
        val n = minOf(start.length, end.length)
        return if (end.take(n) < start.take(n)) "The end is before the start." else null
    }

    // ── Contacts and links ──────────────────────────────────────────────────

    private val emailRe = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

    fun emailError(value: String): String? =
        if (value.isBlank() || emailRe.matches(value.trim())) null else "Enter an email like aline.uwase@gmail.com."

    fun phoneError(value: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return null
        val digits = v.count(Char::isDigit)
        val ok = v.all { it.isDigit() || it in "+ -()" } && digits in 9..15
        return if (ok) null else "Enter a phone number like +250 788 123 456."
    }

    /** "linkedin.com/in/x" → "https://linkedin.com/in/x". */
    fun normalizeUrl(url: String): String {
        val u = url.trim()
        if (u.isEmpty()) return u
        return if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(u)) u else "https://$u"
    }

    /** The engine drops links that aren't web addresses with a dot in the host; say so before saving. */
    fun urlError(url: String): String? {
        val u = url.trim()
        if (u.isEmpty()) return null
        val host = normalizeUrl(u)
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        val schemeOk = !Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(u) ||
            Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(u)
        val ok = schemeOk && !u.any(Char::isWhitespace) && host.contains('.') &&
            !host.startsWith('.') && !host.endsWith('.')
        return if (ok) null else "Enter a web address like linkedin.com/in/aline-uwase."
    }

    /** "https://www.linkedin.com/in/x" → "Linkedin". */
    fun guessLabel(url: String): String {
        val host = url.trim()
            .replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^www\\."), "")
            .split('/', '.').firstOrNull().orEmpty()
        return if (host.isEmpty()) "Link" else host.replaceFirstChar { it.uppercase() }
    }

    /** "https://www.linkedin.com/in/x" → "linkedin.com". */
    fun domainOf(url: String): String {
        val host = url.substringAfter("://", "").substringBefore('/').substringBefore('?')
        return if (host.isEmpty()) url else host.removePrefix("www.")
    }

    // ── Chips ───────────────────────────────────────────────────────────────

    /** One typed skill or job, cleaned like the web: one line, single spaces, at most 60 characters. */
    fun cleanChip(typed: String, max: Int = TalentLimits.Chip): String =
        typed.replace(Regex("\\s+"), " ").trim().take(max).trim()

    /** [list] with [typed] added, unless it is empty, already there (any case) or the list is full. */
    fun addChip(list: List<String>, typed: String, max: Int): List<String> {
        val text = cleanChip(typed)
        if (text.isEmpty() || list.size >= max || list.any { it.equals(text, ignoreCase = true) }) return list
        return list + text
    }

    // Skills employers in Kigali ask for, by words in the wanted job.
    private val roleSkills = listOf(
        Regex("account|financ|audit|cashier|bank") to
            listOf("Excel", "QuickBooks", "Bookkeeping", "RRA tax filing", "Sage", "Reporting"),
        Regex("develop|software|engineer|program|web|it\\b|tech") to
            listOf("JavaScript", "React", "Python", "SQL", "Git", "Networking"),
        Regex("sales|market|business|agent") to
            listOf("Sales", "Negotiation", "Social media", "CRM", "Customer care", "MoMo"),
        Regex("hotel|hospitality|waiter|chef|cook|barista|reception|tourism") to
            listOf("Customer service", "Food safety", "Barista", "Housekeeping", "Front desk"),
        Regex("driver|logistic|delivery|transport") to
            listOf("Driving licence B", "Driving licence C", "Route planning", "Vehicle care"),
        Regex("nurse|health|medic|pharma|lab") to
            listOf("Patient care", "First aid", "Vital signs", "Record keeping"),
        Regex("teach|tutor|educat|trainer") to
            listOf("Lesson planning", "Classroom management", "Curriculum (CBC)", "Coaching"),
        Regex("design|graphic|creative|media|photo|video") to
            listOf("Canva", "Photoshop", "Figma", "Video editing", "Photography"),
        Regex("data|analyst|research|m&e|monitoring") to
            listOf("Excel", "Power BI", "Kobo Toolbox", "SPSS", "M&E"),
        Regex("admin|secretar|assistant|office|hr|human") to
            listOf("MS Office", "Filing", "Scheduling", "Payroll", "Report writing"),
    )
    private val generalSkills = listOf(
        "MS Office",
        "Customer service",
        "Communication",
        "Teamwork",
        "Driving licence B",
        "Problem solving",
    )

    /** Up to 10 skills worth offering for this person's wanted jobs and headline, minus the ones they have. */
    fun skillSuggestions(desiredRoles: List<String>, headline: String, have: List<String>): List<String> {
        val text = (desiredRoles + headline).joinToString(" ").lowercase()
        val found = roleSkills.filter { it.first.containsMatchIn(text) }.flatMap { it.second }
        val taken = have.map { it.lowercase() }.toSet()
        return (found + generalSkills).distinct().filter { it.lowercase() !in taken }.take(10)
    }

    /** Up to 8 common jobs to offer in the goals editor, minus the ones already picked. */
    fun roleSuggestions(have: List<String>): List<String> {
        val taken = have.map { it.lowercase() }.toSet()
        return popularRoles.filter { it.lowercase() !in taken }.take(8)
    }

    private val popularRoles = listOf(
        "Accountant", "Sales agent", "Driver", "Receptionist", "Software developer",
        "Teacher", "Nurse", "Waiter", "Data analyst", "Graphic designer",
    )

    /** Offered as one-tap chips in the languages editor. */
    val commonLanguages = listOf("Kinyarwanda", "English", "French", "Swahili")

    /**
     * Tapping a language chip: removes it when it is there (any case),
     * otherwise adds it at "good", like the web; nothing past the engine's 10.
     */
    fun toggleLanguage(list: List<TalentLanguage>, name: String): List<TalentLanguage> = when {
        list.any { it.name.equals(name, ignoreCase = true) } ->
            list.filterNot { it.name.equals(name, ignoreCase = true) }
        list.size >= TalentLimits.Languages -> list
        else -> list + TalentLanguage(name, TalentLanguageLevel.Good)
    }

    /** A language typed by hand joins at "basic" (web), cleaned to 30 characters; repeats are ignored. */
    fun addLanguage(list: List<TalentLanguage>, typed: String): List<TalentLanguage> {
        val name = cleanChip(typed, TalentLimits.LanguageName)
        val known = list.any { it.name.equals(name, ignoreCase = true) }
        if (name.isEmpty() || known || list.size >= TalentLimits.Languages) return list
        return list + TalentLanguage(name, TalentLanguageLevel.Basic)
    }

    // ── Interview ───────────────────────────────────────────────────────────

    /** Draft parts that came out different from the profile, in CV order (keys of TalentProfileFields). */
    fun changedKeys(base: TalentProfileFields, draft: TalentInterviewDraft): List<String> = buildList {
        if (draft.headline.isNotBlank() && draft.headline != base.headline) add("headline")
        if (draft.summary.isNotBlank() && draft.summary != base.summary) add("summary")
        if (draft.location.isNotBlank() && draft.location != base.location) add("location")
        if (draft.skills.isNotEmpty() && draft.skills != base.skills) add("skills")
        if (draft.languages.isNotEmpty() && draft.languages != base.languages) add("languages")
        if (draft.experience.isNotEmpty() && draft.experience != base.experience) add("experience")
        if (draft.education.isNotEmpty() && draft.education != base.education) add("education")
        if (draft.desiredRoles.isNotEmpty() && draft.desiredRoles != base.desiredRoles) add("desiredRoles")
    }

    /** Parts the person had already written that saving the draft will replace. */
    fun replacedLabels(base: TalentProfileFields, draft: TalentInterviewDraft): List<String> = buildList {
        if (base.headline.isNotBlank() && draft.headline != base.headline) add("headline")
        if (base.summary.isNotBlank() && draft.summary != base.summary) add("summary")
        if (base.experience.isNotEmpty() && draft.experience != base.experience) add("jobs")
        if (base.education.isNotEmpty() && draft.education != base.education) add("studies")
    }

    /** "a", "a and b", "a, b and c". */
    fun joinWords(items: List<String>): String =
        if (items.size <= 1) items.firstOrNull().orEmpty()
        else items.dropLast(1).joinToString(", ") + " and " + items.last()
}
