package org.muzzzlo.player.core.tags

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TagSource
import java.io.File

/**
 * Read speed on a real phone with its own music library (condition for closing task 0.3.2, ARCHITECTURE.md, section 5).
 *
 * Reads every MediaStore music file the way the scanner does: open the `content://` URI, read tags and audio
 * properties by descriptor behind [NativeCallGuard]. Two passes: the first is close to a first scan (cold page cache
 * unless the files were read recently), the second shows the warm cost. Results go to logcat (tag `TagReadBenchmark`)
 * and to the instrumentation output. The test is skipped when the device has no music.
 *
 * ```
 * ./gradlew :core:tags:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=org.muzzzlo.player.core.tags.TagReadBenchmark
 * ```
 */
class TagReadBenchmark {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @Test
    fun readWholeLibrary() {
        grantAudioPermission()
        val files = musicFiles()
        assumeTrue("No music on the device", files.isNotEmpty())

        val guardDir = File(context.cacheDir, "benchmark-guard").apply { deleteRecursively() }
        val reader = TagLibTagReader(TagLibBridge(), NativeCallGuard(guardDir), InPlaceDispatchers)
        val report =
            buildString {
                val device = "${Build.MODEL}, API ${Build.VERSION.SDK_INT}"
                appendLine("Files: ${files.size}, TagLib ${TagLibBridge().version()}, $device")
                appendLine(pass(FIRST_PASS, files, reader))
                appendLine(pass("pass 2", files, reader))
                appendLine(breakdown(files))
            }
        report.lines().forEach { Log.i(TAG, it) }
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$report") })
    }

    private fun pass(
        name: String,
        files: List<MusicFile>,
        reader: TagLibTagReader,
    ): String {
        val nanos = LongArray(files.size)
        var unreadable = 0
        runBlocking {
            files.forEachIndexed { i, (uri, name) ->
                val start = System.nanoTime()
                val result =
                    context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { pfd ->
                        reader.readTags(TagSource.Descriptor(pfd.fd, uri, name))
                    }
                nanos[i] = System.nanoTime() - start
                if (result !is TagReadResult.Success) {
                    unreadable++
                    if (name == FIRST_PASS) Log.w(TAG, "unreadable: $uri")
                }
            }
        }
        val sorted = nanos.sorted()
        val totalMs = nanos.sum() / NANOS_PER_MS

        fun micros(value: Long) = "${value / NANOS_PER_US} µs"
        return "$name: total $totalMs ms, per file mean ${micros(nanos.average().toLong())}, " +
            "median ${micros(sorted[sorted.size / 2])}, p95 ${micros(sorted[sorted.size * 95 / 100])}, " +
            "max ${micros(sorted.last())}, unreadable $unreadable"
    }

    /** Warm-cache split of one read: opening the URI, the guard's marker, TagLib with and without audio properties. */
    private fun breakdown(files: List<MusicFile>): String {
        val bridge = TagLibBridge()
        val guard = NativeCallGuard(File(context.cacheDir, "benchmark-guard"))
        var open = 0L
        var tagsOnly = 0L
        var withAudio = 0L
        var marker = 0L
        for ((uri, name) in files) {
            var start = System.nanoTime()
            val pfd = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r") ?: continue
            open += System.nanoTime() - start
            pfd.use {
                val source = TagSource.Descriptor(it.fd, uri, name)
                start = System.nanoTime()
                bridge.readTags(source, withAudioInfo = false)
                tagsOnly += System.nanoTime() - start
                start = System.nanoTime()
                bridge.readTags(source, withAudioInfo = true)
                withAudio += System.nanoTime() - start
                start = System.nanoTime()
                guard.runGuarded(uri) {}
                marker += System.nanoTime() - start
            }
        }

        fun mean(total: Long) = "${total / files.size / NANOS_PER_US} µs"
        return "breakdown (mean per file): open ${mean(open)}, marker ${mean(marker)}, " +
            "TagLib tags only ${mean(tagsOnly)}, TagLib with audio ${mean(withAudio)}"
    }

    /** A MediaStore music file: its content URI and display name, as the scanner passes them to the reader. */
    private data class MusicFile(val uri: String, val name: String?)

    private fun musicFiles(): List<MusicFile> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val files = ArrayList<MusicFile>()
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(0)).toString()
                files += MusicFile(uri, cursor.getString(1))
            }
        }
        return files
    }

    private fun grantAudioPermission() {
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
    }

    private object InPlaceDispatchers : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private companion object {
        const val TAG = "TagReadBenchmark"
        const val FIRST_PASS = "pass 1"
        const val NANOS_PER_MS = 1_000_000L
        const val NANOS_PER_US = 1_000L
    }
}
