package org.muzzzlo.player.core.model

/**
 * Turns the raw genre values of a file into separate genres (task 2.5.4).
 *
 * Every value is cut by `;`: ID3v2.3 has no separate values and keeps several genres in one string joined with
 * `; `, and the tag editor writes them so. A comma does not cut: a genre with a comma in its name is more common
 * than several genres joined by commas. Unlike the artist rule, `;` cuts also when the file has several values.
 */
object GenreSplitter {
    /**
     * @param values values of the genre field in file order.
     * @return genres in order, trimmed, without empty parts and repeats (compared by [NameNormalizer.key]).
     */
    fun split(values: List<String>): List<String> =
        values
            .flatMap { it.split(';') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { NameNormalizer.key(it) }
}
