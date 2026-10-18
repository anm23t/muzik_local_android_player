package org.muzzzlo.player.core.tags

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TagSource
import java.io.File
import kotlin.random.Random

/**
 * Fuzzes the native reader with damaged copies of the reference fixtures (docs/decisions/005, risk 1).
 *
 * A crash inside TagLib kills the test JVM, so simply finishing is the main check. Damaged files are made
 * at run time from a fixed seed, so a failure is reproducible and no broken files sit in the repository.
 */
class BrokenFilesTest {
    @TempDir
    lateinit var tempDir: File

    private val reader by lazy {
        TagLibTagReader(TagLibBridge(), NativeCallGuard(File(tempDir, "guard")), TestDispatchers)
    }

    @ParameterizedTest
    @ValueSource(strings = ["mp3_v23.mp3", "mp3_v24.mp3", "flac.flac", "m4a.m4a", "ogg.ogg", "opus.opus"])
    fun `survives truncated files`(name: String) =
        runTest {
            val original = resourceFile("tags/$name").readBytes()
            val cuts = (0 until TRUNCATIONS).map { original.size.toLong() * it / TRUNCATIONS } + listOf(1L, 4L, 10L)

            for (length in cuts.distinct()) {
                readDamaged(name, "cut$length", original.copyOf(length.toInt()))
            }
        }

    @ParameterizedTest
    @ValueSource(strings = ["mp3_v23.mp3", "mp3_v24.mp3", "flac.flac", "m4a.m4a", "ogg.ogg", "opus.opus"])
    fun `survives corrupted bytes`(name: String) =
        runTest {
            val original = resourceFile("tags/$name").readBytes()
            val random = Random(name.hashCode())

            repeat(MUTATIONS) { round ->
                val damaged = original.copyOf()
                // Tag headers sit at the start, so hit the first kilobytes more often than the audio.
                val zone = if (random.nextBoolean()) minOf(HEADER_ZONE, damaged.size) else damaged.size
                repeat(random.nextInt(1, MAX_BYTES_PER_MUTATION + 1)) {
                    damaged[random.nextInt(zone)] = random.nextInt(256).toByte()
                }
                readDamaged(name, "mut$round", damaged)
            }
        }

    @Test
    fun `survives an empty file and random noise`() =
        runTest {
            assertEquals(TagReadResult.Unreadable, readDamaged("empty.mp3", "empty", ByteArray(0)))
            readDamaged("noise.flac", "noise", Random(1).nextBytes(4096))
            // A valid ID3v2 header that claims a tag far longer than the file.
            readDamaged("id3.mp3", "id3", byteArrayOf(0x49, 0x44, 0x33, 4, 0, 0, 0x7F, 0x7F, 0x7F, 0x7F))
        }

    private suspend fun readDamaged(
        name: String,
        variant: String,
        bytes: ByteArray,
    ): TagReadResult {
        val file = File(tempDir, "$variant-$name").apply { writeBytes(bytes) }
        val source = TagSource.FilePath(file.path)
        val result = reader.readTags(source)
        assertTrue(result is TagReadResult.Success || result == TagReadResult.Unreadable) { "$variant: $result" }
        reader.readCover(source)
        file.delete()
        return result
    }

    private companion object {
        const val TRUNCATIONS = 40
        const val MUTATIONS = 300
        const val MAX_BYTES_PER_MUTATION = 8
        const val HEADER_ZONE = 2048
    }
}
