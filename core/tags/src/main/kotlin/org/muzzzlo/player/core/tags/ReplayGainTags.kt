package org.muzzzlo.player.core.tags

import org.muzzzlo.player.core.model.ReplayGainInfo
import kotlin.math.abs

/**
 * Reads ReplayGain values from a TagLib property map. ID3v2 `TXXX:REPLAYGAIN_*`, Vorbis comments and MP4 freeform
 * atoms all arrive under the name of the field; Opus files carry `R128_*` gains instead (ARCHITECTURE.md, section 6).
 */
internal object ReplayGainTags {
    private val gainValue = Regex("""^\s*([+-]?\d+(?:[.,]\d+)?)\s*(?:dB)?\s*$""", RegexOption.IGNORE_CASE)
    private val peakValue = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*$""")
    private val r128Value = Regex("""^\s*([+-]?\d+)\s*$""")

    /** Gains beyond this many decibels are taken for broken values. */
    private const val MAX_GAIN_DB = 64f

    /** Opus R128 gains are relative to −23 LUFS, ReplayGain to about −18 LUFS: the difference is 5 dB. */
    private const val R128_TO_REPLAY_GAIN_DB = 5f
    private const val R128_STEPS_PER_DB = 256f

    /**
     * ReplayGain values of a file. Keys are matched ignoring case, because M4A freeform atoms and some ID3v2 taggers
     * write them in lower case. Opus `R128_TRACK_GAIN` and `R128_ALBUM_GAIN` (Q7.8 integers relative to −23 LUFS)
     * take priority over `REPLAYGAIN_*` gains and are moved to the ReplayGain reference level. Broken values are null.
     */
    fun read(properties: Map<String, List<String>>): ReplayGainInfo {
        val byKey = properties.mapKeys { (key, _) -> key.uppercase() }

        fun value(key: String) = byKey[key].orEmpty().firstOrNull { it.isNotBlank() }
        return ReplayGainInfo(
            trackGainDb = parseR128(value("R128_TRACK_GAIN")) ?: parseGain(value("REPLAYGAIN_TRACK_GAIN")),
            trackPeak = parsePeak(value("REPLAYGAIN_TRACK_PEAK")),
            albumGainDb = parseR128(value("R128_ALBUM_GAIN")) ?: parseGain(value("REPLAYGAIN_ALBUM_GAIN")),
            albumPeak = parsePeak(value("REPLAYGAIN_ALBUM_PEAK")),
        )
    }

    /** A gain such as `-6.50 dB`, `+1.2 dB`, `-6,5` or `3`; null when broken or beyond ±[MAX_GAIN_DB]. */
    fun parseGain(value: String?): Float? =
        value?.let { gainValue.find(it) }?.groupValues?.get(1)?.toDecimal()
            ?.takeIf { it.isFinite() && abs(it) <= MAX_GAIN_DB }

    /** A peak such as `0.988` or `1.02`; null when broken, zero or negative. */
    fun parsePeak(value: String?): Float? =
        value?.let { peakValue.find(it) }?.groupValues?.get(1)?.toDecimal()?.takeIf { it.isFinite() && it > 0f }

    /** An Opus R128 gain (`-1280` is −5 dB at −23 LUFS) in ReplayGain decibels; null when broken. */
    fun parseR128(value: String?): Float? =
        value?.let { r128Value.find(it) }?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { it in Short.MIN_VALUE..Short.MAX_VALUE }
            ?.let { it / R128_STEPS_PER_DB + R128_TO_REPLAY_GAIN_DB }

    private fun String.toDecimal(): Float? = replace(',', '.').toFloatOrNull()
}
