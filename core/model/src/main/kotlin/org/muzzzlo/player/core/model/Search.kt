package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.Flow
import java.text.Normalizer

/** How well a text matches a search query. Earlier entries rank higher (task 1.2.4). */
enum class SearchMatch {
    /** The whole text is the query: "abba" for "ABBA". */
    EXACT,

    /** The text starts with the query: "abb" for "ABBA". */
    PREFIX,

    /** A word of the text starts with the query: "creat" for "Tyler, The Creator". */
    WORD_PREFIX,

    /** The query is somewhere inside the text: "bb" for "ABBA". */
    CONTAINS,

    /** Every word of the query starts a word of the text, in any order: "creator tyler". */
    ALL_WORDS,
}

/**
 * Search keys and matching. A key is [NameNormalizer.key] (case, spaces and `&`/`and` do not matter) with "ё" read
 * as "е" and accents dropped from Latin letters ("Beyoncé" is found by "beyonce"). "й" stays a letter of its own.
 */
object SearchText {
    fun key(text: String): String {
        val normalized = NameNormalizer.key(text)
        return buildString(normalized.length) {
            normalized.forEach { char ->
                when (char) {
                    'ё' -> append('е')
                    'й' -> append(char)
                    else -> appendWithoutAccents(char)
                }
            }
        }
    }

    /** "é" becomes "e": the letter without its combining marks. */
    private fun StringBuilder.appendWithoutAccents(char: Char) {
        Normalizer.normalize(char.toString(), Normalizer.Form.NFD).forEach { part ->
            if (Character.getType(part) != Character.NON_SPACING_MARK.toInt()) append(part)
        }
    }

    /** How [textKey] matches [queryKey] (both made by [key]), or null when it does not. */
    fun match(
        queryKey: String,
        textKey: String,
    ): SearchMatch? {
        if (queryKey.isEmpty() || textKey.isEmpty()) return null
        return when {
            textKey == queryKey -> SearchMatch.EXACT
            textKey.startsWith(queryKey) -> SearchMatch.PREFIX
            startsAWord(queryKey, textKey) -> SearchMatch.WORD_PREFIX
            textKey.contains(queryKey) -> SearchMatch.CONTAINS
            allWordsStartWords(queryKey, textKey) -> SearchMatch.ALL_WORDS
            else -> null
        }
    }

    /** Words of a key: runs of letters and digits, `&` counts as a word. */
    internal fun words(key: String): List<String> =
        buildList {
            val word = StringBuilder()
            key.forEach { char ->
                if (char.isLetterOrDigit() || char == '&') {
                    word.append(char)
                } else if (word.isNotEmpty()) {
                    add(word.toString())
                    word.clear()
                }
            }
            if (word.isNotEmpty()) add(word.toString())
        }

    private fun startsAWord(
        queryKey: String,
        textKey: String,
    ): Boolean {
        var from = textKey.indexOf(queryKey, startIndex = 1)
        while (from > 0) {
            if (!textKey[from - 1].isLetterOrDigit()) return true
            from = textKey.indexOf(queryKey, startIndex = from + 1)
        }
        return false
    }

    private fun allWordsStartWords(
        queryKey: String,
        textKey: String,
    ): Boolean {
        val queryWords = words(queryKey)
        if (queryWords.size < 2) return false
        val textWords = words(textKey)
        return queryWords.all { query -> textWords.any { it.startsWith(query) } }
    }
}

/** An old spelling of an artist ("Check" decisions, renames), searched together with the artist name. */
data class ArtistAlias(
    val artistId: Long,
    val name: String,
)

/** What the search screen shows for one query; each list is in rank order. */
data class SearchResults(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

/**
 * The library prepared for search: keys are built once per library change, not per typed letter.
 *
 * A track is found by its title and by each of its artists separately (they come split, see `ARCHITECTURE.md`,
 * section 4), also through an old spelling of the artist. An album is found by its title, an artist by the name or
 * an old spelling, a playlist by its name. Results go by [SearchMatch]; a title match goes before an artist match of
 * the same rank, and equal ones keep the order of the input lists (by name).
 */
class SearchIndex(
    tracks: List<Track>,
    albums: List<Album>,
    artists: List<Artist>,
    aliases: List<ArtistAlias>,
    playlists: List<Playlist>,
) {
    private val artistKeys: Map<Long, List<String>>
    private val trackEntries: List<Entry<Track>>
    private val albumEntries = albums.map { Entry(it, listOf(SearchText.key(it.title))) }
    private val artistEntries: List<Entry<Artist>>
    private val playlistEntries = playlists.map { Entry(it, listOf(SearchText.key(it.name))) }

    init {
        val aliasKeys = aliases.groupBy({ it.artistId }, { SearchText.key(it.name) })
        artistKeys = artists.associate { it.id to (listOf(SearchText.key(it.name)) + aliasKeys[it.id].orEmpty()) }
        artistEntries = artists.map { Entry(it, artistKeys.getValue(it.id)) }
        // Old spellings reach a track through the artist with the same name key.
        val spellingsByName = artists.associate { SearchText.key(it.name) to artistKeys.getValue(it.id) }
        trackEntries =
            tracks.map { track ->
                val artistNameKeys = track.artists.map(SearchText::key)
                val spellings = artistNameKeys.flatMap { spellingsByName[it] ?: listOf(it) }
                Entry(track, listOf(SearchText.key(track.title)), spellings.distinct())
            }
    }

    /** Results for [query]; a blank query finds nothing. */
    fun search(query: String): SearchResults {
        val queryKey = SearchText.key(query)
        if (queryKey.isEmpty()) return SearchResults()
        return SearchResults(
            tracks = ranked(trackEntries, queryKey),
            albums = ranked(albumEntries, queryKey),
            artists = ranked(artistEntries, queryKey),
            playlists = ranked(playlistEntries, queryKey),
        )
    }

    private fun <T> ranked(
        entries: List<Entry<T>>,
        queryKey: String,
    ): List<T> =
        entries
            .mapIndexedNotNull { index, entry -> entry.rank(queryKey)?.let { Ranked(entry.item, it, index) } }
            .sortedWith(compareBy({ it.rank }, { it.index }))
            .map { it.item }

    /** An item with its main keys (title, name) and secondary keys (artists of a track). */
    private class Entry<T>(
        val item: T,
        val mainKeys: List<String>,
        val secondaryKeys: List<String> = emptyList(),
    ) {
        /** Smaller is better: the match rank, a secondary key a half step lower. */
        fun rank(queryKey: String): Int? {
            val main = mainKeys.mapNotNull { SearchText.match(queryKey, it) }.minOrNull()
            val secondary = secondaryKeys.mapNotNull { SearchText.match(queryKey, it) }.minOrNull()
            val mainRank = main?.let { it.ordinal * 2 }
            val secondaryRank = secondary?.let { it.ordinal * 2 + 1 }
            return listOfNotNull(mainRank, secondaryRank).minOrNull()
        }
    }

    private class Ranked<T>(
        val item: T,
        val rank: Int,
        val index: Int,
    )
}

/** The searchable library; a new [SearchIndex] comes after every change of tracks, artists or playlists. */
interface SearchRepository {
    fun observeIndex(): Flow<SearchIndex>
}
