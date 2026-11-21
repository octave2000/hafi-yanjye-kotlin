package com.hafiyanjye.app.data.api

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.DateTimeException

/** One JSON configuration for the whole app. New engine fields never break old builds. */
val HafiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * The engine wraps every answer:
 *   { ok: true,  data,  meta: { requestId, timestamp } }
 *   { ok: false, error: { code, message }, meta }
 * Pure (no Android, no network), so it is unit-tested.
 */
object Envelope {

    /** The decoded `data` of a successful answer. Throws [ApiException] for anything else. */
    fun <T> unwrap(
        status: Int,
        contentType: String?,
        body: String,
        data: DeserializationStrategy<T>,
    ): T {
        val json = if (contentType.orEmpty().contains("application/json")) parse(body) else null
        val ok = status in 200..299 && json?.get("ok")?.primitiveBoolean() == true
        if (ok) {
            return try {
                HafiJson.decodeFromJsonElement(data, json["data"] ?: JsonNull)
            } catch (_: SerializationException) {
                throw ApiException(status, ApiException.BAD_RESPONSE, UNREADABLE)
            } catch (_: IllegalArgumentException) {
                throw ApiException(status, ApiException.BAD_RESPONSE, UNREADABLE)
            } catch (_: DateTimeException) {
                // A date the engine wrote in a shape Instant can't read.
                throw ApiException(status, ApiException.BAD_RESPONSE, UNREADABLE)
            }
        }

        val error = json?.get("error") as? JsonObject
        val requestId = (json?.get("meta") as? JsonObject)?.get("requestId")?.primitiveString()
        if (error != null) {
            throw ApiException(
                status = status,
                code = error["code"]?.primitiveString() ?: ApiException.REQUEST_FAILED,
                message = error["message"]?.primitiveString() ?: fallbackMessage(status),
                requestId = requestId,
            )
        }
        // A 2xx that isn't an envelope means we are not talking to the engine.
        if (status in 200..299) throw ApiException(status, ApiException.BAD_RESPONSE, UNREADABLE)
        throw ApiException(status, ApiException.REQUEST_FAILED, fallbackMessage(status), requestId)
    }

    private fun parse(body: String): JsonObject? =
        try {
            HafiJson.parseToJsonElement(body) as? JsonObject
        } catch (_: SerializationException) {
            null
        }

    private fun JsonElement.primitiveBoolean(): Boolean? =
        runCatching { jsonPrimitive.booleanOrNull }.getOrNull()

    private fun JsonElement.primitiveString(): String? =
        runCatching { jsonPrimitive.contentOrNull }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun fallbackMessage(status: Int): String = when (status) {
        401 -> "Please sign in again."
        403 -> "You're not allowed to do that."
        404 -> "We couldn't find that."
        429 -> "Too many tries. Wait a moment and try again."
        in 500..599 -> "Our server had a problem. Try again in a moment."
        else -> "The request failed ($status)."
    }

    private const val UNREADABLE = "The server sent an answer we couldn't read."
}
