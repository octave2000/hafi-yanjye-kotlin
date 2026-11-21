package com.hafiyanjye.app.ui.listings

import android.content.ContentResolver
import android.content.Context
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
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Longest side of a listing photo after resizing, as on the web. */
private const val MAX_SIDE = 1600

/** The engine refuses anything larger (1.5 MB). */
private const val MAX_BYTES = 1_572_864

/** Older phones decode at up to twice the target first, then scale down smoothly. */
private const val DECODE_LIMIT = MAX_SIDE * 2

/** A photo that could not be prepared; [message] is meant for the owner. */
class PhotoProblem(message: String) : Exception(message)

/** A photo ready to send: JPEG bytes and their pixel size. */
class PreparedPhoto(val jpeg: ByteArray, val width: Int, val height: Int)

/**
 * Turns a picked photo into a JPEG of at most 1600 px and 1.5 MB, upright and
 * on white (web: resizeToJpeg). Decoding near the target size keeps a 50 MP
 * camera photo from filling the memory.
 */
suspend fun prepareListingPhoto(context: Context, uri: Uri): PreparedPhoto = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val decoded = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeScaled(resolver, uri) else decodeLegacy(resolver, uri)
    } catch (problem: PhotoProblem) {
        throw problem
    } catch (_: Exception) {
        throw PhotoProblem("This photo couldn't be opened. Try a JPEG or PNG.")
    } catch (_: OutOfMemoryError) {
        throw PhotoProblem("This photo is too large to open. Try another one.")
    }
    try {
        encode(decoded)
    } finally {
        decoded.recycle()
    }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeScaled(resolver: ContentResolver, uri: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        val scale = scaleFor(info.size.width, info.size.height)
        decoder.setTargetSize(
            max(1, (info.size.width * scale).roundToInt()),
            max(1, (info.size.height * scale).roundToInt()),
        )
        // Software memory, so the bitmap can be drawn on a Canvas below.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

/** Android 8 and 8.1: sample down while decoding, then apply the camera's EXIF rotation by hand. */
private fun decodeLegacy(resolver: ContentResolver, uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw PhotoProblem("This photo couldn't be opened. Try a JPEG or PNG.")
    }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > DECODE_LIMIT) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw PhotoProblem("This photo couldn't be opened. Try a JPEG or PNG.")
    val orientation = resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val matrix = orientationMatrix(orientation) ?: return bitmap
    val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (upright !== bitmap) bitmap.recycle()
    return upright
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

/** Transparent PNGs become white, not black; a second, lower quality is tried before giving up. */
private fun encode(source: Bitmap): PreparedPhoto {
    val scale = scaleFor(source.width, source.height)
    val width = max(1, (source.width * scale).roundToInt())
    val height = max(1, (source.height * scale).roundToInt())
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        var jpeg = compress(output, quality = 85)
        if (jpeg.size > MAX_BYTES) jpeg = compress(output, quality = 70)
        if (jpeg.size > MAX_BYTES) throw PhotoProblem("This photo is too large.")
        return PreparedPhoto(jpeg, width, height)
    } finally {
        output.recycle()
    }
}

private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}

private fun scaleFor(width: Int, height: Int): Float = minOf(1f, MAX_SIDE.toFloat() / max(width, height))
