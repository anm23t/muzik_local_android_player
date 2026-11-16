package org.muzzzlo.player.core.model

/**
 * Which folders are in the library (task 2.5.5). Two modes with their own lists:
 * - all folders except [AppSettings.excludedFolders], matched exactly (a subfolder is its own entry);
 * - only [AppSettings.selectedFolders] with their subfolders, when [AppSettings.onlySelectedFolders] is on.
 *
 * While nothing is selected the second mode does not apply and the first one works, so turning the switch on does
 * not empty the library before the user picks a folder.
 *
 * Paths are MediaStore `RELATIVE_PATH` values (`Music/Rock/`); the storage root is the empty path.
 */
object FolderSelection {
    /** The selected mode is on and has something selected, so it decides. */
    fun selectionApplies(settings: AppSettings): Boolean =
        settings.onlySelectedFolders && settings.selectedFolders.isNotEmpty()

    /** Files lying directly in the folder [relativePath] belong to the library. */
    fun includes(
        relativePath: String,
        settings: AppSettings,
    ): Boolean =
        if (selectionApplies(settings)) {
            settings.selectedFolders.any { covers(it, relativePath) }
        } else {
            relativePath !in settings.excludedFolders
        }

    /**
     * The selected folder [selected] takes in [relativePath]: the folder itself or one of its subfolders. `Music/`
     * does not take in `Music2/`. The storage root takes in only the files lying in the root: selecting it is not a
     * way to select everything, the other mode does that. Case is ignored, as on the shared storage of Android.
     */
    fun covers(
        selected: String,
        relativePath: String,
    ): Boolean {
        if (selected.isEmpty()) return relativePath.isEmpty()
        val folder = if (selected.endsWith('/')) selected else "$selected/"
        val path = if (relativePath.endsWith('/')) relativePath else "$relativePath/"
        return path.startsWith(folder, ignoreCase = true)
    }

    /**
     * The outermost selected folder other than [relativePath] itself that takes it in; null when none does. Such a
     * folder is in the library through its parent and cannot be left out alone.
     */
    fun coveringParent(
        relativePath: String,
        selected: Set<String>,
    ): String? =
        selected
            .filter { !it.equals(relativePath, ignoreCase = true) && covers(it, relativePath) }
            .minByOrNull { it.length }
}
