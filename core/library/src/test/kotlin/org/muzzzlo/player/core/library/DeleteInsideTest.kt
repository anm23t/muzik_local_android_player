package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DeleteInsideTest {
    @TempDir lateinit var root: File

    private val folder get() = File(root, "playlist_covers").also { it.mkdirs() }

    @Test
    fun `a file in the folder is deleted`() {
        val cover = File(folder, "1-100.jpg").apply { writeText("x") }

        deleteInside(folder, cover.absolutePath)

        assertFalse(cover.exists())
    }

    @Test
    fun `a file outside the folder is left alone`() {
        val other = File(root, "music.mp3").apply { writeText("x") }
        val inSubfolder = File(File(folder, "sub").apply { mkdirs() }, "2-1.jpg").apply { writeText("x") }

        deleteInside(folder, other.absolutePath)
        deleteInside(folder, inSubfolder.absolutePath)

        assertTrue(other.exists())
        assertTrue(inSubfolder.exists())
    }

    @Test
    fun `no path and a missing file do nothing`() {
        deleteInside(folder, null)
        deleteInside(folder, File(folder, "gone.jpg").absolutePath)
    }
}
