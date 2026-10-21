package org.muzzzlo.player.core.tags

import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Protects the app from files that crash native code (TagLib).
 *
 * A crash in native code kills the process instead of throwing an exception, so it cannot be caught.
 * Instead, [runGuarded] leaves an "in progress" marker for a file before the native call and removes it after.
 * If the process dies during the call, the marker survives, and the next start calls [recoverAfterCrash].
 *
 * A surviving marker alone does not prove the file is to blame: the process also dies when it runs out of memory,
 * when the system kills it in the background or when it crashes somewhere else, and with parallel reads every file
 * in progress leaves a marker. So quarantine takes two steps:
 * 1. a file with a surviving marker becomes a suspect ([suspects]);
 * 2. the next call for a suspect runs alone: it waits for the other guarded calls to finish and keeps new ones
 *    out until it returns. If it returns, the file is cleared; if the process dies on it again, the file goes to
 *    the quarantine list and [isQuarantined] tells the scanner to skip it.
 *
 * Markers are plain files in [directory] (the app's private storage), one per running call, so concurrent calls do
 * not overwrite each other; markers of solo calls live in their own folder.
 *
 * Markers are not fsync'ed: after a process crash the kernel still writes them out, and only a power loss could
 * drop one. Syncing every read would make scanning a large library many times slower.
 *
 * All methods do file I/O: call them off the main thread.
 *
 * @param directory folder for markers, the suspect list and the quarantine list; created on demand.
 */
class NativeCallGuard(internal val directory: File) {
    private val markersDirectory = File(directory, MARKERS_DIRECTORY)
    private val soloMarkersDirectory = File(directory, SOLO_MARKERS_DIRECTORY)
    private val quarantineFile = File(directory, QUARANTINE_FILE)
    private val suspectsFile = File(directory, SUSPECTS_FILE)
    private val markerCounter = AtomicLong()
    private val listsLock = Any()

    /** Ordinary calls share the read lock, a suspect's call takes the write lock to run alone. */
    private val soloLock = ReentrantReadWriteLock(true)

    /** In-memory copy of the suspect list, so ordinary calls do not read a file. Guarded by [listsLock]. */
    private var suspectCache: Set<String>? = null

    /**
     * Runs [block] (a native call that reads or writes the file identified by [fileKey]) behind a marker.
     * A suspect runs alone; when its call returns (with a result or a Kotlin exception), it is cleared.
     *
     * @param fileKey stable identifier of the file, for example its content URI; must be a single line.
     */
    fun <T> runGuarded(
        fileKey: String,
        block: () -> T,
    ): T {
        requireValidKey(fileKey)
        // A thread that already holds the read lock cannot take the write lock; such a nested call runs as usual.
        val solo = fileKey in suspects() && soloLock.readHoldCount == 0
        val lock = if (solo) soloLock.writeLock() else soloLock.readLock()
        lock.lock()
        try {
            val marker = writeMarker(fileKey, if (solo) soloMarkersDirectory else markersDirectory)
            try {
                return block()
            } finally {
                // A Kotlin exception is not a native crash: the file is not to blame, remove the marker.
                marker.delete()
                if (solo) clearSuspect(fileKey)
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Sorts out the markers left by a process that died: files that were read alone go to quarantine, the others
     * become suspects. Call once at startup, before the first [runGuarded].
     *
     * @return keys of the files quarantined by this call, empty after a clean shutdown or a first crash.
     */
    fun recoverAfterCrash(): Set<String> {
        val markers = markersDirectory.listFiles().orEmpty().filter { it.isFile }
        val soloMarkers = soloMarkersDirectory.listFiles().orEmpty()
        if (markers.isEmpty() && soloMarkers.isEmpty()) return emptySet()
        val crashedAlone = soloMarkers.mapNotNull { readMarker(it) }.toSet()
        val crashedWithOthers = markers.mapNotNull { readMarker(it) }.toSet()
        synchronized(listsLock) {
            val quarantine = readQuarantine() + crashedAlone
            writeQuarantine(quarantine)
            writeSuspects(suspects() + crashedWithOthers - quarantine)
        }
        (markers + soloMarkers).forEach { it.delete() }
        return crashedAlone
    }

    /** Files whose call was interrupted once; their next call runs alone. */
    fun suspects(): Set<String> =
        synchronized(listsLock) {
            suspectCache ?: readList(suspectsFile).also { suspectCache = it }
        }

    /** True if the file crashed native code before and must be skipped. */
    fun isQuarantined(fileKey: String): Boolean = fileKey in quarantined()

    /** All quarantined file keys. */
    fun quarantined(): Set<String> = synchronized(listsLock) { readQuarantine() }

    /**
     * Removes [fileKey] from quarantine, for example after the user replaced the file or asked to try again.
     * The file becomes a suspect: its next call runs alone, and one more crash sends it straight back.
     */
    fun release(fileKey: String) {
        synchronized(listsLock) {
            val current = readQuarantine()
            if (fileKey in current) {
                writeQuarantine(current - fileKey)
                writeSuspects(suspects() + fileKey)
            }
        }
    }

    private fun clearSuspect(fileKey: String) {
        synchronized(listsLock) {
            val current = suspects()
            if (fileKey in current) writeSuspects(current - fileKey)
        }
    }

    private fun writeMarker(
        fileKey: String,
        folder: File,
    ): File {
        folder.mkdirs()
        val marker = File(folder, "${markerCounter.incrementAndGet()}-${System.nanoTime()}")
        marker.writeText(fileKey)
        return marker
    }

    /** Null for an empty marker: the process died between creating the file and writing the key. */
    private fun readMarker(marker: File): String? = marker.readText().takeIf { it.isNotEmpty() }

    private fun readQuarantine(): Set<String> = readList(quarantineFile)

    private fun writeQuarantine(keys: Set<String>) = writeList(quarantineFile, keys)

    private fun writeSuspects(keys: Set<String>) {
        writeList(suspectsFile, keys)
        suspectCache = keys
    }

    private fun readList(file: File): Set<String> =
        if (file.exists()) file.readLines().filter { it.isNotEmpty() }.toSet() else emptySet()

    private fun writeList(
        file: File,
        keys: Set<String>,
    ) {
        directory.mkdirs()
        // Write a temporary file and rename it, so a crash mid-write never leaves a truncated list.
        val temporary = File(directory, "${file.name}.tmp")
        temporary.writeText(keys.joinToString(separator = "\n"))
        check(temporary.renameTo(file)) { "Cannot update ${file.path}" }
    }

    private fun requireValidKey(fileKey: String) {
        require(fileKey.isNotEmpty()) { "File key must not be empty" }
        require('\n' !in fileKey && '\r' !in fileKey) { "File key must be a single line: $fileKey" }
    }

    private companion object {
        const val MARKERS_DIRECTORY = "in-progress"
        const val SOLO_MARKERS_DIRECTORY = "in-progress-solo"
        const val QUARANTINE_FILE = "quarantine.txt"
        const val SUSPECTS_FILE = "suspects.txt"
    }
}
