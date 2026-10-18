package org.muzzzlo.player.core.tags

import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Protects the app from files that crash native code (TagLib).
 *
 * A crash in native code kills the process instead of throwing an exception, so it cannot be caught.
 * Instead, [runGuarded] leaves an "in progress" marker for a file before the native call and removes it after.
 * If the process dies during the call, the marker survives, and the next start calls [recoverAfterCrash]:
 * the marked files move to the quarantine list and [isQuarantined] tells the scanner to skip them.
 *
 * Markers are plain files in [directory] (the app's private storage), one per running call,
 * so concurrent calls do not overwrite each other. After a crash every file that was in progress is quarantined:
 * with parallel reads the culprit cannot be told apart from its neighbours.
 *
 * Markers are not fsync'ed: after a process crash the kernel still writes them out, and only a power loss could
 * drop one. Syncing every read would make scanning a large library many times slower.
 *
 * All methods do file I/O: call them off the main thread.
 *
 * @param directory folder for markers and the quarantine list; created on demand.
 */
class NativeCallGuard(internal val directory: File) {
    private val markersDirectory = File(directory, MARKERS_DIRECTORY)
    private val quarantineFile = File(directory, QUARANTINE_FILE)
    private val markerCounter = AtomicLong()
    private val quarantineLock = Any()

    /**
     * Runs [block] (a native call that reads or writes the file identified by [fileKey]) behind a marker.
     *
     * @param fileKey stable identifier of the file, for example its content URI; must be a single line.
     */
    fun <T> runGuarded(
        fileKey: String,
        block: () -> T,
    ): T {
        val marker = writeMarker(fileKey)
        try {
            return block()
        } finally {
            // A Kotlin exception is not a native crash: the file is not to blame, remove the marker.
            marker.delete()
        }
    }

    /**
     * Moves files whose native call never finished (the process crashed) to the quarantine list.
     * Call once at startup, before the first [runGuarded].
     *
     * @return keys of the files quarantined by this call, empty after a clean shutdown.
     */
    fun recoverAfterCrash(): Set<String> {
        val markers = markersDirectory.listFiles().orEmpty()
        if (markers.isEmpty()) return emptySet()
        val crashed = markers.mapNotNull { readMarker(it) }.toSet()
        synchronized(quarantineLock) {
            writeQuarantine(readQuarantine() + crashed)
        }
        markers.forEach { it.delete() }
        return crashed
    }

    /** True if the file crashed native code before and must be skipped. */
    fun isQuarantined(fileKey: String): Boolean = fileKey in quarantined()

    /** All quarantined file keys. */
    fun quarantined(): Set<String> = synchronized(quarantineLock) { readQuarantine() }

    /** Removes [fileKey] from quarantine, for example after the user replaced the file. */
    fun release(fileKey: String) {
        synchronized(quarantineLock) {
            val current = readQuarantine()
            if (fileKey in current) writeQuarantine(current - fileKey)
        }
    }

    private fun writeMarker(fileKey: String): File {
        requireValidKey(fileKey)
        markersDirectory.mkdirs()
        val marker = File(markersDirectory, "${markerCounter.incrementAndGet()}-${System.nanoTime()}")
        marker.writeText(fileKey)
        return marker
    }

    /** Null for an empty marker: the process died between creating the file and writing the key. */
    private fun readMarker(marker: File): String? = marker.readText().takeIf { it.isNotEmpty() }

    private fun readQuarantine(): Set<String> =
        if (quarantineFile.exists()) quarantineFile.readLines().filter { it.isNotEmpty() }.toSet() else emptySet()

    private fun writeQuarantine(keys: Set<String>) {
        directory.mkdirs()
        // Write a temporary file and rename it, so a crash mid-write never leaves a truncated list.
        val temporary = File(directory, "$QUARANTINE_FILE.tmp")
        temporary.writeText(keys.joinToString(separator = "\n"))
        check(temporary.renameTo(quarantineFile)) { "Cannot update ${quarantineFile.path}" }
    }

    private fun requireValidKey(fileKey: String) {
        require(fileKey.isNotEmpty()) { "File key must not be empty" }
        require('\n' !in fileKey && '\r' !in fileKey) { "File key must be a single line: $fileKey" }
    }

    private companion object {
        const val MARKERS_DIRECTORY = "in-progress"
        const val QUARANTINE_FILE = "quarantine.txt"
    }
}
