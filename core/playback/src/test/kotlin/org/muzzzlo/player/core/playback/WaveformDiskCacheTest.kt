package org.muzzzlo.player.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.muzzzlo.player.core.model.Waveform
import org.muzzzlo.player.core.model.WaveformPeaks
import java.io.File

class WaveformDiskCacheTest {
    @TempDir lateinit var folder: File

    private var now = 1_000_000L
    private val peaks = WaveformPeaks(floatArrayOf(0f, 0.2f, 1f))

    private fun cache(limit: Long = WaveformDiskCache.DEFAULT_LIMIT_BYTES) = WaveformDiskCache(folder, limit) { now }

    @Test
    fun `stored peaks read back`() {
        val cache = cache()

        cache.put("a", peaks)

        assertEquals(Waveform.decode(Waveform.encode(peaks)), cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test
    fun `a damaged file reads as missing and is removed`() {
        val cache = cache()
        cache.put("a", peaks)
        File(folder, "a.wf").writeBytes(byteArrayOf(1, 2, 3))

        assertNull(cache.get("a"))
        assertEquals(false, File(folder, "a.wf").exists())
    }

    @Test
    fun `over the limit the files used the longest ago go first`() {
        val size = Waveform.encode(peaks).size.toLong()
        val cache = cache(limit = size * 2)
        cache.put("old", peaks)
        now += 10
        cache.put("used", peaks)
        now += 10
        cache.get("old")
        now += 10

        cache.put("new", peaks)

        assertEquals(setOf("old.wf", "new.wf"), folder.list()!!.toSet())
    }

    @Test
    fun `the newest file stays even over the limit`() {
        val cache = cache(limit = 1)

        cache.put("a", peaks)

        assertEquals(listOf("a.wf"), folder.list()!!.toList())
    }

    @Test
    fun `key changes with the file version`() {
        val key = WaveformDiskCache.keyOf("content://1", 10, 20)

        assertEquals(64, key.length)
        assertEquals(key, WaveformDiskCache.keyOf("content://1", 10, 20))
        assertNotEquals(key, WaveformDiskCache.keyOf("content://1", 11, 20))
        assertNotEquals(key, WaveformDiskCache.keyOf("content://1", 10, 21))
        assertNotEquals(key, WaveformDiskCache.keyOf("content://2", 10, 20))
    }

    @Test
    fun `alac cookie with box version is offered without it`() {
        val cookie = ByteArray(AlacConfig.COOKIE_SIZE) { it.toByte() }
        val withVersion = ByteArray(4) + cookie

        assertEquals(listOf(cookie.toList()), alacCookies(cookie).map { it.toList() })
        assertEquals(listOf(withVersion.toList(), cookie.toList()), alacCookies(withVersion).map { it.toList() })
    }
}
