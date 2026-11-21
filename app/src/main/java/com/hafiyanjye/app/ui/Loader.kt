package com.hafiyanjye.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hafiyanjye.app.AppContainer
import com.hafiyanjye.app.data.api.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The app's long-lived objects, for screens. Provided once in MainActivity. */
val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** A ViewModel built from the [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContainer.current
    return viewModel(key = key) { create(container) }
}

/** Something fetched from the engine, as a screen sees it. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>

    /** [refreshing]: a newer copy is on its way; [staleError]: the last refresh failed. */
    data class Ready<T>(val value: T, val refreshing: Boolean = false, val staleError: String? = null) : Load<T>

    data class Failed(val message: String) : Load<Nothing>
}

/**
 * Loads one thing and keeps it fresh. The first load shows a skeleton; later
 * refreshes keep what is on screen, so a slow network never blanks a list.
 */
class Loader<T>(private val scope: CoroutineScope, private val fetch: suspend () -> T) {

    private val _state = MutableStateFlow<Load<T>>(Load.Loading)
    val state: StateFlow<Load<T>> = _state.asStateFlow()

    private var running: Job? = null

    /** A reload asked for while one was running: the running one may already be out of date. */
    private var again = false

    init {
        refresh()
    }

    fun refresh() {
        if (running?.isActive == true) {
            again = true
            return
        }
        val shown = _state.value
        _state.value = if (shown is Load.Ready) shown.copy(refreshing = true, staleError = null) else Load.Loading
        running = scope.launch {
            try {
                _state.value = Load.Ready(fetch())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val current = _state.value
                _state.value = if (current is Load.Ready) {
                    current.copy(refreshing = false, staleError = error.userMessage())
                } else {
                    Load.Failed(error.userMessage())
                }
            }
            if (again) {
                again = false
                refresh()
            }
        }
    }

    /** Change what is shown without asking the engine (after an action whose result is known). */
    fun mutate(transform: (T) -> T) {
        val shown = _state.value
        if (shown is Load.Ready) _state.value = shown.copy(value = transform(shown.value))
    }

    /** Reload whenever [signal] emits, e.g. matching live events or the hub's resyncs. */
    fun refreshOn(signal: Flow<*>): Loader<T> {
        scope.launch { signal.collect { refresh() } }
        return this
    }
}
