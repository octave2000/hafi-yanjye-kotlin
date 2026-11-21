package com.hafiyanjye.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.hafiyanjye.app.ui.AppRoot
import com.hafiyanjye.app.ui.LocalContainer
import com.hafiyanjye.app.ui.nav.Routes
import com.hafiyanjye.app.ui.theme.HafiTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as HafiApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars; dark icons, since the app is always light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Not after a rotation: the tap was already handled then.
        if (savedInstanceState == null) openFrom(intent)
        val container = container
        setContent {
            HafiTheme {
                CompositionLocalProvider(LocalContainer provides container) {
                    AppRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFrom(intent)
    }

    /**
     * A notification tap carries the engine's web path in the string extra
     * [EXTRA_URL] ("/jobs/12"); the shell opens the matching screen.
     */
    private fun openFrom(intent: Intent?) {
        val route = Routes.fromWebPath(intent?.getStringExtra(EXTRA_URL)) ?: return
        container.pendingRoute.value = route
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
