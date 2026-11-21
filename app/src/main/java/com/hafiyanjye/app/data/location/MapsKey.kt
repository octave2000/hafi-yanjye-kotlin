package com.hafiyanjye.app.data.location

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.hafiyanjye.app.BuildConfig
import okhttp3.Request
import java.security.MessageDigest

/**
 * The Google Maps key (android/local.properties, `MAPS_API_KEY`) and the headers
 * Google needs to accept a key restricted to this Android app when it is used
 * over HTTPS (Routes, Places) rather than through the Maps SDK. Without the
 * signing certificate header a restricted key is refused, so [attach] must run
 * once before the first HTTPS call; the map and the picker do it when they appear.
 */
object MapsKey {
    val value: String = BuildConfig.MAPS_API_KEY.trim()
    val present: Boolean get() = value.isNotEmpty()

    @Volatile
    private var certSha1: String? = null

    @Volatile
    private var attached = false

    /** Reads the app's signing certificate once; cheap after the first call. */
    fun attach(context: Context) {
        if (attached) return
        certSha1 = runCatching { signingCertSha1(context.applicationContext) }.getOrNull()
        attached = true
    }

    internal fun sign(builder: Request.Builder, key: String = value): Request.Builder {
        builder.header("X-Goog-Api-Key", key)
        builder.header("X-Android-Package", BuildConfig.APPLICATION_ID)
        certSha1?.let { builder.header("X-Android-Cert", it) }
        return builder
    }

    private fun signingCertSha1(app: Context): String? {
        val signer = firstSigner(app) ?: return null
        return MessageDigest.getInstance("SHA-1")
            .digest(signer.toByteArray())
            .joinToString("") { "%02X".format(it) }
    }

    private fun firstSigner(app: Context): Signature? {
        val manager = app.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
                ?.apkContentsSigners
                ?.firstOrNull()
        } else {
            @Suppress("DEPRECATION") // The only way to read the signer before Android 9.
            manager.getPackageInfo(app.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        }
    }
}
