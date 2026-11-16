package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.Flow

/** A playlist of the user that fills itself by [rules] (task 2.5.6); recomputed every time it is shown. */
data class SmartPlaylist(
    val id: Long,
    val name: String,
    val rules: SmartRules,
    val createdAtMs: Long,
)

/** Smart playlists and the track facts their rules look at. Implemented in `core:library`. */
interface SmartPlaylistRepository {
    /** Every smart playlist, oldest first. */
    fun observePlaylists(): Flow<List<SmartPlaylist>>

    /** The tracks [rules] pick from the library; follows every change of tracks, marks and listens. */
    fun observeTracks(rules: SmartRules): Flow<List<Track>>

    suspend fun playlist(id: Long): SmartPlaylist?

    /** Creates a smart playlist and returns its id. */
    suspend fun create(
        name: String,
        rules: SmartRules,
    ): Long

    suspend fun update(
        id: Long,
        name: String,
        rules: SmartRules,
    )

    suspend fun delete(id: Long)
}

/** Every present track of the library as [TrackFacts], the input of rules. Implemented in `core:library`. */
interface TrackFactsSource {
    fun observeFacts(): Flow<List<TrackFacts>>
}
