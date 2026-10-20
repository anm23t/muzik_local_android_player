package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ListenSessionTest {
    private var now = 0L
    private val finished = mutableListOf<FinishedListen>()
    private val session = ListenSession({ now }, { WALL + now }, finished::add)

    @Test
    fun `a track played to its end is one completed listen`() {
        session.onTrackChanged(A, ListenEnd.MANUAL)
        session.onPlayingChanged(true)
        now = 180_000
        session.onTrackChanged(B, ListenEnd.COMPLETED)

        assertEquals(listOf(FinishedListen(A, WALL, 180_000, ListenEnd.COMPLETED)), finished)
    }

    @Test
    fun `the next track opens at once while playing`() {
        session.onTrackChanged(A, ListenEnd.MANUAL)
        session.onPlayingChanged(true)
        now = 10_000
        session.onTrackChanged(B, ListenEnd.COMPLETED)
        now = 25_000
        session.onTrackChanged(null, ListenEnd.MANUAL)

        assertEquals(FinishedListen(B, WALL + 10_000, 15_000, ListenEnd.MANUAL), finished.last())
    }

    @Test
    fun `pauses are not counted and the start is when it first sounded`() {
        session.onTrackChanged(A, ListenEnd.MANUAL)
        now = 5_000
        session.onPlayingChanged(true)
        now = 15_000
        session.onPlayingChanged(false)
        now = 100_000
        session.onPlayingChanged(true)
        now = 110_000
        session.finish(ListenEnd.INTERRUPTED)

        assertEquals(listOf(FinishedListen(A, WALL + 5_000, 20_000, ListenEnd.INTERRUPTED)), finished)
    }

    @Test
    fun `a track that never sounded is not reported`() {
        session.onTrackChanged(A, ListenEnd.MANUAL)
        now = 50_000
        session.onTrackChanged(B, ListenEnd.MANUAL)
        session.finish(ListenEnd.INTERRUPTED)

        assertEquals(emptyList<FinishedListen>(), finished)
    }

    @Test
    fun `a change while paused closes the listen, the next opens when it plays`() {
        session.onTrackChanged(A, ListenEnd.MANUAL)
        session.onPlayingChanged(true)
        now = 3_000
        session.onPlayingChanged(false)
        now = 9_000
        session.onTrackChanged(B, ListenEnd.MANUAL)
        now = 20_000
        session.onPlayingChanged(true)
        now = 22_000
        session.finish(ListenEnd.MANUAL)

        assertEquals(
            listOf(
                FinishedListen(A, WALL, 3_000, ListenEnd.MANUAL),
                FinishedListen(B, WALL + 20_000, 2_000, ListenEnd.MANUAL),
            ),
            finished,
        )
    }

    @Test
    fun `after the end of the queue playing again opens a new listen of the same track`() {
        session.onTrackChanged(A, ListenEnd.MANUAL)
        session.onPlayingChanged(true)
        now = 180_000
        session.finish(ListenEnd.COMPLETED)
        session.onPlayingChanged(false)
        now = 200_000
        session.onPlayingChanged(true)
        now = 205_000
        session.finish(ListenEnd.MANUAL)

        assertEquals(FinishedListen(A, WALL + 200_000, 5_000, ListenEnd.MANUAL), finished.last())
        assertEquals(2, finished.size)
    }

    @Test
    fun `repeated play state reports change nothing`() {
        session.onTrackChanged(A, ListenEnd.MANUAL)
        session.onPlayingChanged(true)
        now = 4_000
        session.onPlayingChanged(true)
        now = 6_000
        session.onPlayingChanged(false)
        session.onPlayingChanged(false)
        session.finish(ListenEnd.MANUAL)

        assertEquals(6_000, finished.single().playedMs)
    }

    @Test
    fun `finish twice reports once`() {
        session.onTrackChanged(A, ListenEnd.MANUAL)
        session.onPlayingChanged(true)
        now = 1_000
        session.finish(ListenEnd.COMPLETED)
        session.finish(ListenEnd.INTERRUPTED)

        assertEquals(1, finished.size)
    }

    private companion object {
        const val WALL = 1_700_000_000_000L
        val A = Track(1, "content://1", "A", listOf("X"), null, null, 180_000, null)
        val B = Track(2, "content://2", "B", listOf("Y"), null, null, 200_000, null)
    }
}
