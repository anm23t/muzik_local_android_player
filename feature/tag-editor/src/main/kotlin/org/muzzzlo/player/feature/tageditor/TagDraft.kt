package org.muzzzlo.player.feature.tageditor

import org.muzzzlo.player.core.model.CoverEdit
import org.muzzzlo.player.core.model.TagEdit
import org.muzzzlo.player.core.model.TrackTags

/** Fields of the tag editor in screen order. Track and disc each cover a number and a total. */
enum class TagField {
    Cover,
    Title,
    Artists,
    Album,
    AlbumArtists,
    Track,
    Disc,
    Year,
    Genres,
    Lyrics,
}

/** Why a field cannot be written. */
enum class FieldError {
    /** Not a whole number from 1 to [MAX_NUMBER]. */
    NotANumber,

    /** A total without its number: ID3v2 and MP4 store both as one `3/10` value and would drop it. */
    TotalWithoutNumber,
}

/** Largest track, disc and year value the editor accepts. */
const val MAX_NUMBER = 9999

/**
 * What the user has typed in the editor: texts as they are, numbers not parsed yet, empty rows of lists kept.
 * [toEdit] turns it into the [TagEdit] that is written.
 */
data class TagDraft(
    val title: String = "",
    val artists: List<String> = emptyList(),
    val album: String = "",
    val albumArtists: List<String> = emptyList(),
    val trackNumber: String = "",
    val trackTotal: String = "",
    val discNumber: String = "",
    val discTotal: String = "",
    val year: String = "",
    val genres: List<String> = emptyList(),
    val lyrics: String = "",
    val cover: CoverEdit = CoverEdit.Keep,
) {
    /** Fields that cannot be written as they are; empty when [toEdit] succeeds. */
    fun errors(): Map<TagField, FieldError> =
        buildMap {
            pairError(trackNumber, trackTotal)?.let { put(TagField.Track, it) }
            pairError(discNumber, discTotal)?.let { put(TagField.Disc, it) }
            if (parse(year) == Invalid) put(TagField.Year, FieldError.NotANumber)
        }

    /** The edit to write, null while [errors] is not empty. Texts are trimmed and blank values dropped. */
    fun toEdit(): TagEdit? {
        if (errors().isNotEmpty()) return null
        return TagEdit(
            title = title.cleaned(),
            artists = artists.cleaned(),
            album = album.cleaned(),
            albumArtists = albumArtists.cleaned(),
            trackNumber = parse(trackNumber).value,
            trackTotal = parse(trackTotal).value,
            discNumber = parse(discNumber).value,
            discTotal = parse(discTotal).value,
            year = parse(year).value,
            genres = genres.cleaned(),
            lyrics = lyrics.cleaned(),
            cover = cover,
        )
    }

    companion object {
        /** The draft that shows [tags] as they are. */
        fun from(tags: TrackTags): TagDraft =
            TagDraft(
                title = tags.title.orEmpty(),
                artists = tags.artists,
                album = tags.album.orEmpty(),
                albumArtists = tags.albumArtists,
                trackNumber = tags.trackNumber?.toString().orEmpty(),
                trackTotal = tags.trackTotal?.toString().orEmpty(),
                discNumber = tags.discNumber?.toString().orEmpty(),
                discTotal = tags.discTotal?.toString().orEmpty(),
                year = tags.year?.toString().orEmpty(),
                genres = tags.genres,
                lyrics = tags.lyrics.orEmpty(),
            )
    }
}

internal fun String.cleaned(): String? = trim().ifEmpty { null }

internal fun List<String>.cleaned(): List<String> = mapNotNull { it.cleaned() }

private fun pairError(
    number: String,
    total: String,
): FieldError? {
    val parsedNumber = parse(number)
    val parsedTotal = parse(total)
    return when {
        parsedNumber == Invalid || parsedTotal == Invalid -> FieldError.NotANumber
        parsedNumber.value == null && parsedTotal.value != null -> FieldError.TotalWithoutNumber
        else -> null
    }
}

/** A number field: empty, a valid number or invalid text. */
internal sealed interface Parsed {
    val value: Int?
}

internal data class Valid(override val value: Int?) : Parsed

internal data object Invalid : Parsed {
    override val value: Int? get() = null
}

internal fun parse(text: String): Parsed {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return Valid(null)
    val number = trimmed.takeIf { digits -> digits.all { it in '0'..'9' } }?.toIntOrNull()
    return if (number != null && number in 1..MAX_NUMBER) Valid(number) else Invalid
}
