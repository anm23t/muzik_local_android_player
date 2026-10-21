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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * A native crash cannot happen in a JVM test, so a crash is simulated by leaving the marker behind:
 * the guarded block throws [SimulatedCrash] after copying the marker aside, and the copy is restored afterwards.
 */
class NativeCallGuardTest {
    @TempDir
    lateinit var dir: File

    private val markersDir get() = File(dir, "in-progress")
    private val soloMarkersDir get() = File(dir, "in-progress-solo")

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
    fun `first crash makes the file a suspect, not quarantined`() {
        simulateCrash(NativeCallGuard(dir), URI_A)

        val restarted = NativeCallGuard(dir)
        val quarantinedNow = restarted.recoverAfterCrash()

        assertTrue(quarantinedNow.isEmpty())
        assertEquals(setOf(URI_A), restarted.suspects())
        assertFalse(restarted.isQuarantined(URI_A))
        assertTrue(markersDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `suspect that crashes again while read alone is quarantined`() {
        crashAndRestart(URI_A)
        val guard = NativeCallGuard(dir)
        simulateCrash(guard, URI_A)

        val restarted = NativeCallGuard(dir)

        assertEquals(setOf(URI_A), restarted.recoverAfterCrash())
        assertTrue(restarted.isQuarantined(URI_A))
        assertFalse(restarted.isQuarantined(URI_B))
        assertTrue(restarted.suspects().isEmpty())
        assertTrue(soloMarkersDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `suspect read without a crash is cleared`() {
        crashAndRestart(URI_A)
        val guard = NativeCallGuard(dir)

        val seenSolo = guard.runGuarded(URI_A) { soloMarkersDir.listFiles().orEmpty().map { it.readText() } }

        assertEquals(listOf(URI_A), seenSolo)
        assertTrue(guard.suspects().isEmpty())
        assertTrue(NativeCallGuard(dir).suspects().isEmpty())
        assertTrue(NativeCallGuard(dir).recoverAfterCrash().isEmpty())
    }

    @Test
    fun `Kotlin exception in a suspect's call clears it`() {
        crashAndRestart(URI_A)
        val guard = NativeCallGuard(dir)

        assertThrows<IllegalStateException> { guard.runGuarded<Unit>(URI_A) { error("boom") } }

        assertTrue(guard.suspects().isEmpty())
        assertFalse(guard.isQuarantined(URI_A))
    }

    @Test
    fun `all calls in progress during a crash become suspects`() {
        val guard = NativeCallGuard(dir)
        guard.runGuarded(URI_A) { simulateCrash(guard, URI_B) }
        // The outer marker was removed by its finally block; put it back as a crash would have left it.
        File(markersDir, "outer").writeText(URI_A)

        val restarted = NativeCallGuard(dir)

        assertTrue(restarted.recoverAfterCrash().isEmpty())
        assertEquals(setOf(URI_A, URI_B), restarted.suspects())
        assertTrue(restarted.quarantined().isEmpty())
    }

    @Test
    fun `only the file read alone is quarantined, not its earlier neighbours`() {
        crashAndRestart(URI_A)
        crashAndRestart(URI_B)
        // Both are suspects now; URI_A crashes again alone.
        simulateCrash(NativeCallGuard(dir), URI_A)

        val restarted = NativeCallGuard(dir)

        assertEquals(setOf(URI_A), restarted.recoverAfterCrash())
        assertEquals(setOf(URI_A), restarted.quarantined())
        assertEquals(setOf(URI_B), restarted.suspects())
    }

    @Test
    fun `suspect waits for calls in progress and keeps new calls out`() {
        crashAndRestart(URI_A)
        val guard = NativeCallGuard(dir)
        val ordinaryStarted = CountDownLatch(1)
        val releaseOrdinary = CountDownLatch(1)
        val suspectStarted = CountDownLatch(1)
        val releaseSuspect = CountDownLatch(1)
        val lateStarted = CountDownLatch(1)

        val ordinary =
            thread {
                guard.runGuarded(URI_B) {
                    ordinaryStarted.countDown()
                    releaseOrdinary.await()
                }
            }
        ordinaryStarted.await()
        val suspect =
            thread {
                guard.runGuarded(URI_A) {
                    suspectStarted.countDown()
                    releaseSuspect.await()
                }
            }
        assertFalse(suspectStarted.await(WAIT_MS, TimeUnit.MILLISECONDS), "suspect ran next to another call")

        releaseOrdinary.countDown()
        assertTrue(suspectStarted.await(TIMEOUT_S, TimeUnit.SECONDS))
        val late = thread { guard.runGuarded(URI_C) { lateStarted.countDown() } }
        assertFalse(lateStarted.await(WAIT_MS, TimeUnit.MILLISECONDS), "a call ran next to the suspect")

        releaseSuspect.countDown()
        assertTrue(lateStarted.await(TIMEOUT_S, TimeUnit.SECONDS))
        listOf(ordinary, suspect, late).forEach { it.join() }
        assertTrue(guard.suspects().isEmpty())
    }

    @Test
    fun `ordinary calls run in parallel`() {
        val guard = NativeCallGuard(dir)
        val bothInside = CountDownLatch(2)

        val threads =
            listOf(URI_A, URI_B).map { key ->
                thread {
                    guard.runGuarded(key) {
                        bothInside.countDown()
                        bothInside.await(TIMEOUT_S, TimeUnit.SECONDS)
                    }
                }
            }
        threads.forEach { it.join() }

        assertEquals(0, bothInside.count)
    }

    @Test
    fun `nested call for a suspect runs inside the outer call without a deadlock`() {
        crashAndRestart(URI_A)
        val guard = NativeCallGuard(dir)

        val result = guard.runGuarded(URI_B) { guard.runGuarded(URI_A) { 7 } }

        assertEquals(7, result)
        // It did not run alone, so it is still a suspect.
        assertEquals(setOf(URI_A), guard.suspects())
    }

    @Test
    fun `recovery is idempotent and keeps earlier quarantine`() {
        quarantine(URI_A)
        crashAndRestart(URI_B)

        val restarted = NativeCallGuard(dir)

        assertTrue(restarted.recoverAfterCrash().isEmpty())
        assertEquals(setOf(URI_A), restarted.quarantined())
        assertEquals(setOf(URI_B), restarted.suspects())
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
    fun `release takes a file out of quarantine and makes it a suspect`() {
        quarantine(URI_A)
        val guard = NativeCallGuard(dir)

        guard.release(URI_A)
        guard.release(URI_B)

        assertFalse(guard.isQuarantined(URI_A))
        assertFalse(NativeCallGuard(dir).isQuarantined(URI_A))
        assertEquals(setOf(URI_A), NativeCallGuard(dir).suspects())
    }

    @Test
    fun `released file that crashes once goes straight back`() {
        quarantine(URI_A)
        NativeCallGuard(dir).release(URI_A)
        simulateCrash(NativeCallGuard(dir), URI_A)

        assertEquals(setOf(URI_A), NativeCallGuard(dir).recoverAfterCrash())
    }

    @Test
    fun `keys with spaces, Cyrillic and emoji survive the round trip`() {
        val key = "content://media/external/audio/media/1?name=трек 🎧.flac"
        quarantine(key)

        assertEquals(setOf(key), NativeCallGuard(dir).quarantined())
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
        val leftovers = mutableListOf<Pair<File, String>>()
        assertThrows<SimulatedCrash> {
            guard.runGuarded<Unit>(key) {
                listOf(markersDir, soloMarkersDir)
                    .flatMap { folder -> folder.listFiles().orEmpty().toList() }
                    .filter { marker -> marker.isFile && marker.readText() == key }
                    .forEach { marker -> leftovers.add(marker to key) }
                throw SimulatedCrash()
            }
        }
        leftovers.forEach { (marker, text) -> marker.writeText(text) }
    }

    /** A crash during an ordinary call, then a restart: [key] becomes a suspect. */
    private fun crashAndRestart(key: String) {
        simulateCrash(NativeCallGuard(dir), key)
        NativeCallGuard(dir).recoverAfterCrash()
    }

    /** Two crashes on [key], the second while it ran alone: [key] is quarantined. */
    private fun quarantine(key: String) {
        crashAndRestart(key)
        crashAndRestart(key)
    }

    private class SimulatedCrash : RuntimeException()

    private companion object {
        const val URI_A = "content://media/external/audio/media/1"
        const val URI_B = "content://media/external/audio/media/2"
        const val URI_C = "content://media/external/audio/media/3"
        const val WAIT_MS = 200L
        const val TIMEOUT_S = 5L
    }
}
