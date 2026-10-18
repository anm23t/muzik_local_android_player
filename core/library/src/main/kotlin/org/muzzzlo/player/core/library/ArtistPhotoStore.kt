package org.muzzzlo.player.core.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * Keeps the artist photos the user picks (CLAUDE.md, decision 10). A photo is decoded, turned upright, reduced to
 * [MAX_SIDE] and stored as a JPEG in the app's private folder, so it does not depend on access to the original.
 * Photos are not part of the JSON backup (ARCHITECTURE.md, section 2). Call off the main thread.
 */
internal class ArtistPhotoStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val resolver = context.contentResolver
        private val folder = File(context.filesDir, FOLDER)

        /** Stores a reduced copy of the picture at [sourceUri] and returns the path of the copy. */
        fun save(
            artistId: Long,
            sourceUri: String,
        ): String {
            val uri = Uri.parse(sourceUri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image: $sourceUri" }

            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_SIDE)
                }
            val decoded = open(uri).use { BitmapFactory.decodeStream(it, null, options) }
            requireNotNull(decoded) { "Cannot decode: $sourceUri" }
            val photo = decoded.reducedAndUpright(rotationDegrees(uri))

            folder.mkdirs()
            // A new name for every photo, so screens that remember the old picture by its path see the change.
            val file = File(folder, "$artistId-${System.currentTimeMillis()}.jpg")
            val partial = File(folder, "${file.name}.part")
            partial.outputStream().use { check(photo.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) }
            check(partial.renameTo(file)) { "Cannot store the photo" }
            return file.absolutePath
        }

        /** Deletes a stored photo. Paths outside the photo folder are ignored. */
        fun delete(path: String?) {
            val file = path?.let(::File) ?: return
            if (file.parentFile == folder) file.delete()
        }

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
            val (targetWidth, targetHeight) = scaledSize(width, height, MAX_SIDE)
            val matrix =
                Matrix().apply {
                    setScale(targetWidth.toFloat() / width, targetHeight.toFloat() / height)
                    postRotate(degrees.toFloat())
                }
            return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        }

        private companion object {
            const val FOLDER = "artist_photos"

            /** The biggest photo on screen is 100 dp; 512 px stays sharp on dense screens. */
            const val MAX_SIDE = 512
            const val JPEG_QUALITY = 90

            /** Camera photos store their rotation in EXIF in steps of a quarter turn. */
            const val QUARTER_TURN = 90
            const val HALF_TURN = 180
        }
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
