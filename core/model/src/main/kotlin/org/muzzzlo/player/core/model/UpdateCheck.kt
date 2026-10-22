package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * The newest release of the project on GitHub.
 *
 * @property tag the release tag as published, for example `v1.1`.
 * @property pageUrl the address of the release page (https, on github.com).
 */
data class ReleaseInfo(
    val tag: String,
    val pageUrl: String,
)

/** What asking for the latest release gave. */
sealed interface ReleaseLookup {
    data class Found(val release: ReleaseInfo) : ReleaseLookup

    /** The project has no published release yet. */
    data object NoRelease : ReleaseLookup

    /** No network, a timeout, an error answer or an answer that cannot be read. */
    data object Failed : ReleaseLookup
}

/** Asks where the latest release is. The only place of the app that talks to the network (task 1.1.9). */
interface ReleaseSource {
    suspend fun latest(): ReleaseLookup
}

/**
 * What the app remembers about checking for updates.
 *
 * @property enabled null until the user was asked once ("Check for updates?"), then their answer.
 * @property lastCheckMs when the last check that got an answer ran (epoch milliseconds); null before the first one.
 * @property announcedTag the tag of the release the user was told about last, so one release is announced once.
 */
data class UpdateCheckState(
    val enabled: Boolean? = null,
    val lastCheckMs: Long? = null,
    val announcedTag: String? = null,
)

/** Stores [UpdateCheckState]. It is not part of the backup: consent to use the network belongs to one installation. */
interface UpdateCheckStore {
    val state: Flow<UpdateCheckState>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setChecked(atMs: Long)

    suspend fun setAnnounced(tag: String)
}

/** Version numbers of release tags. */
object ReleaseVersion {
    private val NUMBERS = Regex("^[vV]?(\\d+(?:\\.\\d+)*)")

    /**
     * The numbers of a tag or version name: `v1.1` and `1.1` are [1, 1], `1.0-start` is [1, 0]. Null when the text
     * does not begin with a number. What follows the numbers (`-start`, `-debug`) is ignored.
     */
    fun parse(text: String): List<Int>? =
        NUMBERS.find(text.trim())?.groupValues?.get(1)?.split('.')?.map { it.toIntOrNull() ?: return null }

    /**
     * Whether [candidate] is a higher version than [current]; missing parts count as 0 (`1.1` equals `1.1.0`).
     * False when either of them cannot be read, so an odd tag never announces an update.
     */
    fun isNewer(
        candidate: String,
        current: String,
    ): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return false
        val firstDifference =
            (0 until maxOf(a.size, b.size))
                .map { a.getOrElse(it) { 0 }.compareTo(b.getOrElse(it) { 0 }) }
                .firstOrNull { it != 0 }
        return firstDifference != null && firstDifference > 0
    }
}

/** Reading the answer of GitHub for `releases/latest`. */
object GithubRelease {
    private const val PAGE_PREFIX = "https://github.com/"

    /**
     * The release in [json], or null when the text is not JSON, has no tag or page address, or the page is not an
     * https address on github.com (the app only ever opens such links).
     */
    fun parse(json: String): ReleaseInfo? {
        val root = (runCatching { Json.parse(json) }.getOrNull() as? Map<*, *>) ?: return null
        val tag = (root["tag_name"] as? String)?.trim().orEmpty()
        val page = (root["html_url"] as? String)?.trim().orEmpty()
        if (tag.isEmpty() || !page.startsWith(PAGE_PREFIX) || page.length == PAGE_PREFIX.length) return null
        return ReleaseInfo(tag, page)
    }
}

/** When to ask again. */
object UpdateSchedule {
    const val INTERVAL_MS = 24L * 60 * 60 * 1000

    /**
     * True before the first check and once [INTERVAL_MS] has passed. A last check in the future (the clock was set
     * back) counts as due, so the check cannot be stuck.
     */
    fun isDue(
        lastCheckMs: Long?,
        nowMs: Long,
    ): Boolean = lastCheckMs == null || nowMs < lastCheckMs || nowMs - lastCheckMs >= INTERVAL_MS
}

/**
 * Looks for a newer release when the user agreed and a day has passed since the last check (task 1.1.9). The app
 * only tells about a release, it never downloads or installs it.
 *
 * @param currentVersion the version name of the running app, for example `1.0-start`.
 */
class UpdateChecker(
    private val store: UpdateCheckStore,
    private val source: ReleaseSource,
    private val currentVersion: String,
    private val clock: () -> Long,
) {
    /**
     * The release to tell the user about, or null: no consent, not due yet, no network, nothing newer, or this
     * release was announced already. A failed lookup does not count as a check, so the next start tries again.
     */
    suspend fun checkIfDue(): ReleaseInfo? {
        val state = store.state.first()
        val now = clock()
        if (state.enabled != true || !UpdateSchedule.isDue(state.lastCheckMs, now)) return null
        return when (val lookup = source.latest()) {
            ReleaseLookup.Failed -> null
            ReleaseLookup.NoRelease -> null.also { store.setChecked(now) }
            is ReleaseLookup.Found -> {
                store.setChecked(now)
                lookup.release.takeIf {
                    ReleaseVersion.isNewer(it.tag, currentVersion) && it.tag != state.announcedTag
                }
            }
        }
    }
}
