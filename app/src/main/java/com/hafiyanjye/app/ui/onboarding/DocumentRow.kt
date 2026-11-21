package com.hafiyanjye.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.onboarding.DocumentDetailsInput
import com.hafiyanjye.app.data.onboarding.DocumentKind
import com.hafiyanjye.app.data.onboarding.DocumentStatus
import com.hafiyanjye.app.data.onboarding.WorkDocument
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiChip
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import java.time.LocalDate
import java.time.Year
import java.time.ZoneOffset

/** One CV or certificate with its status in words, and View, Open, Add details, Remove. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DocumentRow(
    document: WorkDocument,
    services: List<Pair<String, String>>,
    ui: CredentialsUi,
    vm: CredentialsViewModel,
    onRemove: () -> Unit,
) {
    var editing by rememberSaveable(document.id) { mutableStateOf(false) }
    val opening = ui.openingId == document.id
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HIcon(
                id = if (document.kind == DocumentKind.Cv) R.drawable.ic_file_text else R.drawable.ic_award,
                size = 24.dp,
                tint = Hafi.AccentStrong,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(document.title, style = HafiType.BodyStrong)
                document.meta?.let { Text(it, style = HafiType.Caption, color = Hafi.FgMuted) }
                document.cvSummary?.let { Text(it, style = HafiType.Caption, color = Hafi.FgMuted) }
                StatusBadge(document)
                document.checkNote?.let { Text(it, style = HafiType.Caption, color = Hafi.FgMuted) }
                linkedServices(document, services)?.let {
                    Text("For: $it", style = HafiType.Caption, color = Hafi.FgMuted)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HafiButton(
                text = "View",
                onClick = { vm.open(document, external = false) },
                kind = ButtonKind.Ghost,
                icon = R.drawable.ic_eye,
                loading = opening,
                enabled = ui.openingId == null,
            )
            HafiButton(
                text = "Open in another app",
                onClick = { vm.open(document, external = true) },
                kind = ButtonKind.Ghost,
                icon = R.drawable.ic_external_link,
                enabled = ui.openingId == null,
            )
            if (document.editable && !editing) {
                HafiButton(
                    text = "Add details",
                    onClick = {
                        vm.clearDetailsError()
                        editing = true
                    },
                    kind = ButtonKind.Ghost,
                    icon = R.drawable.ic_pen_line,
                )
            }
            HafiButton(
                text = "Remove",
                onClick = onRemove,
                kind = ButtonKind.Ghost,
                icon = R.drawable.ic_trash_2,
                enabled = ui.removingId == null,
            )
        }
        if (editing) {
            DetailsForm(
                document = document,
                services = services,
                saving = ui.savingId == document.id,
                error = ui.detailsError,
                onSave = { input -> vm.saveDetails(input) { editing = false } },
                onCancel = { editing = false },
            )
        }
    }
}

/** Status in words and an icon, never colour alone (web: StatusBadge). */
@Composable
private fun StatusBadge(document: WorkDocument) {
    val (icon, word, fill) = when {
        document.expired -> Triple(R.drawable.ic_clock, "Expired", Hafi.Surface2)
        document.status == DocumentStatus.Checked -> Triple(R.drawable.ic_badge_check, "Checked by AI", Hafi.AccentSoft)
        document.status == DocumentStatus.SelfDeclared -> Triple(R.drawable.ic_pen_line, "Self-declared", Hafi.Surface2)
        else -> Triple(R.drawable.ic_triangle_alert, "Couldn't read", Hafi.WarnSoft)
    }
    Row(
        modifier = Modifier
            .background(fill)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIcon(icon, size = 14.dp, tint = Hafi.Fg)
        Text(word, style = HafiType.Caption, color = Hafi.Fg)
    }
}

private fun linkedServices(document: WorkDocument, services: List<Pair<String, String>>): String? =
    document.categorySlugs
        .map { slug -> services.firstOrNull { it.first == slug }?.second ?: slug }
        .joinToString(", ")
        .takeIf { it.isNotEmpty() }

/** Details typed by the job seeker for a file the AI couldn't check (they count half). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsForm(
    document: WorkDocument,
    services: List<Pair<String, String>>,
    saving: Boolean,
    error: String?,
    onSave: (DocumentDetailsInput) -> Unit,
    onCancel: () -> Unit,
) {
    val id = document.id
    var title by rememberSaveable(id) { mutableStateOf(DocumentDetails.initialTitle(document)) }
    var issuer by rememberSaveable(id) { mutableStateOf(document.issuer.orEmpty()) }
    var year by rememberSaveable(id) { mutableStateOf(document.issuedYear?.toString().orEmpty()) }
    var expires by rememberSaveable(id) { mutableStateOf(DocumentDetails.initialExpiry(document)) }
    var slugs by rememberSaveable(id) { mutableStateOf(document.categorySlugs) }
    var problem by rememberSaveable(id) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HafiField(
            label = "Title",
            value = title,
            onValueChange = { title = it.take(DocumentDetails.MAX_TEXT) },
            placeholder = if (document.kind == DocumentKind.Cv) "CV" else "e.g. Electrical installation, Level 4",
            optional = document.kind == DocumentKind.Cv,
        )
        HafiField(
            label = "Issued by",
            value = issuer,
            onValueChange = { issuer = it.take(DocumentDetails.MAX_TEXT) },
            placeholder = "e.g. IPRC Kigali",
            optional = true,
        )
        HafiField(
            label = "Year",
            value = year,
            onValueChange = { year = it.filter(Char::isDigit).take(4) },
            placeholder = "2022",
            optional = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        HafiField(
            label = "Valid until (if any)",
            value = expires,
            onValueChange = { expires = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
            placeholder = "2027-06-30",
            optional = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (document.kind == DocumentKind.Certificate && services.isNotEmpty()) {
            GroupTitle("Which of your services is it for?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                services.forEach { (slug, name) ->
                    HafiChip(
                        text = name,
                        selected = slug in slugs,
                        onClick = { slugs = if (slug in slugs) slugs - slug else slugs + slug },
                    )
                }
            }
        }
        FormAlert(problem ?: error)
        Text(
            text = "Details you type yourself show as Self-declared and count half.",
            style = HafiType.Caption,
            color = Hafi.FgMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HafiButton(
                text = "Save details",
                onClick = {
                    DocumentDetails.input(document, title, issuer, year, expires, slugs)
                        .onSuccess {
                            problem = null
                            onSave(it)
                        }
                        .onFailure { problem = it.message }
                },
                loading = saving,
            )
            HafiButton("Cancel", onCancel, kind = ButtonKind.Ghost, enabled = !saving)
        }
    }
}

/** The details form's rules, matching the engine (core/providers/documents.ts updateDocumentDetails). */
object DocumentDetails {
    const val MAX_TEXT = 80
    private const val FIRST_YEAR = 1950
    private val datePattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** The engine's default titles are left blank so the job seeker types a real one. */
    fun initialTitle(document: WorkDocument): String =
        if (document.title == "Certificate" || document.title == "CV") "" else document.title

    fun initialExpiry(document: WorkDocument): String =
        document.expiresOn?.atZone(ZoneOffset.UTC)?.toLocalDate()?.toString().orEmpty()

    /** The request body, or the problem to fix first. */
    fun input(
        document: WorkDocument,
        title: String,
        issuer: String,
        year: String,
        expires: String,
        slugs: List<String>,
        thisYear: Int = Year.now().value,
    ): Result<DocumentDetailsInput> {
        val cleanTitle = title.replace(Regex("\\s+"), " ").trim()
            .ifEmpty { if (document.kind == DocumentKind.Cv) "CV" else "" }
        val issuedYear = year.trim().toIntOrNull()
        val problem = when {
            cleanTitle.length !in 2..MAX_TEXT -> "Give it a title (2–80 letters)."
            year.isNotBlank() && (issuedYear == null || issuedYear !in FIRST_YEAR..thisYear) ->
                "Enter the year it was issued ($FIRST_YEAR to $thisYear)."
            expires.isNotBlank() && !validDate(expires) -> "Write the date like 2027-06-30."
            else -> null
        }
        if (problem != null) return Result.failure(IllegalArgumentException(problem))
        return Result.success(
            DocumentDetailsInput(
                id = document.id,
                title = cleanTitle,
                issuer = issuer.replace(Regex("\\s+"), " ").trim().ifEmpty { null },
                issuedYear = issuedYear,
                expiresOn = expires.trim().ifEmpty { null },
                categorySlugs = if (document.kind == DocumentKind.Certificate) slugs else emptyList(),
            ),
        )
    }

    private fun validDate(text: String): Boolean =
        datePattern.matches(text) && runCatching { LocalDate.parse(text) }.isSuccess
}
