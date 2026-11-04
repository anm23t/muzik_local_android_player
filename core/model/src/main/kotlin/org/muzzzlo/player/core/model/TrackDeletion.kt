package org.muzzzlo.player.core.model

/**
 * How the file of a track is deleted on a given Android version (task 2.0.6). The track row stays, marked missing,
 * so a file brought back from the system trash returns with its rating, favorite flag and playlist places.
 */
enum class DeleteMethod(
    /** The file is gone for good, so the screen warns first; the system trash keeps it otherwise. */
    val isPermanent: Boolean,
) {
    /** Android 11+: `MediaStore.createTrashRequest`, the system asks and keeps the file in its trash for 30 days. */
    TRASH(isPermanent = false),

    /** Android 10: the delete throws `RecoverableSecurityException`, whose system dialog asks the user. */
    CONSENT_DIALOG(isPermanent = true),

    /** Android 8–9: the runtime permission to write to the storage, as for tag writes. */
    WRITE_PERMISSION(isPermanent = true),
    ;

    companion object {
        private const val ANDROID_10 = 29
        private const val ANDROID_11 = 30

        /** The method for the Android API level [sdk]. */
        fun forSdk(sdk: Int): DeleteMethod =
            when {
                sdk >= ANDROID_11 -> TRASH
                sdk == ANDROID_10 -> CONSENT_DIALOG
                else -> WRITE_PERMISSION
            }
    }
}

/** What the screen must do before the file of a track can go. */
sealed interface DeleteRequest {
    /** Nothing: the file can be deleted at once ([TrackFileDeletion.delete]). */
    data object NotNeeded : DeleteRequest

    /** Request the runtime [permission] the usual way, then delete. */
    data class RuntimePermission(val permission: String) : DeleteRequest

    /**
     * Launch the system dialog behind [handle]; on "Allow" the file is trashed (Android 11+) or may be deleted
     * (Android 10). The handle is an Android object that only the screen of the app module opens.
     */
    data class SystemDialog(val handle: SystemDialogHandle) : DeleteRequest

    /** The track or its file is gone already; there is nothing to delete. */
    data object Gone : DeleteRequest
}

/** An Android system dialog to launch, opaque outside the platform code. */
interface SystemDialogHandle

/**
 * Deletes the files of tracks (task 2.0.6). Implemented in `core:library`; calls switch to the I/O dispatcher.
 *
 * The flow: [request], then the screen does what it says, then [delete] (where the app removes the file itself)
 * and [markDeleted].
 */
interface TrackFileDeletion {
    /** The method of this device. */
    val method: DeleteMethod

    /** What the screen must ask the user before the file of [trackId] can go. */
    suspend fun request(trackId: Long): DeleteRequest

    /**
     * Removes the file of [trackId] after the user agreed (Android 10 and older; with the trash the system has done
     * it already, then this does nothing). False when the file could not be deleted.
     */
    suspend fun delete(trackId: Long): Boolean

    /** Marks the track deleted: hidden like a missing file, its cover thumbnail dropped. */
    suspend fun markDeleted(trackId: Long)

    /** Forgets tracks deleted more than [TrashRules.KEEP_DAYS] ago whose files did not come back. */
    suspend fun purgeExpired(nowMs: Long)
}

/** How long a track deleted by the app is kept in the database (the time the system trash keeps the file). */
object TrashRules {
    const val KEEP_DAYS = 30
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Tracks deleted before this moment are forgotten. */
    fun cutoff(nowMs: Long): Long = nowMs - KEEP_DAYS * DAY_MS

    /** The name of the lyrics file next to the audio file [displayName]: `Song.flac` → `Song.lrc`. */
    fun lyricsFileName(displayName: String): String {
        val dot = displayName.lastIndexOf('.')
        val base = if (dot > 0) displayName.substring(0, dot) else displayName
        return "$base.lrc"
    }
}

/** Queue positions holding [trackId], from the last to the first, so removing them one by one keeps the rest valid. */
fun queueIndicesToRemove(
    queue: List<Track>,
    trackId: Long,
): List<Int> = queue.indices.filter { queue[it].id == trackId }.reversed()
