package org.muzzzlo.player.core.model

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale
import kotlin.math.abs

/**
 * One line of a playlist file with the `#EXTINF` line before it, if there was one.
 *
 * @property path the location as written in the file: absolute, relative, a `file://` URL or a Windows path.
 * @property durationSeconds from `#EXTINF`; null when missing or unknown (`-1`).
 * @property title the text after the comma of `#EXTINF`, usually "Artist - Title".
 */
data class M3uEntry(
    val path: String,
    val durationSeconds: Int? = null,
    val title: String? = null,
)

/**
 * A track to write to a playlist file.
 *
 * @property path absolute path of the file on the device, for example `/storage/emulated/0/Music/a.flac`.
 */
data class M3uItem(
    val path: String,
    val durationMs: Long,
    val artists: List<String>,
    val title: String,
)

/** Reading and writing M3U and M3U8 playlists (extended M3U: `#EXTM3U` and `#EXTINF`). */
object M3u {
    private const val HEADER = "#EXTM3U"
    private const val INFO = "#EXTINF:"
    private const val MS_PER_SECOND = 1000

    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /** Windows playlists without UTF-8 are usually in this code page in Russian-speaking countries. */
    private val FALLBACK: Charset = Charset.forName("windows-1251")

    /**
     * Text of a playlist file. M3U8 is UTF-8; an old M3U may be in a local code page, so bytes that are not valid
     * UTF-8 are read as Windows-1251. A UTF-8 byte order mark is dropped.
     */
    fun decode(bytes: ByteArray): String {
        val body = if (bytes.startsWith(UTF8_BOM)) bytes.copyOfRange(UTF8_BOM.size, bytes.size) else bytes
        val strict =
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            strict.decode(ByteBuffer.wrap(body)).toString()
        } catch (_: CharacterCodingException) {
            String(body, FALLBACK)
        }
    }

    /** The entries of [text] in order. Comments, directives other than `#EXTINF` and blank lines are skipped. */
    fun parse(text: String): List<M3uEntry> {
        val entries = ArrayList<M3uEntry>()
        var info: Pair<Int?, String?>? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("\uFEFF")
            when {
                line.isEmpty() -> Unit
                line.startsWith(INFO, ignoreCase = true) -> info = parseInfo(line.substring(INFO.length))
                line.startsWith("#") -> Unit
                else -> {
                    entries += M3uEntry(line, info?.first, info?.second)
                    info = null
                }
            }
        }
        return entries
    }

    /** An M3U8 file (UTF-8, `\n` line ends) with the tracks in order. */
    fun encode(items: List<M3uItem>): String =
        buildString {
            append(HEADER).append('\n')
            for (item in items) {
                val seconds = if (item.durationMs > 0) (item.durationMs + MS_PER_SECOND / 2) / MS_PER_SECOND else -1
                val name =
                    listOf(item.artists.joinToString(", "), item.title)
                        .map(::oneLine)
                        .filter { it.isNotEmpty() }
                        .joinToString(" - ")
                append(INFO).append(seconds).append(',').append(name).append('\n')
                append(oneLine(item.path)).append('\n')
            }
        }

    /**
     * The playlist name for a file called [fileName]: without the `.m3u` or `.m3u8` extension, cleaned like a name
     * typed by the user ([PlaylistNames.clean]); [fallback] when nothing is left.
     */
    fun playlistName(
        fileName: String?,
        fallback: String,
    ): String {
        val base = fileName.orEmpty().replace(Regex("\\.m3u8?$", RegexOption.IGNORE_CASE), "")
        return PlaylistNames.clean(base) ?: fallback
    }

    /** `123 tvg-id="x",Artist - Title`: the number before attributes, the text after the first comma. */
    private fun parseInfo(value: String): Pair<Int?, String?> {
        val comma = value.indexOf(',')
        val head = if (comma >= 0) value.substring(0, comma) else value
        val seconds = head.trim().takeWhile { it == '-' || it.isDigit() }.toIntOrNull()?.takeIf { it >= 0 }
        val title = if (comma >= 0) value.substring(comma + 1).trim().ifEmpty { null } else null
        return seconds to title
    }

    private fun oneLine(text: String): String = text.replace(Regex("[\\r\\n]+"), " ").trim()

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}

/**
 * A file of the library for [M3uMatcher].
 *
 * @property path where the file is: an absolute path or one relative to the storage root, `/` between folders.
 */
data class LibraryFile(
    val trackId: Long,
    val path: String,
    val durationMs: Long,
)

/**
 * The result of matching a playlist file against the library.
 *
 * @property trackIds found tracks in the order of the file; a track listed twice is there twice.
 * @property unmatched paths of the entries that matched no file, as written in the playlist.
 */
data class M3uMatch(
    val trackIds: List<Long>,
    val unmatched: List<String>,
)

/**
 * Finds library files for the entries of a playlist written on another device or computer, where the storage
 * root differs (`C:\Music\...`, `/sdcard/Music/...`, `../Music/...`).
 *
 * Paths are compared by their last parts, ignoring case: the file whose path shares the longest tail with the entry
 * wins, so a match by folder and name beats a match by name alone. A tail of the name alone is accepted too (the
 * fallback "by file name"). Among files with equally long tails the one whose duration is within a second of
 * `#EXTINF` wins, otherwise the first in [files].
 */
object M3uMatcher {
    private const val DURATION_TOLERANCE_MS = 1000L
    private const val MS_PER_SECOND = 1000L

    /** `%XX` */
    private const val ESCAPE_LENGTH = 3
    private const val HEX = 16

    fun match(
        entries: List<M3uEntry>,
        files: List<LibraryFile>,
    ): M3uMatch {
        val byName = HashMap<String, MutableList<Pair<LibraryFile, List<String>>>>()
        for (file in files) {
            val parts = parts(file.path)
            val name = parts.lastOrNull() ?: continue
            byName.getOrPut(name) { ArrayList() } += file to parts
        }
        val found = ArrayList<Long>()
        val unmatched = ArrayList<String>()
        for (entry in entries) {
            val parts = parts(entry.path)
            val candidates = parts.lastOrNull()?.let(byName::get)
            val best = candidates?.let { pick(entry, parts, it) }
            if (best != null) found += best.trackId else unmatched += entry.path
        }
        return M3uMatch(found, unmatched)
    }

    /**
     * Lowercase parts of [path] without the parts that say nothing about the file: a `file://` scheme (its `%XX`
     * escapes are decoded), a drive letter, `.` and `..`.
     */
    internal fun parts(path: String): List<String> {
        var text = path.trim()
        if (text.startsWith("file:", ignoreCase = true)) {
            text = percentDecode(text.substring("file:".length))
        }
        return text.replace('\\', '/')
            .split('/')
            .filterIndexed { index, part -> !(index == 0 && part.length == 2 && part[1] == ':') }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .map { it.lowercase(Locale.ROOT) }
    }

    private fun pick(
        entry: M3uEntry,
        parts: List<String>,
        candidates: List<Pair<LibraryFile, List<String>>>,
    ): LibraryFile {
        val longest = candidates.maxOf { (_, other) -> commonTail(parts, other) }
        val best = candidates.filter { (_, other) -> commonTail(parts, other) == longest }.map { it.first }
        val seconds = entry.durationSeconds ?: return best.first()
        return best.firstOrNull { abs(it.durationMs - seconds * MS_PER_SECOND) <= DURATION_TOLERANCE_MS }
            ?: best.first()
    }

    private fun commonTail(
        a: List<String>,
        b: List<String>,
    ): Int {
        var n = 0
        while (n < a.size && n < b.size && a[a.size - 1 - n] == b[b.size - 1 - n]) n++
        return n
    }

    /** `%20` and other escapes of a URL as UTF-8; a broken escape stays as written. */
    private fun percentDecode(text: String): String {
        if ('%' !in text) return text
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val hex =
                if (c == '%' && i + ESCAPE_LENGTH <= text.length) {
                    text.substring(
                        i + 1,
                        i + ESCAPE_LENGTH,
                    )
                } else {
                    null
                }
            val value = hex?.toIntOrNull(HEX)
            if (value != null) {
                bytes.write(value)
                i += ESCAPE_LENGTH
            } else {
                bytes.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return bytes.toString(Charsets.UTF_8.name())
    }
}
