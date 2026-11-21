package com.hafiyanjye.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.hafiyanjye.app.data.Badges
import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.api.HafiApi
import com.hafiyanjye.app.data.chat.ChatUnreadTracker
import com.hafiyanjye.app.data.location.MapsKey
import com.hafiyanjye.app.data.profile.NotificationsTracker
import com.hafiyanjye.app.data.push.PushManager
import com.hafiyanjye.app.data.realtime.RealtimeHub
import com.hafiyanjye.app.data.realtime.transport.RealtimeConnection
import com.hafiyanjye.app.data.session.KeystoreTokenVault
import com.hafiyanjye.app.data.session.Session
import com.hafiyanjye.app.data.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The app's long-lived objects, built once. Screens reach them through
 * `LocalContainer.current` (ui/Loader.kt) or `containerViewModel { … }`.
 */
class AppContainer(val app: Application) {
    /** Lives as long as the process: for work that must outlast a screen. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val session = Session(KeystoreTokenVault(app))

    /** The engine. Features build their own typed API class on top of it. */
    val client = ApiClient(
        baseUrl = BuildConfig.API_BASE_URL,
        http = http,
        token = session::token,
        onSessionRejected = session::onRejected,
    )

    /** Account endpoints (sign in, me, role). */
    val api = HafiApi(client).also { session.api = it }

    /** Live events from the engine. */
    val hub = RealtimeHub()

    /** Unread counts for the shell. */
    val badges = Badges()

    /**
     * A screen to open as soon as the signed-in shell is showing: set from a
     * notification tap (MainActivity), consumed by MainShell. A route from
     * `Routes`, never a web path.
     */
    val pendingRoute = MutableStateFlow<String?>(null)

    init {
        // Counts belong to an account: nothing carries over to the next sign-in.
        appScope.launch {
            session.state.collect { if (it is SessionState.SignedOut) badges.clear() }
        }
    }
}

class HafiApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Google's Routes and Places calls need the app's signature before any map has appeared.
        MapsKey.attach(this)
        PushManager.get(container).start()
        RealtimeConnection(container).start()
        NotificationsTracker(container).start()
        ChatUnreadTracker(container).start()
    }

    /** Photos load over the same HTTP client as the API (one connection pool). */
    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.http })) }
            .build()
}
