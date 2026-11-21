package com.hafiyanjye.app.core

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Display formatting shared across screens (Kigali / RWF / Rwandan phones). Mirrors web/src/lib/format.ts. */
object Format {

    /** 20000 → "20,000 RWF" */
    fun rwf(amount: Int): String = "${NumberFormat.getIntegerInstance(Locale.US).format(amount)} RWF"

    /** A request's budget: "18,000 RWF", or "No budget" when none was given. */
    fun budget(amount: Int?): String = if (amount == null) "No budget" else rwf(amount)

    /** 0.85 → "850 m", 2.43 → "2.4 km", 12.6 → "13 km" */
    fun km(km: Double): String = when {
        !km.isFinite() || km < 0 -> "—"
        km < 1 -> "${max(10, ((km * 1000) / 10).roundToInt() * 10)} m"
        km < 10 -> String.format(Locale.US, "%.1f km", km)
        else -> "${km.roundToInt()} km"
    }

    /** "just now", "3 min ago", "2 h ago", "Mon", "12 Nov" */
    fun timeAgo(at: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val seconds = ((now.toEpochMilli() - at.toEpochMilli()) / 1000.0).roundToLong()
        if (seconds < 45) return "just now"
        val minutes = (seconds / 60.0).roundToLong()
        if (minutes < 60) return "$minutes min ago"
        val hours = (minutes / 60.0).roundToLong()
        if (hours < 24) return "$hours h ago"
        val local = at.atZone(zone)
        return if (hours < 24 * 7) WEEKDAY.format(local) else DAY_MONTH.format(local)
    }

    /** Whole seconds from now until [at] (0 when past). */
    fun secondsUntil(at: Instant, now: Instant = Instant.now()): Long {
        val millis = at.toEpochMilli() - now.toEpochMilli()
        return if (millis > 0) (millis + 999) / 1000 else 0
    }

    /** 48 → "48 s", 130 → "2 min", 3900 → "1 h 5 min" */
    fun duration(seconds: Long): String {
        val s = max(0, seconds)
        if (s < 60) return "$s s"
        val minutes = (s / 60.0).roundToLong()
        if (minutes < 60) return "$minutes min"
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest > 0) "$hours h $rest min" else "$hours h"
    }

    /** "+250781234567" → "+250 78 123 4567" (other numbers: lightly grouped). */
    fun phone(e164: String): String {
        val d = e164.filter(Char::isDigit)
        return when {
            d.startsWith("250") && d.length == 12 ->
                "+250 ${d.substring(3, 5)} ${d.substring(5, 8)} ${d.substring(8)}"
            d.length == 10 && d.startsWith("07") ->
                "+250 ${d.substring(1, 3)} ${d.substring(3, 6)} ${d.substring(6)}"
            e164.startsWith("+") -> "+$d"
            else -> e164
        }
    }

    /**
     * What is sent to the engine for a typed phone number: international
     * numbers ("+…", "00…") as typed, local ones with Rwanda's +250.
     * Empty when nothing was typed.
     */
    fun fullPhone(typed: String, dial: String = "250"): String {
        val text = typed.trim()
        if (text.isEmpty()) return ""
        if (text.startsWith("+") || text.startsWith("00")) return text
        val digits = text.filter(Char::isDigit).trimStart('0')
        return if (digits.isEmpty()) "" else "+$dial$digits"
    }

    private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.UK)
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
}
