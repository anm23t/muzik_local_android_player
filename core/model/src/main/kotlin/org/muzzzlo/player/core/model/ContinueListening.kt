package org.muzzzlo.player.core.model

/** The "Continue listening" row of the home screen (ARCHITECTURE.md, section 7). */
object ContinueListening {
    /** Tiles in the row; playlists (1.1) share it with albums. */
    const val MAX_TILES = 5

    /**
     * The albums of the row, most recently listened first, at most [limit].
     *
     * @param recentIds albums of the latest listens, the most recent first ([StatsRepository.recentAlbumIds]).
     * @param albums every album of the library; an id that is not here any more (the album was deleted or merged)
     *   is skipped.
     * @param queueAlbum album of the restored queue. It is shown, alone, only while the history gives no album
     *   at all, so a fresh install or an update before the first listen does not leave the row empty.
     */
    fun albums(
        recentIds: List<Long>,
        albums: List<Album>,
        queueAlbum: Album?,
        limit: Int = MAX_TILES,
    ): List<Album> {
        val byId = albums.associateBy { it.id }
        val fromHistory = recentIds.distinct().mapNotNull(byId::get).take(limit)
        return fromHistory.ifEmpty { listOfNotNull(queueAlbum).take(limit) }
    }
}
