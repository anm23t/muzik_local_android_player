package org.muzzzlo.player.core.tags

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.muzzzlo.player.core.model.ReplayGainInfo

class ReplayGainTagsTest {
    @ParameterizedTest(name = "gain \"{0}\" -> {1}")
    @CsvSource(
        nullValues = ["null"],
        value = [
            "-6.50 dB, -6.5",
            "+1.20 dB, 1.2",
            "'  -3.1dB ', -3.1",
            "-6.5 DB, -6.5",
            "'-6,5 dB', -6.5",
            "3, 3",
            "0.00 dB, 0",
            "64, 64",
            "-64.0 dB, -64",
            "64.1, null",
            "-100 dB, null",
            "dB, null",
            "'', null",
            "-6.5 dB extra, null",
            "1e3, null",
            "NaN, null",
            "--6, null",
        ],
    )
    fun `parses ReplayGain gains`(
        value: String?,
        gain: Float?,
    ) {
        assertEquals(gain, ReplayGainTags.parseGain(value ?: ""))
    }

    @ParameterizedTest(name = "peak \"{0}\" -> {1}")
    @CsvSource(
        nullValues = ["null"],
        value = [
            "0.988547, 0.988547",
            "1.02, 1.02",
            "'0,5', 0.5",
            "' 1 ', 1",
            "0, null",
            "0.000000, null",
            "-0.5, null",
            "abc, null",
            "'', null",
        ],
    )
    fun `parses ReplayGain peaks`(
        value: String?,
        peak: Float?,
    ) {
        assertEquals(peak, ReplayGainTags.parsePeak(value ?: ""))
    }

    @ParameterizedTest(name = "R128 \"{0}\" -> {1}")
    @CsvSource(
        nullValues = ["null"],
        value = [
            "0, 5",
            "-1280, 0",
            "-2560, -5",
            "256, 6",
            "' -128 ', 4.5",
            "-32768, -123",
            "32767, 132.99609",
            "32768, null",
            "-1.5, null",
            "'', null",
        ],
    )
    fun `moves Opus R128 gains to the ReplayGain level`(
        value: String?,
        gain: Float?,
    ) {
        assertEquals(gain, ReplayGainTags.parseR128(value ?: ""))
    }

    @Test
    fun `reads all four ReplayGain values`() {
        val info =
            map(
                "REPLAYGAIN_TRACK_GAIN" to listOf("-6.50 dB"),
                "REPLAYGAIN_TRACK_PEAK" to listOf("0.9"),
                "REPLAYGAIN_ALBUM_GAIN" to listOf("-7.25 dB"),
                "REPLAYGAIN_ALBUM_PEAK" to listOf("1.01"),
            ).let(ReplayGainTags::read)
        assertEquals(ReplayGainInfo(-6.5f, 0.9f, -7.25f, 1.01f), info)
    }

    @Test
    fun `matches ReplayGain keys ignoring case`() {
        val info =
            map(
                "replaygain_track_gain" to listOf("-2 dB"),
                "Replaygain_Album_Peak" to listOf("0.5"),
            ).let(ReplayGainTags::read)
        assertEquals(ReplayGainInfo(trackGainDb = -2f, albumPeak = 0.5f), info)
    }

    @Test
    fun `R128 gains win over ReplayGain gains, peaks still come from ReplayGain`() {
        val info =
            map(
                "R128_TRACK_GAIN" to listOf("-1280"),
                "REPLAYGAIN_TRACK_GAIN" to listOf("-9 dB"),
                "REPLAYGAIN_TRACK_PEAK" to listOf("0.7"),
                "REPLAYGAIN_ALBUM_GAIN" to listOf("-3 dB"),
            ).let(ReplayGainTags::read)
        assertEquals(ReplayGainInfo(trackGainDb = 0f, trackPeak = 0.7f, albumGainDb = -3f), info)
    }

    @Test
    fun `broken R128 falls back to the ReplayGain gain`() {
        val info =
            map("R128_TRACK_GAIN" to listOf("loud"), "REPLAYGAIN_TRACK_GAIN" to listOf("-4 dB"))
                .let(ReplayGainTags::read)
        assertEquals(-4f, info.trackGainDb)
    }

    @Test
    fun `takes the first non-blank value and gives nothing without tags`() {
        val info = map("REPLAYGAIN_TRACK_GAIN" to listOf(" ", "-1 dB")).let(ReplayGainTags::read)
        assertEquals(-1f, info.trackGainDb)
        assertEquals(ReplayGainInfo.NONE, ReplayGainTags.read(emptyMap()))
    }

    private fun map(vararg entries: Pair<String, List<String>>) = mapOf(*entries)
}
