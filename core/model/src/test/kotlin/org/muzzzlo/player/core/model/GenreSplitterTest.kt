package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class GenreSplitterTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `split table`(
        @Suppress("UNUSED_PARAMETER") case: String,
        values: List<String>,
        expected: List<String>,
    ) {
        assertEquals(expected, GenreSplitter.split(values))
    }

    companion object {
        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                Arguments.of("no values", emptyList<String>(), emptyList<String>()),
                Arguments.of("one genre", listOf("Rock"), listOf("Rock")),
                Arguments.of("separate values", listOf("Rock", "Pop"), listOf("Rock", "Pop")),
                Arguments.of("ID3v2.3 joined", listOf("Rock; Pop"), listOf("Rock", "Pop")),
                Arguments.of("semicolon without space", listOf("Rock;Pop"), listOf("Rock", "Pop")),
                Arguments.of(
                    "semicolon inside one of several",
                    listOf("Jazz", "Rock; Pop"),
                    listOf("Jazz", "Rock", "Pop"),
                ),
                Arguments.of("comma does not cut", listOf("Rock, Pop"), listOf("Rock, Pop")),
                Arguments.of("blank parts dropped", listOf(" ; Rock ;;", "  "), listOf("Rock")),
                Arguments.of(
                    "repeats by key",
                    listOf("Drum & Bass", "drum and bass", "DRUM & BASS"),
                    listOf("Drum & Bass"),
                ),
                Arguments.of("trimmed", listOf("  Ambient  "), listOf("Ambient")),
                Arguments.of("cyrillic", listOf("Рок; Поп"), listOf("Рок", "Поп")),
            )
    }
}
