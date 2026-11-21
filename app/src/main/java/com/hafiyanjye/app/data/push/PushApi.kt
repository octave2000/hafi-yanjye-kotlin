package com.hafiyanjye.app.data.push

import com.hafiyanjye.app.data.api.ApiClient
import kotlinx.serialization.Serializable

/** Engine: input of POST /push/devices/register. A raw FCM token on Android is read as FCM by the engine. */
@Serializable
data class RegisterDevice(val token: String, val platform: String)

/** Engine: input of POST /push/devices/unregister. */
@Serializable
data class UnregisterDevice(val token: String)

@Serializable
data class DeviceRegistered(val registered: Boolean = false)

@Serializable
data class DeviceRemoved(val removed: Boolean = false)

/** This phone's push registration on the engine (engine/src/api/v1/push/devices). */
class PushApi(private val client: ApiClient) {

    suspend fun register(token: String): DeviceRegistered =
        client.post("/api/v1/push/devices/register", RegisterDevice(token, platform = "android"))

    /** Answers removed = false when the engine had no such token for this account, which is fine. */
    suspend fun unregister(token: String): DeviceRemoved =
        client.post("/api/v1/push/devices/unregister", UnregisterDevice(token))
}
