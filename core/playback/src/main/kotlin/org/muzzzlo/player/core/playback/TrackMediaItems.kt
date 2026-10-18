package org.muzzzlo.player.core.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.muzzzlo.player.core.model.Track

private const val KEY_ID = "track_id"
private const val KEY_DURATION = "duration_ms"
private const val KEY_ARTISTS = "artists"
private const val KEY_ALBUM_ID = "album_id"
private const val KEY_TRACK_NUMBER = "track_number"
private const val ABSENT = -1L

/** Builds the Media3 item. The full [Track] rides in the metadata extras, so the player can give it back. */
internal fun Track.toMediaItem(): MediaItem {
    val extras =
        Bundle().apply {
            putLong(KEY_ID, id)
            putLong(KEY_DURATION, durationMs)
            putStringArray(KEY_ARTISTS, artists.toTypedArray())
            putLong(KEY_ALBUM_ID, albumId ?: ABSENT)
            putInt(KEY_TRACK_NUMBER, trackNumber ?: ABSENT.toInt())
        }
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistsText.ifEmpty { null })
                .setAlbumTitle(albumTitle)
                .setExtras(extras)
                .build(),
        )
        .build()
}

/** Reads the [Track] stored by [toMediaItem]; null for items that were not created by this app. */
internal fun MediaItem.toTrack(): Track? {
    val extras = mediaMetadata.extras ?: return null
    if (!extras.containsKey(KEY_ID)) return null
    return Track(
        id = extras.getLong(KEY_ID),
        uri = localConfiguration?.uri?.toString().orEmpty(),
        title = mediaMetadata.title?.toString().orEmpty(),
        artists = extras.getStringArray(KEY_ARTISTS)?.toList().orEmpty(),
        albumId = extras.getLong(KEY_ALBUM_ID, ABSENT).takeIf { it != ABSENT },
        albumTitle = mediaMetadata.albumTitle?.toString(),
        durationMs = extras.getLong(KEY_DURATION),
        trackNumber = extras.getInt(KEY_TRACK_NUMBER, ABSENT.toInt()).takeIf { it >= 0 },
    )
}
