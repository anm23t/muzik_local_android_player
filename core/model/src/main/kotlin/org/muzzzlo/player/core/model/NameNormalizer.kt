package org.muzzzlo.player.core.model

/** Builds comparison keys for names: case, extra spaces and `&`/`and` do not matter. */
object NameNormalizer {
    private val whitespace = Regex("\\s+")
    private val andWord = Regex("(^|\\s)and(\\s|$)")

    fun key(name: String): String {
        val lowered = name.trim().lowercase().replace("&", " and ")
        return lowered.replace(whitespace, " ").replace(andWord, "$1&$2").trim()
    }
}
