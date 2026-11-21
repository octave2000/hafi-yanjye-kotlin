package com.hafiyanjye.app.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where the session token is kept between launches. */
interface TokenVault {
    fun read(): String?
    fun write(token: String)
    fun clear()
}

/**
 * Keeps the session token encrypted on the phone. The AES-256-GCM key lives in
 * the Android Keystore (hardware-backed where the phone has it) and never
 * leaves it; only the ciphertext is written to the app's private preferences,
 * which are also excluded from backups (res/xml/data_extraction_rules.xml).
 */
class KeystoreTokenVault(context: Context) : TokenVault {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var cached: String? = null

    @Volatile
    private var loaded = false

    override fun read(): String? {
        if (loaded) return cached
        synchronized(this) {
            if (!loaded) {
                cached = decrypt(prefs.getString(KEY_TOKEN, null))
                loaded = true
            }
            return cached
        }
    }

    override fun write(token: String) = synchronized(this) {
        prefs.edit().putString(KEY_TOKEN, encrypt(token)).apply()
        cached = token
        loaded = true
    }

    override fun clear() = synchronized(this) {
        prefs.edit().remove(KEY_TOKEN).apply()
        cached = null
        loaded = true
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    /** Null when nothing is stored or it can't be opened (key wiped, data damaged). */
    private fun decrypt(stored: String?): String? {
        if (stored.isNullOrEmpty()) return null
        return try {
            val sealed = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES).toString(Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            prefs.edit().remove(KEY_TOKEN).apply()
            null
        } catch (_: IllegalArgumentException) {
            prefs.edit().remove(KEY_TOKEN).apply()
            null
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "session"
        const val KEY_TOKEN = "token"
        const val KEY_ALIAS = "hafi.session.v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
