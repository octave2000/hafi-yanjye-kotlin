package com.hafiyanjye.app.ui.rentals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.data.rentals.CarBody
import com.hafiyanjye.app.data.rentals.HomeType
import com.hafiyanjye.app.data.rentals.ListingFilters
import com.hafiyanjye.app.data.rentals.ListingKind
import com.hafiyanjye.app.data.rentals.Transmission
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.HafiSelect
import com.hafiyanjye.app.ui.kit.HafiSheet

/** The engine's highest allowed price; a larger maximum filters nothing. */
private const val MaxPriceRwf = 50_000_000
private val SeatChoices = listOf(2, 4, 5, 7, 9, 14)
private val BedroomChoices = listOf(1, 2, 3, 4, 5)

/**
 * Every filter of the current kind, edited as a draft: nothing changes the
 * results until "Show results", and "Reset" clears the draft.
 */
@Composable
internal fun RentalFiltersSheet(
    filters: ListingFilters,
    onApply: (ListingFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(filters) { mutableStateOf(filters) }
    val count = filterChips(draft.cleaned()).size
    HafiSheet(onDismiss = onDismiss, title = if (count > 0) "Filters ($count on)" else "Filters") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PriceField(draft) { draft = it }
            if (draft.kind == ListingKind.Car) {
                CarFilters(draft) { draft = it }
            } else {
                HomeFilters(draft) { draft = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HafiButton(
                    text = "Reset",
                    onClick = { draft = ListingFilters(draft.kind) },
                    modifier = Modifier.weight(1f),
                    kind = ButtonKind.Outline,
                    enabled = count > 0,
                )
                HafiButton(
                    text = "Show results",
                    onClick = { onApply(draft.cleaned()) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PriceField(draft: ListingFilters, onChange: (ListingFilters) -> Unit) {
    val per = if (draft.kind == ListingKind.Car) "a day" else "a month"
    HafiField(
        label = "Maximum price (RWF $per)",
        value = draft.maxPriceRwf?.toString().orEmpty(),
        onValueChange = { typed ->
            val digits = typed.filter(Char::isDigit).take(8)
            val price = digits.toIntOrNull()?.coerceAtMost(MaxPriceRwf)
            onChange(draft.copy(maxPriceRwf = price?.takeIf { it > 0 }))
        },
        modifier = Modifier.fillMaxWidth(),
        placeholder = if (draft.kind == ListingKind.Car) "e.g. 60000" else "e.g. 400000",
        optional = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun CarFilters(draft: ListingFilters, onChange: (ListingFilters) -> Unit) {
    HafiField(
        label = "Make",
        value = draft.make.orEmpty(),
        onValueChange = { onChange(draft.copy(make = it.take(MaxMakeLength).ifEmpty { null })) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = "e.g. Toyota",
        optional = true,
    )
    AnyOf(
        label = "Type",
        options = CarBody.entries,
        selected = draft.body,
        text = { it.label() },
        onSelect = { onChange(draft.copy(body = it)) },
    )
    AnyOf(
        label = "Seats",
        options = SeatChoices,
        selected = draft.minSeats,
        text = { "$it+ seats" },
        onSelect = { onChange(draft.copy(minSeats = it)) },
    )
    AnyOf(
        label = "Gearbox",
        options = Transmission.entries,
        selected = draft.transmission,
        text = { it.label() },
        onSelect = { onChange(draft.copy(transmission = it)) },
    )
    AnyOf(
        label = "Driver",
        options = listOf(true, false),
        selected = draft.withDriver,
        text = { if (it) "With driver" else "Self drive" },
        onSelect = { onChange(draft.copy(withDriver = it)) },
    )
}

@Composable
private fun HomeFilters(draft: ListingFilters, onChange: (ListingFilters) -> Unit) {
    AnyOf(
        label = "Type",
        options = HomeType.entries,
        selected = draft.homeType,
        text = { it.label() },
        onSelect = { onChange(draft.copy(homeType = it)) },
    )
    AnyOf(
        label = "Bedrooms",
        options = BedroomChoices,
        selected = draft.minBedrooms,
        text = { "$it+ bedrooms" },
        onSelect = { onChange(draft.copy(minBedrooms = it)) },
    )
    AnyOf(
        label = "Furniture",
        options = listOf(true, false),
        selected = draft.furnished,
        text = { if (it) "Furnished" else "Unfurnished" },
        onSelect = { onChange(draft.copy(furnished = it)) },
    )
}

/** A select whose first choice is "Any" (no filter). A value set by the ask box but not offered here is kept. */
@Composable
private fun <T> AnyOf(
    label: String,
    options: List<T>,
    selected: T?,
    text: (T) -> String,
    onSelect: (T?) -> Unit,
) {
    val shown = if (selected != null && selected !in options) options + selected else options
    HafiSelect(
        label = label,
        options = listOf("Any") + shown.map(text),
        selectedIndex = selected?.let { shown.indexOf(it) + 1 } ?: 0,
        onSelect = { index -> onSelect(if (index == 0) null else shown[index - 1]) },
        modifier = Modifier.fillMaxWidth(),
    )
}
