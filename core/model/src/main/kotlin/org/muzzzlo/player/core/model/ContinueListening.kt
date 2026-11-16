package org.muzzzlo.player.core.model

/** A tile of "Continue listening": an album or a playlist (task 1.1.5). */
sealed interface ContinueItem {
    data class AlbumItem(
        val album: Album,
    ) : ContinueItem

    data class PlaylistItem(
        val playlist: Playlist,
    ) : ContinueItem

    /** Today's generated playlist that was played (task 2.5.7); it stays while its set lives. */
    data class GeneratedItem(
        val playlist: GeneratedPlaylist,
    ) : ContinueItem
}

/** The "Continue listening" row of the home screen (ARCHITECTURE.md, section 7). */
object ContinueListening {
    /** Tiles in the row; albums and playlists share it. */
    const val MAX_TILES = 5

    /**
     * The tiles of the row, most recent first, at most [limit]. An album counts from its latest listen outside
     * playlists, a playlist from the last time it was started ([Playlist.lastPlayedAtMs]); on a tie the playlist goes
     * first, it was started before its own tracks were heard.
     *
     * @param recentAlbums albums of the latest listens ([StatsRepository.recentAlbums]).
     * @param albums every album of the library; an id that is not here any more (the album was deleted or merged)
     *   is skipped.
     * @param playlists every playlist; one never started, or without a track on the device, is not shown.
     * @param generated today's generated playlists; one with a listen since it was built is shown, dated by it.
     * @param queueAlbum album of the restored queue. It is shown, alone, only while nothing else is, so a fresh
     *   install or an update before the first listen does not leave the row empty.
     */
    fun items(
        recentAlbums: List<RecentAlbum>,
        albums: List<Album>,
        playlists: List<Playlist>,
        queueAlbum: Album?,
        limit: Int = MAX_TILES,
        generated: List<GeneratedPlaylist> = emptyList(),
    ): List<ContinueItem> {
        val byId = albums.associateBy { it.id }
        val albumTiles =
            recentAlbums.distinctBy { it.albumId }.mapNotNull { recent ->
                byId[recent.albumId]?.let { Dated(recent.lastListenedAtMs, PLAYLIST_LAST, ContinueItem.AlbumItem(it)) }
            }
        val playlistTiles =
            playlists.filter { it.trackCount > 0 }.mapNotNull { playlist ->
                playlist.lastPlayedAtMs?.let { Dated(it, PLAYLIST_FIRST, ContinueItem.PlaylistItem(playlist)) }
            }
        val generatedTiles =
            generated.filter { it.isShown }.mapNotNull { playlist ->
                playlist.lastStartedAtMs?.let { Dated(it, PLAYLIST_FIRST, ContinueItem.GeneratedItem(playlist)) }
            }
        val fromHistory =
            (albumTiles + playlistTiles + generatedTiles)
                .sortedWith(compareByDescending<Dated> { it.atMs }.thenBy { it.tieOrder })
                .map { it.item }
                .take(limit)
        return fromHistory.ifEmpty { listOfNotNull(queueAlbum?.let(ContinueItem::AlbumItem)).take(limit) }
    }

    private class Dated(
        val atMs: Long,
        val tieOrder: Int,
        val item: ContinueItem,
    )

    private const val PLAYLIST_FIRST = 0
    private const val PLAYLIST_LAST = 1
}
