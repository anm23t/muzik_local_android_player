package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class TrackDeletionTest {
    @ParameterizedTest
    @CsvSource(
        "26, WRITE_PERMISSION",
        "28, WRITE_PERMISSION",
        "29, CONSENT_DIALOG",
        "30, TRASH",
        "33, TRASH",
        "36, TRASH",
    )
    fun `the method follows the Android version`(
        sdk: Int,
        expected: DeleteMethod,
    ) {
        assertEquals(expected, DeleteMethod.forSdk(sdk))
    }

    @Test
    fun `only the trash keeps the file`() {
        assertFalse(DeleteMethod.TRASH.isPermanent)
        assertTrue(DeleteMethod.CONSENT_DIALOG.isPermanent)
        assertTrue(DeleteMethod.WRITE_PERMISSION.isPermanent)
    }

    @Test
    fun `tracks deleted 30 days ago are forgotten`() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        assertEquals(70 * day, TrashRules.cutoff(now))
    }

    @Test
    fun `the lyrics file has the name of the audio file`() {
        val cases =
            listOf(
                "Song.flac" to "Song.lrc",
                "01. Intro.mp3" to "01. Intro.lrc",
                "a.b.c.m4a" to "a.b.c.lrc",
                "NoExtension" to "NoExtension.lrc",
                ".hidden" to ".hidden.lrc",
            )
        for ((audio, lyrics) in cases) assertEquals(lyrics, TrashRules.lyricsFileName(audio), audio)
    }

    @Test
    fun `every place of the track in the queue is removed from the end`() {
        val queue = listOf(track(1), track(7), track(2), track(7))
        assertEquals(listOf(3, 1), queueIndicesToRemove(queue, 7))
        assertEquals(emptyList<Int>(), queueIndicesToRemove(queue, 9))
        assertEquals(emptyList<Int>(), queueIndicesToRemove(emptyList(), 7))
    }

    private fun track(id: Long) = Track(id, "u$id", "T", emptyList(), null, null, 1, null)
}
