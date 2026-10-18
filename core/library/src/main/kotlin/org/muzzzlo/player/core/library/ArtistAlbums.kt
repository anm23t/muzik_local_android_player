package org.muzzzlo.player.core.library

import org.muzzzlo.player.core.database.dao.ArtistAlbumRef
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.NameNormalizer

/**
 * The albums of an artist in display order: albums credited to them first, then albums where they only appear
 * on some tracks. Inside each group the newest year comes first, albums without a year go last, then by title.
 * Refs to albums missing from [albums] (no present tracks) are dropped.
 */
internal fun artistAlbums(
    albums: List<Album>,
    refs: List<ArtistAlbumRef>,
): List<Album> {
    val isOwn = refs.associate { it.albumId to it.isOwn }
    return albums
        .filter { it.id in isOwn }
        .sortedWith(
            compareByDescending<Album> { isOwn.getValue(it.id) }
                .thenByDescending { it.year ?: Int.MIN_VALUE }
                .thenBy { NameNormalizer.key(it.title) }
                .thenBy { it.id },
        )
}
