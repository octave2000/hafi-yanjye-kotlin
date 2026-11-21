package com.hafiyanjye.app.ui.rentals

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.rentals.ListingAttributes
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** One fact on the detail page: an icon, what it is, and its value. */
internal data class ListingFact(@DrawableRes val icon: Int, val label: String, val value: String)

/** The car's or the home's facts, in the web's order and words. */
internal fun listingFacts(attributes: ListingAttributes): List<ListingFact> {
    attributes.car?.let { car ->
        return listOf(
            ListingFact(R.drawable.ic_car_front, "Type", car.body.label()),
            ListingFact(R.drawable.ic_calendar_days, "Year", car.year.toString()),
            ListingFact(R.drawable.ic_users, "Seats", "${car.seats} seats"),
            ListingFact(R.drawable.ic_settings_2, "Gearbox", car.transmission.label()),
            ListingFact(R.drawable.ic_fuel, "Fuel", car.fuel.label()),
            ListingFact(R.drawable.ic_user_round, "Driver", if (car.withDriver) "Comes with a driver" else "You drive"),
        )
    }
    val home = attributes.home ?: return emptyList()
    return listOf(
        ListingFact(R.drawable.ic_house, "Type", home.homeType.label()),
        ListingFact(R.drawable.ic_house, "Bedrooms", bedroomsFact(home)),
        ListingFact(R.drawable.ic_shower_head, "Bathrooms", home.bathrooms.toString()),
        ListingFact(R.drawable.ic_sofa, "Furniture", if (home.furnished) "Furnished" else "Unfurnished"),
    )
}

/** The facts two to a row, each row as tall as its tallest fact so long words wrap neatly. */
@Composable
internal fun Facts(attributes: ListingAttributes, modifier: Modifier = Modifier) {
    val facts = listingFacts(attributes)
    if (facts.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        facts.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { fact ->
                    FactTile(
                        fact = fact,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FactTile(fact: ListingFact, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = "${fact.label}: ${fact.value}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HIcon(fact.icon, size = 20.dp, tint = Hafi.AccentStrong)
        Column {
            Text(fact.label, style = HafiType.Caption, color = Hafi.FgMuted)
            Text(fact.value, style = HafiType.BodyStrong, color = Hafi.Fg)
        }
    }
}
