package com.hafiyanjye.app.ui.onboarding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** A photo resized to JPEG, with the size the engine stores alongside it. */
class SizedPhoto(val bytes: ByteArray, val width: Int, val height: Int)

/** A CV or certificate ready to send: a PDF as it is, or a photo as JPEG. */
class DocumentFile(val bytes: ByteArray, val contentType: String)

/** Limits shared with the engine and the web (lib/upload-image.ts, lib/upload-document.ts). */
object UploadLimits {
    /** The engine refuses larger photos. */
    const val MAX_PHOTO_BYTES = 1_572_864
    const val WORK_PHOTO_SIDE = 1600

    /** The engine refuses larger documents. */
    const val MAX_DOCUMENT_BYTES = 5 * 1024 * 1024

    /** Photos of documents stay readable at this size. */
    const val DOCUMENT_SIDE = 2000

    const val PDF = "application/pdf"
    val documentTypes = arrayOf(PDF, "image/jpeg", "image/png", "image/webp")
}

/**
 * Decodes a picked photo upright, shrinks its longest side to [side] and
 * encodes JPEG within [maxBytes] (85, then 70 quality, as the web does).
 * Transparent PNGs land on white, not black.
 */
internal suspend fun readPhoto(context: Context, uri: Uri, side: Int, maxBytes: Int): SizedPhoto =
    withContext(Dispatchers.IO) {
        val decoded = decodeUpright(context, uri, side)
        val scale = minOf(1.0, side.toDouble() / max(decoded.width, decoded.height))
        val width = (decoded.width * scale).roundToInt().coerceAtLeast(1)
        val height = (decoded.height * scale).roundToInt().coerceAtLeast(1)
        val flat = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(Bitmap.createScaledBitmap(decoded, width, height, true), 0f, 0f, null)
        }
        decoded.recycle()
        try {
            var bytes = encodeJpeg(flat, 85)
            if (bytes.size > maxBytes) bytes = encodeJpeg(flat, 70)
            require(bytes.size <= maxBytes) { "This photo is too large." }
            SizedPhoto(bytes, width, height)
        } finally {
            flat.recycle()
        }
    }

/** Reads a picked CV or certificate: PDFs up to 5 MB as they are, photos resized to 2000 px JPEG. */
internal suspend fun readDocument(context: Context, uri: Uri): DocumentFile = withContext(Dispatchers.IO) {
    val type = context.contentResolver.getType(uri).orEmpty()
    when {
        type.startsWith("image/") -> {
            val photo = readPhoto(context, uri, UploadLimits.DOCUMENT_SIDE, UploadLimits.MAX_DOCUMENT_BYTES)
            DocumentFile(photo.bytes, "image/jpeg")
        }
        type == UploadLimits.PDF -> DocumentFile(readPdf(context, uri), UploadLimits.PDF)
        else -> throw IllegalArgumentException("Choose a PDF or a photo (JPEG, PNG or WebP).")
    }
}

/** Stops reading one byte past the limit, so a huge file never fills memory. */
private fun readPdf(context: Context, uri: Uri): ByteArray {
    val limit = UploadLimits.MAX_DOCUMENT_BYTES
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        while (output.size() <= limit) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: throw IllegalStateException("This document couldn't be opened.")
    require(bytes.size <= limit) { "This PDF is too large (at most 5 MB)." }
    return bytes
}

private fun decodeUpright(context: Context, uri: Uri, side: Int): Bitmap {
    val resolver = context.contentResolver
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the EXIF orientation itself.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = minOf(1.0, side.toDouble() / max(info.size.width, info.size.height))
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        } else {
            decodeLegacy(context, uri, side)
        }
    } catch (error: Exception) {
        throw IllegalArgumentException("This photo couldn't be opened. Try a JPEG or PNG.", error)
    }
}

private fun decodeLegacy(context: Context, uri: Uri, side: Int): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > side * 2) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IllegalStateException("No image")
    val orientation = resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val matrix = orientationMatrix(orientation) ?: return raw
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { if (it !== raw) raw.recycle() }
}

private fun orientationMatrix(orientation: Int): Matrix? = when (orientation) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { setScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { setRotate(180f) }
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { setScale(1f, -1f) }
    ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply {
        setRotate(90f)
        postScale(-1f, 1f)
    }
    ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { setRotate(90f) }
    ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply {
        setRotate(270f)
        postScale(-1f, 1f)
    }
    ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { setRotate(270f) }
    else -> null
}

private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray = ByteArrayOutputStream().use { output ->
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
    output.toByteArray()
}

private const val BUFFER = 8192
