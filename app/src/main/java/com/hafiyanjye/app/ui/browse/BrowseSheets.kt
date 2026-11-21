package com.hafiyanjye.app.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.browse.BrowseFilters
import com.hafiyanjye.app.data.browse.FilterOption
import com.hafiyanjye.app.data.browse.FilterOptions
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.Divider
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiChip
import com.hafiyanjye.app.ui.kit.HafiSheet
import com.hafiyanjye.app.ui.kit.HafiSwitchRow
import com.hafiyanjye.app.ui.kit.HafiTextArea
import com.hafiyanjye.app.ui.location.LocationPicker
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** The filters sheet (web: the phone sheet on app/services/page.tsx). Results update as you choose. */
@Composable
internal fun FiltersSheet(
    filters: BrowseFilters,
    located: Boolean,
    total: Int?,
    onChange: ((BrowseFilters) -> BrowseFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    HafiSheet(onDismiss = onDismiss, title = "Filters") {
        Text("Results update as you choose.", style = HafiType.Body, color = Hafi.FgMuted)
        Spacer(Modifier.height(20.dp))
        FilterPanel(filters, located, onChange)
        Spacer(Modifier.height(20.dp))
        Divider()
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HafiButton(
                "Clear all",
                { onChange { it.cleared() } },
                Modifier.weight(1f),
                kind = ButtonKind.Outline,
                enabled = filters.activeCount > 0,
            )
            HafiButton(showResultsLabel(total), onDismiss, Modifier.weight(2f))
        }
    }
}

/** Every filter except the service (web: components/browse/filter-panel.tsx). */
@Composable
private fun FilterPanel(
    filters: BrowseFilters,
    located: Boolean,
    onChange: ((BrowseFilters) -> BrowseFilters) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ChipGroup(
            title = "Distance",
            hint = if (located) null else "Set your location to use this.",
            options = FilterOptions.Distances,
            selected = filters.km,
            enabled = located,
        ) { km -> onChange { it.copy(km = km) } }
        HafiSwitchRow(
            title = "Available now",
            checked = filters.online,
            onCheckedChange = { online -> onChange { it.copy(online = online) } },
            description = "Online and taking requests.",
        )
        ChipGroup("Rating", FilterOptions.Ratings, filters.rating) { rating -> onChange { it.copy(rating = rating) } }
        ChipGroup("Starting price", FilterOptions.Prices, filters.price, hint = "RWF or less.") { price ->
            onChange { it.copy(price = price) }
        }
        ChipGroup("Level", FilterOptions.Levels, filters.level) { level -> onChange { it.copy(level = level) } }
        HafiSwitchRow(
            title = "Checked CV or certificate",
            checked = filters.checked,
            onCheckedChange = { checked -> onChange { it.copy(checked = checked) } },
            description = "Our AI read it and the name matched.",
        )
        ChipGroup("Speaks", FilterOptions.Languages, filters.language) { language ->
            onChange { it.copy(language = language) }
        }
    }
}

/** One filter as a row of chips; exactly one is chosen, "Any" included. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(
    title: String,
    options: List<FilterOption<T>>,
    selected: T?,
    hint: String? = null,
    enabled: Boolean = true,
    onSelect: (T?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(title, style = HafiType.Label, color = Hafi.Fg, modifier = Modifier.semantics { heading() })
            if (hint != null) Text(hint, style = HafiType.Caption, color = Hafi.FgMuted)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                HafiChip(
                    text = option.label,
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                    enabled = enabled,
                )
            }
        }
    }
}

/**
 * Hire one person directly (web: components/browse/hire-dialog.tsx). The
 * request goes to them only; they accept (you're hired together) or decline.
 */
@Composable
internal fun HireSheet(form: HireForm, hire: HireController) {
    val target = form.target
    HafiSheet(onDismiss = hire::close, title = "Hire ${target.displayName}") {
        Text(
            "We send ${target.firstName} your request. You're hired together as soon as they accept, " +
                "then you can chat or call.",
            style = HafiType.Body,
            color = Hafi.FgMuted,
        )
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            HafiTextArea(
                label = "What do you need?",
                value = form.text,
                onValueChange = hire::setText,
                placeholder = "e.g. Two sockets in the kitchen stopped working, today if possible",
                enabled = !form.sending,
                maxLength = HireController.MaxNeedLength,
            )
            if (target.categories.size > 1) ServiceChoice(form, hire)
            LocationPicker(
                value = form.location,
                onChange = hire::setLocation,
                label = "Where",
                error = form.placeError,
                enabled = !form.sending,
            )
            FormAlert(form.error)
            HireButtons(form, hire)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceChoice(form: HireForm, hire: HireController) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Which service", style = HafiType.Label, color = Hafi.Fg, modifier = Modifier.semantics { heading() })
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            form.target.categories.forEach { service ->
                HafiChip(
                    text = service.nameEn,
                    selected = form.service == service.slug,
                    onClick = { hire.setService(service.slug) },
                    enabled = !form.sending,
                )
            }
        }
    }
}

@Composable
private fun HireButtons(form: HireForm, hire: HireController) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HafiButton(
            "Cancel",
            hire::close,
            Modifier.weight(1f),
            kind = ButtonKind.Outline,
            enabled = !form.sending,
        )
        HafiButton(
            "Send hire request",
            hire::send,
            Modifier.weight(2f),
            loading = form.sending,
            loadingText = "Sending request",
            icon = R.drawable.ic_user_check,
        )
    }
}
