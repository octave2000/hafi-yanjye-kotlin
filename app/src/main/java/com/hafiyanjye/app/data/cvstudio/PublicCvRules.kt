package com.hafiyanjye.app.data.cvstudio

import com.hafiyanjye.app.data.talent.TalentExperience
import java.time.YearMonth

/** What the public CV page works out from a profile (web components/talent/public/contact.ts). */
object PublicCvRules {

    /** Digits of a phone number in international form, Rwanda (250) by default. */
    fun intlDigits(phone: String): String {
        var digits = phone.filter(Char::isDigit)
        if (digits.isEmpty()) return ""
        if (digits.startsWith("00")) digits = digits.drop(2)
        return when {
            digits.startsWith("250") -> digits
            digits.startsWith("0") -> "250" + digits.drop(1)
            // A local number without the leading 0 (78x xxx xxx).
            digits.length == 9 -> "250$digits"
            else -> digits
        }
    }

    fun telUri(phone: String): String? = intlDigits(phone).takeIf { it.isNotEmpty() }?.let { "tel:+$it" }

    fun mailUri(email: String): String? = email.trim().takeIf { it.isNotEmpty() }?.let { "mailto:$it" }

    /** "https://www.github.com/aline/" → "github.com/aline". */
    fun shortUrl(url: String): String = url.trim().replace(Regex("^https?://(www\\.)?"), "").removeSuffix("/")

    /** A link the browser can open: "github.com/x" gets https://. */
    fun absoluteUrl(url: String): String {
        val trimmed = url.trim()
        val hasScheme = Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
        return if (hasScheme) trimmed else "https://$trimmed"
    }

    private fun monthIndex(value: String): Int? {
        val parts = value.split("-")
        val year = parts[0].toIntOrNull() ?: return null
        if (year < 1900) return null
        val month = parts.getOrNull(1)?.let { (it.toIntOrNull() ?: 1).coerceIn(1, 12) } ?: 1
        return year * 12 + (month - 1)
    }

    /** Whole years from the earliest job start to the latest end (or [today]). */
    fun yearsOfExperience(jobs: List<TalentExperience>, today: YearMonth = YearMonth.now()): Int {
        val now = today.year * 12 + today.monthValue - 1
        var first = Int.MAX_VALUE
        var last = Int.MIN_VALUE
        for (job in jobs) {
            val start = monthIndex(job.start) ?: continue
            first = minOf(first, start)
            val end = job.end?.takeIf { it.isNotEmpty() }?.let { monthIndex(it) ?: now } ?: now
            last = maxOf(last, end)
        }
        if (first == Int.MAX_VALUE || last < first) return 0
        return (last - first) / 12
    }
}
