package com.hafiyanjye.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.AppContainer
import com.hafiyanjye.app.data.chat.ChatApi
import com.hafiyanjye.app.data.chat.ConversationList
import com.hafiyanjye.app.data.realtime.RealtimeEvent
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.Loader
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** The Chat tab: my conversations, kept live by chat events and resyncs, with presence dots. */
class ConversationsViewModel(container: AppContainer) : ViewModel() {
    private val api = ChatApi(container.client)

    val loader: Loader<ConversationList> = Loader(viewModelScope) { api.conversations() }
        .refreshOn(container.hub.resyncs)
        .refreshOn(
            container.hub.events.filter {
                it.type == RealtimeEvent.ChatMessage || it.type == RealtimeEvent.ChatRead
            },
        )

    /** User ids of the people in the list who are online now. */
    val online: StateFlow<Set<Int>> = presenceOf(
        api,
        loader.state.map { state ->
            (state as? Load.Ready)?.value?.items?.map { it.other.userId }?.toSet().orEmpty()
        },
        viewModelScope,
    )
}
