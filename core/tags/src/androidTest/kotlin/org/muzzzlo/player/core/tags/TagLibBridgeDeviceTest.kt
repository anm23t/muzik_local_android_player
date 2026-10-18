package org.muzzzlo.player.core.tags

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Smoke test of libtaglib_jni.so on a device or emulator: the library loads from the APK for the device ABI
 * and reads a file by path and by descriptor. Run with `./gradlew :core:tags:connectedDebugAndroidTest`.
 */
class TagLibBridgeDeviceTest {
    private val bridge = TagLibBridge()

    @Test
    fun loadsTheBundledTagLib() {
        assertEquals("2.3.2", bridge.version())
    }

    @Test
    fun readsByPathAndByDescriptor() {
        val file = copyFixture()

        assertEquals(SMOKE_TITLE, bridge.readTitle(file))
        assertEquals(1000, bridge.readAudioProperties(file)?.durationMs)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            assertEquals(SMOKE_TITLE, bridge.readTitle(pfd.fd))
        }
    }

    private fun copyFixture(): File {
        val target = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "трек 🎧.flac")
        val fixture = javaClass.classLoader?.getResourceAsStream("smoke.flac")
        requireNotNull(fixture) { "smoke.flac is missing" }.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return target
    }

    private companion object {
        const val SMOKE_TITLE = "Smoke 🎵 тест"
    }
}
