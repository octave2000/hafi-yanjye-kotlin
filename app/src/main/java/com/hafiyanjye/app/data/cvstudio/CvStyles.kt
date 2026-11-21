package com.hafiyanjye.app.data.cvstudio

import com.hafiyanjye.app.data.talent.CvStylePatch
import com.hafiyanjye.app.data.talent.CvStyle
import com.hafiyanjye.app.data.talent.CvTemplate
import com.hafiyanjye.app.data.talent.TalentPatch
import java.text.Normalizer
import kotlin.math.pow
import kotlin.random.Random

/** The CV look's rules, shared with the web (web/src/lib/talent.ts) and the engine. */
object CvStyles {
    private val Hex = Regex("^#[0-9a-fA-F]{6}$")

    /** Text on a light accent. */
    const val DARK_ON_ACCENT = "#0a1a11"

    /** Text on every other accent. */
    const val LIGHT_ON_ACCENT = "#ffffff"

    fun isHex(value: String): Boolean = Hex.matches(value)

    /** A saved style the document can draw: an accent that isn't "#rrggbb" falls back to the default green. */
    fun normalized(style: CvStyle): CvStyle {
        val accent = if (isHex(style.accent)) style.accent.lowercase() else CvStyle.Default.accent
        return style.copy(accent = accent)
    }

    /** The update body for a style change: the whole look, as the web sends it. */
    fun saveBody(style: CvStyle): TalentPatch = TalentPatch(
        style = CvStylePatch(
            template = style.template,
            accent = style.accent,
            font = style.font,
            showPhoto = style.showPhoto,
        ),
    )

    /** Readable text colour on a solid accent: white unless the accent is light (web textOnAccent). */
    fun textOnAccent(accent: String): String {
        if (!isHex(accent)) return LIGHT_ON_ACCENT
        fun channel(index: Int): Double {
            val value = accent.substring(1 + index * 2, 3 + index * 2).toInt(16) / 255.0
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        val luminance = 0.2126 * channel(0) + 0.7152 * channel(1) + 0.0722 * channel(2)
        return if (luminance > 0.4) DARK_ON_ACCENT else LIGHT_ON_ACCENT
    }

    /** "Surprise me": another template and another swatch, keeping font and photo. */
    fun surprise(current: CvStyle, random: Random = Random.Default): CvStyle {
        val templates = CvTemplate.entries.filter { it != current.template }
        val accents = CvStyle.Accents.filter { it != current.accent }
        return current.copy(template = templates.random(random), accent = accents.random(random))
    }

    /** "Jane Doe" → "Jane-Doe-CV.pdf", the engine's download name (cv-routes.ts cvPdfFilename). */
    fun pdfFileName(fullName: String): String {
        val base = Normalizer.normalize(fullName, Normalizer.Form.NFKD)
            .replace(Regex("[^\\x20-\\x7e]"), "")
            .replace(Regex("[^A-Za-z0-9]+"), "-")
            .trim('-')
            .take(60)
        return if (base.isEmpty()) "CV.pdf" else "$base-CV.pdf"
    }

    /** The public CV link on the web, or null when this build has no web address. */
    fun publicUrl(webBase: String, profileId: Int): String? {
        val base = webBase.trim().trimEnd('/')
        return if (base.isEmpty()) null else "$base/cv/$profileId"
    }
}
