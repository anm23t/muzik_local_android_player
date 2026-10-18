package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens

/** Which cover [CoverImage] shows. */
sealed interface CoverSource {
    /** The cover of one track's file. */
    data class Track(val id: Long) : CoverSource

    /** The cover of an album (that of its first track with a picture). */
    data class Album(val id: Long) : CoverSource
}

/** Loads the small covers for [CoverImage]; the app provides the implementation (cover cache of task 0.3.7). */
fun interface CoverLoader {
    /** The cover, or null when there is none. Suspends off the main thread as needed. */
    suspend fun load(source: CoverSource): ImageBitmap?
}

/** The loader [CoverImage] uses. Without one provided (previews, tests) every cover is missing. */
val LocalCoverLoader = staticCompositionLocalOf { CoverLoader { null } }

/**
 * A cover from the cover cache, or [CoverPlaceholder] while it loads and when there is none.
 * Pass the modifier the slot of [CoverTile], [CoverCard], [AlbumRow] or [MiniPlayerView] gives.
 *
 * @param placeholderShape corners of the placeholder border, the same as the clip of [modifier].
 */
@Composable
fun CoverImage(
    source: CoverSource?,
    modifier: Modifier = Modifier,
    placeholderShape: Shape = MuzzzloDimens.RowShape,
) {
    val loader = LocalCoverLoader.current
    val cover by produceState<ImageBitmap?>(initialValue = null, source, loader) {
        value = source?.let { loader.load(it) }
    }
    val image = cover
    if (image == null) {
        CoverPlaceholder(modifier, placeholderShape)
    } else {
        Image(bitmap = image, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    }
}
