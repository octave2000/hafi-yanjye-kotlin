package com.hafiyanjye.app.ui.listings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.rentals.CarBody
import com.hafiyanjye.app.data.rentals.FuelType
import com.hafiyanjye.app.data.rentals.HomeType
import com.hafiyanjye.app.data.rentals.ListingKind
import com.hafiyanjye.app.data.rentals.PricePeriod
import com.hafiyanjye.app.data.rentals.Transmission
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.HafiSwitchRow
import com.hafiyanjye.app.ui.kit.HafiTextArea
import com.hafiyanjye.app.ui.location.GeoPoint
import com.hafiyanjye.app.ui.location.HafiMap
import com.hafiyanjye.app.ui.location.Kigali
import com.hafiyanjye.app.ui.location.LocationPicker
import com.hafiyanjye.app.ui.location.LocationSource
import com.hafiyanjye.app.ui.location.MapMarker
import com.hafiyanjye.app.ui.location.MarkerKind
import com.hafiyanjye.app.ui.location.PickedLocation
import com.hafiyanjye.app.ui.rentals.label
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** Everything a form section needs: the draft, its messages, and how to change it. */
internal class FormContext(
    val draft: ListingDraft,
    val problems: Map<DraftField, String>,
    val enabled: Boolean,
    val focus: Map<DraftField, FocusRequester>,
    val edit: ((ListingDraft) -> ListingDraft) -> Unit,
) {
    fun focusOf(field: DraftField): Modifier = Modifier.focusRequester(focus.getValue(field))

    fun editCar(transform: (CarDraft) -> CarDraft) = edit { it.copy(car = transform(it.car)) }

    fun editHome(transform: (HomeDraft) -> HomeDraft) = edit { it.copy(home = transform(it.home)) }
}

private val NumberKeys = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
private val WordKeys = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)

/** Car or home, asked only for a new listing: the engine never lets a listing change kind. */
@Composable
internal fun KindSection(form: FormContext, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .semantics { contentDescription = "What are you renting out?" }
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KindTile(
                icon = R.drawable.ic_car_front,
                title = "A car",
                description = "Rented by the day, with or without a driver",
                selected = form.draft.kind == ListingKind.Car,
                onClick = { form.edit { it.copy(kind = ListingKind.Car) } },
                modifier = Modifier.weight(1f),
            )
            KindTile(
                icon = R.drawable.ic_house,
                title = "A home",
                description = "Apartment, house or room, by the night or month",
                selected = form.draft.kind == ListingKind.Home,
                onClick = { form.edit { it.copy(kind = ListingKind.Home) } },
                modifier = Modifier.weight(1f),
            )
        }
        FieldMessage(error = form.problems[DraftField.Kind])
    }
}

/** The facts of the car or home, then title and description. */
@Composable
internal fun DetailsSection(form: FormContext, modifier: Modifier = Modifier) {
    FormSection(title = "Details", modifier = modifier) {
        if (form.draft.kind == ListingKind.Car) CarFields(form) else HomeFields(form)
        HafiField(
            label = "Title",
            value = form.draft.title,
            onValueChange = { typed -> form.edit { it.copy(title = typed.take(MAX_TITLE)) } },
            modifier = form.focusOf(DraftField.Title),
            placeholder = form.draft.titlePlaceholder,
            hint = "Leave empty to use the suggested title.",
            error = form.problems[DraftField.Title],
            optional = true,
            enabled = form.enabled,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
        )
        HafiTextArea(
            label = "Description",
            value = form.draft.description,
            onValueChange = { typed -> form.edit { it.copy(description = typed.take(MAX_DESCRIPTION)) } },
            modifier = form.focusOf(DraftField.Description),
            hint = "What's included, rules, how pickup or visits work.",
            error = form.problems[DraftField.Description],
            optional = true,
            enabled = form.enabled,
            minLines = 5,
            maxLength = MAX_DESCRIPTION,
        )
    }
}

@Composable
private fun CarFields(form: FormContext) {
    val car = form.draft.car
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HafiField(
            label = "Make",
            value = car.make,
            onValueChange = { typed -> form.editCar { it.copy(make = typed.take(MAX_MAKE)) } },
            modifier = form.focusOf(DraftField.Make).weight(1f),
            placeholder = "Toyota",
            error = form.problems[DraftField.Make],
            enabled = form.enabled,
            keyboardOptions = WordKeys,
        )
        HafiField(
            label = "Model",
            value = car.model,
            onValueChange = { typed -> form.editCar { it.copy(model = typed.take(MAX_MODEL)) } },
            modifier = form.focusOf(DraftField.Model).weight(1f),
            placeholder = "RAV4",
            error = form.problems[DraftField.Model],
            enabled = form.enabled,
            keyboardOptions = WordKeys,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HafiField(
            label = "Year",
            value = car.year,
            onValueChange = { typed -> form.editCar { it.copy(year = typed.filter(Char::isDigit).take(4)) } },
            modifier = form.focusOf(DraftField.Year).weight(1f),
            placeholder = "2019",
            error = form.problems[DraftField.Year],
            enabled = form.enabled,
            keyboardOptions = NumberKeys,
        )
        HafiField(
            label = "Seats",
            value = car.seats,
            onValueChange = { typed -> form.editCar { it.copy(seats = typed.filter(Char::isDigit).take(2)) } },
            modifier = form.focusOf(DraftField.Seats).weight(1f),
            error = form.problems[DraftField.Seats],
            enabled = form.enabled,
            keyboardOptions = NumberKeys,
        )
    }
    LabelledField("Type") {
        ChoiceRow(
            label = "Type",
            value = car.body,
            options = CarBody.entries.map { Choice(it, it.label()) },
            onChange = { body -> form.editCar { it.copy(body = body) } },
            enabled = form.enabled,
        )
    }
    LabelledField("Gearbox") {
        ChoiceRow(
            label = "Gearbox",
            value = car.transmission,
            options = Transmission.entries.map { Choice(it, it.label()) },
            onChange = { transmission -> form.editCar { it.copy(transmission = transmission) } },
            enabled = form.enabled,
        )
    }
    LabelledField("Fuel") {
        ChoiceRow(
            label = "Fuel",
            value = car.fuel,
            options = FuelType.entries.map { Choice(it, it.label()) },
            onChange = { fuel -> form.editCar { it.copy(fuel = fuel) } },
            enabled = form.enabled,
        )
    }
    HafiSwitchRow(
        title = "Comes with a driver",
        checked = car.withDriver,
        onCheckedChange = { on -> form.editCar { it.copy(withDriver = on) } },
        description = "Turn off if people drive it themselves.",
        enabled = form.enabled,
    )
}

@Composable
private fun HomeFields(form: FormContext) {
    val home = form.draft.home
    LabelledField("Type") {
        ChoiceRow(
            label = "Type",
            value = home.homeType,
            options = HomeType.entries.map { Choice(it, it.label()) },
            onChange = { type -> form.editHome { it.copy(homeType = type) } },
            enabled = form.enabled,
        )
    }
    // A room is one bedroom by definition, so it is not asked.
    if (home.homeType != HomeType.Room) {
        LabelledField("Bedrooms") {
            Stepper(
                label = "Bedrooms",
                value = home.bedrooms,
                range = 0..MAX_ROOMS,
                onChange = { count -> form.editHome { it.copy(bedrooms = count) } },
                enabled = form.enabled,
                format = { if (it == 0) "Studio" else it.toString() },
            )
        }
    }
    LabelledField("Bathrooms") {
        Stepper(
            label = "Bathrooms",
            value = home.bathrooms,
            range = 0..MAX_ROOMS,
            onChange = { count -> form.editHome { it.copy(bathrooms = count) } },
            enabled = form.enabled,
        )
    }
    HafiSwitchRow(
        title = "Furnished",
        checked = home.furnished,
        onCheckedChange = { on -> form.editHome { it.copy(furnished = on) } },
        enabled = form.enabled,
    )
}

/** The price, and for homes whether it is per month or per night. */
@Composable
internal fun PriceSection(form: FormContext, modifier: Modifier = Modifier) {
    val draft = form.draft
    FormSection(title = "Price", modifier = modifier) {
        if (draft.kind == ListingKind.Home) {
            LabelledField("Charged") {
                ChoiceRow(
                    label = "Charged per",
                    value = draft.homePeriod,
                    options = listOf(Choice(PricePeriod.Month, "Per month"), Choice(PricePeriod.Night, "Per night")),
                    onChange = { period -> form.edit { it.copy(homePeriod = period) } },
                    enabled = form.enabled,
                )
            }
        }
        val per = when (draft.period) {
            PricePeriod.Day -> "day"
            PricePeriod.Night -> "night"
            PricePeriod.Month -> "month"
        }
        HafiField(
            label = "Price per $per (RWF)",
            value = draft.price,
            onValueChange = { typed -> form.edit { it.copy(price = priceDigits(typed)) } },
            modifier = form.focusOf(DraftField.Price),
            placeholder = draft.pricePlaceholder,
            hint = "Between 1,000 and 50,000,000 RWF.",
            error = form.problems[DraftField.Price],
            enabled = form.enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        )
    }
}

/** Where it is: the place picker, and a map whose pin moves where it is tapped. */
@Composable
internal fun PlaceSection(form: FormContext, modifier: Modifier = Modifier) {
    val location = form.draft.location
    val setLocation: (PickedLocation) -> Unit = { picked -> form.edit { it.copy(location = picked) } }
    FormSection(
        title = "Where it is",
        description = "People see only the area. You share the exact place in the chat.",
        modifier = modifier,
    ) {
        LocationPicker(
            value = location,
            onChange = setLocation,
            label = "Address or area",
            error = form.problems[DraftField.Location],
            enabled = form.enabled,
        )
        val center = location?.point ?: Kigali.Center
        HafiMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            center = center,
            zoom = if (location == null) 12f else 15f,
            markers = listOfNotNull(
                location?.let { MapMarker("listing", it.point, MarkerKind.Pin, title = it.label ?: "Your listing") },
            ),
            onMapClick = { point: GeoPoint ->
                if (form.enabled) {
                    setLocation(PickedLocation(point.lat, point.lng, location?.label, LocationSource.Pin))
                }
            },
        )
        Text("Tap the map to put the pin on the exact spot.", style = HafiType.Caption, color = Hafi.FgMuted)
    }
}

/** Asked only when the account has no phone: the engine needs one before a listing goes live. */
@Composable
internal fun PhoneSection(form: FormContext, modifier: Modifier = Modifier) {
    FormSection(
        title = "Your phone number",
        description = "People who are signed in can call you about this listing.",
        modifier = modifier,
    ) {
        HafiField(
            label = "Mobile number",
            value = form.draft.phone,
            onValueChange = { typed -> form.edit { it.copy(phone = typed.take(20)) } },
            modifier = form.focusOf(DraftField.Phone),
            placeholder = "078 123 4567",
            error = form.problems[DraftField.Phone],
            enabled = form.enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        )
    }
}
