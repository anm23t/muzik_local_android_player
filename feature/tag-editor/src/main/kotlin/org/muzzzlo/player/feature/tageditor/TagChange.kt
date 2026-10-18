package org.muzzzlo.player.feature.tageditor

import org.muzzzlo.player.core.model.CoverEdit
import org.muzzzlo.player.core.model.TagEdit
import org.muzzzlo.player.core.model.TrackTags

/**
 * One changed field for the confirmation before writing: the values as they are in the file and as they will be.
 * List values are joined with ` · ` (a `;` or `,` may be part of one value), track and disc as `3/10`, a missing
 * value is empty. Lyrics and cover are not shown as text: for them both values are empty and the screen names
 * the change.
 */
data class TagChange(
    val field: TagField,
    val before: String,
    val after: String,
)

/**
 * The fields [edit] changes in a file whose tags are [original], in screen order. Removing the cover of a file
 * without pictures is no change.
 */
fun changesOf(
    original: TrackTags,
    edit: TagEdit,
): List<TagChange> {
    val before = TagEdit.from(original).cleaned()
    val after = edit.cleaned()
    return buildList {
        val coverChanged =
            when (after.cover) {
                CoverEdit.Keep -> false
                CoverEdit.Remove -> original.hasCover
                is CoverEdit.Replace -> true
            }
        if (coverChanged) add(TagChange(TagField.Cover, "", ""))
        addIfChanged(TagField.Title, before.title.orEmpty(), after.title.orEmpty())
        addIfChanged(TagField.Artists, before.artists, after.artists)
        addIfChanged(TagField.Album, before.album.orEmpty(), after.album.orEmpty())
        addIfChanged(TagField.AlbumArtists, before.albumArtists, after.albumArtists)
        addIfChanged(TagField.Track, before.trackText(), after.trackText())
        addIfChanged(TagField.Disc, before.discText(), after.discText())
        addIfChanged(TagField.Year, before.year?.toString().orEmpty(), after.year?.toString().orEmpty())
        addIfChanged(TagField.Genres, before.genres, after.genres)
        if (before.lyrics != after.lyrics) add(TagChange(TagField.Lyrics, "", ""))
    }
}

private fun MutableList<TagChange>.addIfChanged(
    field: TagField,
    before: String,
    after: String,
) {
    if (before != after) add(TagChange(field, before, after))
}

/** The same trimming the writer does, so equal values compare equal. */
private fun TagEdit.cleaned(): TagEdit =
    copy(
        title = title?.cleaned(),
        artists = artists.cleaned(),
        album = album?.cleaned(),
        albumArtists = albumArtists.cleaned(),
        genres = genres.cleaned(),
        lyrics = lyrics?.cleaned(),
    )

/** Lists are compared value by value: `[A; B]` and `[A, B]` differ even if they read the same when joined. */
private fun MutableList<TagChange>.addIfChanged(
    field: TagField,
    before: List<String>,
    after: List<String>,
) {
    if (before != after) add(TagChange(field, before.joined(), after.joined()))
}

private fun List<String>.joined(): String = joinToString(" · ")

private fun TagEdit.trackText(): String = pairText(trackNumber, trackTotal)

private fun TagEdit.discText(): String = pairText(discNumber, discTotal)

private fun pairText(
    number: Int?,
    total: Int?,
): String =
    when {
        number == null -> ""
        total == null -> number.toString()
        else -> "$number/$total"
    }
