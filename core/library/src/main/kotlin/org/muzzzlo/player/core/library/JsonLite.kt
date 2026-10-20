package org.muzzzlo.player.core.library

/**
 * Reads the two small JSON shapes the library stores or ships, without a JSON library (and so in plain JVM tests):
 * an array of strings (`track.artist_tag_values`) and the built-in compound names list.
 */
internal object JsonLite {
    private val stringLiteral = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
    private const val QUOTED = "\"((?:[^\"\\\\]|\\\\.)*)\""
    private val entry = Regex("\\{\\s*\"id\"\\s*:\\s*$QUOTED\\s*,\\s*\"name\"\\s*:\\s*$QUOTED\\s*\\}")

    /** The strings of a JSON array of strings, `[]` and anything that is not an array give an empty list. */
    fun strings(json: String): List<String> {
        val text = json.trim()
        if (!text.startsWith("[") || !text.endsWith("]")) return emptyList()
        return stringLiteral.findAll(text).map { unescape(it.groupValues[1]) }.toList()
    }

    /** Id and name of every entry of the built-in list (`{"id": "...", "name": "..."}`), in file order. */
    fun builtInEntries(json: String): List<Pair<String, String>> =
        entry.findAll(json).map { unescape(it.groupValues[1]) to unescape(it.groupValues[2]) }.toList()

    private fun unescape(raw: String): String {
        if ('\\' !in raw) return raw
        val out = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i + 1 >= raw.length) {
                out.append(c)
                i++
                continue
            }
            when (val next = raw[i + 1]) {
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'u' ->
                    if (raw.length >= i + UNICODE_ESCAPE_LENGTH) {
                        val hex = raw.substring(i + ESCAPE_PREFIX_LENGTH, i + UNICODE_ESCAPE_LENGTH)
                        out.append(hex.toInt(HEX_RADIX).toChar())
                        i += UNICODE_ESCAPE_LENGTH - ESCAPE_PREFIX_LENGTH
                    } else {
                        out.append(next)
                    }
                else -> out.append(next)
            }
            i += 2
        }
        return out.toString()
    }

    private const val UNICODE_ESCAPE_LENGTH = 6
    private const val ESCAPE_PREFIX_LENGTH = 2
    private const val HEX_RADIX = 16
}
