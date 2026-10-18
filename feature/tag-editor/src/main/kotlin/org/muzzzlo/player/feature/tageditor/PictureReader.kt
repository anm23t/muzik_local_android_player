package org.muzzzlo.player.feature.tageditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.EmbeddedPicture
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject

/** Turns the image the user picked for the cover into a picture to embed; replaced by a fake in tests. */
fun interface PictureReader {
    /** The picture at [uri] (a `content://` URI from the photo picker), null when it cannot be read. */
    suspend fun read(uri: String): EmbeddedPicture?
}

/**
 * [PictureReader] on the content resolver. A JPEG or PNG up to [MAX_SIDE] pixels and [MAX_BYTES] is embedded
 * byte for byte; anything bigger or in another format is scaled down and stored as a JPEG, so a 12 MP photo does
 * not add megabytes to every file.
 */
internal class ContentPictureReader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dispatchers: DispatcherProvider,
    ) : PictureReader {
        @Suppress("SwallowedException")
        override suspend fun read(uri: String): EmbeddedPicture? =
            withContext(dispatchers.io) {
                try {
                    val bytes = context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
                    bytes?.let(::toPicture)
                } catch (e: IOException) {
                    null
                } catch (e: SecurityException) {
                    null
                }
            }

        private fun toPicture(bytes: ByteArray): EmbeddedPicture? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return null
            val mime = bounds.outMimeType
            if (mime in KEPT_AS_IS && maxOf(width, height) <= MAX_SIDE && bytes.size <= MAX_BYTES) {
                return EmbeddedPicture(bytes, mime, EmbeddedPicture.TYPE_FRONT_COVER)
            }
            return scaledJpeg(bytes, width, height)?.let { EmbeddedPicture(it, JPEG, EmbeddedPicture.TYPE_FRONT_COVER) }
        }

        private fun scaledJpeg(
            bytes: ByteArray,
            width: Int,
            height: Int,
        ): ByteArray? {
            var sample = 1
            while (maxOf(width, height) / (sample * 2) >= MAX_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            val scale = MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
            val bitmap =
                if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        decoded,
                        (decoded.width * scale).toInt().coerceAtLeast(1),
                        (decoded.height * scale).toInt().coerceAtLeast(1),
                        true,
                    )
                } else {
                    decoded
                }
            return ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        }

        private companion object {
            const val MAX_SIDE = 1200
            const val MAX_BYTES = 1_000_000
            const val JPEG_QUALITY = 90
            const val JPEG = "image/jpeg"
            val KEPT_AS_IS = setOf(JPEG, "image/png")
        }
    }
