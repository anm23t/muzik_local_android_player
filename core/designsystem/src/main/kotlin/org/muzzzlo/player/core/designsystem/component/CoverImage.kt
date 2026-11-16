package org.muzzzlo.player.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import org.muzzzlo.player.core.designsystem.theme.MuzzzloCoverGradients
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloMotion

/** Which cover [CoverImage] shows. */
sealed interface CoverSource {
    /** The cover of one track's file. */
    data class Track(val id: Long) : CoverSource

    /** The cover of an album (that of its first track with a picture). */
    data class Album(val id: Long) : CoverSource

    /** A picture the user picked, a file in the app's private folder (the cover of a playlist). */
    data class File(val path: String) : CoverSource

    /** A gradient of [MuzzzloCoverGradients] (generated and smart playlists, task 2.5.7); drawn, not loaded. */
    data class Gradient(val index: Int) : CoverSource
}

/**
 * The cover a playlist stands for itself with: the picture the user picked, else its gradient (a kept generated
 * playlist, task 2.5.7), else the cover of its first track.
 */
fun playlistCoverSource(
    coverPath: String?,
    firstTrackId: Long?,
    gradient: Int? = null,
): CoverSource? =
    coverPath?.let { CoverSource.File(it) }
        ?: gradient?.let { CoverSource.Gradient(it) }
        ?: firstTrackId?.let { CoverSource.Track(it) }

/** Loads the small covers for [CoverImage]; the app provides the implementation (cover cache of task 0.3.7). */
fun interface CoverLoader {
    /** The cover, or null when there is none. Suspends off the main thread as needed. */
    suspend fun load(source: CoverSource): ImageBitmap?

    /**
     * The cover last loaded for [source], without waiting, so a cover composed again (the player turning to its
     * lyrics) starts with its picture instead of the placeholder; [load] still runs and replaces it.
     */
    fun cached(source: CoverSource): ImageBitmap? = null
}

/** The loader [CoverImage] uses. Without one provided (previews, tests) every cover is missing. */
val LocalCoverLoader = staticCompositionLocalOf { CoverLoader { null } }

/**
 * A cover from the cover cache, or [CoverPlaceholder] while it loads and when there is none.
 * Pass the modifier the slot of [CoverTile], [CoverCard], [AlbumRow] or [MiniPlayerView] gives.
 *
 * @param placeholderShape corners of the placeholder border, the same as the clip of [modifier].
 * @param fadeIn a new cover fades in over the placeholder (the player and the mini player, task 2.0.9). The old
 * picture goes at once, so a cover that flies back in after a swipe never shows the previous track.
 */
@Composable
fun CoverImage(
    source: CoverSource?,
    modifier: Modifier = Modifier,
    placeholderShape: Shape = MuzzzloDimens.RowShape,
    fadeIn: Boolean = false,
) {
    if (source is CoverSource.Gradient) {
        GradientCover(source.index, modifier, placeholderShape)
        return
    }
    val loader = LocalCoverLoader.current
    // The picture is kept with its source, so after a change of source the old picture is never shown as the new.
    val loaded by produceState(initialValue = source to source?.let(loader::cached), source, loader) {
        value = source to source?.let(loader::cached)
        value = source to source?.let { loader.load(it) }
    }
    val image = loaded.second.takeIf { loaded.first == source }
    if (!fadeIn) {
        if (image == null) {
            CoverPlaceholder(modifier, placeholderShape)
        } else {
            Image(bitmap = image, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
        }
        return
    }
    val alpha = remember(source) { Animatable(if (image != null) 1f else 0f) }
    LaunchedEffect(image) { if (image != null) alpha.animateTo(1f, MuzzzloMotion.timed(MuzzzloMotion.SHORT_MS)) }
    Box(modifier) {
        CoverPlaceholder(Modifier.matchParentSize(), placeholderShape)
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.matchParentSize().graphicsLayer { this.alpha = alpha.value },
                contentScale = ContentScale.Crop,
            )
        }
    }
}
