package spike

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicContainer.dynamicContainer
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import spike.jaudiotagger.JAudioTaggerSpike
import spike.taglib.TagLibSpike
import java.io.File
import java.io.FileDescriptor
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs the same checks (plan of task 0.3.0, step 3) against both libraries and every fixture format.
 * Each check is its own test; failures mean "not supported" and end up in build/matrix/matrix.md.
 */
class MatrixTest {
    private val fixtures = File(System.getProperty("spike.fixtures"))
    private val cover1 by lazy { File(fixtures, "cover.png").readBytes() }
    private val cover2 by lazy { File(fixtures, "cover2.png").readBytes() }

    private val formats = listOf("mp3_v23.mp3", "mp3_v24.mp3", "flac.flac", "m4a.m4a", "ogg.ogg", "opus.opus")
    private val libraries: List<TagSpike> by lazy {
        listOf(JAudioTaggerSpike(), TagLibSpike(System.getProperty("spike.taglib.lib")))
    }

    private val edit by lazy {
        SpikeEdit(
            title = "Новое название ✓",
            artists = listOf("Gamma", "Delta"),
            lyrics = "[00:00.50]Новая строка\n[00:01.50]Second line",
            cover = cover2,
        )
    }

    @TestFactory
    fun matrix(): List<DynamicContainer> =
        libraries.map { lib ->
            dynamicContainer(lib.name, formats.map { fixture -> dynamicContainer(fixture, checks(lib, fixture)) })
        }

    private fun checks(lib: TagSpike, fixture: String) = buildList {
        val source = File(fixtures, fixture)
        val v23 = fixture == "mp3_v23.mp3"
        val expectedArtists = if (v23) listOf("Alpha; Beta") else listOf("Alpha", "Beta")
        val writtenArtists = if (v23) listOf("Gamma; Delta") else listOf("Gamma", "Delta")

        add(check(lib, fixture, "read: core fields") {
            val t = lib.read(source)
            assertEq("Spike Title", t.title)
            assertEq("Spike Album", t.album)
            assertEq("Alpha", t.albumArtist)
            assertEq("Test", t.genre)
            check(t.year?.startsWith("2024") == true) { "year=${t.year}" }
            check(t.track?.startsWith("3") == true) { "track=${t.track}" }
            check(t.disc?.startsWith("1") == true) { "disc=${t.disc}" }
        })
        add(check(lib, fixture, "read: multiple artists") { assertEq(expectedArtists, lib.read(source).artists) })
        add(check(lib, fixture, "read: cover") { assertEq(Oracle.sha(cover1), Oracle.sha(lib.read(source).cover)) })
        add(check(lib, fixture, "read: lyrics (LRC text kept)") {
            assertEq("[00:01.00]Line one\n[00:02.00]Line two", lib.read(source).lyrics?.replace("\r\n", "\n"))
        })

        // One write, many observations of the result through the independent oracle.
        val written by lazy { writeCopy(lib, source, edit) }
        val before by lazy { Oracle.tags(source) }
        add(check(lib, fixture, "write: succeeds") { written.getOrThrow() })
        add(check(lib, fixture, "write: title (unicode)") { assertEq(edit.title, scalar(after(written), "title")) })
        add(check(lib, fixture, "write: multiple artists") {
            assertEq(writtenArtists, list(after(written), "artists"))
        })
        if (fixture.endsWith(".mp3")) {
            add(check(lib, fixture, "write: ID3 version kept") {
                assertEq(before.getString("id3_version"), after(written).getString("id3_version"))
            })
        }
        add(check(lib, fixture, "write: cover replaced") {
            assertEq(listOf(Oracle.sha(cover2)), list(after(written), "covers"))
        })
        add(check(lib, fixture, "write: lyrics") {
            assertEq(listOf(edit.lyrics), list(after(written), "lyrics").map { it.replace("\r\n", "\n") })
        })
        add(check(lib, fixture, "write: unknown fields kept") {
            assertEq(before.getJSONObject("unknown").toMap(), after(written).getJSONObject("unknown").toMap())
        })
        add(check(lib, fixture, "write: untouched fields kept") {
            for (key in listOf("album", "albumartist", "genre", "track")) {
                assertEq(before.get(key).toString(), after(written).get(key).toString(), key)
            }
        })
        add(check(lib, fixture, "write: audio data unchanged") {
            assertEq(Oracle.audioMd5(source), Oracle.audioMd5(written.getOrThrow()))
        })
        add(check(lib, fixture, "write: library re-reads its own result") {
            val t = lib.read(written.getOrThrow())
            assertEq(edit.title, t.title)
            assertEq(writtenArtists, t.artists)
        })
        add(check(lib, fixture, "write: remove cover") {
            val file = writeCopy(lib, source, edit.copy(cover = null)).getOrThrow()
            val o = Oracle.tags(file)
            assertEq(emptyList<String>(), list(o, "covers"))
            assertEq(before.getJSONObject("unknown").toMap(), o.getJSONObject("unknown").toMap())
        })
        if (lib is TagLibSpike) {
            add(check(lib, fixture, "fd: read via file descriptor") {
                RandomAccessFile(source, "r").use { raf -> assertEq(expectedArtists, lib.readByFd(rawFd(raf.fd)).artists) }
            })
        }
    }

    private fun writeCopy(lib: TagSpike, source: File, edit: SpikeEdit): Result<File> = runCatching {
        // The safe-write flow works on a private temporary copy (ARCHITECTURE.md, section 5).
        val dir = Files.createTempDirectory("spike-${lib.name}").toFile()
        val copy = source.copyTo(File(dir, source.name))
        lib.write(copy, edit)
        copy
    }

    private fun after(written: Result<File>) = Oracle.tags(written.getOrThrow())

    private fun check(lib: TagSpike, fixture: String, name: String, body: () -> Unit) = dynamicTest(name) {
        val result = runCatching(body)
        results[Triple(lib.name, fixture, name)] = result.exceptionOrNull()?.let { "✘ ${it.message?.lineSequence()?.first()?.take(MAX_NOTE)}" } ?: "✔"
        result.getOrThrow()
    }

    companion object {
        private const val MAX_NOTE = 90
        private val results = ConcurrentHashMap<Triple<String, String, String>, String>()

        @JvmStatic
        @AfterAll
        fun writeReport() {
            val dir = File(System.getProperty("spike.reports")).apply { mkdirs() }
            val fixtures = results.keys.map { it.second }.distinct().sorted()
            val report = buildString {
                appendLine("# Tag library matrix")
                for (lib in results.keys.map { it.first }.distinct().sorted()) {
                    appendLine("\n## $lib\n")
                    appendLine("| Check | ${fixtures.joinToString(" | ")} |")
                    appendLine("|---|${fixtures.joinToString("") { "---|" }}")
                    for (check in results.keys.filter { it.first == lib }.map { it.third }.distinct()) {
                        val cells = fixtures.map { results[Triple(lib, it, check)] ?: "–" }
                        appendLine("| $check | ${cells.joinToString(" | ")} |")
                    }
                }
            }
            File(dir, "matrix.md").writeText(report)
        }

        private fun rawFd(fd: FileDescriptor): Int =
            FileDescriptor::class.java.getDeclaredField("fd").apply { isAccessible = true }.getInt(fd)

        private fun scalar(o: JSONObject, key: String): String? =
            when (val v = o.opt(key)) {
                is JSONArray -> v.optString(0, null)
                null, JSONObject.NULL -> null
                else -> v.toString()
            }

        private fun list(o: JSONObject, key: String): List<String> =
            when (val v = o.opt(key)) {
                is JSONArray -> (0 until v.length()).map { v.get(it).toString() }
                null, JSONObject.NULL -> emptyList()
                else -> listOf(v.toString())
            }

        private fun assertEq(expected: Any?, actual: Any?, what: String = "") =
            check(expected == actual) { "$what expected <$expected> but was <$actual>".trim() }
    }
}
