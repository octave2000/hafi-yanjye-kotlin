package com.hafiyanjye.app.ui.listings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.HafiSheet
import com.hafiyanjye.app.ui.kit.ProgressBar
import com.hafiyanjye.app.ui.kit.RemoteImage
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import kotlin.math.roundToInt

/** What the photo grid can do; the ViewModel does the work. */
internal class PhotoActions(
    val add: (List<Uri>) -> Unit,
    val move: (index: Int, by: Int) -> Unit,
    val makeCover: (String) -> Unit,
    val remove: (String) -> Unit,
    val retry: (Long) -> Unit,
    val drop: (Long) -> Unit,
)

/** One square in the grid: a kept photo, one on its way, or the add button. */
private sealed interface PhotoTile {
    data class Kept(val url: String, val index: Int) : PhotoTile

    data class Sending(val upload: PhotoUpload) : PhotoTile

    data object Add : PhotoTile
}

/**
 * Photos: several picked at once, uploaded one by one with their own progress,
 * retry for those that fail, reorder, cover and remove. Up to [MAX_LISTING_PHOTOS].
 */
@Composable
internal fun PhotosSection(
    photos: List<String>,
    uploads: List<PhotoUpload>,
    error: String?,
    enabled: Boolean,
    actions: PhotoActions,
    modifier: Modifier = Modifier,
) {
    val room = MAX_LISTING_PHOTOS - photos.size - uploads.size
    val pickMany = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_LISTING_PHOTOS),
    ) { picked -> actions.add(picked) }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) actions.add(listOf(picked))
    }
    val openPicker = {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        // The multiple picker refuses a limit of one.
        if (room > 1) pickMany.launch(request) else pickOne.launch(request)
    }
    var actionsFor by rememberSaveable { mutableStateOf<String?>(null) }

    val tiles = photos.mapIndexed { index, url -> PhotoTile.Kept(url, index) } +
        uploads.map { PhotoTile.Sending(it) } +
        if (room > 0 && enabled) listOf(PhotoTile.Add) else emptyList()

    FormSection(
        title = "Photos",
        description = "Up to $MAX_LISTING_PHOTOS. The first one is the cover. " +
            "Listings with photos get far more messages.",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { tile ->
                        val cell = Modifier
                            .weight(1f)
                            .aspectRatio(4f / 3f)
                        when (tile) {
                            is PhotoTile.Kept -> KeptPhoto(tile, photos.size, enabled, cell) { actionsFor = tile.url }
                            is PhotoTile.Sending -> SendingPhoto(tile.upload, actions, cell)
                            PhotoTile.Add -> AddPhotos(openPicker, cell)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        FieldMessage(error = error)
    }

    val chosen = actionsFor
    val index = photos.indexOf(chosen)
    if (chosen != null && index >= 0) {
        PhotoActionsSheet(
            index = index,
            count = photos.size,
            onDismiss = { actionsFor = null },
            onMove = { by -> actions.move(index, by) },
            onCover = {
                actions.makeCover(chosen)
                actionsFor = null
            },
            onRemove = {
                actions.remove(chosen)
                actionsFor = null
            },
        )
    }
}

@Composable
private fun KeptPhoto(
    tile: PhotoTile.Kept,
    count: Int,
    enabled: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val name = if (tile.index == 0) "Cover photo" else "Photo ${tile.index + 1} of $count"
    Box(
        modifier = modifier
            .background(Hafi.Surface2)
            .border(1.dp, Hafi.Line)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Photo options", onClick = onOpen)
            .semantics { contentDescription = name },
    ) {
        RemoteImage(tile.url, null, Modifier.fillMaxSize())
        if (tile.index == 0) {
            Text(
                text = "Cover",
                style = HafiType.Micro,
                color = Hafi.InkFg,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Hafi.Ink)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clearAndSetSemantics { },
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(Hafi.Surface)
                .border(1.dp, Hafi.Line)
                .padding(4.dp)
                .clearAndSetSemantics { },
        ) {
            HIcon(R.drawable.ic_pencil, size = 16.dp, tint = Hafi.Fg)
        }
    }
}

@Composable
private fun SendingPhoto(upload: PhotoUpload, actions: PhotoActions, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(if (upload.failed) Hafi.DangerSoft else Hafi.Surface2)
            .border(1.dp, if (upload.failed) Hafi.Danger else Hafi.LineStrong)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val error = upload.error
        if (error == null) {
            val percent = (upload.progress * 100).roundToInt()
            Text(
                text = if (percent == 0) "Preparing…" else "Uploading… $percent%",
                style = HafiType.Caption,
                color = Hafi.Fg,
            )
            ProgressBar(upload.progress, Modifier.fillMaxWidth())
        } else {
            Text(
                text = error,
                style = HafiType.Caption,
                color = Hafi.Danger,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                HafiIconButton(R.drawable.ic_refresh_cw, "Try this photo again", { actions.retry(upload.key) })
                HafiIconButton(R.drawable.ic_x, "Remove this photo", { actions.drop(upload.key) }, tint = Hafi.Danger)
            }
        }
    }
}

@Composable
private fun AddPhotos(onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(Hafi.Surface)
            .border(1.dp, Hafi.LineStrong)
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HIcon(R.drawable.ic_image_plus, size = 24.dp, tint = Hafi.FgMuted)
        Text("Add photos", style = HafiType.Label, color = Hafi.Fg)
    }
}

@Composable
private fun PhotoActionsSheet(
    index: Int,
    count: Int,
    onDismiss: () -> Unit,
    onMove: (Int) -> Unit,
    onCover: () -> Unit,
    onRemove: () -> Unit,
) {
    HafiSheet(onDismiss = onDismiss, title = if (index == 0) "Cover photo" else "Photo ${index + 1} of $count") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (index > 0) {
                HafiButton(
                    "Make it the cover",
                    onCover,
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.Outline,
                    icon = R.drawable.ic_star,
                )
                HafiButton(
                    "Move earlier",
                    { onMove(-1) },
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.Outline,
                    icon = R.drawable.ic_arrow_left,
                )
            }
            if (index < count - 1) {
                HafiButton(
                    "Move later",
                    { onMove(1) },
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.Outline,
                    icon = R.drawable.ic_arrow_right,
                )
            }
            HafiButton(
                "Remove photo",
                onRemove,
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Destructive,
                icon = R.drawable.ic_trash_2,
            )
        }
    }
}
