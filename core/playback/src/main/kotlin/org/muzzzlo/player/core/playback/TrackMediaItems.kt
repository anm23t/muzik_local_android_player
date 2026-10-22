package org.muzzzlo.player.core.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.muzzzlo.player.core.model.CrossfadeTrack
import org.muzzzlo.player.core.model.ReplayGainInfo
import org.muzzzlo.player.core.model.Track

private const val KEY_ID = "track_id"
private const val KEY_DURATION = "duration_ms"
private const val KEY_ARTISTS = "artists"
private const val KEY_ALBUM_ID = "album_id"
private const val KEY_TRACK_NUMBER = "track_number"
private const val KEY_DISC_NUMBER = "disc_number"
private const val KEY_TRACK_GAIN = "rg_track_gain"
private const val KEY_TRACK_PEAK = "rg_track_peak"
private const val KEY_ALBUM_GAIN = "rg_album_gain"
private const val KEY_ALBUM_PEAK = "rg_album_peak"
private const val KEY_PLAYLIST_ID = "playlist_id"
private const val ABSENT = -1L

/**
 * Builds the Media3 item. The full [Track] rides in the metadata extras, so the player can give it back, together
 * with the playlist the queue was started from ([playlistId]).
 */
internal fun Track.toMediaItem(playlistId: Long? = null): MediaItem {
    val extras =
        Bundle().apply {
            putLong(KEY_ID, id)
            putLong(KEY_DURATION, durationMs)
            putStringArray(KEY_ARTISTS, artists.toTypedArray())
            putLong(KEY_ALBUM_ID, albumId ?: ABSENT)
            putInt(KEY_TRACK_NUMBER, trackNumber ?: ABSENT.toInt())
            putInt(KEY_DISC_NUMBER, discNumber ?: ABSENT.toInt())
            putOptionalFloat(KEY_TRACK_GAIN, replayGain.trackGainDb)
            putOptionalFloat(KEY_TRACK_PEAK, replayGain.trackPeak)
            putOptionalFloat(KEY_ALBUM_GAIN, replayGain.albumGainDb)
            putOptionalFloat(KEY_ALBUM_PEAK, replayGain.albumPeak)
            if (playlistId != null) putLong(KEY_PLAYLIST_ID, playlistId)
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
        albumId = albumId(),
        albumTitle = mediaMetadata.albumTitle?.toString(),
        durationMs = extras.getLong(KEY_DURATION),
        trackNumber = extras.getInt(KEY_TRACK_NUMBER, ABSENT.toInt()).takeIf { it >= 0 },
        discNumber = extras.getInt(KEY_DISC_NUMBER, ABSENT.toInt()).takeIf { it >= 0 },
        replayGain = replayGainInfo(),
    )
}

/** The playlist stored by [toMediaItem]; null for items queued another way. */
internal fun MediaItem.playlistId(): Long? =
    mediaMetadata.extras?.takeIf { it.containsKey(KEY_PLAYLIST_ID) }?.getLong(KEY_PLAYLIST_ID)

/** ReplayGain values stored by [toMediaItem]; none for other items. */
internal fun MediaItem.replayGainInfo(): ReplayGainInfo {
    val extras = mediaMetadata.extras ?: return ReplayGainInfo.NONE
    return ReplayGainInfo(
        trackGainDb = extras.optionalFloat(KEY_TRACK_GAIN),
        trackPeak = extras.optionalFloat(KEY_TRACK_PEAK),
        albumGainDb = extras.optionalFloat(KEY_ALBUM_GAIN),
        albumPeak = extras.optionalFloat(KEY_ALBUM_PEAK),
    )
}

/** What the crossfade rules need to know about this item; the length comes from the library. */
internal fun MediaItem.crossfadeTrack(): CrossfadeTrack {
    val extras = mediaMetadata.extras

    fun optionalInt(key: String) = extras?.getInt(key, ABSENT.toInt())?.takeIf { it >= 0 }
    return CrossfadeTrack(
        albumId = albumId(),
        discNumber = optionalInt(KEY_DISC_NUMBER),
        trackNumber = optionalInt(KEY_TRACK_NUMBER),
        durationMs = extras?.getLong(KEY_DURATION) ?: 0,
    )
}

/** Album id stored by [toMediaItem]; null for tracks without an album and for other items. */
internal fun MediaItem.albumId(): Long? = mediaMetadata.extras?.getLong(KEY_ALBUM_ID, ABSENT)?.takeIf { it != ABSENT }

private fun Bundle.putOptionalFloat(
    key: String,
    value: Float?,
) {
    if (value != null) putFloat(key, value)
}

private fun Bundle.optionalFloat(key: String): Float? = if (containsKey(key)) getFloat(key) else null
