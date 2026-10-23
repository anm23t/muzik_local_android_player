package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SearchTest {
    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(
        "ABBA, abba",
        "'  Daft   Punk ', daft punk",
        "Simon & Garfunkel, simon & garfunkel",
        "Simon and Garfunkel, simon & garfunkel",
        "Beyoncé, beyonce",
        "Sigur Rós, sigur ros",
        "Motörhead, motorhead",
        "Ёлка, елка",
        "Сплин, сплин",
        "Мой рок-н-ролл, мой рок-н-ролл",
        "Йорш, йорш",
    )
    fun `keys ignore case, spaces, accents and yo`(
        text: String,
        expected: String,
    ) {
        assertEquals(expected, SearchText.key(text))
    }

    @ParameterizedTest(name = "\"{0}\" in \"{1}\": {2}")
    @CsvSource(
        // query, text, match (NONE when there is no match)
        "abba, ABBA, EXACT",
        "abb, ABBA, PREFIX",
        "creat, 'Tyler, The Creator', WORD_PREFIX",
        "the creator, 'Tyler, The Creator', WORD_PREFIX",
        "bb, ABBA, CONTAINS",
        "creator tyler, 'Tyler, The Creator', ALL_WORDS",
        "punk daft, Daft Punk, ALL_WORDS",
        "punk rock, Daft Punk, NONE",
        "xyz, ABBA, NONE",
        "beyonce, Beyoncé, EXACT",
        "елка, Ёлка, EXACT",
        "ёлка, Елка, EXACT",
        "и, Йорш, NONE",
        // "and" is read as "&", a word of its own.
        "and, Simon & Garfunkel, WORD_PREFIX",
        "simon and, Simon & Garfunkel, PREFIX",
        "' ', ABBA, NONE",
        "rock, Мой рок-н-ролл, NONE",
        "ролл, Мой рок-н-ролл, WORD_PREFIX",
    )
    fun `match table`(
        query: String,
        text: String,
        expected: String,
    ) {
        val match = SearchText.match(SearchText.key(query), SearchText.key(text))
        assertEquals(expected, match?.name ?: "NONE")
    }

    @Test
    fun `a blank query finds nothing`() {
        val index = index(tracks = listOf(track(1, "Song", "A")))
        assertTrue(index.search("").isEmpty)
        assertTrue(index.search("   ").isEmpty)
    }

    @Test
    fun `a track with several artists is found by each of them`() {
        val index =
            index(
                tracks = listOf(track(1, "Duet", "Alpha", "Beta")),
                artists = listOf(artist(1, "Alpha"), artist(2, "Beta")),
            )
        assertEquals(listOf(1L), index.search("alpha").tracks.map { it.id })
        assertEquals(listOf(1L), index.search("beta").tracks.map { it.id })
    }

    @Test
    fun `a track is found through an old spelling of its artist`() {
        val index =
            index(
                tracks = listOf(track(1, "Song", "Mana")),
                artists = listOf(artist(7, "Mana")),
                aliases = listOf(ArtistAlias(7, "Maná")),
            )
        assertEquals(listOf(1L), index.search("maná").tracks.map { it.id })
        assertEquals(listOf(7L), index.search("maná").artists.map { it.id })
    }

    @Test
    fun `an alias of an artist without tracks finds nothing`() {
        val index = index(tracks = listOf(track(1, "Song", "A")), aliases = listOf(ArtistAlias(99, "Ghost")))
        assertTrue(index.search("ghost").isEmpty)
    }

    @Test
    fun `a compound name is one artist found by the whole name and by its words`() {
        val index =
            index(
                tracks = listOf(track(1, "Song", "Tyler, The Creator")),
                artists = listOf(artist(1, "Tyler, The Creator")),
            )
        assertEquals(1, index.search("tyler, the creator").artists.size)
        assertEquals(1, index.search("creator").tracks.size)
    }

    @Test
    fun `better matches go first, a title before an artist of the same rank`() {
        val tracks =
            listOf(
                track(1, "Lovesong", "X"),
                track(2, "Love", "X"),
                track(3, "My Love", "X"),
                track(4, "Other", "Love Inc"),
                track(5, "Glove", "X"),
                track(6, "Lovely", "X"),
            )
        val ids = index(tracks = tracks).search("love").tracks.map { it.id }
        // EXACT title, PREFIX titles (input order), PREFIX artist, WORD_PREFIX title, CONTAINS title.
        assertEquals(listOf(2L, 1L, 6L, 4L, 3L, 5L), ids)
    }

    @Test
    fun `albums and playlists are found by their names`() {
        val index =
            index(
                albums = listOf(Album(1, "Discovery", listOf("Daft Punk"), 2001, 14)),
                playlists = listOf(Playlist(3, "Road trip", 5, null, 0, null)),
            )
        assertEquals(listOf(1L), index.search("disc").albums.map { it.id })
        assertEquals(listOf(3L), index.search("trip").playlists.map { it.id })
        assertTrue(index.search("daft").albums.isEmpty())
    }

    private fun index(
        tracks: List<Track> = emptyList(),
        albums: List<Album> = emptyList(),
        artists: List<Artist> = emptyList(),
        aliases: List<ArtistAlias> = emptyList(),
        playlists: List<Playlist> = emptyList(),
    ) = SearchIndex(tracks, albums, artists, aliases, playlists)

    private fun track(
        id: Long,
        title: String,
        vararg artists: String,
    ) = Track(id, "content://media/$id", title, artists.toList(), null, null, 1000, null)

    private fun artist(
        id: Long,
        name: String,
    ) = Artist(id, name, trackCount = 1)
}
