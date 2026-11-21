package com.hafiyanjye.app.ui.pings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.hafiyanjye.app.ui.nav.Routes

/** The job seeker's home: pings and the work dashboard. */
fun NavGraphBuilder.pingsGraph(nav: NavHostController) {
    composable(Routes.Pings) {
        PingsScreen(onNavigate = { nav.navigate(it) })
    }
}
