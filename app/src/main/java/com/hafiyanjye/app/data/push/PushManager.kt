package com.hafiyanjye.app.data.push

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.hafiyanjye.app.AppContainer
import com.hafiyanjye.app.BuildConfig
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Whether Android lets this app show notifications. */
enum class PushPermission {
    Granted,

    /** Android 13+ and the person has not answered yet: the switch asks. */
    NotAsked,

    /** Refused, or turned off in Android settings: only the settings screen can change it. */
    Blocked,
}

/** What the profile's push switch shows. */
data class PushState(
    /** This build carries a Firebase config (the four FIREBASE_* Gradle properties). */
    val configured: Boolean,
    /** The person's choice on this phone; on by default, like any Android app's notifications. */
    val wanted: Boolean = true,
    val permission: PushPermission = PushPermission.NotAsked,
    /** The engine holds this phone's token for the signed-in account. */
    val registered: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val on: Boolean get() = wanted && registered && permission == PushPermission.Granted
}

/** The four public values of the Firebase Android app, from Gradle properties. Null when any is missing. */
internal data class FirebaseConfig(val appId: String, val apiKey: String, val projectId: String, val senderId: String) {
    companion object {
        fun of(appId: String, apiKey: String, projectId: String, senderId: String): FirebaseConfig? =
            if (listOf(appId, apiKey, projectId, senderId).any { it.isBlank() }) {
                null
            } else {
                FirebaseConfig(appId.trim(), apiKey.trim(), projectId.trim(), senderId.trim())
            }
    }
}

/**
 * This phone's push registration through Firebase Cloud Messaging: one per
 * process. When signed in and Android allows notifications, the FCM token is
 * registered with the engine (again on a new token or another account); it is
 * unregistered and revoked on sign-out (Session.beforeLogout) and when the
 * person turns push off. Without a Firebase config nothing of Firebase starts.
 */
class PushManager private constructor(private val container: AppContainer) {

    private val app: Application = container.app
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val api = PushApi(container.client)

    /** One registration change at a time: sign-in, a new token and the switch can race otherwise. */
    private val mutex = Mutex()

    private val messaging: FirebaseMessaging? = startFirebase()

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<PushState> = _state.asStateFlow()

    private var started = false

    private fun startFirebase(): FirebaseMessaging? {
        val config = FirebaseConfig.of(
            BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.FIREBASE_SENDER_ID,
        ) ?: return null
        return try {
            // The init provider is removed from the manifest, so this is the only place Firebase starts.
            if (FirebaseApp.getApps(app).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(config.appId)
                    .setApiKey(config.apiKey)
                    .setProjectId(config.projectId)
                    .setGcmSenderId(config.senderId)
                    .build()
                FirebaseApp.initializeApp(app, options)
            }
            FirebaseMessaging.getInstance()
        } catch (_: IllegalArgumentException) {
            // A malformed app id or key: behave as a build without push rather than crash at launch.
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    /** Called once from HafiApp.onCreate; safe to call again. */
    fun start() {
        if (started) return
        started = true
        if (messaging == null) return
        createChannel()
        container.session.beforeLogout { mutex.withLock { unregister() } }
        container.appScope.launch {
            container.session.state
                .mapNotNull { session ->
                    when (session) {
                        is SessionState.SignedIn -> session.me.id
                        SessionState.SignedOut -> SIGNED_OUT
                        else -> null
                    }
                }
                .distinctUntilChanged()
                .collect { account -> if (account == SIGNED_OUT) forgetAfterSignOut() else sync() }
        }
    }

    /** Re-reads Android's permission and brings the engine in line (the profile calls this on resume). */
    fun refresh() {
        container.appScope.launch { sync() }
    }

    /** Firebase gave this phone a new token: the engine must learn it. */
    fun onNewToken() = refresh()

    /** The profile switch. Turning on assumes Android permission was already asked for by the screen. */
    fun setWanted(wanted: Boolean) {
        prefs.edit { putBoolean(KEY_WANTED, wanted) }
        if (!wanted) prefs.edit { remove(KEY_DENIED) }
        refresh()
    }

    /** The person refused Android's permission prompt: show "blocked" instead of "off" from now on. */
    fun onPermissionResult(granted: Boolean) {
        prefs.edit { putBoolean(KEY_DENIED, !granted) }
        if (granted) setWanted(true) else refresh()
    }

    fun permission(): PushPermission {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!runtimeGranted) {
            return if (prefs.getBoolean(KEY_DENIED, false)) PushPermission.Blocked else PushPermission.NotAsked
        }
        val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
        val channelOff = channel?.importance == NotificationManager.IMPORTANCE_NONE
        val appOff = !NotificationManagerCompat.from(app).areNotificationsEnabled()
        return if (appOff || channelOff) PushPermission.Blocked else PushPermission.Granted
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Job updates and messages",
            NotificationManager.IMPORTANCE_HIGH,
        )
        channel.description = "New matches, messages and nearby jobs."
        app.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Registers or unregisters so the engine matches: signed in, wanted and permitted. */
    private suspend fun sync() = operation {
        val messaging = messaging ?: return@operation
        val account = (container.session.state.value as? SessionState.SignedIn)?.me?.id ?: return@operation
        val wanted = prefs.getBoolean(KEY_WANTED, true)
        if (!wanted || permission() != PushPermission.Granted) {
            unregister()
            return@operation
        }
        val token = messaging.token.await()
        val previous = prefs.getString(KEY_TOKEN, null)
        if (previous != null && previous != token) {
            // Best effort: FCM refuses the old token anyway, and the engine then drops its row.
            runCatching { api.unregister(previous) }
        }
        api.register(token)
        prefs.edit {
            putString(KEY_TOKEN, token)
            putInt(KEY_ACCOUNT, account)
        }
    }

    /** Removes this phone from the engine and revokes the token, so nothing more arrives for this account. */
    private suspend fun unregister() {
        val token = prefs.getString(KEY_TOKEN, null) ?: return
        try {
            api.unregister(token)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Offline: revoking the token below is enough, the engine's next send fails and drops the row.
        } finally {
            prefs.edit {
                remove(KEY_TOKEN)
                remove(KEY_ACCOUNT)
            }
            NotificationManagerCompat.from(app).cancelAll()
            _state.value = snapshot()
        }
        messaging?.deleteToken()?.await()
    }

    /**
     * Signed out without the logout hook (the session expired, or the account
     * was deleted): the engine can no longer be asked, so revoke the token here.
     */
    private suspend fun forgetAfterSignOut() = operation {
        if (prefs.getString(KEY_TOKEN, null) == null) return@operation
        prefs.edit {
            remove(KEY_TOKEN)
            remove(KEY_ACCOUNT)
        }
        runCatching { messaging?.deleteToken()?.await() }
        NotificationManagerCompat.from(app).cancelAll()
    }

    private suspend fun operation(block: suspend () -> Unit) {
        mutex.withLock {
            _state.value = snapshot().copy(busy = true)
            var error: String? = null
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = "Couldn't update push notifications on this phone. ${failure.userMessage()}"
            }
            _state.value = snapshot().copy(error = error)
        }
    }

    /** Whether the token on record belongs to this account; a message for anyone else is dropped. */
    fun registeredFor(accountId: Int): Boolean =
        prefs.getString(KEY_TOKEN, null) != null && prefs.getInt(KEY_ACCOUNT, NO_ACCOUNT) == accountId

    private fun snapshot(): PushState {
        val account = (container.session.state.value as? SessionState.SignedIn)?.me?.id
        return PushState(
            configured = messaging != null,
            wanted = prefs.getBoolean(KEY_WANTED, true),
            permission = permission(),
            registered = account != null && registeredFor(account),
        )
    }

    companion object {
        /** The engine sends every message on this channel (push-fcm.ts: channel_id "default"). */
        const val CHANNEL_ID = "default"

        private const val PREFS = "push"
        private const val KEY_WANTED = "wanted"
        private const val KEY_DENIED = "permission-denied"
        private const val KEY_TOKEN = "token"
        private const val KEY_ACCOUNT = "account"
        private const val NO_ACCOUNT = -1
        private const val SIGNED_OUT = -1

        @Volatile
        private var instance: PushManager? = null

        fun get(container: AppContainer): PushManager = instance ?: synchronized(this) {
            instance ?: PushManager(container).also { instance = it }
        }
    }
}

/** Firebase's Task as a suspending call. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        val failure = task.exception
        when {
            failure != null -> continuation.resumeWithException(failure)
            task.isCanceled -> continuation.resumeWithException(CancellationException("Firebase task cancelled"))
            else -> continuation.resume(task.result)
        }
    }
}
