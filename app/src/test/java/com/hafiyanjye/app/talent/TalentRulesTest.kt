package com.hafiyanjye.app.talent

import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.talent.TalentExperience
import com.hafiyanjye.app.data.talent.TalentLanguage
import com.hafiyanjye.app.data.talent.TalentLanguageLevel
import com.hafiyanjye.app.data.talent.TalentLimits
import com.hafiyanjye.app.data.talent.TalentLink
import com.hafiyanjye.app.data.talent.TalentProfileDto
import com.hafiyanjye.app.data.talent.TalentRules
import com.hafiyanjye.app.data.talent.TalentSection
import com.hafiyanjye.app.ui.talent.editorError
import com.hafiyanjye.app.ui.talent.sectionPatch
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The web's editor rules and what each section's Save sends. */
class TalentRulesTest {
    private val profile: TalentProfileDto = HafiJson.decodeFromString<JsonObject>(TalentFixtures.profile)["data"]
        ?.let { HafiJson.decodeFromJsonElement(TalentProfileDto.serializer(), it) }
        ?: error("no profile")

    @Test
    fun `next step follows the engine's missing order`() {
        assertEquals(TalentSection.Summary, TalentRules.nextStep(profile.missing)?.section)
        assertNull(TalentRules.nextStep(emptyList()))
    }

    @Test
    fun `dates read like the web`() {
        assertEquals("Mar 2021", TalentRules.formatCvDate("2021-03"))
        assertEquals("Mar 2021 – Now", TalentRules.formatCvRange("2021-03", null))
        assertEquals("2016 – 2018", TalentRules.formatCvRange("2016", "2018"))
        assertEquals("", TalentRules.formatCvRange("", ""))
        assertEquals("2023-05", TalentRules.dateInput("202305"))
        assertNotNull(TalentRules.dateError("2024-13"))
        assertNotNull(TalentRules.rangeError("2024-05", "2023"))
        assertNull(TalentRules.rangeError("2024", "2024-01"))
    }

    @Test
    fun `links are checked, completed and labelled`() {
        assertNotNull(TalentRules.urlError("javascript:alert(1)"))
        assertNotNull(TalentRules.urlError("localhost/me"))
        assertNull(TalentRules.urlError("linkedin.com/in/aline"))
        assertEquals("https://example.rw/work", TalentRules.normalizeUrl("example.rw/work"))
        assertEquals("Linkedin", TalentRules.guessLabel("https://www.linkedin.com/in/x"))
        assertEquals("linkedin.com", TalentRules.domainOf("https://www.linkedin.com/in/x"))
    }

    @Test
    fun `chips skip repeats and stop at the engine's limit`() {
        assertEquals(listOf("Excel"), TalentRules.addChip(listOf("Excel"), "excel", 30))
        assertEquals(listOf("Excel", "Sage"), TalentRules.addChip(listOf("Excel"), "  Sage ", 30))
        val five = listOf("A", "B", "C", "D", "E")
        assertEquals(five, TalentRules.addChip(five, "F", TalentLimits.DesiredRoles))
    }

    @Test
    fun `languages toggle at good and typed ones join at basic`() {
        val one = TalentRules.toggleLanguage(emptyList(), "English")
        assertEquals(listOf(TalentLanguage("English", TalentLanguageLevel.Good)), one)
        assertEquals(emptyList<TalentLanguage>(), TalentRules.toggleLanguage(one, "english"))
        val typed = TalentRules.addLanguage(one, "  Lingala ")
        assertEquals(TalentLanguage("Lingala", TalentLanguageLevel.Basic), typed.last())
        assertEquals(typed, TalentRules.addLanguage(typed, "LINGALA"))
    }

    @Test
    fun `skill suggestions follow the wanted job and skip owned skills`() {
        val ideas = TalentRules.skillSuggestions(listOf("Accountant"), "", listOf("Excel"))
        assertEquals("QuickBooks", ideas.first())
        assertEquals(false, ideas.contains("Excel"))
    }

    @Test
    fun `experience save drops empty rows and trims`() {
        val draft = profile.copy(
            experience = listOf(
                TalentExperience(role = " Cook ", company = "Serena", end = ""),
                TalentExperience.blank(),
            ),
        )
        val sent = sectionPatch(TalentSection.Experience, draft, acceptedSummary = null).experience
        assertEquals(listOf(TalentExperience(role = "Cook", company = "Serena", end = "")), sent)
    }

    @Test
    fun `links save completes the address and guesses the label`() {
        val draft = profile.copy(links = listOf(TalentLink(url = "github.com/aline"), TalentLink(url = " ")))
        val sent = sectionPatch(TalentSection.Links, draft, acceptedSummary = null).links
        assertEquals(listOf(TalentLink(label = "Github", url = "https://github.com/aline")), sent)
    }

    @Test
    fun `details save sends a removed photo as empty and an accepted summary along`() {
        val patch = sectionPatch(TalentSection.Basics, profile.copy(photo = null), acceptedSummary = "New summary")
        assertEquals("", patch.photo)
        assertEquals("New summary", patch.summary)
        assertNull(sectionPatch(TalentSection.Basics, profile, acceptedSummary = null).summary)
    }

    @Test
    fun `an end before its start stops the save`() {
        val draft = profile.copy(experience = listOf(TalentExperience(role = "Cook", start = "2022", end = "2020")))
        assertNotNull(editorError(TalentSection.Experience, draft))
        assertNull(editorError(TalentSection.Experience, profile))
    }
}
