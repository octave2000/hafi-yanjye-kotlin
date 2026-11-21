package com.hafiyanjye.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.AppContainer
import com.hafiyanjye.app.data.chat.ChatApi
import com.hafiyanjye.app.data.chat.ChatOpenInput
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.Loader
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.FullScreenLoading
import com.hafiyanjye.app.ui.kit.HafiButton

/** Starts or reopens a chat once (POST /api/v1/chat/open); the screen then hands over to the thread. */
class OpenChatViewModel(container: AppContainer, input: ChatOpenInput) : ViewModel() {
    private val api = ChatApi(container.client)
    val loader: Loader<Int> = Loader(viewModelScope) { api.open(input) }
}

/** "Opening…" while the engine finds or creates the conversation; its refusal is shown with Try again. */
@Composable
internal fun OpenChatScreen(
    viewModel: OpenChatViewModel,
    onOpened: (conversationId: Int) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.loader.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        val ready = state as? Load.Ready ?: return@LaunchedEffect
        onOpened(ready.value)
    }
    DetailScaffold(title = "Chat", onBack = onBack) { padding ->
        when (val shown = state) {
            is Load.Failed -> Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorPanel("Couldn't open the chat. ${shown.message}", viewModel.loader::refresh)
                HafiButton("Go back", onBack, kind = ButtonKind.Outline)
            }
            else -> FullScreenLoading(
                "Opening…",
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
        }
    }
}
