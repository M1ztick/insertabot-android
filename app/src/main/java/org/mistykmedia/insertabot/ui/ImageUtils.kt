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

private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
    if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) return bitmap
    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
    return if (bitmap.width > bitmap.height) {
        Bitmap.createScaledBitmap(bitmap, maxDimension, (maxDimension / ratio).toInt(), true)
    } else {
        Bitmap.createScaledBitmap(bitmap, (maxDimension * ratio).toInt(), maxDimension, true)
    }
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
