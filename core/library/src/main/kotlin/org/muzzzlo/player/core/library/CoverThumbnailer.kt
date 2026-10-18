package org.muzzzlo.player.core.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Cuts the small cover out of the picture embedded in a file: decoded with a power-of-two sample size to save
 * memory on big pictures, reduced to [MAX_SIDE] and encoded as JPEG. Call off the main thread.
 */
internal class CoverThumbnailer
    @Inject
    constructor() {
        /** JPEG bytes of the reduced [picture], or null when it is not an image Android can decode. */
        fun thumbnail(picture: ByteArray): ByteArray? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_SIDE)
                }
            val decoded = BitmapFactory.decodeByteArray(picture, 0, picture.size, options) ?: return null
            val (width, height) = scaledSize(decoded.width, decoded.height, MAX_SIDE)
            val reduced =
                if (width == decoded.width) decoded else Bitmap.createScaledBitmap(decoded, width, height, true)
            val out = ByteArrayOutputStream()
            check(reduced.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "Cannot encode the cover" }
            return out.toByteArray()
        }

        companion object {
            /** The biggest cover on screen is the player's, 284 dp (about 800 px on dense screens); 512 stays sharp. */
            const val MAX_SIDE = 512
            const val JPEG_QUALITY = 85
        }
    }
