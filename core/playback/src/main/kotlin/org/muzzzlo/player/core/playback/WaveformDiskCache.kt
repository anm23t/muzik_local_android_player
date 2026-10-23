package org.muzzzlo.player.core.playback

import org.muzzzlo.player.core.model.Waveform
import org.muzzzlo.player.core.model.WaveformPeaks
import java.io.File
import java.security.MessageDigest

/**
 * Folder of waveform files (`cacheDir/waveforms`, next to the covers) with a size limit, like the cover cache:
 * a hit refreshes the file time, and over [limitBytes] the files used the longest ago go first. Android-free, so it
 * is unit-tested on the JVM. A damaged file reads as missing and is decoded again.
 */
internal class WaveformDiskCache(
    private val folder: File,
    private val limitBytes: Long = DEFAULT_LIMIT_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun get(key: String): WaveformPeaks? {
        val file = fileFor(key)
        if (!file.isFile) return null
        val peaks = runCatching { Waveform.decode(file.readBytes()) }.getOrNull()
        if (peaks == null) file.delete() else file.setLastModified(clock())
        return peaks
    }

    fun put(
        key: String,
        peaks: WaveformPeaks,
    ) {
        folder.mkdirs()
        val file = fileFor(key)
        val partial = File(folder, "${file.name}$PART")
        partial.writeBytes(Waveform.encode(peaks))
        if (!partial.renameTo(file)) {
            partial.delete()
            return
        }
        file.setLastModified(clock())
        trim(keep = file)
    }

    private fun trim(keep: File) {
        val entries =
            folder.listFiles { f -> f.isFile && f.name.endsWith(EXTENSION) }
                .orEmpty()
                .map { Triple(it, it.lastModified(), it.length()) }
                .sortedByDescending { it.second }
        var total = 0L
        for ((file, _, size) in entries) {
            total += size
            if (total > limitBytes && file != keep) file.delete()
        }
    }

    private fun fileFor(key: String) = File(folder, "$key$EXTENSION")

    companion object {
        /** About 4000 tracks. */
        const val DEFAULT_LIMIT_BYTES = 8L * 1024 * 1024
        private const val EXTENSION = ".wf"
        private const val PART = ".part"
        private const val HEX_MASK = 0xFF

        /** Key of a file version: a new size or modification time decodes the file again. */
        fun keyOf(
            uri: String,
            size: Long,
            modified: Long,
        ): String =
            MessageDigest.getInstance("SHA-256")
                .digest("$uri|$size|$modified".toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and HEX_MASK) }
    }
}
