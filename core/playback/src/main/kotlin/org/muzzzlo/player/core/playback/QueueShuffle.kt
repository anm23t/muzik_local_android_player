package org.muzzzlo.player.core.playback

import kotlin.random.Random

/**
 * Shuffle as a real reorder of the queue (task 2.5.3): the queue on screen is always the order of playback.
 *
 * Both functions return a permutation: element `i` of the result is the old index of the item that goes to place
 * `i`. The caller moves its own items, so their extras (the playlist mark) stay with them.
 */
internal object QueueShuffle {
    /**
     * Turning shuffle on: the item at [currentIndex] goes first, the others follow in random order. With no current
     * item ([currentIndex] out of range) everything is shuffled.
     */
    fun shuffledOrder(
        size: Int,
        currentIndex: Int,
        random: Random,
    ): List<Int> {
        if (currentIndex !in 0 until size) return (0 until size).shuffled(random)
        val others = (0 until size).filter { it != currentIndex }.shuffled(random)
        return listOf(currentIndex) + others
    }

    /**
     * Turning shuffle off: the queue goes back to [original], the order it had when shuffle was turned on.
     *
     * - Items of [original] still in [queue] take their original order; removed ones are simply gone.
     * - Items added while shuffled (not matched in [original]) stay together in their current order and go right
     *   after the current item. When the current item is itself an added one, the block goes after the nearest
     *   original item before it in [queue], or to the start when there is none.
     * - The same track may stand in the queue more than once: ids are matched by count, in queue order.
     *
     * Moves made while shuffled are not kept: the original order wins.
     */
    fun unshuffledOrder(
        queue: List<Long>,
        currentIndex: Int,
        original: List<Long>,
    ): List<Int> {
        val free = HashMap<Long, ArrayDeque<Int>>()
        queue.forEachIndexed { index, id -> free.getOrPut(id) { ArrayDeque() }.addLast(index) }
        val restored = original.mapNotNull { id -> free[id]?.removeFirstOrNull() }
        val taken = restored.toHashSet()
        val added = queue.indices.filter { it !in taken }
        if (added.isEmpty()) return restored
        val anchor =
            if (currentIndex in taken) {
                currentIndex
            } else {
                (currentIndex - 1 downTo 0).firstOrNull { it in taken }
            }
        val insertAt = if (anchor == null) 0 else restored.indexOf(anchor) + 1
        return restored.subList(0, insertAt) + added + restored.subList(insertAt, restored.size)
    }
}
