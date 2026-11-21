package com.hafiyanjye.app.data.api

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Talks to the Hafi Yanjye engine.
 *
 * Every call sends the session as `Authorization: Bearer <token>` when there
 * is one, and returns the envelope's decoded `data`. When the engine refuses a
 * token we sent (401), [onSessionRejected] runs so the app can sign the person
 * out; a 401 without a token (a wrong password at sign-in) is only thrown.
 */
class ApiClient(
    private val baseUrl: String,
    val http: OkHttpClient,
    private val token: () -> String?,
    private val onSessionRejected: () -> Unit = {},
) {
    suspend inline fun <reified T> get(path: String, query: Map<String, Any?> = emptyMap()): T =
        request("GET", path, query, null, serializer<T>())

    suspend inline fun <reified T> delete(path: String, query: Map<String, Any?> = emptyMap()): T =
        request("DELETE", path, query, null, serializer<T>())

    /** POST with no input. */
    suspend inline fun <reified T> post(path: String): T =
        request("POST", path, emptyMap(), EMPTY_BODY, serializer<T>())

    suspend inline fun <reified B, reified T> post(path: String, body: B): T =
        request("POST", path, emptyMap(), encode(serializer<B>(), body), serializer<T>())

    /** PUT with no input. */
    suspend inline fun <reified T> put(path: String): T =
        request("PUT", path, emptyMap(), EMPTY_BODY, serializer<T>())

    suspend inline fun <reified B, reified T> put(path: String, body: B): T =
        request("PUT", path, emptyMap(), encode(serializer<B>(), body), serializer<T>())

    @PublishedApi
    internal fun <B> encode(strategy: SerializationStrategy<B>, body: B): String =
        HafiJson.encodeToString(strategy, body)

    /** POST raw bytes (a photo, a file) as the request body. */
    suspend inline fun <reified T> upload(
        path: String,
        bytes: ByteArray,
        contentType: String,
        query: Map<String, Any?> = emptyMap(),
    ): T = execute(
        newRequest(path, query).post(bytes.toRequestBody(contentType.toMediaType())).build(),
        serializer<T>(),
    )

    /** The raw bytes of a file the engine serves (a PDF, a private document). */
    suspend fun download(path: String, query: Map<String, Any?> = emptyMap()): ByteArray {
        val request = newRequest(path, query).header("Accept", "*/*").get().build()
        val sentToken = request.header("Authorization") != null
        val response = send(request)
        if (response.status !in 200..299) {
            // Failures are still envelopes; let the usual path turn them into an ApiException.
            try {
                Envelope.unwrap(
                    response.status,
                    response.contentType,
                    response.bytes.toString(Charsets.UTF_8),
                    JsonElement.serializer(),
                )
            } catch (error: ApiException) {
                if (error.status == 401 && sentToken) onSessionRejected()
                throw error
            }
            throw ApiException(
                response.status,
                ApiException.REQUEST_FAILED,
                "The download failed (${response.status}).",
            )
        }
        return response.bytes
    }

    /**
     * A request to the engine with the address and the session filled in, for
     * calls the JSON helpers don't cover (multipart, streams). Finish it with
     * a method and pass it to [execute], or to [http] directly.
     */
    fun newRequest(path: String, query: Map<String, Any?> = emptyMap()): Request.Builder {
        val url = (baseUrl.trimEnd('/') + path).toHttpUrlOrNull()?.newBuilder()
            ?: throw ApiException(0, ApiException.NETWORK_ERROR, "This build has no server address.")
        for ((key, value) in query) {
            when (value) {
                null -> Unit
                is Iterable<*> -> value.filterNotNull().forEach { url.addQueryParameter(key, it.toString()) }
                else -> url.addQueryParameter(key, value.toString())
            }
        }
        return Request.Builder()
            .url(url.build())
            .header("Accept", "application/json")
            .apply { token()?.let { header("Authorization", "Bearer $it") } }
    }

    /** Runs a request built with [newRequest] and returns the envelope's decoded `data`. */
    suspend fun <T> execute(request: Request, data: DeserializationStrategy<T>): T {
        val sentToken = request.header("Authorization") != null
        val response = send(request)
        try {
            return Envelope.unwrap(
                response.status,
                response.contentType,
                response.bytes.toString(Charsets.UTF_8),
                data,
            )
        } catch (error: ApiException) {
            if (error.status == 401 && sentToken) onSessionRejected()
            throw error
        }
    }

    private suspend fun send(request: Request): RawResponse =
        try {
            http.newCall(request).await()
        } catch (_: InterruptedIOException) {
            throw ApiException(0, ApiException.TIMEOUT, "That took too long. Check your connection and try again.")
        } catch (_: IOException) {
            throw ApiException(
                0,
                ApiException.NETWORK_ERROR,
                "Can't reach Hafi Yanjye. Check your connection and try again.",
            )
        }

    @PublishedApi
    internal suspend fun <T> request(
        method: String,
        path: String,
        query: Map<String, Any?>,
        jsonBody: String?,
        data: DeserializationStrategy<T>,
    ): T = execute(newRequest(path, query).method(method, jsonBody?.toJsonBody()).build(), data)

    @PublishedApi
    internal companion object {
        const val EMPTY_BODY = "{}"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private fun String.toJsonBody(): RequestBody = toRequestBody(JSON)
    }
}

/** The parts of an HTTP answer the envelope needs, read off the network thread's response. */
private class RawResponse(val status: Int, val contentType: String?, val bytes: ByteArray)

/** Runs the call without blocking a thread; cancelling the coroutine cancels the call. */
private suspend fun Call.await(): RawResponse = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            val raw = try {
                response.use { RawResponse(it.code, it.header("Content-Type"), it.body.bytes()) }
            } catch (e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
                return
            }
            continuation.resume(raw)
        }
    })
}
