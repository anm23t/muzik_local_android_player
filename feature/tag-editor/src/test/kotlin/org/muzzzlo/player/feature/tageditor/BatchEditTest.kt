package org.muzzzlo.player.feature.tageditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.CoverEdit
import org.muzzzlo.player.core.model.EmbeddedPicture
import org.muzzzlo.player.core.model.TagEdit
import org.muzzzlo.player.core.model.TrackTags

class BatchEditTest {
    private val one =
        TrackTags(
            title = "One",
            artists = listOf("A"),
            album = "Album",
            albumArtists = listOf("X"),
            year = 2020,
            genres = listOf("Rock"),
            trackNumber = 1,
        )
    private val two =
        TrackTags(
            title = "Two",
            artists = listOf("B"),
            album = "Album",
            albumArtists = listOf("X"),
            year = 2021,
            genres = listOf("Rock"),
            trackNumber = 2,
        )

    @Test
    fun `equal values are shared and different ones are mixed`() {
        val draft = BatchDraft.from(listOf(one, two))

        assertFalse(draft.album.mixed)
        assertEquals("Album", draft.album.value)
        assertEquals(listOf("X"), draft.albumArtists.value)
        assertTrue(draft.year.mixed)
        assertEquals("", draft.year.value)
        assertEquals(emptyList<TagChange>(), draft.changes())
    }

    @Test
    fun `a missing value on every track is shared as empty`() {
        val draft = BatchDraft.from(listOf(TrackTags(), TrackTags()))

        assertFalse(draft.album.mixed)
        assertFalse(draft.album.changed)
    }

    @Test
    fun `a mixed field is applied only when something is typed`() {
        val draft = BatchDraft.from(listOf(one, two))

        assertEquals(2020, draft.applyTo(one).year)
        assertEquals(2021, draft.applyTo(two).year)
        val typed = draft.copy(year = draft.year.edit("1999"))
        assertEquals(1999, typed.applyTo(one).year)
        assertEquals(1999, typed.applyTo(two).year)
    }

    @Test
    fun `a shared field can be cleared for every track`() {
        val draft = BatchDraft.from(listOf(one, two))
        val cleared = draft.copy(album = draft.album.edit("  "), genres = draft.genres.edit(emptyList()))

        assertEquals(null, cleared.applyTo(one).album)
        assertEquals(emptyList<String>(), cleared.applyTo(two).genres)
    }

    @Test
    fun `other fields of a track stay as they are`() {
        val draft = BatchDraft.from(listOf(one, two))
        val edited = draft.copy(album = draft.album.edit("New"))

        val edit = edited.applyTo(two)

        assertEquals(TagEdit.from(two).copy(album = "New"), edit)
        assertEquals("Two", edit.title)
        assertEquals(listOf("B"), edit.artists)
        assertEquals(2, edit.trackNumber)
    }

    @Test
    fun `list values are trimmed and blank ones dropped`() {
        val draft = BatchDraft.from(listOf(one, two))
        val edited = draft.copy(albumArtists = draft.albumArtists.edit(listOf(" Y ", " ", "Z")))

        assertEquals(listOf("Y", "Z"), edited.applyTo(one).albumArtists)
    }

    @Test
    fun `reordering the album artists is a change written to every track`() {
        val tracks = listOf(one.copy(albumArtists = listOf("X", "Y")), two.copy(albumArtists = listOf("X", "Y")))
        val draft = BatchDraft.from(tracks)
        val moved = draft.copy(albumArtists = draft.albumArtists.edit(draft.albumArtists.value.moveDown(0)))

        assertTrue(moved.albumArtists.changed)
        assertEquals(listOf("Y", "X"), moved.applyTo(tracks[0]).albumArtists)
        assertEquals(listOf("Y", "X"), moved.applyTo(tracks[1]).albumArtists)
    }

    @Test
    fun `the cover goes to every track`() {
        val picture = EmbeddedPicture(byteArrayOf(1), "image/png", EmbeddedPicture.TYPE_FRONT_COVER)
        val draft = BatchDraft.from(listOf(one, two)).copy(cover = CoverEdit.Replace(picture))

        assertEquals(CoverEdit.Replace(picture), draft.applyTo(one).cover)
        assertEquals(listOf(TagChange(TagField.Cover, "", "")), draft.changes())
    }

    @Test
    fun `changes list the new values in screen order`() {
        val draft = BatchDraft.from(listOf(one, two))
        val edited =
            draft.copy(
                album = draft.album.edit("New"),
                year = draft.year.edit("1999"),
                genres = draft.genres.edit(listOf("Pop", "Soul")),
            )

        assertEquals(
            listOf(
                TagChange(TagField.Album, "", "New"),
                TagChange(TagField.Year, "", "1999"),
                TagChange(TagField.Genres, "", "Pop · Soul"),
            ),
            edited.changes(),
        )
    }

    @Test
    fun `a bad year is an error only when it is applied`() {
        val draft = BatchDraft.from(listOf(one, two))

        assertEquals(emptyMap<TagField, FieldError>(), draft.errors())
        assertEquals(
            mapOf(TagField.Year to FieldError.NotANumber),
            draft.copy(year = draft.year.edit("x")).errors(),
        )
        assertEquals(emptyMap<TagField, FieldError>(), draft.copy(year = draft.year.edit("1999")).errors())
    }
}
