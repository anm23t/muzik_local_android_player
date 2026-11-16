package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FolderSelectionTest {
    @ParameterizedTest(name = "{0} covers ''{1}'': {2}")
    @CsvSource(
        "Music/, Music/, true",
        "Music/, Music/Rock/, true",
        "Music/, Music/Rock/Live/, true",
        "Music/, Music2/, false",
        "Music/, MusicOld/Rock/, false",
        "Music/Rock/, Music/, false",
        "Music, Music/Rock/, true",
        "music/, Music/Rock/, true",
        "Music/, '', false",
        "'', '', true",
        "'', Music/, false",
        "Download/Music/, Music/, false",
    )
    fun `folder covers itself and its subfolders`(
        selected: String,
        path: String,
        expected: Boolean,
    ) {
        assertEquals(expected, FolderSelection.covers(selected, path))
    }

    @Test
    fun `the default mode excludes exact folders`() {
        val settings = AppSettings(excludedFolders = setOf("Music/Podcasts/"))
        assertFalse(FolderSelection.includes("Music/Podcasts/", settings))
        assertTrue(FolderSelection.includes("Music/Podcasts/Extra/", settings))
        assertTrue(FolderSelection.includes("Music/", settings))
    }

    @Test
    fun `the selected mode takes selected folders with subfolders and ignores exclusions`() {
        val settings =
            AppSettings(
                excludedFolders = setOf("Music/Rock/"),
                onlySelectedFolders = true,
                selectedFolders = setOf("Music/"),
            )
        assertTrue(FolderSelection.includes("Music/Rock/", settings))
        assertTrue(FolderSelection.includes("Music/", settings))
        assertFalse(FolderSelection.includes("Download/", settings))
        assertFalse(FolderSelection.includes("", settings))
    }

    @Test
    fun `an empty selection falls back to the default mode`() {
        val settings = AppSettings(excludedFolders = setOf("A/"), onlySelectedFolders = true)
        assertFalse(FolderSelection.selectionApplies(settings))
        assertTrue(FolderSelection.includes("B/", settings))
        assertFalse(FolderSelection.includes("A/", settings))
    }

    @Test
    fun `switching the mode off keeps the selection but does not use it`() {
        val settings = AppSettings(onlySelectedFolders = false, selectedFolders = setOf("A/"))
        assertFalse(FolderSelection.selectionApplies(settings))
        assertTrue(FolderSelection.includes("B/", settings))
    }

    @Test
    fun `the covering parent is the outermost selected folder above`() {
        val selected = setOf("Music/", "Music/Rock/", "Other/")
        assertEquals("Music/", FolderSelection.coveringParent("Music/Rock/Live/", selected))
        assertEquals("Music/", FolderSelection.coveringParent("Music/Rock/", selected))
        assertNull(FolderSelection.coveringParent("Music/", selected))
        assertNull(FolderSelection.coveringParent("Download/", selected))
    }
}
