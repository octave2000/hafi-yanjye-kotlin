package com.hafiyanjye.app.ui.match

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hafiyanjye.app.ui.jobs.JobsScreen
import com.hafiyanjye.app.ui.nav.Routes

/** My requests (tab) and one request with its live matching. */
fun NavGraphBuilder.matchGraph(nav: NavHostController) {
    composable(Routes.Jobs) {
        JobsScreen(
            onAskForHelp = { nav.navigate(Routes.Find) },
            onRequest = { jobId -> nav.navigate(Routes.request(jobId)) },
        )
    }
    composable(
        Routes.RequestPattern,
        arguments = listOf(navArgument("jobId") { type = NavType.IntType }),
    ) { entry ->
        RequestScreen(jobId = requireNotNull(entry.arguments).getInt("jobId"), nav = nav)
    }
}
