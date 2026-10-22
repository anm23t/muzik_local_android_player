package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateCheckTest {
    @Test
    fun `version numbers are read from tags and version names`() {
        val cases =
            mapOf(
                "v1.1" to listOf(1, 1),
                "1.1" to listOf(1, 1),
                "V2.10.3" to listOf(2, 10, 3),
                "1.0-start" to listOf(1, 0),
                "1.1-debug" to listOf(1, 1),
                " 1.2 " to listOf(1, 2),
                "7" to listOf(7),
            )
        cases.forEach { (text, numbers) -> assertEquals(numbers, ReleaseVersion.parse(text), text) }
        listOf("", "latest", "release-1.1", "v", ".1").forEach { assertNull(ReleaseVersion.parse(it), it) }
    }

    @Test
    fun `a higher version is newer, an equal or lower one is not`() {
        val newer =
            listOf("v1.1" to "1.0-start", "1.10" to "1.9", "2.0" to "1.99.9", "1.0.1" to "1.0", "v1.1.0.1" to "1.1")
        newer.forEach { (a, b) -> assertTrue(ReleaseVersion.isNewer(a, b), "$a over $b") }
        val notNewer =
            listOf("v1.0" to "1.0-start", "1.1" to "1.1.0", "1.0" to "1.1", "1.9" to "1.10", "1.1-debug" to "1.1")
        notNewer.forEach { (a, b) -> assertFalse(ReleaseVersion.isNewer(a, b), "$a over $b") }
    }

    @Test
    fun `an unreadable tag or version never counts as newer`() {
        assertFalse(ReleaseVersion.isNewer("nightly", "1.0"))
        assertFalse(ReleaseVersion.isNewer("2.0", "dev"))
    }

    @Test
    fun `the answer of GitHub gives the tag and the page`() {
        val json =
            """{"url":"x","tag_name":"v1.1","name":"Version 1.1","draft":false,""" +
                """"html_url":"https://github.com/anm23t/muzik_local_android_player/releases/tag/v1.1","assets":[]}"""
        assertEquals(
            ReleaseInfo("v1.1", "https://github.com/anm23t/muzik_local_android_player/releases/tag/v1.1"),
            GithubRelease.parse(json),
        )
    }

    @Test
    fun `an answer that cannot be trusted gives nothing`() {
        val page = "https://github.com/a/b/releases/tag/v2"
        val answers =
            listOf(
                "",
                "not json",
                "[]",
                """{"message":"Not Found"}""",
                """{"tag_name":"","html_url":"$page"}""",
                """{"tag_name":"v2"}""",
                """{"tag_name":"v2","html_url":"http://github.com/a/b/releases/tag/v2"}""",
                """{"tag_name":"v2","html_url":"https://example.com/a/b/releases/tag/v2"}""",
                """{"tag_name":"v2","html_url":"https://github.com.evil.example/x"}""",
                """{"tag_name":"v2","html_url":"https://github.com/"}""",
                """{"tag_name":2,"html_url":"$page"}""",
            )
        answers.forEach { assertNull(GithubRelease.parse(it), it) }
    }

    @Test
    fun `a check is due before the first one and a day after the last`() {
        val day = UpdateSchedule.INTERVAL_MS
        assertTrue(UpdateSchedule.isDue(null, 5))
        assertFalse(UpdateSchedule.isDue(1_000, 1_000))
        assertFalse(UpdateSchedule.isDue(1_000, 1_000 + day - 1))
        assertTrue(UpdateSchedule.isDue(1_000, 1_000 + day))
        assertTrue(UpdateSchedule.isDue(1_000, 999))
    }

    private val release = ReleaseInfo("v1.1", "https://github.com/a/b/releases/tag/v1.1")

    private class Store(initial: UpdateCheckState) : UpdateCheckStore {
        val current = MutableStateFlow(initial)
        override val state: Flow<UpdateCheckState> = current

        override suspend fun setEnabled(enabled: Boolean) {
            current.value = current.value.copy(enabled = enabled)
        }

        override suspend fun setChecked(atMs: Long) {
            current.value = current.value.copy(lastCheckMs = atMs)
        }

        override suspend fun setAnnounced(tag: String) {
            current.value = current.value.copy(announcedTag = tag)
        }
    }

    private class Source(val lookup: ReleaseLookup) : ReleaseSource {
        var calls = 0

        override suspend fun latest(): ReleaseLookup = lookup.also { calls++ }
    }

    private fun check(
        state: UpdateCheckState,
        lookup: ReleaseLookup,
        version: String = "1.0-start",
        now: Long = 10_000_000_000,
    ): Triple<ReleaseInfo?, Store, Source> {
        val store = Store(state)
        val source = Source(lookup)
        val found = runBlocking { UpdateChecker(store, source, version) { now }.checkIfDue() }
        return Triple(found, store, source)
    }

    @Test
    fun `without consent nothing is asked`() {
        listOf(null, false).forEach { enabled ->
            val (found, _, source) = check(UpdateCheckState(enabled = enabled), ReleaseLookup.Found(release))
            assertNull(found)
            assertEquals(0, source.calls)
        }
    }

    @Test
    fun `a newer release is reported and the check is remembered`() {
        val (found, store, source) = check(UpdateCheckState(enabled = true), ReleaseLookup.Found(release))
        assertEquals(release, found)
        assertEquals(1, source.calls)
        assertEquals(10_000_000_000, store.current.value.lastCheckMs)
    }

    @Test
    fun `a check within a day asks nothing`() {
        val state = UpdateCheckState(enabled = true, lastCheckMs = 10_000_000_000 - 1_000)
        val (found, store, source) = check(state, ReleaseLookup.Found(release))
        assertNull(found)
        assertEquals(0, source.calls)
        assertEquals(state, store.current.value)
    }

    @Test
    fun `the same release is announced once, the next one again`() {
        val announced = UpdateCheckState(enabled = true, announcedTag = "v1.1")
        assertNull(check(announced, ReleaseLookup.Found(release)).first)
        val next = ReleaseInfo("v1.2", "https://github.com/a/b/releases/tag/v1.2")
        assertEquals(next, check(announced, ReleaseLookup.Found(next)).first)
    }

    @Test
    fun `the running version or an older release is not an update`() {
        assertNull(check(UpdateCheckState(enabled = true), ReleaseLookup.Found(release), version = "1.1").first)
        assertNull(check(UpdateCheckState(enabled = true), ReleaseLookup.Found(release), version = "1.2-debug").first)
    }

    @Test
    fun `a failed lookup is not remembered so the next start tries again`() {
        val (found, store, source) = check(UpdateCheckState(enabled = true), ReleaseLookup.Failed)
        assertNull(found)
        assertEquals(1, source.calls)
        assertNull(store.current.value.lastCheckMs)
    }

    @Test
    fun `a project without releases counts as a check`() {
        val (found, store, _) = check(UpdateCheckState(enabled = true), ReleaseLookup.NoRelease)
        assertNull(found)
        assertEquals(10_000_000_000, store.current.value.lastCheckMs)
    }
}
