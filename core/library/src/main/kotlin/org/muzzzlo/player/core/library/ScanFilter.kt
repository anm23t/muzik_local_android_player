package org.muzzzlo.player.core.library

import org.muzzzlo.player.core.model.AppSettings

/** Leaves out the files the user excluded in the settings: whole folders and files that are too short. */
internal object ScanFilter {
    fun accepts(
        file: ScannedFile,
        settings: AppSettings,
    ): Boolean =
        file.durationMs >= settings.minTrackSeconds * MS_PER_SECOND &&
            (file.relativePath ?: "") !in settings.excludedFolders

    fun apply(
        files: List<ScannedFile>,
        settings: AppSettings,
    ): List<ScannedFile> = files.filter { accepts(it, settings) }

    private const val MS_PER_SECOND = 1000L
}
