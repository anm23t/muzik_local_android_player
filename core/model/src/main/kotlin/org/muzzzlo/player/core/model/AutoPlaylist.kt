package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.Flow

/**
 * The playlists that fill themselves from the marks of tracks (task 1.1.7). They are not stored and cannot be edited;
 * in 1.3 they become special cases of smart playlists.
 *
 * @property key the name in navigation routes.
 */
enum class AutoPlaylist(
    val key: String,
) {
    /** Favorite tracks, the latest added first. */
    Favorites("favorites"),

    /** Rated tracks, the highest rating first; tracks without a rating are not in it. */
    TopSongs("top-songs"),
    ;

    companion object {
        /** The playlist with this [key], null for an unknown one. */
        fun fromKey(key: String?): AutoPlaylist? = entries.firstOrNull { it.key == key }
    }
}

/** The tracks of the automatic playlists. Tracks whose files are missing are left out. */
interface AutoPlaylistRepository {
    /** Tracks of [playlist] in its order; the list follows every change of marks. */
    fun observeTracks(playlist: AutoPlaylist): Flow<List<Track>>
}
