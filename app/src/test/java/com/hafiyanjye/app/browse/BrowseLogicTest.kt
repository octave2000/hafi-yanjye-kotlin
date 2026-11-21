package com.hafiyanjye.app.browse

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.browse.BrowseApi
import com.hafiyanjye.app.data.match.JobCategory
import com.hafiyanjye.app.data.match.PriceUnit
import com.hafiyanjye.app.ui.browse.HireController
import com.hafiyanjye.app.ui.browse.HireTarget
import com.hafiyanjye.app.ui.browse.distanceLabel
import com.hafiyanjye.app.ui.browse.experienceLabel
import com.hafiyanjye.app.ui.browse.languageNames
import com.hafiyanjye.app.ui.browse.resultsCount
import com.hafiyanjye.app.ui.browse.servicesLine
import com.hafiyanjye.app.ui.browse.servicesTitle
import com.hafiyanjye.app.ui.browse.startingPrice
import com.hafiyanjye.app.ui.location.PickedLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The words the services list and profile show, and the hire form's checks. */
class BrowseLogicTest {
    private val electrician = JobCategory(id = 1, slug = "electrician", nameEn = "Electrician", icon = "Zap")
    private val mason = JobCategory(id = 4, slug = "mason", nameEn = "Mason", icon = "BrickWall")
    private val carpenter = JobCategory(id = 5, slug = "carpenter", nameEn = "Carpenter", icon = "Hammer")

    @Test
    fun `distances read like the web`() {
        assertEquals("Under 1 km", distanceLabel(0.5))
        assertEquals("3 km", distanceLabel(3.0))
        assertEquals("2.5 km", distanceLabel(2.5))
    }

    @Test
    fun `prices start, never cap`() {
        assertEquals("From 10,000 RWF / job", startingPrice(10_000, PriceUnit.Job))
        assertEquals("Ask for a quote", startingPrice(null, PriceUnit.Hour))
    }

    @Test
    fun `labels for the list and profile`() {
        assertEquals("Kinyarwanda, English, ln", languageNames(listOf("en", "ln", "rw")))
        assertEquals("Electrician · Mason · +1", servicesLine(listOf(electrician, mason, carpenter)))
        assertEquals("Electricians near you", servicesTitle(electrician))
        assertEquals("Find help near you", servicesTitle(null))
        assertEquals("13 electricians near Kimironko", resultsCount(13, electrician, "Kimironko"))
        assertEquals("1 electrician", resultsCount(1, electrician, null))
        assertEquals("1 person", resultsCount(1, null, null))
        assertEquals("New", experienceLabel(0))
        assertEquals("1 yr", experienceLabel(1))
    }

    @Test
    fun `hire form keeps the listed service and checks before sending`() {
        val hire = HireController(
            BrowseApi(ApiClient("http://localhost/", OkHttpClient(), { null })),
            CoroutineScope(Dispatchers.Unconfined),
        )
        val target = HireTarget(52, "Solange Murekatete", listOf(electrician, mason))
        hire.open(target, defaultLocation = null, defaultService = "mason")
        assertEquals("mason", hire.form.value?.service)

        hire.send()
        assertEquals("Tell Solange what you need in a few words.", hire.form.value?.error)

        hire.setText("Fix two sockets")
        hire.send()
        assertEquals("Where should they come? Use your location or type your area.", hire.form.value?.placeError)

        hire.setLocation(PickedLocation(-1.95, 30.06, "Kacyiru"))
        assertNull(hire.form.value?.placeError)

        hire.open(target, defaultLocation = null, defaultService = "plumber")
        assertEquals("electrician", hire.form.value?.service)
        assertEquals("", hire.form.value?.text)
    }
}
