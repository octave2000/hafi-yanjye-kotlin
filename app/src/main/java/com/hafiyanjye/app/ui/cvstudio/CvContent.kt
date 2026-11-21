package com.hafiyanjye.app.ui.cvstudio

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.cvstudio.CvStyles
import com.hafiyanjye.app.data.talent.CvFont
import com.hafiyanjye.app.data.talent.CvStyle
import com.hafiyanjye.app.data.talent.TalentEducation
import com.hafiyanjye.app.data.talent.TalentExperience
import com.hafiyanjye.app.data.talent.TalentLanguage
import com.hafiyanjye.app.data.talent.TalentProfileFields
import com.hafiyanjye.app.ui.theme.Poppins

/**
 * The CV's own ink (web cv/shared.tsx). These are colours of the printed
 * document, like the accent swatches, not of the app's chrome.
 */
internal object CvInk {
    val Ink = Color(0xFF0F1713)
    val Body = Color(0xFF3B4540)
    val Muted = Color(0xFF6A746E)
    val Rule = Color(0xFFDFE4E1)
    val Paper = Color(0xFFFFFFFF)
}

/** A contact line on the CV, with the icon the Bold and Sidebar templates show. */
internal data class CvContact(val key: String, @param:DrawableRes val icon: Int, val text: String)

/** The parts of a profile a template draws, empties removed (web cvContent). */
internal data class CvContent(
    val name: String,
    val headline: String,
    val summary: String,
    val skills: List<String>,
    val languages: List<TalentLanguage>,
    val experience: List<TalentExperience>,
    val education: List<TalentEducation>,
    val contacts: List<CvContact>,
    /** Engine photo path, already null when the style hides it. */
    val photo: String?,
)

private val LeadingBullet = Regex("^\\s*[-•*–]\\s*")

/** One achievement per line; leading bullets and dashes dropped. */
internal fun detailLines(details: String): List<String> =
    details.split("\n").map { it.replace(LeadingBullet, "").trim() }.filter { it.isNotEmpty() }

/** "https://www.github.com/aline/" → "github.com/aline". */
internal fun cleanUrl(url: String): String =
    url.trim().replace(Regex("^https?://(www\\.)?"), "").removeSuffix("/")

internal fun cvContent(fields: TalentProfileFields, style: CvStyle): CvContent {
    val contacts = buildList {
        fields.email.trim().takeIf { it.isNotEmpty() }?.let { add(CvContact("email", R.drawable.ic_mail, it)) }
        fields.phone.trim().takeIf { it.isNotEmpty() }?.let { add(CvContact("phone", R.drawable.ic_phone, it)) }
        fields.location.trim().takeIf { it.isNotEmpty() }?.let { add(CvContact("loc", R.drawable.ic_map_pin, it)) }
        fields.links.forEachIndexed { index, link ->
            if (link.url.isNotBlank()) add(CvContact("link-$index", R.drawable.ic_globe, cleanUrl(link.url)))
        }
    }
    return CvContent(
        name = fields.fullName.trim().ifEmpty { "Your name" },
        headline = fields.headline.trim(),
        summary = fields.summary.trim(),
        skills = fields.skills.map { it.trim() }.filter { it.isNotEmpty() },
        languages = fields.languages.filter { it.name.isNotBlank() },
        experience = fields.experience.filter { it.role.isNotBlank() || it.company.isNotBlank() },
        education = fields.education.filter { it.school.isNotBlank() || it.degree.isNotBlank() },
        contacts = contacts,
        photo = fields.photo?.takeIf { style.showPhoto && it.isNotBlank() },
    )
}

/** "#0b7a40" as a colour; anything else draws in the default green. */
internal fun accentColor(hex: String): Color {
    val valid = if (CvStyles.isHex(hex)) hex else CvStyle.Default.accent
    return Color(0xFF000000 or valid.substring(1).toLong(16))
}

internal fun onAccentColor(hex: String): Color = accentColor(CvStyles.textOnAccent(hex))

/**
 * The typeface for the document. Only Poppins ships with the app; the web's
 * Geist is drawn with the phone's sans-serif and Serif with its serif.
 */
internal fun cvFontFamily(font: CvFont): FontFamily = when (font) {
    CvFont.Poppins -> Poppins
    CvFont.Geist -> FontFamily.SansSerif
    CvFont.Serif -> FontFamily.Serif
}
