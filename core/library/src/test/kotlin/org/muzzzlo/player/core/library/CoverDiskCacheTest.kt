package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CoverDiskCacheTest {
    @TempDir
    lateinit var folder: File

    private var now = 1_000_000L
    private val ticking: () -> Long = { now.also { now += 1_000 } }

    private fun cache(limit: Long) = CoverDiskCache(folder, limit, ticking)

    private fun bytes(size: Int) = ByteArray(size) { 1 }

    private fun names() = folder.list().orEmpty().filterNot { it.endsWith(".part") }.sorted()

    @Test
    fun `an unknown key is a miss`() {
        assertNull(cache(100).get("a"))
    }

    @Test
    fun `a stored cover is returned with its bytes`() {
        val cache = cache(100)
        cache.put("a", bytes(10))
        assertEquals(10, cache.get("a")?.length())
    }

    @Test
    fun `a stored cover is found by a new cache over the same folder`() {
        cache(100).put("a", bytes(10))
        assertNotNull(cache(100).get("a"))
    }

    @Test
    fun `within the limit nothing is deleted`() {
        val cache = cache(30)
        cache.put("a", bytes(10))
        cache.put("b", bytes(10))
        cache.put("c", bytes(10))
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), names())
    }

    @Test
    fun `over the limit the cover used longest ago goes first`() {
        val cache = cache(30)
        cache.put("a", bytes(10))
        cache.put("b", bytes(10))
        cache.put("c", bytes(10))
        cache.put("d", bytes(10))
        assertEquals(listOf("b.jpg", "c.jpg", "d.jpg"), names())
    }

    @Test
    fun `a hit protects the cover from the next trim`() {
        val cache = cache(30)
        cache.put("a", bytes(10))
        cache.put("b", bytes(10))
        cache.put("c", bytes(10))
        cache.get("a")
        cache.put("d", bytes(10))
        assertEquals(listOf("a.jpg", "c.jpg", "d.jpg"), names())
    }

    @Test
    fun `several old covers go when one big cover arrives`() {
        val cache = cache(30)
        cache.put("a", bytes(10))
        cache.put("b", bytes(10))
        cache.put("c", bytes(10))
        cache.put("big", bytes(25))
        assertEquals(listOf("big.jpg"), names())
    }

    @Test
    fun `the new cover stays even when it alone is over the limit`() {
        val cache = cache(5)
        cache.put("a", bytes(10))
        assertTrue(cache.get("a") != null)
    }

    @Test
    fun `a zero limit keeps only the newest cover`() {
        val cache = cache(0)
        cache.put("a", bytes(10))
        cache.put("b", bytes(10))
        assertEquals(listOf("b.jpg"), names())
    }

    @Test
    fun `storing a key again replaces the bytes`() {
        val cache = cache(100)
        cache.put("a", bytes(10))
        cache.put("a", bytes(20))
        assertEquals(20, cache.get("a")?.length())
        assertEquals(listOf("a.jpg"), names())
    }

    @Test
    fun `no partial file is left after a store`() {
        cache(100).put("a", bytes(10))
        assertFalse(folder.list().orEmpty().any { it.endsWith(".part") })
    }

    @Test
    fun `files of other kinds in the folder are not counted or deleted`() {
        File(folder, "note.txt").writeBytes(bytes(1000))
        val cache = cache(10)
        cache.put("a", bytes(10))
        assertTrue(File(folder, "note.txt").exists())
        assertNotNull(cache.get("a"))
    }

    @Test
    fun `the key follows the file address, size and change time`() {
        val base = coverKey("content://media/1", 100, 5000)
        assertEquals(base, coverKey("content://media/1", 100, 5000))
        assertNotEquals(base, coverKey("content://media/2", 100, 5000))
        assertNotEquals(base, coverKey("content://media/1", 101, 5000))
        assertNotEquals(base, coverKey("content://media/1", 100, 5001))
    }

    @Test
    fun `the key is a safe file name even without stamps`() {
        val key = coverKey("content://media/external/audio/media/1?x=/../y", null, null)
        assertEquals(40, key.length)
        assertTrue(key.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(key, coverKey("content://media/external/audio/media/1?x=/../y", 0, 0))
    }
}
