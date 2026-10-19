package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

class CrossfadeTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("overlapCases")
    fun `overlap follows settings, album order and track lengths`(
        @Suppress("UNUSED_PARAMETER") name: String,
        settings: CrossfadeSettings,
        current: CrossfadeTrack,
        next: CrossfadeTrack?,
        repeatOne: Boolean,
        expectedMs: Long,
    ) {
        assertEquals(expectedMs, Crossfade.overlapMs(settings, current, next, repeatOne))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("albumCases")
    fun `continues album`(
        @Suppress("UNUSED_PARAMETER") name: String,
        current: CrossfadeTrack,
        next: CrossfadeTrack,
        expected: Boolean,
    ) {
        assertEquals(expected, Crossfade.continuesAlbum(current, next))
    }

    @ParameterizedTest
    @CsvSource("-1, 0", "0, 0", "5, 5", "12, 12", "13, 12", "1000, 12")
    fun `seconds are clamped`(
        input: Int,
        expected: Int,
    ) {
        assertEquals(expected, CrossfadeSettings(seconds = input).clamped().seconds)
    }

    @Test
    fun `defaults match ARCHITECTURE section 9`() {
        assertEquals(CrossfadeSettings(seconds = 0, withinAlbum = false), AppSettings().crossfade)
    }

    @Test
    fun `fade curve starts, meets and ends at the right volumes`() {
        assertEquals(0f, Crossfade.fadeInVolume(0f), TOLERANCE)
        assertEquals(1f, Crossfade.fadeOutVolume(0f), TOLERANCE)
        assertEquals(0.7071f, Crossfade.fadeInVolume(0.5f), TOLERANCE)
        assertEquals(0.7071f, Crossfade.fadeOutVolume(0.5f), TOLERANCE)
        assertEquals(1f, Crossfade.fadeInVolume(1f), TOLERANCE)
        assertEquals(0f, Crossfade.fadeOutVolume(1f), TOLERANCE)
    }

    @ParameterizedTest
    @CsvSource("0", "0.1", "0.25", "0.5", "0.8", "1")
    fun `fade keeps the power constant`(progress: Float) {
        val fadeIn = Crossfade.fadeInVolume(progress)
        val fadeOut = Crossfade.fadeOutVolume(progress)
        assertEquals(1f, fadeIn * fadeIn + fadeOut * fadeOut, TOLERANCE)
    }

    @ParameterizedTest
    @CsvSource("-0.5, 0, 1", "1.5, 1, 0")
    fun `fade progress outside the range is clamped`(
        progress: Float,
        expectedIn: Float,
        expectedOut: Float,
    ) {
        assertEquals(expectedIn, Crossfade.fadeInVolume(progress), TOLERANCE)
        assertEquals(expectedOut, Crossfade.fadeOutVolume(progress), TOLERANCE)
    }

    companion object {
        private const val TOLERANCE = 0.0005f
        private const val MINUTES_4 = 240_000L

        private fun track(
            album: Long? = null,
            disc: Int? = null,
            no: Int? = null,
            durationMs: Long = MINUTES_4,
        ) = CrossfadeTrack(album, disc, no, durationMs)

        private val five = CrossfadeSettings(seconds = 5)
        private val withAlbum = CrossfadeSettings(seconds = 5, withinAlbum = true)

        @JvmStatic
        fun overlapCases(): List<Arguments> =
            listOf(
                Arguments.of("off", CrossfadeSettings(), track(1), track(2), false, 0L),
                Arguments.of("different albums", five, track(1, no = 3), track(2, no = 4), false, 5_000L),
                Arguments.of("no albums", five, track(), track(), false, 5_000L),
                Arguments.of("end of queue", five, track(1), null, false, 0L),
                Arguments.of("repeat one", five, track(1), track(2), true, 0L),
                Arguments.of("album in order", five, track(1, no = 3), track(1, no = 4), false, 0L),
                Arguments.of("album, within album on", withAlbum, track(1, no = 3), track(1, no = 4), false, 5_000L),
                Arguments.of("same album, shuffled", five, track(1, no = 3), track(1, no = 7), false, 5_000L),
                Arguments.of("maximum", CrossfadeSettings(seconds = 12), track(1), track(2), false, 12_000L),
                Arguments.of("over maximum", CrossfadeSettings(seconds = 30), track(1), track(2), false, 12_000L),
                Arguments.of("negative is off", CrossfadeSettings(seconds = -3), track(1), track(2), false, 0L),
                Arguments.of("short current track", five, track(1, durationMs = 6_000), track(2), false, 3_000L),
                Arguments.of("short next track", five, track(1), track(2, durationMs = 4_000), false, 2_000L),
                Arguments.of("unknown length", five, track(1, durationMs = 0), track(2), false, 0L),
            )

        @JvmStatic
        fun albumCases(): List<Arguments> =
            listOf(
                Arguments.of("next number", track(1, no = 3), track(1, no = 4), true),
                Arguments.of("previous number", track(1, no = 4), track(1, no = 3), false),
                Arguments.of("skips a number", track(1, no = 3), track(1, no = 5), false),
                Arguments.of("same number", track(1, no = 3), track(1, no = 3), false),
                Arguments.of("other album", track(1, no = 3), track(2, no = 4), false),
                Arguments.of("no album", track(null, no = 3), track(null, no = 4), false),
                Arguments.of("next disc starts", track(1, disc = 1, no = 12), track(1, disc = 2, no = 1), true),
                Arguments.of("disc 2, not first", track(1, disc = 1, no = 12), track(1, disc = 2, no = 2), false),
                Arguments.of("disc 2, next number", track(1, disc = 1, no = 3), track(1, disc = 2, no = 4), false),
                Arguments.of("disc missing is disc 1", track(1, no = 3), track(1, disc = 1, no = 4), true),
                Arguments.of("no disc to disc 2", track(1, no = 9), track(1, disc = 2, no = 1), true),
                Arguments.of("no numbers", track(1), track(1), true),
                Arguments.of("next has no number", track(1, no = 3), track(1), true),
            )
    }
}
