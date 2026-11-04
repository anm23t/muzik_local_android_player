package org.muzzzlo.player.feature.settings

import org.muzzzlo.player.core.model.SettingsRepository

/**
 * Changes of the "Library" group since 2.0 for [SettingsViewModel], kept apart so that the view model does not grow.
 * [saveAndScan] runs a repository call off the screen and then scans the library, [save] only runs it.
 */
class LibrarySettingsEditor(
    private val saveAndScan: (suspend SettingsRepository.() -> Unit) -> Unit,
    private val save: (suspend SettingsRepository.() -> Unit) -> Unit,
) {
    /** The folder opened for `.lrc` files (task 2.0.7); no scan, lyrics are read when a track plays. */
    fun onLyricsFolderChosen(treeUri: String) = save { setLyricsFolder(treeUri) }

    /** The covers of the folders are looked up by a scan, which also clears them when turned off (task 2.0.5). */
    fun onFolderCoversChanged(enabled: Boolean) = saveAndScan { setFolderCovers(enabled) }
}
