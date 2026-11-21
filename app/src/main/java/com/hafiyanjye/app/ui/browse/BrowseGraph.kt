package com.hafiyanjye.app.ui.browse

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hafiyanjye.app.data.browse.BrowseApi
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.nav.Routes

/** The services list and a job seeker's public profile. */
fun NavGraphBuilder.browseGraph(nav: NavHostController) {
    composable(
        Routes.ServicesPattern,
        arguments = listOf(
            navArgument("categoryId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("q") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { entry ->
        val categoryId = entry.arguments?.getString("categoryId")?.toIntOrNull()
        val q = entry.arguments?.getString("q")
        val viewModel = containerViewModel(key = "services-$categoryId-$q") {
            ServicesViewModel(BrowseApi(it.client), it.hub, categoryId, q)
        }
        ServicesScreen(viewModel, nav)
    }
    composable(
        Routes.PersonPattern,
        arguments = listOf(navArgument("providerId") { type = NavType.IntType }),
    ) { entry ->
        val jobSeekerId = entry.arguments?.getInt("providerId") ?: 0
        val viewModel = containerViewModel(key = "person-$jobSeekerId") {
            PersonViewModel(BrowseApi(it.client), it.hub, jobSeekerId)
        }
        PersonScreen(viewModel, nav)
    }
}
