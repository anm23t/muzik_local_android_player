package org.muzzzlo.player.core.model

/** Text is not valid JSON, or valid JSON of a wrong shape. */
class JsonException(message: String) : Exception(message)

/**
 * A small JSON reader and writer for the backup, so `core:model` stays free of Android and of libraries.
 * Values are `null`, [Boolean], [Long], [Double], [String], [List] and [Map] with string keys.
 */
object Json {
    /** Parses [text]; throws [JsonException] on anything that is not one complete JSON value. */
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.skipSpaces()
        if (!reader.atEnd()) reader.fail("Unexpected text after the value")
        return value
    }

    /** Writes [value] as compact JSON, pretty-printed with two spaces when [pretty] is set. */
    fun write(
        value: Any?,
        pretty: Boolean = false,
    ): String = StringBuilder().also { write(it, value, pretty, 0) }.toString()

    private fun write(
        out: StringBuilder,
        value: Any?,
        pretty: Boolean,
        depth: Int,
    ) {
        when (value) {
            null -> out.append("null")
            is Boolean, is Int, is Long -> out.append(value.toString())
            is Float -> out.append(number(value.toDouble()))
            is Double -> out.append(number(value))
            is String -> quote(out, value)
            is List<*> -> block(out, '[', ']', value, pretty, depth) { item -> write(out, item, pretty, depth + 1) }
            is Map<*, *> ->
                block(out, '{', '}', value.entries.toList(), pretty, depth) { entry ->
                    quote(out, entry.key.toString())
                    out.append(if (pretty) ": " else ":")
                    write(out, entry.value, pretty, depth + 1)
                }
            else -> throw JsonException("Cannot write ${value::class.simpleName}")
        }
    }

    private fun number(value: Double): String {
        if (value.isNaN() || value.isInfinite()) throw JsonException("JSON has no NaN or infinity")
        val whole = value == Math.rint(value) && Math.abs(value) < MAX_EXACT
        return if (whole) value.toLong().toString() else value.toString()
    }

    private fun <T> block(
        out: StringBuilder,
        open: Char,
        close: Char,
        items: List<T>,
        pretty: Boolean,
        depth: Int,
        item: (T) -> Unit,
    ) {
        out.append(open)
        items.forEachIndexed { index, element ->
            if (index > 0) out.append(',')
            if (pretty) newLine(out, depth + 1)
            item(element)
        }
        if (pretty && items.isNotEmpty()) newLine(out, depth)
        out.append(close)
    }

    private fun newLine(
        out: StringBuilder,
        depth: Int,
    ) {
        out.append('\n')
        repeat(depth) { out.append("  ") }
    }

    private fun quote(
        out: StringBuilder,
        text: String,
    ) {
        out.append('"')
        for (char in text) {
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' -> out.append("\\\\")
                char == '\n' -> out.append("\\n")
                char == '\r' -> out.append("\\r")
                char == '\t' -> out.append("\\t")
                char < ' ' -> out.append("\\u").append(char.code.toString(HEX).padStart(UNICODE_DIGITS, '0'))
                else -> out.append(char)
            }
        }
        out.append('"')
    }

    private const val MAX_EXACT = 9.0e15
    private const val HEX = 16
    private const val UNICODE_DIGITS = 4

    private class Reader(private val text: String) {
        private var pos = 0

        fun atEnd() = pos >= text.length

        fun fail(message: String): Nothing = throw JsonException("$message at position $pos")

        fun skipSpaces() {
            while (!atEnd() && text[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            skipSpaces()
            if (atEnd()) fail("Unexpected end")
            return when (val char = text[pos]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (char == '-' || char.isDigit()) number() else fail("Unexpected '$char'")
            }
        }

        private fun literal(
            word: String,
            result: Any?,
        ): Any? {
            if (!text.startsWith(word, pos)) fail("Unexpected text")
            pos += word.length
            return result
        }

        private fun obj(): Map<String, Any?> {
            pos++
            val map = LinkedHashMap<String, Any?>()
            skipSpaces()
            if (peek() == '}') return map.also { pos++ }
            while (true) {
                skipSpaces()
                if (peek() != '"') fail("Expected a key")
                val key = string()
                skipSpaces()
                expect(':')
                map[key] = value()
                skipSpaces()
                if (next() == '}') return map
                pos--
                expect(',')
            }
        }

        private fun array(): List<Any?> {
            pos++
            val list = ArrayList<Any?>()
            skipSpaces()
            if (peek() == ']') return list.also { pos++ }
            while (true) {
                list += value()
                skipSpaces()
                if (next() == ']') return list
                pos--
                expect(',')
            }
        }

        private fun string(): String {
            pos++
            val out = StringBuilder()
            while (true) {
                if (atEnd()) fail("Unterminated string")
                val char = text[pos++]
                when {
                    char == '"' -> return out.toString()
                    char == '\\' -> out.append(escape())
                    char < ' ' -> fail("Control character in a string")
                    else -> out.append(char)
                }
            }
        }

        private fun escape(): Char {
            if (atEnd()) fail("Unterminated escape")
            return when (val char = text[pos++]) {
                '"', '\\', '/' -> char
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    val hex = text.substring(pos, minOf(text.length, pos + UNICODE_DIGITS))
                    val code = hex.takeIf { it.length == UNICODE_DIGITS }?.toIntOrNull(HEX) ?: fail("Bad \\u escape")
                    pos += UNICODE_DIGITS
                    code.toChar()
                }
                else -> fail("Bad escape '\\$char'")
            }
        }

        private fun number(): Any {
            val start = pos
            if (text[pos] == '-') pos++
            while (!atEnd() && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
            val raw = text.substring(start, pos)
            return raw.toLongOrNull() ?: raw.toDoubleOrNull()?.takeIf { it.isFinite() } ?: fail("Bad number '$raw'")
        }

        private fun peek(): Char = if (atEnd()) fail("Unexpected end") else text[pos]

        private fun next(): Char = peek().also { pos++ }

        private fun expect(char: Char) {
            if (next() != char) {
                pos--
                fail("Expected '$char'")
            }
        }
    }
}
