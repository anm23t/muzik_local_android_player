package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

class ReplayGainTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `factor follows mode, tags and clip protection`(
        @Suppress("UNUSED_PARAMETER") name: String,
        settings: ReplayGainSettings,
        info: ReplayGainInfo,
        albumInOrder: Boolean,
        expectedDb: Float,
    ) {
        assertEquals(ReplayGain.dbToLinear(expectedDb), ReplayGain.factor(settings, info, albumInOrder), TOLERANCE)
    }

    @Test
    fun `clip protection keeps the peak at full scale`() {
        val info = ReplayGainInfo(trackGainDb = 6f, trackPeak = 0.8f)
        assertEquals(1.25f, ReplayGain.factor(ReplayGainSettings(mode = ReplayGainMode.TRACK), info, false), TOLERANCE)
    }

    @Test
    fun `clip protection never raises the gain`() {
        val info = ReplayGainInfo(trackGainDb = -6f, trackPeak = 0.5f)
        val factor = ReplayGain.factor(ReplayGainSettings(mode = ReplayGainMode.TRACK), info, false)
        assertEquals(ReplayGain.dbToLinear(-6f), factor, TOLERANCE)
    }

    @Test
    fun `peak over full scale lowers the gain below one`() {
        val info = ReplayGainInfo(trackGainDb = 0f, trackPeak = 1.25f)
        assertEquals(0.8f, ReplayGain.factor(ReplayGainSettings(mode = ReplayGainMode.TRACK), info, false), TOLERANCE)
    }

    @Test
    fun `decibels to linear`() {
        assertEquals(1f, ReplayGain.dbToLinear(0f), TOLERANCE)
        assertEquals(10f, ReplayGain.dbToLinear(20f), TOLERANCE)
        assertEquals(0.5012f, ReplayGain.dbToLinear(-6f), TOLERANCE)
    }

    @ParameterizedTest
    @CsvSource(
        "0, 0",
        "0.2, 0",
        "0.3, 0.5",
        "-6.24, -6",
        "-6.26, -6.5",
        "15, 15",
        "15.5, 15",
        "-20, -15",
        "NaN, 0",
    )
    fun `preamp is stepped and clamped`(
        input: Float,
        expected: Float,
    ) {
        assertEquals(expected, ReplayGainSettings(preampDb = input).clamped().preampDb)
    }

    @ParameterizedTest
    @CsvSource("-6, -6", "0, 0", "3, 0", "-15, -15", "-16, -15", "-0.7, -0.5")
    fun `untagged gain is stepped and clamped to non-positive values`(
        input: Float,
        expected: Float,
    ) {
        assertEquals(expected, ReplayGainSettings(untaggedGainDb = input).clamped().untaggedGainDb)
    }

    @Test
    fun `defaults match ARCHITECTURE section 9`() {
        val settings = ReplayGainSettings()
        assertEquals(ReplayGainMode.AUTO, settings.mode)
        assertEquals(0f, settings.preampDb)
        assertEquals(-6f, settings.untaggedGainDb)
        assertEquals(true, settings.clipProtection)
    }

    companion object {
        private const val TOLERANCE = 1e-4f
        private val noClip = ReplayGainSettings(clipProtection = false)
        private val both = ReplayGainInfo(trackGainDb = -3f, trackPeak = 0.5f, albumGainDb = -5f, albumPeak = 0.6f)

        private fun case(
            name: String,
            settings: ReplayGainSettings,
            info: ReplayGainInfo,
            albumInOrder: Boolean,
            expectedDb: Float,
        ) = Arguments.of(name, settings, info, albumInOrder, expectedDb)

        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                case("off ignores tags", noClip.copy(mode = ReplayGainMode.OFF), both, true, 0f),
                case(
                    "off ignores untagged gain",
                    noClip.copy(mode = ReplayGainMode.OFF),
                    ReplayGainInfo.NONE,
                    false,
                    0f,
                ),
                case("track", noClip.copy(mode = ReplayGainMode.TRACK), both, true, -3f),
                case("album", noClip.copy(mode = ReplayGainMode.ALBUM), both, false, -5f),
                case("auto, album in order", noClip, both, true, -5f),
                case("auto, shuffled or mixed", noClip, both, false, -3f),
                case(
                    "album mode, only track gain",
                    noClip.copy(mode = ReplayGainMode.ALBUM),
                    only(track = -2f),
                    true,
                    -2f,
                ),
                case(
                    "track mode, only album gain",
                    noClip.copy(mode = ReplayGainMode.TRACK),
                    only(album = -4f),
                    false,
                    -4f,
                ),
                case("no tags use the untagged gain", noClip, ReplayGainInfo.NONE, true, -6f),
                case("peak alone is no gain", noClip, ReplayGainInfo(trackPeak = 0.9f), false, -6f),
                case("preamp adds to the tag", noClip.copy(preampDb = 4.5f), both, false, 1.5f),
                case("preamp skips untagged files", noClip.copy(preampDb = 4.5f), ReplayGainInfo.NONE, false, -6f),
                case("lowest preamp", noClip.copy(mode = ReplayGainMode.TRACK, preampDb = -15f), both, false, -18f),
                case("highest preamp", noClip.copy(mode = ReplayGainMode.TRACK, preampDb = 15f), both, false, 12f),
                case("untagged gain 0", noClip.copy(untaggedGainDb = 0f), ReplayGainInfo.NONE, false, 0f),
                case("positive gain without peak", ReplayGainSettings(), only(track = 8f), false, 8f),
                case(
                    "zero peak is ignored",
                    ReplayGainSettings(),
                    ReplayGainInfo(trackGainDb = 8f, trackPeak = 0f),
                    false,
                    8f,
                ),
                case(
                    "clip uses album peak in album mode",
                    ReplayGainSettings(),
                    both.copy(albumGainDb = 10f),
                    true,
                    4.437f,
                ),
            )

        private fun only(
            track: Float? = null,
            album: Float? = null,
        ) = ReplayGainInfo(trackGainDb = track, albumGainDb = album)
    }
}
