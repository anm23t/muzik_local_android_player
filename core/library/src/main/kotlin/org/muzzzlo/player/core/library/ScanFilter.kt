package org.muzzzlo.player.core.library

import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.FolderSelection

/**
 * Leaves out the files the user keeps out in the settings: folders outside the library ([FolderSelection]) and files
 * that are too short.
 */
internal object ScanFilter {
    fun accepts(
        file: ScannedFile,
        settings: AppSettings,
    ): Boolean =
        file.durationMs >= settings.minTrackSeconds * MS_PER_SECOND &&
            FolderSelection.includes(file.relativePath ?: "", settings)

    fun apply(
        files: List<ScannedFile>,
        settings: AppSettings,
    ): List<ScannedFile> = files.filter { accepts(it, settings) }

    private const val MS_PER_SECOND = 1000L
}
