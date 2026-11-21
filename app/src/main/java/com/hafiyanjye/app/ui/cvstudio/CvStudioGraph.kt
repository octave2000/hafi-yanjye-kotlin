package com.hafiyanjye.app.ui.cvstudio

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hafiyanjye.app.ui.nav.Routes

/** The CV studio (templates, style, PDF) and public CVs. */
fun NavGraphBuilder.cvStudioGraph(nav: NavHostController) {
    composable(Routes.CvStudio) {
        CvStudioScreen(
            onBack = { nav.popBackStack() },
            onEditContent = { nav.navigate(Routes.Cv) { launchSingleTop = true } },
        )
    }
    // The argument is the talent profile id, which the web's /cv/:id link carries.
    composable(Routes.PublicCvPattern, arguments = listOf(navArgument("userId") { type = NavType.IntType })) { entry ->
        PublicCvScreen(entry.arguments?.getInt("userId") ?: 0, onBack = { nav.popBackStack() })
    }
}
