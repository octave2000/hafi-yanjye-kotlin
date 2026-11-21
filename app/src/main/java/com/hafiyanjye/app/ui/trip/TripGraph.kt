package com.hafiyanjye.app.ui.trip

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hafiyanjye.app.ui.nav.Routes

/** The live trip of a hired session, opened from the hirer's request and the hired person's ping. */
fun NavGraphBuilder.tripGraph(nav: NavHostController) {
    composable(
        Routes.TripPattern,
        arguments = listOf(navArgument("sessionId") { type = NavType.IntType }),
    ) { entry ->
        val sessionId = entry.arguments?.getInt("sessionId") ?: return@composable
        TripScreen(sessionId, nav)
    }
}
