package com.hafiyanjye.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.session.Session
import com.hafiyanjye.app.data.session.SessionState
import com.hafiyanjye.app.ui.auth.AuthFlow
import com.hafiyanjye.app.ui.kit.EmptyState
import com.hafiyanjye.app.ui.kit.FullScreenLoading
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.LocalToaster
import com.hafiyanjye.app.ui.kit.ToastHost
import com.hafiyanjye.app.ui.kit.Toaster
import com.hafiyanjye.app.ui.shell.MainShell
import com.hafiyanjye.app.ui.theme.rememberReducedMotion
import com.hafiyanjye.app.ui.theme.Hafi
import kotlinx.coroutines.launch

/** Confirms the saved session once per launch (not again on every rotation). */
class RootViewModel(private val session: Session) : ViewModel() {
    init {
        restore()
    }

    fun restore() {
        viewModelScope.launch { session.restore() }
    }
}

/** Which part of the app shows depends only on the session. */
@Composable
fun AppRoot() {
    val reducedMotion = rememberReducedMotion()
    val container = LocalContainer.current
    val root = containerViewModel { RootViewModel(it.session) }
    val session by container.session.state.collectAsStateWithLifecycle()

    val toaster = remember { Toaster() }
    CompositionLocalProvider(LocalToaster provides toaster) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Hafi.Canvas),
        ) {
            AnimatedContent(
                targetState = session,
                // The account changing (a role switch) is not a screen change.
                contentKey = { it::class },
                transitionSpec = {
                    fadeIn(tween(if (reducedMotion) 0 else 220)) togetherWith
                        fadeOut(tween(if (reducedMotion) 0 else 150))
                },
                label = "session",
            ) { state ->
                when (state) {
                    SessionState.Checking -> FullScreenLoading("Checking your session")
                    SessionState.SignedOut -> AuthFlow()
                    is SessionState.SignedIn -> MainShell(state.me)
                    is SessionState.Unreachable -> Unreachable(state.message, onRetry = root::restore)
                }
            }
            // Above every screen, clear of the bottom tab bar and the keyboard.
            ToastHost(
                toaster,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = 72.dp),
            )
        }
    }
}

/** A saved session that the engine could not confirm: keep it and offer to try again. */
@Composable
private fun Unreachable(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            icon = R.drawable.ic_wifi_off,
            title = "Can't reach Hafi Yanjye",
            description = message,
            action = { HafiButton("Try again", onRetry, icon = R.drawable.ic_refresh_cw) },
        )
    }
}
