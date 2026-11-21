package com.hafiyanjye.app.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.pings.WorkCategory
import com.hafiyanjye.app.data.profile.CropView
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.kit.Avatar
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.kit.categoryIcon
import com.hafiyanjye.app.ui.profile.PhotoCropDialog
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** Step 1: profile photo, the name job providers see, and up to three services. */
@Composable
internal fun WhatYouDoStep(
    ui: WorkSetupUi,
    picture: String?,
    categories: Load<List<WorkCategory>>,
    vm: WorkSetupViewModel,
    onRetryCategories: () -> Unit,
) {
    ProfilePhotoPicker(
        picture = picture,
        name = ui.form.name,
        action = ui.avatarAction,
        error = ui.avatarError,
        onCropped = vm::uploadAvatar,
        onRemove = vm::removeAvatar,
    )
    HafiField(
        label = "Your name or business name",
        value = ui.form.name,
        onValueChange = { typed -> vm.edit(FormField.Name) { it.copy(name = typed.take(WorkForm.NAME_LENGTH.last)) } },
        hint = "This is what job providers see. e.g. Jean Bosco Electric",
        error = ui.errors[FormField.Name],
        autofill = ContentType.PersonFullName,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GroupTitle("Services you offer", "(up to ${WorkForm.MAX_SERVICES})")
        when (categories) {
            Load.Loading -> TileGrid { cell -> repeat(SKELETON_TILES) { Skeleton(cell.height(64.dp)) } }
            is Load.Failed -> {
                Text("Couldn't load services.", style = HafiType.Label, color = Hafi.Danger)
                HafiButton("Try again", onRetryCategories, kind = ButtonKind.Outline, icon = R.drawable.ic_refresh_cw)
            }
            is Load.Ready -> ServicePicker(ui = ui, categories = categories.value, vm = vm)
        }
        ui.errors[FormField.Services]?.let { ErrorLine(it) }
    }
}

/** The account photo: framed square in the crop dialog, uploaded at once (the engine saves it). */
@Composable
private fun ProfilePhotoPicker(
    picture: String?,
    name: String,
    action: AvatarAction?,
    error: String?,
    onCropped: (ByteArray, Int) -> Unit,
    onRemove: () -> Unit,
) {
    var picked by rememberSaveable { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) picked = uri
    }
    val status = when {
        error != null -> error
        action == AvatarAction.Upload -> "Uploading…"
        action == AvatarAction.Remove -> "Removing…"
        picture != null -> "Clients see this when you're chosen."
        else -> "A clear photo of your face builds trust (+15 points)."
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            Avatar(name, picture, size = 80.dp)
            if (action != null) CircularProgressIndicator(color = Hafi.Accent, strokeWidth = 2.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Profile photo", style = HafiType.BodyStrong)
            Text(
                text = status,
                style = HafiType.Caption,
                color = if (error != null) Hafi.Danger else Hafi.FgMuted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HafiButton(
                    text = if (picture != null) "Change photo" else "Add a photo",
                    onClick = {
                        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    kind = ButtonKind.Outline,
                    icon = R.drawable.ic_camera,
                    enabled = action == null,
                )
                if (picture != null) {
                    HafiButton(
                        text = "Remove",
                        onClick = onRemove,
                        kind = ButtonKind.Ghost,
                        icon = R.drawable.ic_trash_2,
                        enabled = action == null,
                    )
                }
            }
        }
    }
    picked?.let { uri ->
        PhotoCropDialog(
            uri = uri,
            onCancel = { picked = null },
            onSave = { jpeg ->
                picked = null
                onCropped(jpeg, CropView.OUTPUT)
            },
        )
    }
}

/**
 * Searchable service tiles (web: CategoryMultiSelect). Searching also finds
 * services others registered; when nothing fits, the job seeker adds their own.
 */
@Composable
private fun ServicePicker(ui: WorkSetupUi, categories: List<WorkCategory>, vm: WorkSetupViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val picked = ui.form.services
    val full = picked.size >= WorkForm.MAX_SERVICES
    val shown = ServiceSearch.filter(categories, query, picked)
    val typed = ServiceSearch.typed(query)
    val canAdd = ServiceSearch.canAdd(query, shown)
    val add = { vm.addService(query) { query = "" } }

    HafiField(
        label = "Search services or type yours",
        value = query,
        onValueChange = {
            query = it
            vm.clearServiceError()
        },
        placeholder = "plumber, umuzamu, nanny…",
        leadingIcon = R.drawable.ic_search,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (shown.isEmpty() && canAdd && !full) add() }),
    )
    val count = buildString {
        append("${picked.size} of ${WorkForm.MAX_SERVICES} chosen")
        if (full) append(" · remove one to pick another")
        if (!full && query.isBlank()) append(" · not in the list? Type it above")
    }
    Text(
        text = count,
        style = HafiType.Caption,
        color = if (ui.errors[FormField.Services] != null && picked.isEmpty()) Hafi.Danger else Hafi.FgMuted,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    ui.serviceNotice?.let { notice ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            HIcon(R.drawable.ic_check, size = 16.dp, tint = Hafi.AccentStrong)
            Text(notice, style = HafiType.Caption)
        }
    }
    if (shown.isNotEmpty()) {
        TileGrid { cell ->
            shown.forEach { category ->
                val selected = category.slug in picked
                ToggleTile(
                    title = category.nameEn,
                    subtitle = if (category.listed) category.nameRw else "${category.nameRw} · new",
                    selected = selected,
                    onToggle = { vm.toggleService(category.slug) },
                    icon = categoryIcon(category.icon),
                    enabled = selected || !full,
                    modifier = cell,
                )
            }
        }
    } else if (query.isNotBlank()) {
        Text("No service matches “$typed” yet.", style = HafiType.Body, color = Hafi.FgMuted)
    }
    if (canAdd) {
        HafiButton(
            text = if (ui.addingService) "Adding “$typed”…" else "Add “$typed” as my service",
            onClick = add,
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.Outline,
            icon = R.drawable.ic_plus,
            enabled = !full,
            loading = ui.addingService,
        )
        Text(
            text = if (full) {
                "Remove a service first: you can offer up to ${WorkForm.MAX_SERVICES}."
            } else {
                "We tidy up the name and add it in Kinyarwanda and French, so people can search for it."
            },
            style = HafiType.Caption,
            color = Hafi.FgMuted,
        )
    }
    ui.serviceError?.let { ErrorLine(it) }
}

/** A field-level error read out at once, with an icon so it isn't colour alone. */
@Composable
internal fun ErrorLine(message: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        HIcon(R.drawable.ic_circle_alert, size = 16.dp, tint = Hafi.Danger)
        Text(message, style = HafiType.Caption, color = Hafi.Danger)
    }
}

private const val SKELETON_TILES = 6
