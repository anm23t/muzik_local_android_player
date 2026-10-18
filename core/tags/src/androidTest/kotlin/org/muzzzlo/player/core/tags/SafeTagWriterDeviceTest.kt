package org.muzzzlo.player.core.tags

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.muzzzlo.player.core.model.AudioFormat
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.TagEdit
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TagSource
import org.muzzzlo.player.core.model.TagWriteResult
import org.muzzzlo.player.core.model.TagWriteTarget
import java.io.File

/**
 * The safe write against a real MediaStore file: the original is replaced through `openFileDescriptor(uri, "rwt")`
 * under scoped storage. The test creates the file itself, so it owns it and needs no permission dialog.
 * Android 10+ only (the file is created with `RELATIVE_PATH`). Run with `./gradlew :core:tags:connectedDebugAndroidTest`.
 *
 * Each run gets its own file name: a file left behind by an earlier run would make MediaStore rename the new one.
 */
class SafeTagWriterDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val work = File(context.cacheDir, "safe-write-test")
    private val fileName = "muzzzlo-write-test-${System.currentTimeMillis()}.flac"
    private var uri: Uri? = null

    @Before
    fun setUp() {
        assumeTrue("Needs Android 10+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        work.deleteRecursively()
        uri = insertFixture(fileName)
    }

    /**
     * Cleanup does not fail the test: losing access to the file after the write is checked by the test itself.
     * A failed delete is logged with the owner and pending state, which tell who took the file.
     */
    @After
    fun tearDown() {
        uri?.let { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot delete $uri: owner=${ownerOf(uri)}, pending=${pendingOf(uri)}", e)
            }
        }
        work.deleteRecursively()
    }

    @Test
    fun replacesTheOriginalThroughItsContentUri() =
        runBlocking {
            val uri = requireNotNull(uri)
            val files = MediaStoreOriginalFiles(context)
            val target = TagWriteTarget.ContentUri(uri.toString(), fileName)
            val audioBefore = fingerprintOf(uri)
            val edit = TagEdit(title = "Записано 🎧", artists = listOf("Alpha", "Beta"))

            assertTrue("own file must be writable", files.canWrite(target))
            assertEquals(TagWriteResult.Success, writer(files).writeTags(target, edit))

            val tags = readTags(uri)
            assertEquals("Записано 🎧", tags.title)
            assertEquals(listOf("Alpha", "Beta"), tags.artists)
            assertArrayEquals(audioBefore, fingerprintOf(uri))
            assertEquals("the app must stay the owner of the file", context.packageName, ownerOf(uri))
            assertEquals("the file must not be pending", "0", pendingOf(uri))
            assertTrue("journal must be empty", File(work, "journal").listFiles().orEmpty().isEmpty())
        }

    @Test
    fun ownFilesNeedNoPermissionDialogOnAndroid10() {
        assumeTrue(Build.VERSION.SDK_INT == Build.VERSION_CODES.Q)

        assertEquals(WritePermissionRequest.NotNeeded, TagWritePermission(context).requestFor(listOf(uri.toString())))
    }

    @Test
    fun buildsTheSystemWriteRequestOnAndroid11() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)

        assertTrue(
            TagWritePermission(context).requestFor(listOf(uri.toString())) is WritePermissionRequest.SystemDialog,
        )
    }

    private fun writer(files: OriginalFiles): SafeTagWriter {
        val guard = NativeCallGuard(File(work, "guard"))
        val journal = WriteJournal(File(work, "journal"))
        return SafeTagWriter(TagLibBridge(), guard, journal, files, TagFileRecovery(guard, journal, files), InPlace)
    }

    private suspend fun readTags(uri: Uri) =
        requireNotNull(context.contentResolver.openFileDescriptor(uri, "r")).use { pfd ->
            val guard = NativeCallGuard(File(work, "guard"))
            val reader =
                TagLibTagReader(
                    TagLibBridge(),
                    guard,
                    TagFileRecovery(guard, WriteJournal(File(work, "journal")), PathOriginalFiles()),
                    InPlace,
                )
            val result = reader.readTags(TagSource.Descriptor(pfd.fd, uri.toString(), "test.flac"))
            (result as TagReadResult.Success).fileTags.tags
        }

    private fun fingerprintOf(uri: Uri): ByteArray {
        val copy = File(work, "fingerprint.flac").apply { parentFile?.mkdirs() }
        requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            copy.outputStream().use { input.copyTo(it) }
        }
        return requireNotNull(AudioFingerprint.of(copy, AudioFormat.FLAC))
    }

    private fun ownerOf(uri: Uri): String? = columnOf(uri, OWNER_PACKAGE_NAME)

    private fun pendingOf(uri: Uri): String? = columnOf(uri, MediaStore.MediaColumns.IS_PENDING)

    private fun columnOf(
        uri: Uri,
        column: String,
    ): String? =
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /** Creates a MediaStore audio file owned by the test from the `flac.flac` fixture. */
    private fun insertFixture(name: String): Uri {
        val values =
            ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/flac")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/MuzzzloTest")
            }
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values))
        val fixture = requireNotNull(javaClass.classLoader?.getResourceAsStream("tags/flac.flac"))
        fixture.use { input ->
            requireNotNull(resolver.openOutputStream(uri, "w")).use { input.copyTo(it) }
        }
        return uri
    }

    private companion object {
        const val TAG = "SafeTagWriterDeviceTest"

        // MediaStore.MediaColumns.OWNER_PACKAGE_NAME appeared in Android 11; the column exists in Android 10.
        const val OWNER_PACKAGE_NAME = "owner_package_name"
    }

    private object InPlace : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }
}
