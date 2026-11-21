package com.hafiyanjye.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.profile.CropView
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.HafiSlider
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** The engine refuses anything larger (web/src/lib/upload-image.ts). */
private const val MAX_BYTES = 1_500_000

/** Plenty for a 512 px result at 4x zoom, and small enough to hold in memory on any phone. */
private const val MAX_DECODED_SIDE = 2048

/** Each "Move" action for screen readers shifts the photo this fraction of the frame. */
private const val NUDGE = 0.05f

/** Opens a picked photo upright, scaled down so its longest side is at most [MAX_DECODED_SIDE]. */
private suspend fun decodePhoto(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder applies the camera's orientation itself.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_DECODED_SIDE) {
                val k = MAX_DECODED_SIDE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * k).roundToInt().coerceAtLeast(1),
                    (info.size.height * k).roundToInt().coerceAtLeast(1),
                )
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODED_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("unreadable photo")
        val degrees = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (degrees == 0f) {
            raw
        } else {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true)
        }
    }
}

/** A square JPEG of what the frame shows, small enough for the engine. */
private suspend fun renderCrop(photo: Bitmap, view: CropView): ByteArray = withContext(Dispatchers.Default) {
    val side = CropView.OUTPUT
    val output = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    // Transparent PNGs become white, not black.
    canvas.drawColor(android.graphics.Color.WHITE)
    val scale = view.scale(photo.width, photo.height) * side
    canvas.translate(side / 2f + view.x * side, side / 2f + view.y * side)
    canvas.rotate(view.turn * 90f)
    canvas.scale(scale, scale)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    canvas.drawBitmap(photo, -photo.width / 2f, -photo.height / 2f, paint)

    fun encode(quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        output.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
    var bytes = encode(85)
    if (bytes.size > MAX_BYTES) bytes = encode(70)
    output.recycle()
    check(bytes.size <= MAX_BYTES) { "This photo is too large." }
    bytes
}

/** Keeps a [CropView] across rotation. */
private val CropViewSaver = listSaver<CropView, Float>(
    save = { listOf(it.zoom, it.x, it.y, it.turn.toFloat()) },
    restore = { CropView(zoom = it[0], x = it[1], y = it[2], turn = it[3].toInt()) },
)

/**
 * Rule-of-thirds lines and the face circle (web: photo-crop-dialog), only to
 * help framing: the saved picture is the whole square.
 */
private fun DrawScope.drawFramingGuide(side: Float) {
    val guide = Hafi.Surface.copy(alpha = 0.45f)
    val stroke = 1.dp.toPx()
    for (i in 1..2) {
        val at = side * i / 3f
        drawLine(guide, Offset(at, 0f), Offset(at, size.height), strokeWidth = stroke)
        drawLine(guide, Offset(0f, at), Offset(size.width, at), strokeWidth = stroke)
    }
    val inset = 12.dp.toPx()
    val radius = side / 2f - inset
    val circle = Path().apply { addOval(Rect(center, radius)) }
    // Dim what falls outside the circle, as the web does, so the face is framed where avatars show it.
    clipPath(circle, clipOp = ClipOp.Difference) {
        drawRect(Hafi.Fg.copy(alpha = 0.32f))
    }
    drawCircle(Hafi.Surface.copy(alpha = 0.7f), radius = radius, center = center, style = Stroke(stroke))
}

/**
 * Frame a new profile photo before it is uploaded: drag to move it, pinch or
 * use the slider to zoom, and turn it. [onSave] gets a square JPEG
 * ([CropView.OUTPUT] px) of what the frame shows. Closing without saving
 * discards the photo.
 */
@Composable
fun PhotoCropDialog(uri: Uri, onCancel: () -> Unit, onSave: (ByteArray) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    // Saved, so turning the phone keeps the framing while the photo is decoded again.
    var view by rememberSaveable(uri, stateSaver = CropViewSaver) { mutableStateOf(CropView()) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        try {
            photo = decodePhoto(context, uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "This photo couldn't be opened. Try a JPEG or PNG."
        }
    }
    DisposableEffect(uri) { onDispose { photo = null } }

    val loaded = photo
    fun update(change: (CropView) -> CropView) {
        if (loaded != null && !saving) view = change(view).clamped(loaded.width, loaded.height)
    }
    val canMove = loaded != null && view.maxOffset(loaded.width, loaded.height).let { it.first > 0f || it.second > 0f }

    Dialog(
        onDismissRequest = { if (!saving) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(Hafi.Surface)
                .border(1.dp, Hafi.Line)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Frame your photo", style = HafiType.Heading, modifier = Modifier.semantics { heading() })
                Text(
                    "Center your face so people recognise you when you meet.",
                    style = HafiType.Body,
                    color = Hafi.FgMuted,
                )
            }

            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Hafi.Surface2)
                    .border(1.dp, Hafi.Line)
                    .clipToBounds()
                    .pointerInput(loaded) {
                        if (loaded == null) return@pointerInput
                        detectTransformGestures { _, pan, zoom, _ ->
                            update {
                                it.copy(
                                    zoom = it.zoom * zoom,
                                    x = it.x + pan.x / size.width,
                                    y = it.y + pan.y / size.height,
                                )
                            }
                        }
                    }
                    .semantics {
                        contentDescription = "Photo preview. Drag to move it, pinch to zoom."
                        if (loaded != null) {
                            fun move(label: String, dx: Float, dy: Float) = CustomAccessibilityAction(label) {
                                update { it.copy(x = it.x + dx, y = it.y + dy) }
                                true
                            }
                            customActions = listOf(
                                move("Move photo left", -NUDGE, 0f),
                                move("Move photo right", NUDGE, 0f),
                                move("Move photo up", 0f, -NUDGE),
                                move("Move photo down", 0f, NUDGE),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (loaded != null) {
                    val image = remember(loaded) { loaded.asImageBitmap() }
                    Canvas(Modifier.fillMaxSize()) {
                        val side = size.minDimension
                        val scale = view.scale(loaded.width, loaded.height) * side
                        withTransform({
                            translate(size.width / 2f + view.x * side, size.height / 2f + view.y * side)
                            rotate(view.turn * 90f, pivot = Offset.Zero)
                            scale(scale, scale, pivot = Offset.Zero)
                        }) {
                            drawImage(
                                image = image,
                                dstOffset = IntOffset(-loaded.width / 2, -loaded.height / 2),
                                filterQuality = FilterQuality.Medium,
                            )
                        }
                        drawFramingGuide(side)
                    }
                } else {
                    Text(
                        error ?: "Opening photo…",
                        style = HafiType.Body,
                        color = if (error != null) Hafi.Danger else Hafi.FgMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            Text(
                (if (canMove) "Drag to move." else "Zoom in to move the photo.") + " The square is what people see.",
                style = HafiType.Caption,
                color = Hafi.FgMuted,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HafiIconButton(
                    R.drawable.ic_minus,
                    "Zoom out",
                    onClick = { update { it.copy(zoom = it.zoom - 0.25f) } },
                    enabled = loaded != null && !saving && view.zoom > CropView.MIN_ZOOM,
                )
                HafiSlider(
                    value = view.zoom,
                    onValueChange = { zoom -> update { it.copy(zoom = zoom) } },
                    valueRange = CropView.MIN_ZOOM..CropView.MAX_ZOOM,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Zoom" },
                    enabled = loaded != null && !saving,
                )
                HafiIconButton(
                    R.drawable.ic_plus,
                    "Zoom in",
                    onClick = { update { it.copy(zoom = it.zoom + 0.25f) } },
                    enabled = loaded != null && !saving && view.zoom < CropView.MAX_ZOOM,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HafiButton(
                    text = "Turn left",
                    onClick = { update { it.turned(-1) } },
                    modifier = Modifier.weight(1f),
                    kind = ButtonKind.Outline,
                    icon = R.drawable.ic_rotate_ccw,
                    enabled = loaded != null && !saving,
                )
                HafiButton(
                    text = "Turn right",
                    onClick = { update { it.turned(1) } },
                    modifier = Modifier.weight(1f),
                    kind = ButtonKind.Outline,
                    icon = R.drawable.ic_rotate_cw,
                    enabled = loaded != null && !saving,
                )
            }
            HafiButton(
                text = "Reset",
                onClick = { view = CropView() },
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Ghost,
                enabled = loaded != null && !saving && view != CropView(),
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HafiButton(
                    text = "Cancel",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    kind = ButtonKind.Outline,
                    enabled = !saving,
                )
                HafiButton(
                    text = "Use this photo",
                    onClick = {
                        if (loaded != null && !saving) {
                            saving = true
                            scope.launch {
                                try {
                                    onSave(renderCrop(loaded, view))
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error = failure.message?.takeIf { failure is IllegalStateException }
                                        ?: "Couldn't prepare the photo."
                                    photo = null
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = loaded != null && !saving,
                    loading = saving,
                    loadingText = "Saving…",
                )
            }
        }
    }
}
