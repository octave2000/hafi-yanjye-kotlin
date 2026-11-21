package com.hafiyanjye.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.profile.Countries
import com.hafiyanjye.app.data.profile.Country
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.HafiSheet
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/**
 * The country a phone number belongs to: shows its flag, name and calling
 * code, and opens a searchable list (name, "KE" or "254") to change it.
 */
@Composable
fun CountryCodeField(
    country: Country,
    onChange: (Country) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Country code", style = HafiType.Label, color = Hafi.Fg, modifier = Modifier.clearAndSetSemantics { })
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(if (enabled) Hafi.Surface else Hafi.Surface2)
                .border(1.dp, Hafi.LineStrong)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Change country") { open = true }
                .semantics(mergeDescendants = true) {
                    contentDescription = "Country code: ${country.name}, +${country.dial}"
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(country.flag, style = HafiType.Body, modifier = Modifier.clearAndSetSemantics { })
            Text(
                country.name,
                style = HafiType.Body,
                color = Hafi.Fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clearAndSetSemantics { },
            )
            Text(
                "+${country.dial}",
                style = HafiType.BodyStrong,
                color = Hafi.Fg,
                modifier = Modifier.clearAndSetSemantics { },
            )
            HIcon(R.drawable.ic_chevron_down, size = 18.dp, tint = Hafi.FgMuted)
        }
    }

    if (open) {
        CountrySheet(
            current = country,
            onPick = {
                open = false
                onChange(it)
            },
            onDismiss = { open = false },
        )
    }
}

private sealed interface PickerRow {
    data class Heading(val label: String) : PickerRow
    data class Option(val key: String, val country: Country) : PickerRow
}

/** Searchable list: Rwanda's neighbours first, then every country (web: phone-input.tsx). */
@Composable
private fun CountrySheet(current: Country, onPick: (Country) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val rows: List<PickerRow> = remember(query) {
        if (query.isNotBlank()) {
            Countries.search(query).map { PickerRow.Option(it.iso, it) }
        } else {
            buildList<PickerRow> {
                add(PickerRow.Heading("Nearby"))
                Countries.nearby.forEach { add(PickerRow.Option("near-${it.iso}", it)) }
                add(PickerRow.Heading("All countries"))
                Countries.all.forEach { add(PickerRow.Option(it.iso, it)) }
            }
        }
    }

    HafiSheet(onDismiss = onDismiss, title = "Country") {
        HafiField(
            label = "Search",
            value = query,
            onValueChange = { query = it },
            placeholder = "Search country or code",
            leadingIcon = R.drawable.ic_search,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        )
        if (rows.isEmpty()) {
            Text(
                "No country matches “${query.trim()}”.",
                style = HafiType.Body,
                color = Hafi.FgMuted,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            )
        }
        // The sheet scrolls as a whole; a bounded height lets this long list stay lazy inside it.
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(max = 380.dp)) {
            items(
                items = rows,
                key = { if (it is PickerRow.Option) it.key else "h-${(it as PickerRow.Heading).label}" },
                contentType = { it is PickerRow.Option },
            ) { row ->
                when (row) {
                    is PickerRow.Heading -> Text(
                        row.label.uppercase(),
                        style = HafiType.Micro,
                        color = Hafi.FgSubtle,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
                    )
                    is PickerRow.Option -> CountryRow(row.country, row.country.iso == current.iso, onPick)
                }
            }
        }
    }
}

@Composable
private fun CountryRow(country: Country, selected: Boolean, onPick: (Country) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button) { onPick(country) }
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(country.flag, style = HafiType.Body, modifier = Modifier.clearAndSetSemantics { })
        Text(
            country.name,
            style = if (selected) HafiType.BodyStrong else HafiType.Body,
            color = Hafi.Fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("+${country.dial}", style = HafiType.Body, color = Hafi.FgMuted)
        if (selected) HIcon(R.drawable.ic_check, size = 18.dp, tint = Hafi.AccentStrong)
    }
}
