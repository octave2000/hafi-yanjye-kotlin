package com.hafiyanjye.app.location

import com.hafiyanjye.app.data.location.Geocoding
import com.hafiyanjye.app.data.location.Routing
import com.hafiyanjye.app.data.location.decodePolyline
import com.hafiyanjye.app.ui.location.GeoPoint
import com.hafiyanjye.app.ui.location.Kigali
import com.hafiyanjye.app.ui.location.LocationSource
import com.hafiyanjye.app.ui.location.Neighbourhoods
import com.hafiyanjye.app.ui.location.PickedLocation
import com.hafiyanjye.app.ui.location.ScatterItem
import com.hafiyanjye.app.ui.location.FlatProjection
import com.hafiyanjye.app.ui.location.MapLine
import com.hafiyanjye.app.ui.location.distanceKm
import com.hafiyanjye.app.ui.location.routeLine
import com.hafiyanjye.app.ui.location.sourceHint
import com.hafiyanjye.app.ui.location.placeAround
import com.hafiyanjye.app.ui.location.scatterNoise
import com.hafiyanjye.app.ui.location.scatterPeople
import com.hafiyanjye.app.ui.location.zoomForRadius
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTest {
    @Test
    fun haversine_isZeroForTheSamePoint_andSymmetric() {
        val kimironko = GeoPoint(-1.9497, 30.126)
        assertEquals(0.0, distanceKm(Kigali.Center, Kigali.Center), 1e-9)
        assertEquals(distanceKm(Kigali.Center, kimironko), distanceKm(kimironko, Kigali.Center), 1e-9)
    }

    @Test
    fun haversine_matchesKnownDistances() {
        // One degree of latitude is about 111.2 km everywhere.
        assertEquals(111.19, distanceKm(GeoPoint(0.0, 30.0), GeoPoint(1.0, 30.0)), 0.05)
        // Kigali centre to Kimironko: about 7.2 km as the crow flies.
        assertEquals(7.15, distanceKm(Kigali.Center, GeoPoint(-1.9497, 30.126)), 0.1)
    }

    @Test
    fun polyline_decodesGooglesReferenceExample() {
        val points = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].lat, 1e-6)
        assertEquals(-120.2, points[0].lng, 1e-6)
        assertEquals(40.7, points[1].lat, 1e-6)
        assertEquals(-120.95, points[1].lng, 1e-6)
        assertEquals(43.252, points[2].lat, 1e-6)
        assertEquals(-126.453, points[2].lng, 1e-6)
    }

    @Test
    fun polyline_emptyAndTruncatedInputDoNotThrow() {
        assertTrue(decodePolyline("").isEmpty())
        assertEquals(1, decodePolyline("_p~iF~ps|U_ulL").size)
    }

    @Test
    fun route_parsesARealRoutesApiAnswer() {
        val route = Routing.parseRoute(Fixtures.Route)
        assertEquals(10220, route?.distanceMeters)
        assertEquals(1288, route?.durationSeconds)
        val points = route?.points.orEmpty()
        assertTrue(points.size > 50)
        // Starts at Kigali centre and ends at Kimironko market, within a street or two.
        assertTrue(distanceKm(points.first(), Kigali.Center) < 0.2)
        assertTrue(distanceKm(points.last(), GeoPoint(-1.9497, 30.126)) < 0.3)
    }

    @Test
    fun route_isNullWhenThereIsNoRoute() {
        assertNull(Routing.parseRoute("{}"))
        assertNull(Routing.parseRoute("""{"routes":[]}"""))
        assertNull(Routing.parseRoute("""{"error":{"code":403,"status":"PERMISSION_DENIED"}}"""))
        assertNull(Routing.parseRoute("not json"))
    }

    @Test
    fun route_rejectsMissingAndNegativeMetrics() {
        val polyline = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        val line = """"polyline":{"encodedPolyline":"$polyline"}"""
        assertNull(Routing.parseRoute("""{"routes":[{$line}]}"""))
        assertNull(Routing.parseRoute("""{"routes":[{"distanceMeters":-1,"duration":"3s",$line}]}"""))
        assertNull(Routing.parseRoute("""{"routes":[{"distanceMeters":12,"duration":"-3s",$line}]}"""))
    }

    @Test
    fun polyline_rejectsInvalidCharactersAndOversizedValues() {
        assertTrue(decodePolyline("~~~~~~~~~~~~?").isEmpty())
        assertTrue(decodePolyline("\n\n").isEmpty())
    }

    @Test
    fun neighbourhood_isFoundInsideLongerText_whateverTheCase() {
        val area = Neighbourhoods.resolve("near KIMIRONKO market, gate 2")
        assertEquals("Kimironko", area.matched)
        assertEquals(GeoPoint(-1.9497, 30.126), area.point)
    }

    @Test
    fun neighbourhood_unknownTextFallsBackToCentralKigali() {
        val area = Neighbourhoods.resolve("somewhere nobody mapped")
        assertNull(area.matched)
        assertEquals(Kigali.Center, area.point)
    }

    @Test
    fun neighbourhood_everySuggestionResolves() {
        Neighbourhoods.suggestions.forEach { assertEquals(it, Neighbourhoods.resolve(it).matched) }
    }

    @Test
    fun nominatim_readsTheFirstHit_whoseNumbersAreStrings() {
        val json = """[{"place_id":2131,"licence":"Data © OpenStreetMap contributors","osm_type":"node",
            "lat":"-1.9496912","lon":"30.1259871","category":"amenity","type":"marketplace",
            "name":"Kimironko Market","display_name":"Kimironko Market, KG 11 Avenue, Kigali, Rwanda"}]"""
        val point = Geocoding.parseNominatim(json)
        assertEquals(-1.9496912, point?.lat ?: 0.0, 1e-9)
        assertEquals(30.1259871, point?.lng ?: 0.0, 1e-9)
    }

    @Test
    fun nominatim_nothingFoundOrBrokenIsNull() {
        assertNull(Geocoding.parseNominatim("[]"))
        assertNull(Geocoding.parseNominatim("""[{"lat":"abc","lon":"30.1"}]"""))
        assertNull(Geocoding.parseNominatim("""[{"lat":"-1.9"}]"""))
        assertNull(Geocoding.parseNominatim("""[{"lat":"91","lon":"30"}]"""))
        assertNull(Geocoding.parsePlace("""{"location":{"latitude":-1.9,"longitude":181}}"""))
        assertNull(Geocoding.parseNominatim("<html>Too many requests</html>"))
    }

    @Test
    fun placeSearch_readsSuggestionsAndPlaceDetails() {
        val suggestions = checkNotNull(Geocoding.parseSuggestions(
            """{"suggestions":[{"placePrediction":{"placeId":"ChIJabc","text":{"text":"Kimironko Market, Kigali"},
                "structuredFormat":{"mainText":{"text":"Kimironko Market"},"secondaryText":{"text":"Kigali, Rwanda"}}}},
                {"queryPrediction":{"text":{"text":"markets"}}}]}""",
        ))
        assertEquals(1, suggestions.size)
        assertEquals("ChIJabc", suggestions[0].placeId)
        assertEquals("Kimironko Market", suggestions[0].main)
        assertEquals("Kigali, Rwanda", suggestions[0].secondary)
        assertEquals(emptyList<Any>(), Geocoding.parseSuggestions("{}"))
        assertNull(Geocoding.parseSuggestions("<html>quota</html>"))

        val place = Geocoding.parsePlace(
            """{"displayName":{"text":"Kimironko Market"},"location":{"latitude":-1.9497,"longitude":30.126}}""",
        )
        assertEquals(GeoPoint(-1.9497, 30.126), place?.point)
        assertEquals("Kimironko Market", place?.name)
        assertNull(Geocoding.parsePlace("{}"))
    }

    @Test
    fun scatter_neverPutsAnyoneOnTheirRealSpotOrOnThePin() {
        val radius = 5.0
        val items = listOf(
            ScatterItem("on-the-pin", 0.0),
            ScatterItem("near", 2.0),
            ScatterItem("far", 40.0),
            ScatterItem("unknown", null),
        )
        val placed = scatterPeople(seed = 42, center = Kigali.Center, radiusKm = radius, items = items)
        assertEquals(items.map { it.id }, placed.map { it.id })
        val km = placed.associate { it.id to distanceKm(Kigali.Center, it.position) }
        assertEquals(radius * 0.3, km.getValue("on-the-pin"), 0.05)
        assertEquals(2.0, km.getValue("near"), 0.05)
        assertEquals(radius * 0.95, km.getValue("far"), 0.05)
        assertTrue(km.getValue("unknown") in (radius * 0.35 - 0.05)..(radius * 0.9 + 0.05))
    }

    @Test
    fun scatter_isStableForASeed_andDiffersBetweenSeeds() {
        val items = List(6) { ScatterItem("p$it", null) }
        val a = scatterPeople(7, Kigali.Center, 3.0, items)
        assertEquals(a, scatterPeople(7, Kigali.Center, 3.0, items))
        assertNotEquals(a, scatterPeople(8, Kigali.Center, 3.0, items))
        repeat(50) { assertTrue(scatterNoise(7, it) in 0.0..1.0) }
    }

    @Test
    fun placeAround_goesTheAskedDistance() {
        listOf(0.0, 90.0, 200.0).forEach { bearing ->
            assertEquals(2.5, distanceKm(Kigali.Center, placeAround(Kigali.Center, 2.5, bearing)), 0.03)
        }
        assertTrue(placeAround(Kigali.Center, 1.0, 0.0).lat > Kigali.Center.lat)
        assertTrue(placeAround(Kigali.Center, 1.0, 90.0).lng > Kigali.Center.lng)
    }

    @Test
    fun pickedLocation_usesTheWebsWireNames() {
        val json = Json.encodeToString(
            PickedLocation.serializer(),
            PickedLocation(-1.95, 30.06, "Kiyovu", LocationSource.Typed),
        )
        assertTrue(json.contains("\"source\":\"typed\""))
        val back = Json.decodeFromString(PickedLocation.serializer(), """{"lat":-1.95,"lng":30.06,"source":"gps"}""")
        assertEquals(LocationSource.Gps, back.source)
        assertEquals(GeoPoint(-1.95, 30.06), back.point)
    }

    @Test
    fun zoomForRadius_fitsWiderCirclesFurtherOut() {
        assertEquals(15f, zoomForRadius(null))
        assertTrue(zoomForRadius(1.0) > zoomForRadius(8.0))
        assertEquals(10f, zoomForRadius(500.0))
    }

    @Test
    fun routeLine_isTheRoadWhenKnown_andAStraightDashedLineOtherwise() {
        val kimironko = GeoPoint(-1.9497, 30.126)
        val straight = MapLine(listOf(Kigali.Center, kimironko), dashed = true)
        assertEquals(straight, routeLine(Kigali.Center, kimironko, null))
        val road = Routing.parseRoute(Fixtures.Route)?.points
        assertEquals(MapLine(road.orEmpty()), routeLine(Kigali.Center, kimironko, road))
    }

    @Test
    fun sourceHint_saysWhereThePlaceCameFrom_inTheWebsWords() {
        val typed = PickedLocation(-1.95, 30.06, "Somewhere", LocationSource.Typed)
        assertEquals("From the place you typed", sourceHint(typed))
        assertEquals("Not found on the map: approximate (central Kigali)", sourceHint(typed.copy(approximate = true)))
        assertEquals("From Google Maps", sourceHint(typed.copy(source = LocationSource.Places)))
    }

    @Test
    fun staticMap_projectionRoundTrips_andFitsEveryPoint() {
        val points = listOf(Kigali.Center, GeoPoint(-1.9497, 30.126), GeoPoint(-1.9806, 30.0442))
        val projection = FlatProjection.frame(
            center = Kigali.Center,
            zoom = 14f,
            content = points,
            fit = true,
            widthPx = 400f,
            heightPx = 300f,
            paddingPx = 20f,
            pxPerDp = 1f,
        )
        points.forEach { point ->
            val offset = projection.toOffset(point)
            assertTrue(offset.x in 19f..381f && offset.y in 19f..281f)
            val back = projection.toPoint(offset)
            // Offsets are Floats, so a round trip keeps about 1e-9° of noise; 1e-6° is 11 cm on the ground.
            assertEquals(point.lat, back.lat, 1e-6)
            assertEquals(point.lng, back.lng, 1e-6)
        }
    }
}
