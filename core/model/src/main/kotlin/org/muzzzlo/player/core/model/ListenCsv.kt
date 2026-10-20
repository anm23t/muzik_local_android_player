package org.muzzzlo.player.core.model

/** CSV export of listen events (RFC 4180: comma, quotes doubled, CRLF). Artists are joined with `; `. */
object ListenCsv {
    private const val HEADER = "started_at,played_ms,duration_ms,counted,skipped,title,artists"

    /** [formatTime] turns a Unix time in milliseconds into text, for example ISO 8601 UTC. */
    fun encode(
        events: List<BackupEvent>,
        formatTime: (Long) -> String,
    ): String =
        buildString {
            append(HEADER).append("\r\n")
            for (e in events) {
                append(formatTime(e.startedAtMs)).append(',')
                append(e.playedMs).append(',')
                append(e.durationMs).append(',')
                append(if (e.counted) 1 else 0).append(',')
                append(if (e.skipped) 1 else 0).append(',')
                append(field(e.title)).append(',')
                append(field(e.artists.joinToString("; "))).append("\r\n")
            }
        }

    private fun field(value: String): String {
        val safe = if (value.firstOrNull() in FORMULA_STARTS) "'$value" else value
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /** A spreadsheet runs a cell that starts with one of these as a formula. */
    private val FORMULA_STARTS = setOf('=', '+', '-', '@')
}
