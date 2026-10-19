package org.muzzzlo.player.feature.tageditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ArtistListEditTest {
    @ParameterizedTest(name = "split {0} at {1}")
    @MethodSource("splits")
    fun split(
        values: List<String>,
        index: Int,
        expected: List<String>,
    ) {
        assertEquals(expected, values.splitAt(index))
    }

    @ParameterizedTest(name = "merge {0} at {1}")
    @MethodSource("merges")
    fun merge(
        values: List<String>,
        index: Int,
        expected: List<String>,
    ) {
        assertEquals(expected, values.mergeWithNext(index))
    }

    @ParameterizedTest(name = "move {0} up at {1}")
    @MethodSource("movesUp")
    fun moveUpAt(
        values: List<String>,
        index: Int,
        expected: List<String>,
    ) {
        assertEquals(expected, values.moveUp(index))
    }

    @ParameterizedTest(name = "move {0} down at {1}")
    @MethodSource("movesDown")
    fun moveDownAt(
        values: List<String>,
        index: Int,
        expected: List<String>,
    ) {
        assertEquals(expected, values.moveDown(index))
    }

    @ParameterizedTest(name = "canSplit({0}) = {1}")
    @MethodSource("splittable")
    fun canSplitValue(
        value: String,
        expected: Boolean,
    ) {
        assertEquals(expected, canSplit(value))
    }

    @Test
    fun `merging then splitting restores the names`() {
        val values = listOf("Tyler", "The Creator", "Kanye West")

        assertEquals(values, values.mergeWithNext(0).splitAt(0))
    }

    companion object {
        @JvmStatic
        fun splits(): List<Arguments> =
            listOf(
                Arguments.of(listOf("A; B"), 0, listOf("A", "B")),
                Arguments.of(listOf("A, B, C"), 0, listOf("A", "B", "C")),
                Arguments.of(listOf("A, B; C"), 0, listOf("A, B", "C")),
                Arguments.of(listOf("X", "A; B", "Y"), 1, listOf("X", "A", "B", "Y")),
                Arguments.of(listOf("Tyler, The Creator"), 0, listOf("Tyler", "The Creator")),
                Arguments.of(listOf("Simon & Garfunkel"), 0, listOf("Simon & Garfunkel")),
                Arguments.of(listOf("A; a"), 0, listOf("A; a")),
                Arguments.of(listOf("A", "B"), 5, listOf("A", "B")),
                Arguments.of(listOf("A", "B"), -1, listOf("A", "B")),
                Arguments.of(emptyList<String>(), 0, emptyList<String>()),
            )

        @JvmStatic
        fun merges(): List<Arguments> =
            listOf(
                Arguments.of(listOf("Tyler", "The Creator"), 0, listOf("Tyler, The Creator")),
                Arguments.of(listOf("A", "B", "C"), 1, listOf("A", "B, C")),
                Arguments.of(listOf("A", "B", "C"), 0, listOf("A, B", "C")),
                Arguments.of(listOf("A", " ", "C"), 0, listOf("A", "C")),
                Arguments.of(listOf("", "B"), 0, listOf("B")),
                Arguments.of(listOf("A", "B"), 1, listOf("A", "B")),
                Arguments.of(listOf("A"), 0, listOf("A")),
                Arguments.of(listOf("A", "B"), -1, listOf("A", "B")),
            )

        @JvmStatic
        fun movesUp(): List<Arguments> =
            listOf(
                Arguments.of(listOf("A", "B", "C"), 1, listOf("B", "A", "C")),
                Arguments.of(listOf("A", "B", "C"), 2, listOf("A", "C", "B")),
                Arguments.of(listOf("A", "B", "C"), 0, listOf("A", "B", "C")),
                Arguments.of(listOf("A", "", "C"), 2, listOf("A", "C", "")),
                Arguments.of(listOf("A"), 0, listOf("A")),
                Arguments.of(listOf("A", "B"), 5, listOf("A", "B")),
                Arguments.of(listOf("A", "B"), -1, listOf("A", "B")),
                Arguments.of(emptyList<String>(), 0, emptyList<String>()),
            )

        @JvmStatic
        fun movesDown(): List<Arguments> =
            listOf(
                Arguments.of(listOf("A", "B", "C"), 0, listOf("B", "A", "C")),
                Arguments.of(listOf("A", "B", "C"), 1, listOf("A", "C", "B")),
                Arguments.of(listOf("A", "B", "C"), 2, listOf("A", "B", "C")),
                Arguments.of(listOf("A"), 0, listOf("A")),
                Arguments.of(listOf("A", "B"), 5, listOf("A", "B")),
                Arguments.of(listOf("A", "B"), -1, listOf("A", "B")),
                Arguments.of(emptyList<String>(), 0, emptyList<String>()),
            )

        @JvmStatic
        fun splittable(): List<Arguments> =
            listOf(
                Arguments.of("A; B", true),
                Arguments.of("A, B", true),
                Arguments.of("A", false),
                Arguments.of("", false),
                Arguments.of("A;", false),
                Arguments.of("Simon & Garfunkel", false),
            )
    }
}
