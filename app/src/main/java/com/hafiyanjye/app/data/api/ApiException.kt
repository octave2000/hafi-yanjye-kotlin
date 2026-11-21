package com.hafiyanjye.app.data.api

/**
 * A request that did not succeed. [status] is the HTTP status, or 0 when the
 * engine was never reached (no network, timeout). [code] is the engine's error
 * code ("UNAUTHORIZED", "BAD_REQUEST"…) or one of the client codes below.
 * [message] is written for people and can be shown as is.
 */
class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
    val requestId: String? = null,
) : Exception(message) {

    /** The engine could not be reached at all. */
    val isNetwork: Boolean get() = status == 0

    companion object {
        const val NETWORK_ERROR = "NETWORK_ERROR"
        const val TIMEOUT = "TIMEOUT"
        const val REQUEST_FAILED = "REQUEST_FAILED"
        const val BAD_RESPONSE = "BAD_RESPONSE"
    }
}

/** What to show for any failure: the engine's words, or a plain fallback. */
fun Throwable.userMessage(): String =
    (this as? ApiException)?.message?.takeIf { it.isNotBlank() }
        ?: "Something went wrong. Check your connection and try again."
