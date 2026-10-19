package org.muzzzlo.player.core.model

/**
 * The albums of an artist split into the sections of the artist card (ARCHITECTURE.md, section 9). Every album is
 * in exactly one section; each section goes from the newest year to the oldest, albums without a year last.
 *
 * @property albums albums credited to the artist that are not singles.
 * @property singles small releases credited to the artist: one disc and at most three tracks.
 * @property appearsOn albums credited to someone else where the artist is on some tracks, whatever their size.
 */
data class ArtistDiscography(
    val albums: List<Album> = emptyList(),
    val singles: List<Album> = emptyList(),
    val appearsOn: List<Album> = emptyList(),
) {
    val isEmpty: Boolean get() = albums.isEmpty() && singles.isEmpty() && appearsOn.isEmpty()
}
