package org.muzzzlo.player.core.model

/** The lyrics of a track as the player shows them (task 2.0.7). */
sealed interface Lyrics {
    /** Lyrics without times: the player only scrolls them. */
    data class Plain(val text: String) : Lyrics

    /** Synchronised lyrics (LRC): [lines] in time order, at least one. */
    data class Synced(val lines: List<LyricLine>) : Lyrics
}

/** One line of synchronised lyrics; an empty [text] is a pause between verses. */
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

/**
 * Reads LRC text (task 2.0.7). A line may carry several time tags (`[00:12.00][01:05.50]Chorus`) and becomes one
 * line per tag. `[offset:+500]` moves every line 500 ms earlier, as the format says. Service tags (`[ar:]`, `[ti:]`,
 * `[al:]`, `[by:]` and the like), lines with broken time tags and lines without tags are left out; word times of
 * the extended format (`<00:12.34>`) are dropped from the text. Text without any valid time tag is plain lyrics.
 */
object LrcParser {
    /** `[mm:ss]`, `[mm:ss.x]`, `[mm:ss.xx]`, `[mm:ss.xxx]`; `:` may stand for the dot, as some editors write it. */
    private val TIME_TAG = Regex("""\[(\d{1,3}):([0-5]\d)(?:[.:](\d{1,3}))?]""")

    /** A leading run of time tags. */
    private val LEADING_TIMES = Regex("""^(\s*\[\d{1,3}:[0-5]\d(?:[.:]\d{1,3})?])+""")

    private val OFFSET = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]\s*$""", RegexOption.IGNORE_CASE)
    private val WORD_TIME = Regex("""<\d{1,3}:[0-5]\d(?:[.:]\d{1,3})?>""")

    private const val MS_PER_SECOND = 1000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val FRACTION_DIGITS = 3

    /** The lyrics in [text], null when it is blank. */
    fun parse(text: String): Lyrics? {
        val clean = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        if (clean.isBlank()) return null
        val rows = clean.split('\n')
        val offsetMs = rows.firstNotNullOfOrNull { OFFSET.find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L
        val lines =
            rows.flatMap { row -> timedLines(row) }
                .map { it.copy(timeMs = (it.timeMs - offsetMs).coerceAtLeast(0)) }
                .sortedBy { it.timeMs }
        return if (lines.isEmpty()) Lyrics.Plain(clean.trim()) else Lyrics.Synced(lines)
    }

    private fun timedLines(row: String): List<LyricLine> {
        val tags = LEADING_TIMES.find(row) ?: return emptyList()
        val text = row.substring(tags.range.last + 1).replace(WORD_TIME, "").trim()
        return TIME_TAG.findAll(tags.value).map { LyricLine(timeOf(it), text) }.toList()
    }

    private fun timeOf(tag: MatchResult): Long {
        val (minutes, seconds, fraction) = tag.destructured
        // "5" is 500 ms, "05" 50 ms, "005" 5 ms.
        val fractionMs = if (fraction.isEmpty()) 0L else fraction.padEnd(FRACTION_DIGITS, '0').toLong()
        return (minutes.toLong() * SECONDS_PER_MINUTE + seconds.toLong()) * MS_PER_SECOND + fractionMs
    }
}

/**
 * Index of the line sung at [positionMs]: the last line that started at or before it, -1 before the first line.
 * Lines with the same time count as one moment, the last of them is the current one.
 */
fun Lyrics.Synced.lineAt(positionMs: Long): Int {
    var low = 0
    var high = lines.size - 1
    var found = -1
    while (low <= high) {
        val middle = (low + high) ushr 1
        if (lines[middle].timeMs <= positionMs) {
            found = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    return found
}

/** Where the lyrics of a track come from (task 2.0.7). */
enum class LyricsSource {
    /** The lyrics tag of the file. */
    TAG,

    /** A `.lrc` file of the same name next to the audio file. */
    FILE,
}

/** Lyrics with their source. */
data class TrackLyrics(
    val lyrics: Lyrics,
    val source: LyricsSource,
)

/**
 * The lyrics of a track: the tag of the file wins; only when it is empty is the `.lrc` file next to it read
 * ([readFile] is not called otherwise). Null when neither has any.
 */
suspend fun chooseLyrics(
    tagText: String?,
    readFile: suspend () -> String?,
): TrackLyrics? {
    tagText?.let(LrcParser::parse)?.let { return TrackLyrics(it, LyricsSource.TAG) }
    return readFile()?.let(LrcParser::parse)?.let { TrackLyrics(it, LyricsSource.FILE) }
}

/** Reads the lyrics of tracks, from the file on every call (decision 1). Implemented in `core:library`. */
interface LyricsRepository {
    /** The lyrics of [trackId], null when it has none or its file cannot be read. Switches to the I/O dispatcher. */
    suspend fun lyricsOf(trackId: Long): TrackLyrics?
}

/** How the player keeps synchronised lyrics in view. */
object LyricsScroll {
    /** After the user scrolls the lyrics, the player waits this long before it brings the current line back. */
    const val USER_PAUSE_MS = 4_000L

    /** True when the player may scroll to the current line again: the user's last scroll ended long enough ago. */
    fun autoScrollAllowed(
        userScrolling: Boolean,
        lastUserScrollEndMs: Long?,
        nowMs: Long,
    ): Boolean {
        if (userScrolling) return false
        return lastUserScrollEndMs == null || nowMs - lastUserScrollEndMs >= USER_PAUSE_MS
    }
}
