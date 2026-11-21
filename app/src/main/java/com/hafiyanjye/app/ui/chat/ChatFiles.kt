package com.hafiyanjye.app.ui.chat

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.hafiyanjye.app.data.chat.ChatLimits
import com.hafiyanjye.app.data.chat.ChatUpload
import com.hafiyanjye.app.data.chat.isAcceptedDocument
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A file picked on the phone, before it is read or resized. [uri] is a content Uri as text. */
data class PickedFile(val uri: String, val name: String, val mime: String, val size: Long) {
    val isImage: Boolean get() = mime.startsWith("image/")
}

/** The picked files split into those to send and what to tell the person about the rest (web composer onPick). */
data class PickResult(val sending: List<PickedFile>, val problem: String?)

/** Applies the web's per-pick limits: size per kind, and at most [ChatLimits.MAX_FILES_PER_PICK] at once. */
fun checkPicked(picked: List<PickedFile>): PickResult {
    fun tooLarge(file: PickedFile) =
        file.size > if (file.isImage) ChatLimits.MAX_RAW_PHOTO_BYTES else ChatLimits.MAX_FILE_BYTES
    val fitting = picked.filterNot(::tooLarge)
    val large = picked.filter(::tooLarge)
    val problems = mutableListOf<String>()
    if (large.size == 1) {
        problems += "\"${large.single().name}\" is too large (documents up to 10 MB), so it wasn't sent."
    } else if (large.size > 1) {
        problems += "${large.size} files are too large (documents up to 10 MB), so they weren't sent."
    }
    val most = ChatLimits.MAX_FILES_PER_PICK
    if (fitting.size > most) {
        problems += "You can send up to $most files at once. The first $most are on their way."
    }
    return PickResult(fitting.take(most), problems.takeIf { it.isNotEmpty() }?.joinToString(" "))
}

/** Name, type and size of a picked file, read from the system without opening it. */
internal suspend fun describePicked(context: Context, uri: Uri): PickedFile = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    var name: String? = null
    var size = 0L
    val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    resolver.query(uri, columns, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            name = cursor.getString(0)
            if (!cursor.isNull(1)) size = cursor.getLong(1)
        }
    }
    PickedFile(
        uri = uri.toString(),
        name = name?.takeIf { it.isNotBlank() } ?: "file",
        mime = resolver.getType(uri) ?: "application/octet-stream",
        size = size,
    )
}

/**
 * Reads a picked file for upload: photos are turned upright and resized to a
 * JPEG (longest side 2000, at most 4 MB, as the web does); documents are sent
 * as they are when they are a PDF, Word, Excel or PowerPoint file.
 */
internal suspend fun prepareUpload(context: Context, file: PickedFile): ChatUpload = withContext(Dispatchers.IO) {
    val uri = Uri.parse(file.uri)
    if (file.isImage) return@withContext preparePhoto(context, uri, file.name)
    require(isAcceptedDocument(file.name)) { "Send a photo, a PDF, or a Word, Excel or PowerPoint file." }
    val bytes = readAtMost(context, uri, ChatLimits.MAX_FILE_BYTES)
    ChatUpload(bytes, file.mime, file.name)
}

private fun readAtMost(context: Context, uri: Uri, limit: Long): ByteArray {
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Couldn't open this file. Choose it again.")
    return input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            require(output.size() <= limit) { "This file is too large (at most 10 MB)." }
        }
        require(output.size() > 0) { "This file is empty." }
        output.toByteArray()
    }
}

private fun preparePhoto(context: Context, uri: Uri, name: String): ChatUpload {
    val upright = decodeUpright(context, uri)
    try {
        val scale = minOf(1f, ChatLimits.PHOTO_MAX_SIDE.toFloat() / max(upright.width, upright.height))
        val width = max(1, (upright.width * scale).roundToInt())
        val height = max(1, (upright.height * scale).roundToInt())
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            // A white ground, so transparent PNGs do not turn black as JPEG.
            val canvas = Canvas(output)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(upright, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
            var quality = 85
            var bytes = encodeJpeg(output, quality)
            while (bytes.size > ChatLimits.PHOTO_MAX_BYTES && quality > 40) {
                quality -= 15
                bytes = encodeJpeg(output, quality)
            }
            require(bytes.size <= ChatLimits.PHOTO_MAX_BYTES) { "This photo is too large. Choose a smaller one." }
            val jpegName = name.substringBeforeLast('.').ifBlank { "photo" } + ".jpg"
            return ChatUpload(bytes, "image/jpeg", jpegName, width, height)
        } finally {
            output.recycle()
        }
    } finally {
        upright.recycle()
    }
}

private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
    return output.toByteArray()
}

/** Decodes near the output size, so a camera photo cannot fill the heap, and applies its rotation. */
private fun decodeUpright(context: Context, uri: Uri): Bitmap {
    val resolver = context.contentResolver
    val maxSide = ChatLimits.PHOTO_MAX_SIDE.toFloat()
    if (Build.VERSION.SDK_INT >= 28) {
        // ImageDecoder already applies the EXIF rotation.
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val scale = minOf(1f, maxSide / max(info.size.width, info.size.height))
            decoder.setTargetSize(
                max(1, (info.size.width * scale).roundToInt()),
                max(1, (info.size.height * scale).roundToInt()),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This photo couldn't be opened. Try a JPEG or PNG." }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IllegalArgumentException("This photo couldn't be opened. Try a JPEG or PNG.")
    val orientation = resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val matrix = orientationMatrix(orientation) ?: return decoded
    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (rotated !== decoded) decoded.recycle()
    return rotated
}

private fun orientationMatrix(orientation: Int): Matrix? {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        else -> return null
    }
    return matrix
}

/**
 * Writes a downloaded attachment to cache/chat/<message id>/<name> (the folder
 * res/xml/chat_file_paths.xml shares) and returns a Uri other apps may read.
 */
internal suspend fun cacheForSharing(
    context: Context,
    messageId: Int,
    name: String,
    bytes: ByteArray,
): Uri = withContext(Dispatchers.IO) {
    val folder = File(context.cacheDir, "chat/$messageId").apply { mkdirs() }
    val safeName = name.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").ifBlank { "file" }
    val file = File(folder, safeName)
    file.writeBytes(bytes)
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

/** Opens a cached attachment in an app that can show it; false when the phone has none. */
internal fun openCachedFile(context: Context, uri: Uri, mime: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** Offers a cached attachment to other apps (save to Files, send on). */
internal fun shareCachedFile(context: Context, uri: Uri, mime: String, name: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(name, uri)
    val chooser = Intent.createChooser(send, "Share $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/** Opens the dialler with the number filled in; nothing is called until the person taps call. */
internal fun dial(context: Context, phone: String): Boolean {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
