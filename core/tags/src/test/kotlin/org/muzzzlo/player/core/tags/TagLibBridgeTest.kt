package org.muzzzlo.player.core.tags

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileInputStream

/** Smoke test of the JNI bridge against the host build of libtaglib_jni (task 0.3.1). */
class TagLibBridgeTest {
    private val bridge = TagLibBridge()

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `reports the pinned TagLib version`() {
        assertEquals("2.3.2", bridge.version())
    }

    @Test
    fun `reads audio properties by path`() {
        val audio = requireNotNull(bridge.readAudioProperties(fixture())) { "TagLib could not open smoke.flac" }

        assertEquals(1000, audio.durationMs)
        assertEquals(8000, audio.sampleRateHz)
        assertEquals(1, audio.channels)
    }

    @Test
    fun `reads a title with emoji and Cyrillic intact`() {
        assertEquals(SMOKE_TITLE, bridge.readTitle(fixture()))
    }

    @Test
    fun `opens a path with emoji and Cyrillic`() {
        val file = fixture().copyTo(File(tempDir, "трек 🎧.flac"))

        assertEquals(SMOKE_TITLE, bridge.readTitle(file))
    }

    @Test
    fun `reads by file descriptor and leaves it open for the caller`() {
        FileInputStream(fixture()).use { stream ->
            val fd = stream.fd.toIntFd()

            assertEquals(SMOKE_TITLE, bridge.readTitle(fd))
            assertEquals(1000, bridge.readAudioProperties(fd)?.durationMs)
            // The native side read from a duplicate, so our descriptor is still open. A duplicate shares
            // the file offset, so rewind before reading.
            stream.channel.position(0)
            assertEquals(FLAC_MAGIC, String(stream.readNBytes(FLAC_MAGIC.length)))
        }
    }

    @Test
    fun `returns null for a file that is not audio`() {
        val file = File(tempDir, "not-audio.flac").apply { writeText("plain text, not FLAC") }

        assertNull(bridge.readAudioProperties(file))
        assertNull(bridge.readTitle(file))
    }

    @Test
    fun `returns null for a missing file`() {
        val missing = File(tempDir, "missing.mp3")

        assertNull(bridge.readAudioProperties(missing))
        assertNull(bridge.readTitle(missing))
    }

    @Test
    fun `rejects a negative file descriptor`() {
        assertThrows<IllegalArgumentException> { bridge.readTitle(-1) }
    }

    private fun fixture(): File {
        val url = requireNotNull(javaClass.classLoader?.getResource("smoke.flac")) { "smoke.flac is missing" }
        return File(url.toURI())
    }

    /** The raw descriptor behind a JVM FileDescriptor; on Android this comes from ParcelFileDescriptor.getFd(). */
    private fun java.io.FileDescriptor.toIntFd(): Int =
        java.io.FileDescriptor::class.java.getDeclaredField("fd").apply { isAccessible = true }.getInt(this)

    private companion object {
        const val SMOKE_TITLE = "Smoke 🎵 тест"
        const val FLAC_MAGIC = "fLaC"
    }
}
