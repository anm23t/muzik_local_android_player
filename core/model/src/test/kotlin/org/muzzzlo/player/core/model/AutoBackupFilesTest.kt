package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AutoBackupFilesTest {
    private fun names(vararg days: Int) = days.map { AutoBackupFiles.nameFor(LocalDate.of(2026, 10, it)) }

    @Test
    fun `name carries the date`() {
        assertEquals("muzzzlo-auto-2026-01-05.json", AutoBackupFiles.nameFor(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `nothing is deleted up to the limit`() {
        assertEquals(emptyList<String>(), AutoBackupFiles.toDelete(names(1, 2, 3, 4, 5)))
        assertEquals(emptyList<String>(), AutoBackupFiles.toDelete(emptyList()))
    }

    @Test
    fun `the oldest copies go first whatever the listing order`() {
        val result = AutoBackupFiles.toDelete(names(8, 1, 6, 3, 7, 2, 5))
        assertEquals(names(2, 1).toSet(), result.toSet())
        assertEquals(2, result.size)
    }

    @Test
    fun `files that are not automatic copies are never deleted`() {
        val others =
            listOf(
                "notes.txt",
                "muzzzlo-backup-2020-01-01.json",
                "muzzzlo-auto-2020-01-01.json.bak",
                "muzzzlo-auto-x.json",
            )
        assertEquals(emptyList<String>(), AutoBackupFiles.toDelete(others + names(1, 2, 3)))
    }
}
