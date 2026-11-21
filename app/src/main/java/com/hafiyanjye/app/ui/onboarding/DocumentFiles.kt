package com.hafiyanjye.app.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.hafiyanjye.app.data.onboarding.WorkDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** PDFs start with these bytes; anything else the engine stores here is a JPEG photo. */
internal fun isPdf(bytes: ByteArray): Boolean =
    bytes.size >= PDF_MAGIC.size && PDF_MAGIC.indices.all { bytes[it] == PDF_MAGIC[it] }

/**
 * Saves a downloaded document in cache/documents (shared through the app's
 * FileProvider, see res/xml/chat_file_paths.xml) and asks another app to show
 * it. Fails with a readable message when the phone has no such app.
 */
internal suspend fun openInAnotherApp(context: Context, document: WorkDocument, bytes: ByteArray): Result<Unit> {
    val pdf = isPdf(bytes)
    val mime = if (pdf) "application/pdf" else "image/jpeg"
    val uri = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, "documents").apply { mkdirs() }
        // One file per document: opening it again overwrites, so the cache never grows.
        val file = File(folder, "document-${document.id}.${if (pdf) "pdf" else "jpg"}")
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        Result.success(Unit)
    } catch (_: ActivityNotFoundException) {
        Result.failure(IllegalStateException("No app on this phone can open this file. Use View instead."))
    }
}

private val PDF_MAGIC = "%PDF".toByteArray(Charsets.US_ASCII)
