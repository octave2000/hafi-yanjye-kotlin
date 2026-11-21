package com.hafiyanjye.app.ui.find

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hafiyanjye.app.ui.nav.Routes

/** Find help (the job provider's first tab) and the new request form. */
fun NavGraphBuilder.findGraph(nav: NavHostController) {
    composable(Routes.Find) { entry -> FindScreen(nav, entry.savedStateHandle) }
    composable(
        route = Routes.NewRequestPattern,
        arguments = listOf(
            navArgument("text") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("categoryId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { entry ->
        NewRequestScreen(
            nav = nav,
            saved = entry.savedStateHandle,
            routeText = entry.arguments?.getString("text").orEmpty(),
            routeCategoryId = entry.arguments?.getString("categoryId")?.toIntOrNull(),
        )
    }
}
