package com.hafiyanjye.app.ui.chat

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hafiyanjye.app.data.chat.ChatContextType
import com.hafiyanjye.app.data.chat.ChatOpenInput
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.nav.LocalMe
import com.hafiyanjye.app.ui.nav.Routes

/** The Chat tab, a thread, and the start-or-reopen route other features navigate to. */
fun NavGraphBuilder.chatGraph(nav: NavHostController) {
    composable(Routes.Chat) {
        val me = LocalMe.current
        val viewModel = containerViewModel(key = "chats-${me.id}") { ConversationsViewModel(it) }
        ConversationsScreen(
            viewModel = viewModel,
            meId = me.id,
            onOpen = { nav.navigate(Routes.thread(it)) },
            onBrowseRentals = { nav.navigate(Routes.Rentals) },
        )
    }

    val openArguments = listOf("providerId", "jobId", "listingId").map { name ->
        navArgument(name) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        }
    }
    composable(Routes.ChatOpenPattern, arguments = openArguments) { entry ->
        val arguments = entry.arguments
        val input = ChatOpenInput(
            workerId = arguments?.getString("providerId")?.toIntOrNull(),
            jobId = arguments?.getString("jobId")?.toIntOrNull(),
            listingId = arguments?.getString("listingId")?.toIntOrNull(),
        )
        val me = LocalMe.current
        val viewModel = containerViewModel(key = "chat-open-${me.id}-$input") { OpenChatViewModel(it, input) }
        OpenChatScreen(
            viewModel = viewModel,
            onOpened = { conversationId ->
                // The thread replaces this screen, so Back goes to where the chat was started from.
                nav.navigate(Routes.thread(conversationId)) {
                    popUpTo(Routes.ChatOpenPattern) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onBack = { nav.popBackStack() },
        )
    }

    val threadArguments = listOf(navArgument("conversationId") { type = NavType.IntType })
    composable(Routes.ThreadPattern, arguments = threadArguments) { entry ->
        val conversationId = entry.arguments?.getInt("conversationId") ?: return@composable
        val me = LocalMe.current
        val viewModel = containerViewModel(key = "thread-${me.id}-$conversationId") {
            ThreadViewModel(it, conversationId, me.id)
        }
        ThreadScreen(
            viewModel = viewModel,
            navigation = ThreadNavigation(
                back = { nav.popBackStack() },
                toChats = {
                    if (!nav.popBackStack(Routes.Chat, inclusive = false)) nav.navigate(Routes.Chat)
                },
                toContext = { context ->
                    val route = when {
                        context.type == ChatContextType.Listing -> Routes.rental(context.id)
                        context.mine -> Routes.request(context.id)
                        else -> Routes.Pings
                    }
                    nav.navigate(route)
                },
                toJob = { jobId -> nav.navigate(Routes.request(jobId)) },
            ),
        )
    }
}
