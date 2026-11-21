package com.hafiyanjye.app.ui.talent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A problem with the file the person chose, in words for them. */
internal class CvFileException(message: String) : Exception(message)

/** A CV file read off the phone, ready for POST /api/talent/cv. */
internal class CvFile(val bytes: ByteArray, val contentType: String)

/** A profile photo cropped square and compressed, ready for /api/media. */
internal class SquarePhoto(val jpeg: ByteArray, val side: Int)

/** The kinds the document picker offers for a CV. */
internal val CvMimeTypes = arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")

private const val MAX_CV_BYTES = 5 * 1024 * 1024
private const val CV_MAX_SIDE = 2000
private const val AVATAR_SIDE = 512
private const val MAX_AVATAR_BYTES = 1_500_000

/** A chosen CV ready to send: a PDF as it is (at most 5 MB), a photo as a JPEG of at most 2000 px. */
internal suspend fun readCvFile(context: Context, uri: Uri): CvFile = withContext(Dispatchers.IO) {
    val type = context.contentResolver.getType(uri).orEmpty()
    try {
        when {
            type == "application/pdf" -> {
                val bytes = readAtMost(context, uri, MAX_CV_BYTES)
                    ?: throw CvFileException("This PDF is too large (at most 5 MB).")
                CvFile(bytes, "application/pdf")
            }
            type.startsWith("image/") -> {
                val bitmap = decode(context, uri, CV_MAX_SIDE)
                CvFile(compress(bitmap, MAX_CV_BYTES), "image/jpeg")
            }
            else -> throw CvFileException("Choose a PDF or a photo (JPEG, PNG or WebP).")
        }
    } catch (_: IOException) {
        throw CvFileException("Couldn't open that file. Choose another one.")
    } catch (_: SecurityException) {
        throw CvFileException("Couldn't open that file. Choose another one.")
    }
}

/** A chosen picture as a centred square JPEG for a profile photo. */
internal suspend fun readSquarePhoto(context: Context, uri: Uri): SquarePhoto = withContext(Dispatchers.IO) {
    try {
        val bitmap = decode(context, uri, AVATAR_SIDE * 2)
        val side = min(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val target = min(side, AVATAR_SIDE)
        val scaled = if (side == target) square else Bitmap.createScaledBitmap(square, target, target, true)
        SquarePhoto(compress(scaled, MAX_AVATAR_BYTES), target)
    } catch (_: IOException) {
        throw CvFileException("Couldn't open that photo. Choose another one.")
    } catch (_: SecurityException) {
        throw CvFileException("Couldn't open that photo. Choose another one.")
    }
}

/** The file's bytes, or null when it is larger than [limit]. */
private fun readAtMost(context: Context, uri: Uri, limit: Int): ByteArray? {
    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("no stream")
    return input.use {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = it.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > limit) return null
        }
        out.toByteArray()
    }
}

/** The picture upright, with its longest side at most [maxSide]. */
private fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw CvFileException("That picture can't be read. Choose a JPEG, PNG or WebP.")
    }

    // Decode at a power-of-two fraction first, so a large photo never fills the memory.
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw CvFileException("That picture can't be read. Choose a JPEG, PNG or WebP.")

    val degrees = resolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    val scale = min(1f, maxSide.toFloat() / max(raw.width, raw.height))
    if (degrees == 0f && scale == 1f) return raw
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(degrees)
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}

/** JPEG under [limit] bytes: lower the quality, then the size, until it fits. */
private fun compress(source: Bitmap, limit: Int): ByteArray {
    var bitmap = source
    while (true) {
        for (quality in intArrayOf(88, 78, 66, 54)) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= limit) return out.toByteArray()
        }
        val w = (bitmap.width * 0.75f).roundToInt()
        val h = (bitmap.height * 0.75f).roundToInt()
        if (w < 64 || h < 64) throw CvFileException("That picture is too large. Choose a smaller one.")
        bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
