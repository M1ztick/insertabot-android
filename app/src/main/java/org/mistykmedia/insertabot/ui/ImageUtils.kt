package org.mistykmedia.insertabot.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Media type of everything [uriToJpegBase64] emits; goes on the wire as `mediaType`. */
const val JPEG_MEDIA_TYPE = "image/jpeg"

private const val MAX_IMAGE_DIMENSION = 1024
private const val JPEG_QUALITY = 85
private const val MAX_IMAGE_BYTES = 2_000_000

/**
 * Convert a gallery/camera image URI to a base64 data URI the Worker can see.
 *
 * The image is downscaled to [MAX_IMAGE_DIMENSION] on the long edge, EXIF
 * orientation is corrected, and JPEG quality is reduced until the result is
 * under [MAX_IMAGE_BYTES]. Matches `public/index.js` in `insertabot-cfworker`.
 */
suspend fun uriToJpegBase64(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val original = BitmapFactory.decodeStream(stream)
                ?: return@use null
            val oriented = applyExifOrientation(context, uri, original)
            val scaled = scaleDown(oriented, MAX_IMAGE_DIMENSION)
            val bytes = compressToSize(scaled, JPEG_QUALITY, MAX_IMAGE_BYTES)
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        }
    }.getOrNull()
}

/**
 * Target size for [scaleDown], as `width to height`.
 *
 * The long edge becomes [maxDimension] and the short edge keeps the aspect
 * ratio — but never rounds below 1. A panorama divides its short edge to
 * something under half a pixel (10000x3 at max 1024 truncates to 0), and
 * `createScaledBitmap` rejects a zero dimension; the throw was swallowed by
 * the `runCatching` in [uriToJpegBase64], so the picked image simply never
 * appeared in the composer.
 *
 * Returns the input size unchanged when it already fits, which is how
 * [scaleDown] knows to skip the copy.
 */
internal fun scaledDimensions(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
    if (width <= maxDimension && height <= maxDimension) return width to height
    val ratio = width.toFloat() / height.toFloat()
    return if (width > height) {
        maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
    } else {
        (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
    }
}

private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val (width, height) = scaledDimensions(bitmap.width, bitmap.height, maxDimension)
    if (width == bitmap.width && height == bitmap.height) return bitmap
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}

private fun compressToSize(bitmap: Bitmap, initialQuality: Int, maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    var quality = initialQuality.coerceIn(5, 95)
    do {
        output.reset()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        quality -= 5
    } while (output.size() > maxBytes && quality > 25)
    return output.toByteArray()
}

private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    val degrees = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).rotationDegrees
        }
    }.getOrNull() ?: 0

    return if (degrees == 0) bitmap else {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { bitmap.recycle() }
    }
}
