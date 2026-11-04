package org.muzzzlo.player.core.library

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.InputStream

/**
 * Keeps pictures the user picks (artist photos, playlist covers). A picture is decoded, turned upright, reduced to
 * [maxSide] and stored as a JPEG in [folder] in the app's private folder, so it does not depend on access to the
 * original. Call off the main thread.
 */
internal class ReducedImageStore(
    private val resolver: ContentResolver,
    private val folder: File,
    private val maxSide: Int,
) {
    /** Stores a reduced copy of the picture at [sourceUri] as `<namePrefix>-<time>.jpg` and returns its path. */
    fun save(
        namePrefix: String,
        sourceUri: String,
    ): String {
        val uri = Uri.parse(sourceUri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image: $sourceUri" }

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSide)
            }
        val decoded = open(uri).use { BitmapFactory.decodeStream(it, null, options) }
        requireNotNull(decoded) { "Cannot decode: $sourceUri" }
        val picture = decoded.reducedAndUpright(rotationDegrees(uri))

        folder.mkdirs()
        // A new name for every picture, so screens that remember the old one by its path see the change.
        val file = File(folder, "$namePrefix-${System.currentTimeMillis()}.jpg")
        val partial = File(folder, "${file.name}.part")
        partial.outputStream().use { check(picture.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) }
        check(partial.renameTo(file)) { "Cannot store the picture" }
        return file.absolutePath
    }

    /** Deletes a stored picture. Paths outside [folder] are ignored. */
    fun delete(path: String?) = deleteInside(folder, path)

    private fun open(uri: Uri): InputStream = requireNotNull(resolver.openInputStream(uri)) { "Cannot open: $uri" }

    private fun rotationDegrees(uri: Uri): Int =
        runCatching {
            open(uri).use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> QUARTER_TURN
                    ExifInterface.ORIENTATION_ROTATE_180 -> HALF_TURN
                    ExifInterface.ORIENTATION_ROTATE_270 -> HALF_TURN + QUARTER_TURN
                    else -> 0
                }
            }
        }.getOrDefault(0)

    private fun Bitmap.reducedAndUpright(degrees: Int): Bitmap {
        val (targetWidth, targetHeight) = scaledSize(width, height, maxSide)
        val matrix =
            Matrix().apply {
                setScale(targetWidth.toFloat() / width, targetHeight.toFloat() / height)
                postRotate(degrees.toFloat())
            }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private companion object {
        const val JPEG_QUALITY = 90

        /** Camera photos store their rotation in EXIF in steps of a quarter turn. */
        const val QUARTER_TURN = 90
        const val HALF_TURN = 180
    }
}

/** Deletes the file at [path] when it lies directly in [folder]; any other path (or null) is left alone. */
internal fun deleteInside(
    folder: File,
    path: String?,
) {
    val file = path?.let(::File) ?: return
    if (file.parentFile == folder) file.delete()
}

/**
 * Biggest power of two to decode a [width] x [height] picture with, so that its longer side stays at least [target]
 * (BitmapFactory `inSampleSize`). Decoding smaller saves memory on big camera photos.
 */
internal fun sampleSizeFor(
    width: Int,
    height: Int,
    target: Int,
): Int {
    val longer = maxOf(width, height)
    var sample = 1
    while (longer / (sample * 2) >= target) sample *= 2
    return sample
}

/**
 * Size of a [width] x [height] picture reduced so its longer side is at most [maxSide];
 * smaller pictures keep their size.
 */
internal fun scaledSize(
    width: Int,
    height: Int,
    maxSide: Int,
): Pair<Int, Int> {
    val longer = maxOf(width, height)
    if (longer <= maxSide) return width to height
    val scale = maxSide.toDouble() / longer
    return maxOf(1, Math.round(width * scale).toInt()) to maxOf(1, Math.round(height * scale).toInt())
}
