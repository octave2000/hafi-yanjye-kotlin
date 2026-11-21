package com.hafiyanjye.app.cvstudio

import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.cvstudio.CvStyles
import com.hafiyanjye.app.data.talent.CvFont
import com.hafiyanjye.app.data.talent.CvStyle
import com.hafiyanjye.app.data.talent.CvTemplate
import com.hafiyanjye.app.data.talent.TalentPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class CvStylesTest {
    @Test
    fun `defaults match the web's DEFAULT_CV_STYLE`() {
        val style = CvStyle()
        assertEquals(CvTemplate.Bold, style.template)
        assertEquals("#0b7a40", style.accent)
        assertEquals(CvFont.Poppins, style.font)
        assertEquals(true, style.showPhoto)
        assertEquals(
            listOf("#0b7a40", "#0a1a11", "#2563c9", "#d13438", "#a15f00", "#0f766e", "#c2410c", "#be185d"),
            CvStyle.Accents,
        )
    }

    @Test
    fun `a style change saves the whole look and nothing else`() {
        val body = CvStyles.saveBody(CvStyle(CvTemplate.Sidebar, "#2563c9", CvFont.Serif, showPhoto = false))
        assertEquals(
            """{"style":{"template":"sidebar","accent":"#2563c9","font":"serif","showPhoto":false}}""",
            HafiJson.encodeToString(TalentPatch.serializer(), body),
        )
    }

    @Test
    fun `the default look is still sent in full`() {
        assertEquals(
            """{"style":{"template":"bold","accent":"#0b7a40","font":"poppins","showPhoto":true}}""",
            HafiJson.encodeToString(TalentPatch.serializer(), CvStyles.saveBody(CvStyle())),
        )
    }

    @Test
    fun `an accent that isn't six hex digits draws in the default green`() {
        assertEquals("#0b7a40", CvStyles.normalized(CvStyle(accent = "red;display:none")).accent)
        assertEquals("#2563c9", CvStyles.normalized(CvStyle(accent = "#2563C9")).accent)
    }

    @Test
    fun `text on an accent follows the web's luminance rule`() {
        assertEquals(CvStyles.LIGHT_ON_ACCENT, CvStyles.textOnAccent("#0b7a40"))
        assertEquals(CvStyles.LIGHT_ON_ACCENT, CvStyles.textOnAccent("#0a1a11"))
        assertEquals(CvStyles.DARK_ON_ACCENT, CvStyles.textOnAccent("#f5d90a"))
        assertEquals(CvStyles.DARK_ON_ACCENT, CvStyles.textOnAccent("#ffffff"))
        assertEquals(CvStyles.LIGHT_ON_ACCENT, CvStyles.textOnAccent("nope"))
    }

    @Test
    fun `surprise changes template and accent but keeps font and photo`() {
        val current = CvStyle(CvTemplate.Classic, "#d13438", CvFont.Geist, showPhoto = false)
        repeat(20) { seed ->
            val next = CvStyles.surprise(current, Random(seed))
            assertNotEquals(current.template, next.template)
            assertNotEquals(current.accent, next.accent)
            assertEquals(CvFont.Geist, next.font)
            assertEquals(false, next.showPhoto)
        }
    }

    @Test
    fun `pdf file names match the engine's`() {
        assertEquals("Jean-Bosco-Habimana-CV.pdf", CvStyles.pdfFileName("Jean Bosco Habimana"))
        assertEquals("Ange-Uwimana-CV.pdf", CvStyles.pdfFileName("  Angé Uwimana "))
        assertEquals("CV.pdf", CvStyles.pdfFileName("   "))
    }

    @Test
    fun `public link needs a web address`() {
        assertEquals("https://hafiyanjye.rw/cv/3", CvStyles.publicUrl("https://hafiyanjye.rw/", 3))
        assertNull(CvStyles.publicUrl("", 3))
    }
}
