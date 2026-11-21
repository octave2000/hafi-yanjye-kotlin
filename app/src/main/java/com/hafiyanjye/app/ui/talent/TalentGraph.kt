package com.hafiyanjye.app.ui.talent

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.hafiyanjye.app.ui.nav.Routes

/** The CV tab. Its section editors and the interview open over it; the CV studio has its own graph. */
fun NavGraphBuilder.talentGraph(nav: NavHostController) {
    composable(Routes.Cv) {
        TalentScreen(onStudio = { nav.navigate(Routes.CvStudio) })
    }
}
