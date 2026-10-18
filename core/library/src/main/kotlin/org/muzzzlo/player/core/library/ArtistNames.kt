package org.muzzzlo.player.core.library

/** MediaStore reports this literal for files without an artist tag. */
private const val UNKNOWN_ARTIST = "<unknown>"

/**
 * Turns the artist string from MediaStore into a list of names.
 * Splitting of several artists is a later task (0.4.1), for now a non-empty string is one artist.
 */
internal fun artistNames(raw: String?): List<String> {
    val name = raw?.trim().orEmpty()
    return if (name.isEmpty() || name == UNKNOWN_ARTIST) emptyList() else listOf(name)
}

/** Album name without placeholders; null means "no album". */
internal fun cleanAlbum(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }
