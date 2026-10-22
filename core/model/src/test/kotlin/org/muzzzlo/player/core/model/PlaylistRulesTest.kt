package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.muzzzlo.player.core.model.PlaylistOrder.Slot

class PlaylistRulesTest {
    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(
        "'Road trip', 'Road trip'",
        "'  Road trip  ', 'Road trip'",
        "'Road   trip', 'Road trip'",
        "'Дорога 🚗', 'Дорога 🚗'",
        "'a', 'a'",
    )
    fun `clean names`(
        raw: String,
        expected: String,
    ) = assertEquals(expected, PlaylistNames.clean(raw))

    @Test
    fun `line breaks and tabs become spaces`() = assertEquals("Late night", PlaylistNames.clean("Late\n\tnight"))

    @Test
    fun `blank names are refused`() {
        listOf("", "   ", "\n\t").forEach { assertNull(PlaylistNames.clean(it), "\"$it\"") }
    }

    @Test
    fun `long names are cut without a trailing space`() {
        val name = "a".repeat(PlaylistNames.MAX_LENGTH - 1) + " b"

        assertEquals("a".repeat(PlaylistNames.MAX_LENGTH - 1), PlaylistNames.clean(name))
        assertEquals(PlaylistNames.MAX_LENGTH, PlaylistNames.clean("x".repeat(500))?.length)
    }

    @Test
    fun `reorder without hidden entries takes the new order`() =
        assertEquals(listOf(3L, 1L, 2L), PlaylistOrder.reorder(slots(1, 2, 3), listOf(3, 1, 2)))

    @Test
    fun `hidden entries keep their slots`() {
        // 2 and 4 are missing files.
        val slots = listOf(Slot(1, true), Slot(2, false), Slot(3, true), Slot(4, false), Slot(5, true))

        assertEquals(listOf(5L, 2L, 1L, 4L, 3L), PlaylistOrder.reorder(slots, listOf(5, 1, 3)))
    }

    @Test
    fun `unknown, hidden and repeated ids from the screen are ignored`() {
        val slots = listOf(Slot(1, true), Slot(2, false), Slot(3, true))

        assertEquals(listOf(3L, 2L, 1L), PlaylistOrder.reorder(slots, listOf(3, 99, 2, 3, 1)))
    }

    @Test
    fun `visible entries the screen did not know keep their order at the end`() =
        assertEquals(listOf(3L, 1L, 2L), PlaylistOrder.reorder(slots(1, 2, 3), listOf(3)))

    @Test
    fun `reorder of an empty playlist`() =
        assertEquals(emptyList<Long>(), PlaylistOrder.reorder(emptyList(), listOf(1)))

    @ParameterizedTest(name = "move {0} -> {1}: {2}")
    @CsvSource(
        "0, 2, 'b c a d'",
        "3, 0, 'd a b c'",
        "1, 2, 'a c b d'",
        "2, 2, 'a b c d'",
        "-1, 2, 'a b c d'",
        "0, 4, 'a b c d'",
    )
    fun `move one item`(
        from: Int,
        to: Int,
        expected: String,
    ) = assertEquals(expected.split(" "), PlaylistOrder.move(listOf("a", "b", "c", "d"), from, to))

    private fun slots(vararg ids: Long) = ids.map { Slot(it, true) }
}
