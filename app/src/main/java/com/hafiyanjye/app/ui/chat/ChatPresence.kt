package com.hafiyanjye.app.ui.chat

import com.hafiyanjye.app.data.chat.ChatApi
import com.hafiyanjye.app.data.chat.ChatLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Who of [userIds] is online, asked again every 20 s (web: usePresence) and
 * only while a screen collects it, so a chat in the background polls nothing.
 * A failed poll keeps the last answer: presence is a hint, not worth an error.
 */
internal fun presenceOf(api: ChatApi, userIds: Flow<Set<Int>>, scope: CoroutineScope): StateFlow<Set<Int>> =
    // channelFlow, not flow: values are sent from collectLatest's inner coroutine,
    // which a plain flow forbids ("Flow invariant is violated").
    channelFlow {
        userIds.distinctUntilChanged().collectLatest { ids ->
            if (ids.isEmpty()) {
                send(emptySet())
                return@collectLatest
            }
            while (true) {
                try {
                    send(api.presence(ids))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep the dots as they were until the next poll.
                }
                delay(ChatLimits.PRESENCE_EVERY_MS)
            }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), emptySet())

private const val STOP_AFTER_MS = 5_000L
