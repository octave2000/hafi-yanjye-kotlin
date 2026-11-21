package com.hafiyanjye.app.ui.rentals

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hafiyanjye.app.data.rentals.ListingKind
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.nav.Routes

/** Rentals for the person looking: the tab, the search the find feature hands off to, and one listing. */
fun NavGraphBuilder.rentalsGraph(nav: NavHostController) {
    val openListing: (Int) -> Unit = { nav.navigate(Routes.rental(it)) }
    val askForHelp: (String) -> Unit = { nav.navigate(Routes.newRequest(text = it)) }

    composable(Routes.Rentals) {
        RentalsScreen(
            start = RentalStart(),
            viewModelKey = "rentals-tab",
            onOpenListing = openListing,
            onAskForHelp = askForHelp,
        )
    }
    composable(
        Routes.RentalSearchPattern,
        arguments = listOf("q", "kind", "lat", "lng").map { name ->
            navArgument(name) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        },
    ) { entry ->
        val args = entry.arguments
        val start = rentalStart(
            q = args?.getString("q"),
            kind = args?.getString("kind"),
            lat = args?.getString("lat"),
            lng = args?.getString("lng"),
        )
        DetailScaffold(title = "Rentals", onBack = { nav.popBackStack() }) { padding ->
            RentalsScreen(
                start = start,
                viewModelKey = "rentals-search-${entry.id}",
                onOpenListing = openListing,
                onAskForHelp = askForHelp,
                modifier = Modifier.padding(padding),
                showHeader = false,
            )
        }
    }
    composable(
        Routes.RentalPattern,
        arguments = listOf(navArgument("listingId") { type = NavType.IntType }),
    ) { entry ->
        RentalDetailScreen(
            listingId = entry.arguments?.getInt("listingId") ?: 0,
            onBack = { nav.popBackStack() },
            onChat = { nav.navigate(Routes.chatWith(listingId = it)) },
            onEdit = { nav.navigate(Routes.listingEdit(it)) },
            onBrowse = {
                if (!nav.popBackStack()) nav.navigate(Routes.Rentals)
            },
        )
    }
}

/** The search's starting point from the route's text arguments; anything unreadable is ignored. */
internal fun rentalStart(q: String?, kind: String?, lat: String?, lng: String?): RentalStart {
    val latitude = lat?.toDoubleOrNull()?.takeIf { it.isFinite() }
    val longitude = lng?.toDoubleOrNull()?.takeIf { it.isFinite() }
    val place = if (latitude != null && longitude != null) RentalPlace(latitude, longitude, null) else null
    return RentalStart(
        q = q?.trim().orEmpty(),
        kind = if (kind == "home") ListingKind.Home else ListingKind.Car,
        place = place,
    )
}
