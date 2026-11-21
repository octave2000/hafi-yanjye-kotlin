package com.hafiyanjye.app.ui.onboarding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hafiyanjye.app.R
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.theme.HafiType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One rendered page of a document and how many pages it has. */
private class RenderedPage(val bitmap: Bitmap, val pageCount: Int)

/**
 * Shows the job seeker's own document inside the app: a PDF page by page, or
 * a photo, with zoom. The file was downloaded with the session; nothing leaves
 * the app unless they choose "Open in another app".
 */
@Composable
internal fun DocumentViewer(title: String, bytes: ByteArray, onOpenElsewhere: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var page by remember(bytes) { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var attempt by remember { mutableIntStateOf(0) }
    var rendered by remember { mutableStateOf<RenderedPage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bytes, page, attempt) {
        error = null
        rendered = null
        try {
            rendered = withContext(Dispatchers.IO) { render(context, bytes, page) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "This file couldn't be shown here. Try \"Open in another app\"."
        }
    }
    DisposableEffect(rendered) {
        val shown = rendered
        onDispose { shown?.bitmap?.recycle() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DetailScaffold(
            title = title,
            onBack = onDismiss,
            actions = {
                HafiIconButton(R.drawable.ic_external_link, "Open in another app", onOpenElsewhere)
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ViewerControls(
                    zoom = zoom,
                    onZoom = { zoom = it },
                    page = page,
                    pageCount = rendered?.pageCount ?: 1,
                    onPage = { page = it },
                )
                val shown = rendered
                when {
                    error != null -> ErrorPanel(
                        message = error.orEmpty(),
                        onRetry = { attempt++ },
                        modifier = Modifier.padding(16.dp),
                    )
                    shown == null -> Skeleton(
                        Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                    )
                    else -> PageImage(shown.bitmap, "$title, page ${page + 1}", zoom, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ViewerControls(zoom: Float, onZoom: (Float) -> Unit, page: Int, pageCount: Int, onPage: (Int) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HafiButton(
            text = "Zoom out",
            onClick = { onZoom((zoom - ZOOM_STEP).coerceAtLeast(1f)) },
            kind = ButtonKind.Outline,
            enabled = zoom > 1f,
        )
        HafiButton(
            text = "Zoom in",
            onClick = { onZoom((zoom + ZOOM_STEP).coerceAtMost(MAX_ZOOM)) },
            kind = ButtonKind.Outline,
            enabled = zoom < MAX_ZOOM,
        )
    }
    if (pageCount > 1) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HafiButton("Previous page", { onPage(page - 1) }, kind = ButtonKind.Outline, enabled = page > 0)
            HafiButton("Next page", { onPage(page + 1) }, kind = ButtonKind.Outline, enabled = page + 1 < pageCount)
        }
        Text(
            text = "Page ${page + 1} of $pageCount",
            style = HafiType.Caption,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun PageImage(bitmap: Bitmap, description: String, zoom: Float, modifier: Modifier) {
    val width = LocalConfiguration.current.screenWidthDp.dp
    Box(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = description,
            modifier = Modifier
                .width(width * zoom)
                .aspectRatio(bitmap.width.toFloat() / bitmap.height),
        )
    }
}

/** PdfRenderer needs a seekable file, so the PDF goes to a temporary file first. */
private fun render(context: Context, bytes: ByteArray, page: Int): RenderedPage {
    if (!isPdf(bytes)) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IllegalStateException("Not an image")
        return RenderedPage(bitmap, 1)
    }
    val temp = File.createTempFile("document-", ".pdf", context.cacheDir)
    try {
        temp.writeBytes(bytes)
        ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { pdf ->
                pdf.openPage(page.coerceIn(0, pdf.pageCount - 1)).use { source ->
                    val height = (PAGE_WIDTH.toDouble() * source.height / source.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(PAGE_WIDTH, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    source.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return RenderedPage(bitmap, pdf.pageCount)
                }
            }
        }
    } finally {
        temp.delete()
    }
}

private const val PAGE_WIDTH = 1400
private const val ZOOM_STEP = 0.5f
private const val MAX_ZOOM = 3f
