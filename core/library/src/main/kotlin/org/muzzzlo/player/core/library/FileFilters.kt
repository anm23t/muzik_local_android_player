package org.muzzzlo.player.core.library

/** `.m4b` is the audiobook container; MediaStore does not always flag such files. */
internal fun isAudiobookFile(displayName: String?): Boolean = displayName?.endsWith(".m4b", ignoreCase = true) == true

/** Encodes strings as a JSON array; used for `track.artist_tag_values`. */
internal fun jsonArray(values: List<String>): String =
    values.joinToString(",", "[", "]") { value ->
        buildString {
            append('"')
            for (c in value) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == '\t' -> append("\\t")
                    c < ' ' -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
            append('"')
        }
    }
