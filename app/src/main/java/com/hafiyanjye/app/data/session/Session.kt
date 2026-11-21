package com.hafiyanjye.app.data.session

import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.HafiApi
import com.hafiyanjye.app.data.api.Me
import com.hafiyanjye.app.data.api.Role
import com.hafiyanjye.app.data.api.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList

sealed interface SessionState {
    /** Checking a saved session with the engine. */
    data object Checking : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val me: Me) : SessionState

    /** There is a saved session but the engine can't be reached to confirm it. */
    data class Unreachable(val message: String) : SessionState
}

/**
 * Who is signed in on this phone. The token lives in the [TokenVault]; this
 * class decides when it is saved and dropped, and holds the account for the UI.
 */
class Session(private val vault: TokenVault) {

    /** Set right after construction (the API client needs this class for its token). */
    lateinit var api: HafiApi

    private val _state = MutableStateFlow<SessionState>(
        if (vault.read() == null) SessionState.SignedOut else SessionState.Checking,
    )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun token(): String? = vault.read()

    /** On launch (and "Try again"): confirm the saved session and load the account. */
    suspend fun restore() {
        if (vault.read() == null) {
            _state.value = SessionState.SignedOut
            return
        }
        _state.value = SessionState.Checking
        try {
            _state.value = SessionState.SignedIn(api.me())
        } catch (error: ApiException) {
            // A refused token was already dropped by onRejected(); anything else keeps it.
            if (error.status != 401) _state.value = SessionState.Unreachable(error.userMessage())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Never leave the app stuck on "Checking" or crash at launch.
            _state.value = SessionState.Unreachable(error.userMessage())
        }
    }

    suspend fun login(email: String, password: String) {
        val session = api.login(email, password)
        vault.write(session.token)
        _state.value = SessionState.SignedIn(session.user)
    }

    /** Creates the account and, for a job seeker, switches it to that side straight away. */
    suspend fun register(name: String, email: String, password: String, phone: String?, role: Role) {
        val session = api.register(name, email, password, phone)
        vault.write(session.token)
        val me = if (role != session.user.role) {
            runCatching { api.setRole(role) }.getOrDefault(session.user)
        } else {
            session.user
        }
        _state.value = SessionState.SignedIn(me)
    }

    suspend fun setRole(role: Role) {
        _state.value = SessionState.SignedIn(api.setRole(role))
    }

    /** Reload the account from the engine after something about it changed (photo, work profile). */
    suspend fun refreshMe() {
        if (_state.value !is SessionState.SignedIn) return
        val me = api.me()
        if (_state.value is SessionState.SignedIn) _state.value = SessionState.SignedIn(me)
    }

    /** Show an account the engine just returned (after saving the profile), without another request. */
    fun updateMe(me: Me) {
        if (_state.value is SessionState.SignedIn) _state.value = SessionState.SignedIn(me)
    }

    private val logoutHooks = CopyOnWriteArrayList<suspend () -> Unit>()

    /**
     * Work that needs the session one last time before it ends (removing this
     * phone's push registration). Hooks run in order, each given a few
     * seconds; a failing or slow hook never stops the sign-out.
     */
    fun beforeLogout(hook: suspend () -> Unit) {
        logoutHooks += hook
    }

    /** Runs the hooks, tells the engine (which may be unreachable), then signs out here. */
    suspend fun logout() {
        if (vault.read() != null) {
            for (hook in logoutHooks) {
                try {
                    withTimeoutOrNull(LOGOUT_HOOK_TIMEOUT_MS) { hook() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Best effort: signing out matters more.
                }
            }
            runCatching { api.logout() }
        }
        vault.clear()
        _state.value = SessionState.SignedOut
    }

    /** The engine refused our token: it expired or was revoked. */
    fun onRejected() {
        vault.clear()
        _state.value = SessionState.SignedOut
    }

    private companion object {
        const val LOGOUT_HOOK_TIMEOUT_MS = 4_000L
    }
}
