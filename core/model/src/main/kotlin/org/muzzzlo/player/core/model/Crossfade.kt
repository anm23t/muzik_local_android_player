package org.muzzzlo.player.core.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Crossfade settings (ARCHITECTURE.md, section 9; docs/decisions/011-crossfade.md).
 *
 * @property seconds length of the overlap, from 0 to [MAX_SECONDS]; 0 turns crossfade off.
 * @property withinAlbum crossfade also between consecutive tracks of one album; off keeps albums gapless.
 */
data class CrossfadeSettings(
    val seconds: Int = 0,
    val withinAlbum: Boolean = false,
) {
    /** The same settings with [seconds] brought into its range. */
    fun clamped(): CrossfadeSettings = copy(seconds = clampSeconds(seconds))

    companion object {
        const val MAX_SECONDS = 12

        /** Brings [seconds] into 0..[MAX_SECONDS]. */
        fun clampSeconds(seconds: Int): Int = seconds.coerceIn(0, MAX_SECONDS)
    }
}

/**
 * What the crossfade rules need to know about one track.
 *
 * @property discNumber null is read as disc 1.
 */
data class CrossfadeTrack(
    val albumId: Long?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val durationMs: Long,
)

/** Decides whether an automatic change to the next track is crossfaded, and gives the fade curve. */
object Crossfade {
    /**
     * Length of the overlap between [current] and [next] in milliseconds; 0 means no crossfade, the next track
     * starts after the last sample of the current one (gapless when the files allow).
     *
     * Only automatic changes are crossfaded; a skip, a jump in the queue or a seek is never passed here.
     *
     * @param next the next track in play order; null at the end of the queue.
     * @param repeatOne the current track repeats itself.
     */
    fun overlapMs(
        settings: CrossfadeSettings,
        current: CrossfadeTrack,
        next: CrossfadeTrack?,
        repeatOne: Boolean,
    ): Long {
        val seconds = CrossfadeSettings.clampSeconds(settings.seconds)
        if (seconds == 0 || next == null || repeatOne) return 0
        if (!settings.withinAlbum && continuesAlbum(current, next)) return 0
        val shorter = minOf(current.durationMs, next.durationMs).coerceAtLeast(0)
        return minOf(seconds * MS_PER_SECOND, shorter / 2)
    }

    /**
     * True when [next] follows [current] on the same album: the next track number on the same disc, or track 1 of
     * the next disc. When either track has no number the album order is unknown, and two tracks of one album are
     * taken as consecutive, so a possibly gapless album is not crossfaded.
     */
    fun continuesAlbum(
        current: CrossfadeTrack,
        next: CrossfadeTrack,
    ): Boolean {
        if (current.albumId == null || current.albumId != next.albumId) return false
        val track = current.trackNumber
        val nextTrack = next.trackNumber
        if (track == null || nextTrack == null) return true
        val disc = current.discNumber ?: 1
        val nextDisc = next.discNumber ?: 1
        return (nextDisc == disc && nextTrack == track + 1) || (nextDisc == disc + 1 && nextTrack == 1)
    }

    /**
     * Volume of the incoming track at [progress] (0.0 at the start of the overlap, 1.0 at the end). Together with
     * [fadeOutVolume] the sum of powers stays 1, so the loudness does not dip in the middle.
     */
    fun fadeInVolume(progress: Float): Float = sin(progress.coerceIn(0f, 1f) * QUARTER_TURN)

    /** Volume of the outgoing track at [progress], the pair of [fadeInVolume]. */
    fun fadeOutVolume(progress: Float): Float = cos(progress.coerceIn(0f, 1f) * QUARTER_TURN).coerceAtLeast(0f)

    private const val MS_PER_SECOND = 1000L
    private const val QUARTER_TURN = (PI / 2).toFloat()
}
