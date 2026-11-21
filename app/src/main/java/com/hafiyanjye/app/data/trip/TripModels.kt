package com.hafiyanjye.app.data.trip

import com.hafiyanjye.app.data.api.Role
import com.hafiyanjye.app.data.api.Timestamp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where the hired person is on the trip (engine `TripDto.status`). */
@Serializable
enum class TripStatus {
    @SerialName("not_started")
    NotStarted,

    @SerialName("on_the_way")
    OnTheWay,

    @SerialName("arrived")
    Arrived,
}

/** Why the hired person is late; labels are the web's (`DELAY_REASONS` in live-trip-map.tsx). */
@Serializable
enum class DelayReason(val label: String) {
    @SerialName("traffic")
    Traffic("Traffic congestion"),

    @SerialName("rain")
    Rain("Heavy rain"),

    @SerialName("previous_job")
    PreviousJob("Finishing another job"),

    @SerialName("transport")
    Transport("Transport problem"),

    @SerialName("other")
    Other("Something came up"),
}

/** The three things the hired person can do to the trip (`POST /match/{id}/trip/action`). */
@Serializable
enum class TripAction {
    @SerialName("start")
    Start,

    @SerialName("arrived")
    Arrived,

    @SerialName("stop")
    Stop,
}

/** Where the work is: the job provider's pin. */
@Serializable
data class TripPlace(val lat: Double, val lng: Double, val label: String? = null)

/** The hired job seeker; the engine always has their display name. */
@Serializable
data class TripWorker(val name: String, val picture: String? = null)

/** The job provider who hired; their name may be missing. */
@Serializable
data class TripHirer(val name: String? = null, val picture: String? = null)

/** The latest shared position while on the way. `heading` in degrees, `accuracy` in metres. */
@Serializable
data class TripPosition(
    val lat: Double,
    val lng: Double,
    val heading: Double? = null,
    val accuracy: Double? = null,
    val at: Timestamp,
)

/** The latest "running late" notice. */
@Serializable
data class TripDelay(
    val minutes: Int,
    val reason: DelayReason,
    val note: String? = null,
    val at: Timestamp,
)

/** The live trip of a hired job (engine `TripDto`), seen by its two people only. */
@Serializable
data class Trip(
    val sessionId: Int,
    val jobId: Int,
    /** Who is looking: [Role.Hirer] watches, [Role.Worker] is the one on the way. */
    val role: Role,
    val destination: TripPlace,
    @SerialName("provider") val worker: TripWorker,
    @SerialName("seeker") val hirer: TripHirer,
    val status: TripStatus,
    val startedAt: Timestamp? = null,
    val arrivedAt: Timestamp? = null,
    val position: TripPosition? = null,
    val delay: TripDelay? = null,
    /** False once the job is done or cancelled: the map stays, updates stop. */
    val live: Boolean,
)

/** The engine's `{ ok: true }` answer. */
@Serializable
data class TripOk(val ok: Boolean = true)

@Serializable
internal data class TripActionInput(val action: TripAction)

@Serializable
internal data class TripLocationInput(
    val lat: Double,
    val lng: Double,
    val heading: Double? = null,
    val accuracy: Double? = null,
    val speedKmh: Double? = null,
)

@Serializable
internal data class TripDelayInput(val minutes: Int, val reason: DelayReason, val note: String? = null)
