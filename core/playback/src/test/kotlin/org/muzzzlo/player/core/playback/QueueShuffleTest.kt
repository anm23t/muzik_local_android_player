package org.muzzzlo.player.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.random.Random

class QueueShuffleTest {
    private fun <T> List<T>.reordered(order: List<Int>) = order.map(::get)

    @Test
    fun `shuffle keeps every item once and puts the current one first`() {
        repeat(20) { seed ->
            val order = QueueShuffle.shuffledOrder(10, currentIndex = 7, Random(seed))
            assertEquals(7, order.first())
            assertEquals((0 until 10).toList(), order.sorted())
        }
    }

    @Test
    fun `shuffle really changes the order of a long queue`() {
        val order = QueueShuffle.shuffledOrder(30, currentIndex = 0, Random(1))
        assertNotEquals((0 until 30).toList(), order)
    }

    @Test
    fun `one track and an empty queue`() {
        assertEquals(listOf(0), QueueShuffle.shuffledOrder(1, 0, Random(3)))
        assertEquals(emptyList<Int>(), QueueShuffle.shuffledOrder(0, -1, Random(3)))
        assertEquals(emptyList<Int>(), QueueShuffle.unshuffledOrder(emptyList(), -1, listOf(1L)))
    }

    @Test
    fun `the current track at the end of the queue goes first`() {
        val order = QueueShuffle.shuffledOrder(5, currentIndex = 4, Random(9))
        assertEquals(4, order.first())
    }

    @Test
    fun `without a current item everything is shuffled`() {
        val order = QueueShuffle.shuffledOrder(5, currentIndex = -1, Random(9))
        assertEquals((0 until 5).toList(), order.sorted())
    }

    @Test
    fun `shuffle and back gives the original order`() {
        val original = listOf(10L, 20L, 30L, 40L, 50L)
        val shuffled = original.reordered(QueueShuffle.shuffledOrder(5, currentIndex = 2, Random(4)))
        val back = shuffled.reordered(QueueShuffle.unshuffledOrder(shuffled, currentIndex = 0, original = original))
        assertEquals(original, back)
    }

    @Test
    fun `turning shuffle on again shuffles the current queue`() {
        val original = listOf(1L, 2L, 3L, 4L, 5L, 6L)
        val first = original.reordered(QueueShuffle.shuffledOrder(6, 0, Random(1)))
        val back = first.reordered(QueueShuffle.unshuffledOrder(first, 0, original))
        val second = back.reordered(QueueShuffle.shuffledOrder(6, 0, Random(2)))
        assertEquals(1L, second.first())
        assertEquals(original, second.reordered(QueueShuffle.unshuffledOrder(second, 0, back)))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unshuffleCases")
    fun `unshuffle table`(
        @Suppress("UNUSED_PARAMETER") case: String,
        queue: List<Long>,
        currentIndex: Int,
        original: List<Long>,
        expected: List<Long>,
    ) {
        assertEquals(expected, queue.reordered(QueueShuffle.unshuffledOrder(queue, currentIndex, original)))
    }

    companion object {
        @JvmStatic
        fun unshuffleCases(): List<Arguments> =
            listOf(
                Arguments.of("nothing changed", listOf(3L, 1L, 2L), 0, listOf(1L, 2L, 3L), listOf(1L, 2L, 3L)),
                Arguments.of(
                    "added after the current go right after it",
                    listOf(3L, 9L, 8L, 1L, 2L),
                    0,
                    listOf(1L, 2L, 3L, 4L),
                    listOf(1L, 2L, 3L, 9L, 8L),
                ),
                Arguments.of(
                    "removed tracks are gone",
                    listOf(4L, 2L),
                    1,
                    listOf(1L, 2L, 3L, 4L),
                    listOf(2L, 4L),
                ),
                Arguments.of(
                    "added track playing: after the nearest original before it",
                    listOf(3L, 1L, 9L, 2L),
                    2,
                    listOf(1L, 2L, 3L),
                    listOf(1L, 9L, 2L, 3L),
                ),
                Arguments.of(
                    "added track playing first: block to the start",
                    listOf(9L, 3L, 1L),
                    0,
                    listOf(1L, 3L),
                    listOf(9L, 1L, 3L),
                ),
                Arguments.of(
                    "added and already played still go after the current",
                    listOf(7L, 2L, 1L),
                    1,
                    listOf(1L, 2L),
                    listOf(1L, 2L, 7L),
                ),
                Arguments.of(
                    "a track twice in the queue is matched by count",
                    listOf(2L, 1L, 2L),
                    0,
                    listOf(1L, 2L),
                    listOf(1L, 2L, 2L),
                ),
                Arguments.of(
                    "everything was replaced",
                    listOf(7L, 8L),
                    0,
                    listOf(1L, 2L),
                    listOf(7L, 8L),
                ),
            )
    }
}
