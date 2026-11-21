package com.hafiyanjye.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.pings.PriceUnit
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiChip
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.HafiTextArea
import com.hafiyanjye.app.ui.kit.RemoteImage
import com.hafiyanjye.app.ui.kit.SectionCard
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** Step 3: experience, starting price, bio, phone, languages, work photos and documents. */
@Composable
internal fun AboutYouStep(
    ui: WorkSetupUi,
    vm: WorkSetupViewModel,
    onBioWritten: (String) -> Unit,
    onOpenCredentials: () -> Unit,
) {
    val form = ui.form
    HafiField(
        label = "Years of experience",
        value = form.years,
        onValueChange = { typed ->
            vm.edit(FormField.Years) { it.copy(years = WorkForm.digits(typed, WorkForm.MAX_YEARS_DIGITS)) }
        },
        placeholder = "e.g. 6",
        error = ui.errors[FormField.Years],
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    HafiField(
        label = "Starting price (RWF)",
        value = form.price,
        onValueChange = { typed ->
            vm.edit(FormField.Price) { it.copy(price = WorkForm.digits(typed, WorkForm.MAX_PRICE_DIGITS)) }
        },
        placeholder = "5000",
        hint = "You agree the final price with each client.",
        error = ui.errors[FormField.Price],
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    PriceUnitPicker(form.priceUnit) { unit -> vm.edit { it.copy(priceUnit = unit) } }
    BioField(ui = ui, vm = vm, onBioWritten = onBioWritten)
    HafiField(
        label = "Phone number",
        value = form.phone,
        onValueChange = { typed -> vm.edit(FormField.Phone) { it.copy(phone = typed.take(MAX_PHONE)) } },
        placeholder = "078…",
        hint = "Only shared with a client after they choose you. e.g. 078 123 4567",
        error = ui.errors[FormField.Phone],
        autofill = ContentType.PhoneNumber,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    LanguagePicker(form.languages) { languages -> vm.edit { it.copy(languages = languages) } }
    WorkPhotoGrid(ui = ui, vm = vm)
    if (ui.editing) {
        SectionCard(title = "CV and certificates (optional)") {
            Text(
                text = "Certificates, diplomas or your CV. Our AI reads each file and marks it Checked. " +
                    "No documents? You still get jobs: they're worth up to 3 of 100 ranking points.",
                style = HafiType.Body,
                color = Hafi.FgMuted,
            )
            HafiButton(
                text = "Manage CV and certificates",
                onClick = onOpenCredentials,
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_file_text,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriceUnitPicker(selected: PriceUnit, onSelect: (PriceUnit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupTitle("Price is per")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceUnit.entries.forEach { unit ->
                HafiChip(text = unit.label, selected = unit == selected, onClick = { onSelect(unit) })
            }
        }
    }
}

@Composable
private fun BioField(ui: WorkSetupUi, vm: WorkSetupViewModel, onBioWritten: (String) -> Unit) {
    val bio = ui.form.bio
    val short = WorkForm.BIO_MIN - bio.trim().length
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HafiTextArea(
            label = "Bio (at least ${WorkForm.BIO_MIN} characters)",
            value = bio,
            onValueChange = { typed -> vm.edit(FormField.Bio) { it.copy(bio = typed.take(WorkForm.BIO_MAX)) } },
            placeholder = "I've fixed wiring and installed solar in Kimironko and Remera for 6 years. " +
                "I come with my own tools and give a clear price before starting.",
            hint = if (short > 0) {
                "$short more characters for a full score"
            } else {
                "Great — job providers read this first."
            },
            error = ui.errors[FormField.Bio],
            enabled = !ui.writingBio,
            minLines = 5,
            maxLength = WorkForm.BIO_MAX,
        )
        HafiButton(
            text = "Write it for me",
            onClick = { vm.writeBio(onBioWritten) },
            kind = ButtonKind.Secondary,
            icon = R.drawable.ic_pen_line,
            loading = ui.writingBio,
            loadingText = "Writing…",
        )
    }
}

/** Kinyarwanda, English, Français, Kiswahili, plus up to five typed by name. */
@Composable
private fun LanguagePicker(languages: List<String>, onChange: (List<String>) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val others = Languages.others(languages)
    val full = others.size >= Languages.MAX_OTHER
    val close = {
        adding = false
        draft = ""
        error = null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupTitle("Languages you speak")
        TileGrid { cell ->
            Languages.known.forEach { (code, name) ->
                ToggleTile(
                    title = name,
                    selected = code in languages,
                    onToggle = { onChange(Languages.toggle(languages, code)) },
                    modifier = cell,
                )
            }
            others.forEach { name ->
                ToggleTile(
                    title = name,
                    selected = true,
                    onToggle = { onChange(languages - name) },
                    modifier = cell.semantics { contentDescription = "Remove $name" },
                )
            }
        }
        if (adding && !full) {
            HafiField(
                label = "Language name",
                value = draft,
                onValueChange = {
                    draft = it.take(Languages.MAX_LENGTH)
                    error = null
                },
                placeholder = "e.g. Lingala, Luganda, Kirundi",
                error = error,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HafiButton(
                    text = "Add",
                    onClick = {
                        Languages.add(languages, draft)
                            .onSuccess { updated ->
                                onChange(updated)
                                close()
                            }
                            .onFailure { error = it.message }
                    },
                    enabled = draft.isNotBlank(),
                )
                HafiButton(text = "Cancel", onClick = close, kind = ButtonKind.Outline)
            }
        } else if (!full) {
            HafiButton(
                text = "Other language",
                onClick = { adding = true },
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_plus,
            )
        }
        if (full) {
            Text(
                text = "You can add up to ${Languages.MAX_OTHER} other languages.",
                style = HafiType.Caption,
                color = Hafi.FgMuted,
            )
        }
    }
}

/** Three columns of square work photos; uploads show as placeholders until stored. */
@Composable
private fun WorkPhotoGrid(ui: WorkSetupUi, vm: WorkSetupViewModel) {
    val context = LocalContext.current
    val photos = ui.form.photos
    val room = WorkForm.MAX_PHOTOS - photos.size - ui.photosUploading
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(WorkForm.MAX_PHOTOS),
    ) { uris ->
        vm.addWorkPhotos(
            uris.map { uri ->
                suspend { readPhoto(context, uri, UploadLimits.WORK_PHOTO_SIDE, UploadLimits.MAX_PHOTO_BYTES) }
            },
        )
    }
    val slots = photos.size + ui.photosUploading + if (room > 0) 1 else 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupTitle("Photos of your work", "(${WorkForm.FULL_PHOTOS} or more earn full points)")
        (0 until slots).chunked(PHOTO_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { index ->
                    val cell = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                    when {
                        index < photos.size -> SavedPhoto(photos[index], index, photos.size, cell) {
                            vm.removeWorkPhoto(photos[index])
                        }
                        index < photos.size + ui.photosUploading -> UploadingPhoto(index, cell)
                        else -> HafiButton(
                            text = "Add photos",
                            onClick = { launcher.launch(PickVisualMediaRequest(ImageOnly)) },
                            modifier = cell,
                            kind = ButtonKind.Outline,
                            icon = R.drawable.ic_image_plus,
                        )
                    }
                }
                repeat(PHOTO_COLUMNS - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        val photoError = ui.errors[FormField.Photos]
        val missing = WorkForm.FULL_PHOTOS - photos.size
        Text(
            text = photoError ?: if (missing <= 0) {
                "${photos.size} of ${WorkForm.MAX_PHOTOS} photos. Full points."
            } else {
                "${photos.size} of ${WorkForm.MAX_PHOTOS} photos. Add $missing more for full points."
            },
            style = HafiType.Caption,
            color = if (photoError != null) Hafi.Danger else Hafi.FgMuted,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun SavedPhoto(path: String, index: Int, total: Int, modifier: Modifier, onRemove: () -> Unit) {
    Box(modifier = modifier) {
        RemoteImage(path, "Your work, ${index + 1} of $total", Modifier.fillMaxSize())
        HafiIconButton(
            icon = R.drawable.ic_x,
            label = "Remove work photo ${index + 1}",
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .background(Hafi.Surface),
            tint = Hafi.Fg,
        )
    }
}

@Composable
private fun UploadingPhoto(index: Int, modifier: Modifier) {
    Box(
        modifier = modifier.semantics { contentDescription = "Uploading photo ${index + 1}" },
        contentAlignment = Alignment.Center,
    ) {
        Skeleton(Modifier.fillMaxSize())
        CircularProgressIndicator(color = Hafi.Accent, strokeWidth = 2.dp)
    }
}

private const val PHOTO_COLUMNS = 3
private const val MAX_PHONE = 20
