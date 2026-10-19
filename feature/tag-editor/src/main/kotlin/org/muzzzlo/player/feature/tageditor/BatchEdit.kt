package org.muzzzlo.player.feature.tageditor

import org.muzzzlo.player.core.model.CoverEdit
import org.muzzzlo.player.core.model.TagEdit
import org.muzzzlo.player.core.model.TrackTags

/**
 * One field of the album editor: the value the tracks share, or a note that they differ.
 *
 * @property initial the shared value; [empty] when the tracks differ.
 * @property value what the user has typed.
 * @property mixed the tracks had different values. Such a field is only applied when the user typed something:
 *   leaving it empty keeps every track's own value (clearing a field for the whole album is done in the track editor).
 * @property empty the empty value of the field, `""` or an empty list.
 */
data class BatchField<T>(
    val initial: T,
    val value: T,
    val mixed: Boolean,
    val empty: T,
) {
    /** True when applying the field changes the tracks. */
    val changed: Boolean get() = if (mixed) value != empty else value != initial

    fun edit(newValue: T): BatchField<T> = copy(value = newValue)

    companion object {
        /** The field of tracks whose values are [values]: shared when all equal, mixed otherwise. */
        fun <T> of(
            values: List<T>,
            empty: T,
        ): BatchField<T> {
            val shared = values.distinct().singleOrNull()
            return if (shared != null) {
                BatchField(shared, shared, mixed = false, empty = empty)
            } else {
                BatchField(empty, empty, mixed = values.isNotEmpty(), empty = empty)
            }
        }
    }
}

/**
 * What the user typed in the album editor: the fields that make sense for a whole album. [applyTo] turns it into
 * the [TagEdit] of one track; the track's other fields stay as they are.
 *
 * The cover can only be replaced here: removing it for many files could not be undone exactly when an album
 * write fails and the files are restored.
 */
data class BatchDraft(
    val album: BatchField<String>,
    val albumArtists: BatchField<List<String>>,
    val year: BatchField<String>,
    val genres: BatchField<List<String>>,
    val cover: CoverEdit = CoverEdit.Keep,
) {
    /** Fields that cannot be written; the year is the only one with a format. */
    fun errors(): Map<TagField, FieldError> =
        if (year.changed && parse(year.value) == Invalid) mapOf(TagField.Year to FieldError.NotANumber) else emptyMap()

    /** The fields the user changed, in screen order; the values are what every track will get. */
    fun changes(): List<TagChange> =
        buildList {
            if (cover != CoverEdit.Keep) add(TagChange(TagField.Cover, "", ""))
            if (album.changed) add(TagChange(TagField.Album, "", album.value.trim()))
            if (albumArtists.changed) {
                add(
                    TagChange(TagField.AlbumArtists, "", albumArtists.value.cleaned().joinToString(" · ")),
                )
            }
            if (year.changed) add(TagChange(TagField.Year, "", year.value.trim()))
            if (genres.changed) add(TagChange(TagField.Genres, "", genres.value.cleaned().joinToString(" · ")))
        }

    /** The edit for a track whose tags are [tags]: its own tags with the changed fields replaced. */
    fun applyTo(tags: TrackTags): TagEdit {
        val edit = TagEdit.from(tags).copy(cover = cover)
        return edit.copy(
            album = if (album.changed) album.value.cleaned() else edit.album,
            albumArtists = if (albumArtists.changed) albumArtists.value.cleaned() else edit.albumArtists,
            year = if (year.changed) parse(year.value).value else edit.year,
            genres = if (genres.changed) genres.value.cleaned() else edit.genres,
        )
    }

    companion object {
        /** The draft that shows what the tracks with [tags] have in common. */
        fun from(tags: List<TrackTags>): BatchDraft =
            BatchDraft(
                album = BatchField.of(tags.map { it.album.orEmpty() }, ""),
                albumArtists = BatchField.of(tags.map { it.albumArtists }, emptyList()),
                year = BatchField.of(tags.map { it.year?.toString().orEmpty() }, ""),
                genres = BatchField.of(tags.map { it.genres }, emptyList()),
            )
    }
}
