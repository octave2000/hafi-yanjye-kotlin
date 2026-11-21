package com.hafiyanjye.app.ui.listings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hafiyanjye.app.ui.nav.Routes

/** The owner's side of rentals: the Listings tab, a new listing, and editing one. */
fun NavGraphBuilder.listingsGraph(nav: NavHostController) {
    // After a save, or from "Listing not found", the owner lands on their list, which reloads on return.
    // When the form was opened from elsewhere (the listing page's Edit), it simply closes.
    val backToList: () -> Unit = {
        if (!nav.popBackStack(Routes.Listings, inclusive = false)) nav.popBackStack()
    }

    composable(Routes.Listings) {
        ListingsScreen(
            onNew = { nav.navigate(Routes.ListingNew) },
            onEdit = { nav.navigate(Routes.listingEdit(it)) },
            onView = { nav.navigate(Routes.rental(it)) },
        )
    }
    composable(Routes.ListingNew) {
        ListingEditorScreen(
            listingId = null,
            onBack = { nav.popBackStack() },
            onSaved = backToList,
            onMyListings = backToList,
        )
    }
    composable(
        Routes.ListingEditPattern,
        arguments = listOf(navArgument("listingId") { type = NavType.IntType }),
    ) { entry ->
        ListingEditorScreen(
            listingId = entry.arguments?.getInt("listingId") ?: 0,
            onBack = { nav.popBackStack() },
            onSaved = backToList,
            onMyListings = backToList,
        )
    }
}
