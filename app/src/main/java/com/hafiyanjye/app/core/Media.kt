package com.hafiyanjye.app.core

import com.hafiyanjye.app.BuildConfig

/**
 * A full URL for a picture or file the engine refers to. Stored ones are
 * paths on the engine ("/api/media/…"); Google profile pictures are already
 * full URLs. Null in, null out.
 */
fun mediaUrl(path: String?): String? = when {
    path.isNullOrBlank() -> null
    path.startsWith("http://") || path.startsWith("https://") -> path
    else -> BuildConfig.API_BASE_URL + (if (path.startsWith("/")) path else "/$path")
}
