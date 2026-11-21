package com.hafiyanjye.app.ui.profile

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.hafiyanjye.app.ui.nav.LocalMe
import com.hafiyanjye.app.ui.nav.Routes
import com.hafiyanjye.app.ui.notifications.NotificationsScreen

/** Profile (tab), editing it, and the notifications inbox. */
fun NavGraphBuilder.profileGraph(nav: NavHostController) {
    composable(Routes.Profile) { ProfileScreen(LocalMe.current, nav) }
    composable(Routes.EditProfile) { EditProfileScreen(LocalMe.current, nav) }
    composable(Routes.Notifications) { NotificationsScreen(nav) }
}
