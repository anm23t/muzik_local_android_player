package org.muzzzlo.player.core.tags

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.TagSource

/** Smoke test of the JNI bridge against the host build of libtaglib_jni. */
class TagLibBridgeTest {
    private val bridge = TagLibBridge()

    @Test
    fun `reports the pinned TagLib version`() {
        assertEquals("2.3.2", bridge.version())
    }

    @Test
    fun `reads a title with emoji and Cyrillic intact`() {
        val tags = bridge.readTags(TagSource.FilePath(resourceFile("smoke.flac").path), withAudioInfo = true)

        assertEquals(listOf("Smoke 🎵 тест"), tags?.properties?.get("TITLE"))
        assertEquals(1000L, tags?.audio?.durationMs)
    }
}
