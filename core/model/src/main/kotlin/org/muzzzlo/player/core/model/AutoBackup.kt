package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The automatic weekly backup (ARCHITECTURE.md, section 8). It is not part of [AppSettings] and not part of the
 * backup itself: the folder belongs to this installation.
 *
 * @property folderUri the folder (`content://` tree URI) chosen with the system picker; null until chosen.
 * @property lastRunMs when the last attempt ended, null before the first one.
 * @property lastRunFailed the last attempt did not save a file.
 */
data class AutoBackupSettings(
    val enabled: Boolean = false,
    val folderUri: String? = null,
    val lastRunMs: Long? = null,
    val lastRunFailed: Boolean = false,
)

/** Stores [AutoBackupSettings]; implemented in `feature:settings` over DataStore. */
interface AutoBackupRepository {
    val settings: Flow<AutoBackupSettings>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setFolder(uri: String?)

    suspend fun recordResult(
        timeMs: Long,
        failed: Boolean,
    )
}

/** Starts and stops the weekly run; implemented in `feature:settings` with WorkManager. */
interface AutoBackupScheduler {
    /** Runs once a week while [enabled], nothing otherwise. Calling it again does not move the next run. */
    fun schedule(enabled: Boolean)

    /** Makes one copy as soon as possible, whether or not the weekly run is on. */
    fun runNow()
}

/** File names of the automatic copies and the rule of which old ones to delete. */
object AutoBackupFiles {
    const val KEEP = 5
    private const val PREFIX = "muzzzlo-auto-"
    private const val EXTENSION = ".json"
    private val pattern = Regex("""muzzzlo-auto-\d{4}-\d{2}-\d{2}\.json""")

    fun nameFor(date: LocalDate): String = "$PREFIX$date$EXTENSION"

    /**
     * Of the file names in the folder, the ones to delete so that the newest [KEEP] copies remain. Only names made by
     * [nameFor] are considered; the date in the name sorts the same way as time, and other files are never touched.
     */
    fun toDelete(names: List<String>): List<String> = names.filter { pattern.matches(it) }.sortedDescending().drop(KEEP)
}
