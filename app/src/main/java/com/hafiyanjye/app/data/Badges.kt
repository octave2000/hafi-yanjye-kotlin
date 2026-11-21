package com.hafiyanjye.app.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Small counts shown on the shell (the Chat tab, the bell). The chat and
 * notifications features keep them current; the shell only reads them.
 */
class Badges {
    val chatUnread = MutableStateFlow(0)
    val notificationsUnread = MutableStateFlow(0)

    fun clear() {
        chatUnread.value = 0
        notificationsUnread.value = 0
    }
}
