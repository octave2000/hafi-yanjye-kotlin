package com.hafiyanjye.app.ui.onboarding

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.hafiyanjye.app.ui.nav.Routes

/** The job seeker's work profile setup (first time and editing) and their CV and certificates. */
fun NavGraphBuilder.onboardingGraph(nav: NavHostController) {
    composable(Routes.WorkSetup) { WorkSetupScreen(nav) }
    composable(Routes.Credentials) { CredentialsScreen(nav) }
}
