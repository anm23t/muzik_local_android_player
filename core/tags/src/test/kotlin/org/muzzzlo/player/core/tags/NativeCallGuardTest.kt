package org.muzzzlo.player.core.tags

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

/**
 * A native crash cannot happen in a JVM test, so a crash is simulated by leaving the marker behind:
 * the guarded block throws [SimulatedCrash] after copying the marker aside, and the copy is restored afterwards.
 */
class NativeCallGuardTest {
    @TempDir
    lateinit var dir: File

    private val markersDir get() = File(dir, "in-progress")

    @Test
    fun `returns the block result and removes the marker`() {
        val guard = NativeCallGuard(dir)

        val result = guard.runGuarded(URI_A) { 42 }

        assertEquals(42, result)
        assertTrue(markersDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `marker holds the file key while the call runs`() {
        val guard = NativeCallGuard(dir)

        val seen = guard.runGuarded(URI_A) { markersDir.listFiles().orEmpty().map { it.readText() } }

        assertEquals(listOf(URI_A), seen)
    }

    @Test
    fun `a Kotlin exception removes the marker and is rethrown`() {
        val guard = NativeCallGuard(dir)

        assertThrows<IllegalStateException> { guard.runGuarded<Unit>(URI_A) { error("boom") } }

        assertTrue(markersDir.listFiles().orEmpty().isEmpty())
        assertTrue(guard.recoverAfterCrash().isEmpty())
    }

    @Test
    fun `clean start quarantines nothing`() {
        val guard = NativeCallGuard(dir)

        assertTrue(guard.recoverAfterCrash().isEmpty())
        assertTrue(guard.quarantined().isEmpty())
    }

    @Test
    fun `file that crashed is quarantined after restart`() {
        simulateCrash(NativeCallGuard(dir), URI_A)

        val restarted = NativeCallGuard(dir)
        val crashed = restarted.recoverAfterCrash()

        assertEquals(setOf(URI_A), crashed)
        assertTrue(restarted.isQuarantined(URI_A))
        assertFalse(restarted.isQuarantined(URI_B))
        assertTrue(markersDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `recovery is idempotent and keeps earlier quarantine`() {
        simulateCrash(NativeCallGuard(dir), URI_A)
        NativeCallGuard(dir).recoverAfterCrash()
        simulateCrash(NativeCallGuard(dir), URI_B)

        val restarted = NativeCallGuard(dir)

        assertEquals(setOf(URI_B), restarted.recoverAfterCrash())
        assertTrue(restarted.recoverAfterCrash().isEmpty())
        assertEquals(setOf(URI_A, URI_B), restarted.quarantined())
    }

    @Test
    fun `all calls in progress during a crash are quarantined`() {
        val guard = NativeCallGuard(dir)
        guard.runGuarded(URI_A) { simulateCrash(guard, URI_B) }
        // The outer marker was removed by its finally block; put it back as a crash would have left it.
        markersDir.mkdirs()
        File(markersDir, "outer").writeText(URI_A)

        assertEquals(setOf(URI_A, URI_B), NativeCallGuard(dir).recoverAfterCrash())
    }

    @Test
    fun `empty marker from a crash during marker write is ignored`() {
        markersDir.mkdirs()
        File(markersDir, "1-0").writeText("")

        val guard = NativeCallGuard(dir)

        assertTrue(guard.recoverAfterCrash().isEmpty())
        assertTrue(markersDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `release removes a file from quarantine`() {
        simulateCrash(NativeCallGuard(dir), URI_A)
        val guard = NativeCallGuard(dir).apply { recoverAfterCrash() }

        guard.release(URI_A)
        guard.release(URI_B)

        assertFalse(guard.isQuarantined(URI_A))
        assertFalse(NativeCallGuard(dir).isQuarantined(URI_A))
    }

    @Test
    fun `keys with spaces, Cyrillic and emoji survive the round trip`() {
        val key = "content://media/external/audio/media/1?name=трек 🎧.flac"
        simulateCrash(NativeCallGuard(dir), key)

        assertEquals(setOf(key), NativeCallGuard(dir).recoverAfterCrash())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "line\nbreak", "carriage\rreturn"])
    fun `rejects keys that cannot be stored as one line`(key: String) {
        assertThrows<IllegalArgumentException> { NativeCallGuard(dir).runGuarded(key) { } }
    }

    /** Leaves [key]'s marker behind, as a native crash during the call would. */
    private fun simulateCrash(
        guard: NativeCallGuard,
        key: String,
    ) {
        val leftovers = File(dir, "leftovers").apply { mkdirs() }
        assertThrows<SimulatedCrash> {
            guard.runGuarded(key) {
                markersDir.listFiles().orEmpty().filter { it.readText() == key }
                    .forEach { it.copyTo(File(leftovers, it.name)) }
                throw SimulatedCrash()
            }
        }
        leftovers.listFiles().orEmpty().forEach { it.copyTo(File(markersDir, it.name)) }
        leftovers.deleteRecursively()
    }

    private class SimulatedCrash : RuntimeException()

    private companion object {
        const val URI_A = "content://media/external/audio/media/1"
        const val URI_B = "content://media/external/audio/media/2"
    }
}
