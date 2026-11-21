package com.hafiyanjye.app.ui.location

/** A typed area resolved against the list below; `matched` is null when nothing in the text is known. */
data class ResolvedArea(val point: GeoPoint, val matched: String?)

/** Approximate centres of common Kigali neighbourhoods, for typed places no map service finds. */
object Neighbourhoods {
    private val areas: List<Pair<String, GeoPoint>> = listOf(
        "Kimironko" to GeoPoint(-1.9497, 30.126),
        "Remera" to GeoPoint(-1.9578, 30.1127),
        "Kicukiro" to GeoPoint(-1.975, 30.103),
        "Nyamirambo" to GeoPoint(-1.9806, 30.0442),
        "Kacyiru" to GeoPoint(-1.9395, 30.0881),
        "Kimihurura" to GeoPoint(-1.952, 30.089),
        "Nyarutarama" to GeoPoint(-1.933, 30.105),
        "Kibagabaga" to GeoPoint(-1.929, 30.117),
        "Gisozi" to GeoPoint(-1.919, 30.06),
        "Kinyinya" to GeoPoint(-1.91, 30.095),
        "Gacuriro" to GeoPoint(-1.918, 30.095),
        "Kanombe" to GeoPoint(-1.968, 30.15),
        "Kabeza" to GeoPoint(-1.97, 30.125),
        "Niboye" to GeoPoint(-1.98, 30.115),
        "Kagarama" to GeoPoint(-1.99, 30.105),
        "Gikondo" to GeoPoint(-1.98, 30.073),
        "Nyabugogo" to GeoPoint(-1.938, 30.045),
        "Kiyovu" to GeoPoint(-1.952, 30.062),
        "Nyarugenge" to GeoPoint(-1.95, 30.059),
    )

    /** The chips offered under the typed field. */
    val suggestions: List<String> = listOf("Kimironko", "Remera", "Kicukiro", "Kacyiru", "Nyamirambo")

    /** Best-effort coordinates for a typed address; Kigali centre when no area is named in it. */
    fun resolve(text: String): ResolvedArea {
        val lower = text.lowercase()
        val hit = areas.firstOrNull { lower.contains(it.first.lowercase()) }
        return if (hit != null) ResolvedArea(hit.second, hit.first) else ResolvedArea(Kigali.Center, null)
    }
}
