package spike

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import spike.jaudiotagger.JAudioTaggerSpike
import spike.taglib.TagLibSpike
import java.io.File

/**
 * Rough read speed (tags + audio properties, as the scanner needs) on the host, warm page cache.
 * Only an indication: phones are several times slower. Run with -Pspike.perf.files=2000.
 */
class PerfTest {
    private val fixtures = File(System.getProperty("spike.fixtures"))
    private val reads = System.getProperty("spike.perf.files").toInt()

    @Test
    fun readSpeed() {
        assumeTrue(reads > 0, "set -Pspike.perf.files to run")
        val common = listOf("mp3_v23.mp3", "mp3_v24.mp3", "flac.flac", "m4a.m4a", "ogg.ogg").map { File(fixtures, it) }
        val long = listOf("perf_long.mp3", "perf_long.flac").map { File(fixtures, it) }
        val libs = listOf(JAudioTaggerSpike(), TagLibSpike(System.getProperty("spike.taglib.lib")))
        val report = buildString {
            appendLine("| Library | small files (5 formats), µs/file | 4-min MP3 320k, µs/file | 4-min FLAC, µs/file |")
            appendLine("|---|---|---|---|")
            for (lib in libs) {
                measure(lib, common, reads / 10) // warm-up (JIT, page cache)
                val small = measure(lib, common, reads)
                val mp3 = measure(lib, long.take(1), reads / 10)
                val flac = measure(lib, long.drop(1), reads / 10)
                appendLine("| ${lib.name} | $small | $mp3 | $flac |")
            }
        }
        File(System.getProperty("spike.reports")).apply { mkdirs() }.resolve("perf.md").writeText(report)
        println(report)
    }

    private fun measure(lib: TagSpike, files: List<File>, count: Int): Long {
        val start = System.nanoTime()
        repeat(count) { lib.readForScan(files[it % files.size]) }
        return (System.nanoTime() - start) / count / NANOS_PER_MICRO
    }

    private companion object {
        const val NANOS_PER_MICRO = 1000L
    }
}
