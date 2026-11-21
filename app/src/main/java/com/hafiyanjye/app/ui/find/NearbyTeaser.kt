package com.hafiyanjye.app.ui.find

import com.hafiyanjye.app.data.find.FindApi
import com.hafiyanjye.app.data.find.Nearby
import com.hafiyanjye.app.ui.location.Kigali
import com.hafiyanjye.app.ui.location.PickedLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** What the "who's online near you" line shows. */
sealed interface NearbyLine {
    /** First answer on its way: a skeleton line. */
    data object Checking : NearbyLine

    /** The engine's counts; [hasLocation] says whether they are around the person or around central Kigali. */
    data class Shown(val data: Nearby, val hasLocation: Boolean) : NearbyLine

    /** The lookup failed (rate limit, offline): the line is left out rather than guessing. */
    data object Hidden : NearbyLine
}

/**
 * Keeps the nearby count in step with what is typed, debounced like the web.
 * The last answer stays on screen while a new one loads, so typing never flickers.
 */
class NearbyTeaser(private val api: FindApi, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow<NearbyLine>(NearbyLine.Checking)
    val line: StateFlow<NearbyLine> = mutable.asStateFlow()

    private var running: Job? = null
    private var lastKey: String? = null

    /** Asks again for [text] near [location] (central Kigali when none), at most once per change. */
    fun update(text: String, location: PickedLocation?, categorySlug: String?) {
        val centre = location?.point ?: Kigali.Center
        val query = nearbyQuery(text)
        val key = "$query|$categorySlug|${centre.lat.format3()}|${centre.lng.format3()}"
        if (key == lastKey) return
        lastKey = key
        running?.cancel()
        running = scope.launch {
            delay(DebounceMillis)
            mutable.value = try {
                NearbyLine.Shown(api.nearby(centre.lat, centre.lng, query, categorySlug), location != null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Asking again with the same input later is allowed.
                lastKey = null
                NearbyLine.Hidden
            }
        }
    }

    private fun Double.format3(): String = String.format(Locale.US, "%.3f", this)

    private companion object {
        const val DebounceMillis = 400L
    }
}
