package org.muzzzlo.player.feature.tageditor

import org.muzzzlo.player.core.model.ArtistSplitter
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

/** Separator of the parts of a name glued by [mergeWithNext]; the same comma the splitter cuts by. */
private const val MERGE_SEPARATOR = ", "

/** The artist names [value] splits into by the usual rules (`;`, else `,`); one name when it cannot be split. */
internal fun splitParts(value: String): List<String> = ArtistSplitter.split(listOf(value))

/** True when [splitAt] would change the list: the value holds `;` or `,` between two names. */
internal fun canSplit(value: String): Boolean = splitParts(value).size > 1

/** Replaces the value at [index] with the names it splits into; the list stays as it is when it cannot be split. */
internal fun List<String>.splitAt(index: Int): List<String> {
    val parts = getOrNull(index)?.let(::splitParts).orEmpty()
    if (parts.size < 2) return this
    return take(index) + parts + drop(index + 1)
}

/** Joins the value at [index] with the next one into one name `A, B`; a blank side is dropped. */
internal fun List<String>.mergeWithNext(index: Int): List<String> {
    if (index !in 0 until lastIndex) return this
    val merged = listOfNotNull(this[index].cleaned(), this[index + 1].cleaned()).joinToString(MERGE_SEPARATOR)
    return take(index) + merged + drop(index + 2)
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
