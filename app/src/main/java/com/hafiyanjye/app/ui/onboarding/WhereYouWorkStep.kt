package com.hafiyanjye.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.ui.kit.HafiSlider
import com.hafiyanjye.app.ui.location.HafiMap
import com.hafiyanjye.app.ui.location.Kigali
import com.hafiyanjye.app.ui.location.LocationPicker
import com.hafiyanjye.app.ui.location.LocationSource
import com.hafiyanjye.app.ui.location.MapCircle
import com.hafiyanjye.app.ui.location.MapMarker
import com.hafiyanjye.app.ui.location.MarkerKind
import com.hafiyanjye.app.ui.location.PickedLocation
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** Step 2: where they usually work, the pin on our own map, and how far they will travel. */
@Composable
internal fun WhereYouWorkStep(ui: WorkSetupUi, vm: WorkSetupViewModel) {
    val location = ui.form.location
    val radius = ui.form.radiusKm
    LocationPicker(
        value = location,
        onChange = { picked -> vm.edit(FormField.Location) { it.copy(location = picked) } },
        label = "Where you work",
        error = ui.errors[FormField.Location],
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HafiMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .border(1.dp, Hafi.Line),
            center = location?.point ?: Kigali.Center,
            zoom = 13f,
            markers = location?.let { listOf(MapMarker("work", it.point, kind = MarkerKind.Home)) }.orEmpty(),
            circles = location?.let { listOf(MapCircle(it.point, radius.toDouble())) }.orEmpty(),
            onMapClick = { point ->
                vm.edit(FormField.Location) { form ->
                    form.copy(
                        location = PickedLocation(
                            lat = point.lat,
                            lng = point.lng,
                            label = form.location?.label,
                            source = LocationSource.Pin,
                        ),
                    )
                }
            },
        )
        Text(
            text = "Tap the map to move the pin. Job providers only see your area, never your exact pin.",
            style = HafiType.Caption,
            color = Hafi.FgMuted,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HafiSlider(
            value = radius.toFloat(),
            onValueChange = { value ->
                vm.edit { it.copy(radiusKm = value.toInt().coerceIn(1, WorkForm.MAX_RADIUS_KM)) }
            },
            valueRange = 1f..WorkForm.MAX_RADIUS_KM.toFloat(),
            steps = WorkForm.MAX_RADIUS_KM - 2,
            label = "How far will you travel?",
            valueText = "$radius km",
        )
        Row {
            Text("1 km", style = HafiType.Caption, color = Hafi.FgSubtle, modifier = Modifier.weight(1f))
            Text("${WorkForm.MAX_RADIUS_KM} km", style = HafiType.Caption, color = Hafi.FgSubtle)
        }
    }
    Text(
        text = buildAnnotatedString {
            append("You'll get jobs within ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$radius km") }
            append(" of ")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(location?.label ?: "your pin") }
            append(".")
        },
        style = HafiType.Body,
        color = Hafi.AccentStrong,
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.AccentSoft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
