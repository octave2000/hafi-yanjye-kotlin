package com.hafiyanjye.app.onboarding

import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.pings.PriceUnit
import com.hafiyanjye.app.data.pings.WorkCategory
import com.hafiyanjye.app.data.pings.WorkProfile
import com.hafiyanjye.app.ui.location.PickedLocation
import com.hafiyanjye.app.ui.onboarding.Completeness
import com.hafiyanjye.app.ui.onboarding.FormField
import com.hafiyanjye.app.ui.onboarding.Languages
import com.hafiyanjye.app.ui.onboarding.ServiceSearch
import com.hafiyanjye.app.ui.onboarding.SetupStep
import com.hafiyanjye.app.ui.onboarding.WorkForm
import com.hafiyanjye.app.ui.onboarding.WorkSetupBodies
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkFormTest {

    private val profile: WorkProfile = data(OnboardingFixtures.PROFILE)
    private val categories: List<WorkCategory> = data(OnboardingFixtures.CATEGORIES)

    @Test
    fun `each step checks what the web checks`() {
        assertEquals(setOf(FormField.Name, FormField.Services), WorkForm().validate(SetupStep.WhatYouDo).keys)
        assertEquals(setOf(FormField.Location), WorkForm().validate(SetupStep.WhereYouWork).keys)
        val good = WorkForm(phone = "+250 78 123 4567", years = "0", price = "0")
        assertTrue(good.validate(SetupStep.AboutYou).isEmpty())
        val bad = WorkForm(phone = "0712345678", years = "61", price = "10000001", bio = "a".repeat(1001))
        assertEquals(
            setOf(FormField.Years, FormField.Price, FormField.Bio, FormField.Phone),
            bad.validate(SetupStep.AboutYou).keys,
        )
        assertEquals(
            "Add your phone — chosen clients need it to reach you.",
            WorkForm().validate(SetupStep.AboutYou)[FormField.Phone],
        )
    }

    @Test
    fun `phones compare and clean like the web`() {
        assertTrue(WorkForm.isRwandaPhone("078 123-4567"))
        assertFalse(WorkForm.isRwandaPhone("0741234567"))
        assertEquals("0781234567", WorkForm.localPhone("+250781234567"))
        assertEquals("0781234567", WorkForm.cleanPhone("078 123-4567"))
        assertTrue(WorkForm.samePhone("078 123 4567", "+250781234567"))
    }

    @Test
    fun `the saved profile fills the form for editing`() {
        val form = WorkForm.fromProfile(profile, "+250781234567")
        assertEquals("Jean Bosco Habimana", form.name)
        assertEquals(listOf("electrician"), form.services)
        assertEquals("Kimironko, Kigali", form.location?.label)
        assertEquals(10, form.radiusKm)
        assertEquals("12", form.years)
        assertEquals("10000", form.price)
        assertEquals(PriceUnit.Job, form.priceUnit)
        assertEquals("0781234567", form.phone)
        assertEquals(listOf("rw", "en", "fr"), form.languages)
    }

    @Test
    fun `live strength matches the engine score for the same profile`() {
        val form = WorkForm.fromProfile(profile, "+250781234567")
        // The engine says 70: no profile photo (15) and no work photos (15) are missing.
        val score = form.completeness(hasPicture = false)
        assertEquals(70, score.score)
        assertEquals(profile.completenessTips, score.tips)
        assertEquals("Ready for jobs", score.stage)
        assertEquals(0, WorkForm().completeness(false).score)
        val partial = WorkForm(bio = "a".repeat(40), photos = listOf("/api/media/a"))
        assertEquals(7 + 10, partial.completeness(false).score)
        assertTrue("Add 2 more work photos" in partial.completeness(false).tips)
        assertEquals("Strong", Completeness(85, emptyList()).stage)
    }

    @Test
    fun `step bodies send what the web sends`() {
        val form = WorkForm(
            name = "  Aline Tailoring ",
            services = listOf("tailor"),
            location = PickedLocation(-1.95, 30.06, "Remera"),
            radiusKm = 4,
            years = "",
            price = "",
            priceUnit = PriceUnit.Hour,
            bio = " Neat work. ",
            photos = listOf("/api/media/a"),
            languages = listOf("rw", "Lingala"),
        )
        val first = WorkSetupBodies.whatYouDo(form)
        assertEquals("Aline Tailoring", first.text("displayName"))
        val second = WorkSetupBodies.whereYouWork(form, checkNotNull(form.location))
        assertEquals("Remera", second.text("addressLabel"))
        assertEquals("4", second.text("serviceRadiusKm"))
        val noLabel = WorkSetupBodies.whereYouWork(form, PickedLocation(-1.95, 30.06, null))
        assertFalse("addressLabel" in noLabel)
        val third = WorkSetupBodies.aboutYou(form)
        assertEquals("0", third.text("yearsExperience"))
        assertEquals(JsonNull, third["priceMinRwf"])
        assertEquals(JsonNull, third["priceMaxRwf"])
        assertEquals("hour", third.text("priceUnit"))
        assertEquals("Neat work.", third.text("bio"))
        assertEquals(2, checkNotNull(third["languages"]).jsonArray.size)
    }

    @Test
    fun `service search ignores accents and offers add-your-own`() {
        assertEquals(
            listOf("electrician", "plumber", "tailor"),
            ServiceSearch.filter(categories, "", emptyList()).map { it.slug },
        )
        assertEquals(
            listOf("electrician", "plumber", "tailor", "umuzamu"),
            ServiceSearch.filter(categories, "", listOf("umuzamu")).map { it.slug },
        )
        val accented = ServiceSearch.filter(categories, "electricien", emptyList())
        assertEquals(listOf("electrician"), accented.map { it.slug })
        assertEquals(listOf("umuzamu"), ServiceSearch.filter(categories, "UMUZAMU", emptyList()).map { it.slug })
        val shown = ServiceSearch.filter(categories, "plumber", emptyList())
        assertFalse(ServiceSearch.canAdd("Plumber", shown))
        assertTrue(ServiceSearch.canAdd("Nanny", ServiceSearch.filter(categories, "Nanny", emptyList())))
        assertFalse(ServiceSearch.canAdd("n", emptyList()))
        assertEquals("Baby sitter", ServiceSearch.typed("  Baby   sitter "))
    }

    @Test
    fun `languages keep known codes first and check typed names`() {
        assertEquals(listOf("rw", "fr"), Languages.toggle(listOf("fr"), "rw"))
        assertEquals(listOf("en", "Lingala"), Languages.add(listOf("Lingala"), "english").getOrThrow())
        assertEquals(listOf("rw", "Luganda"), Languages.add(listOf("rw"), " luganda ").getOrThrow())
        assertEquals(listOf("Luganda"), Languages.add(listOf("Luganda"), "LUGANDA").getOrThrow())
        assertTrue(Languages.add(emptyList(), "C3PO").isFailure)
        assertEquals("Kinyarwanda, Français, Lingala", Languages.names(listOf("rw", "fr", "Lingala")))
    }

    @Test
    fun `the draft survives a round trip`() {
        val form = WorkForm(
            name = "Aline",
            location = PickedLocation(-1.95, 30.06, "Remera"),
            priceUnit = PriceUnit.Month,
        )
        val json = HafiJson.encodeToString(WorkForm.serializer(), form)
        assertEquals(form, HafiJson.decodeFromString(WorkForm.serializer(), json))
        assertNull(SetupStep.WhatYouDo.previous)
        assertEquals(SetupStep.Done, SetupStep.AboutYou.next)
    }

    private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.content

    private inline fun <reified T> data(envelope: String): T =
        HafiJson.decodeFromJsonElement(HafiJson.parseToJsonElement(envelope).jsonObject.getValue("data"))
}
