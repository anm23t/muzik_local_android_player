package org.muzzzlo.player.core.playback

/**
 * When a crossfade happens within the outgoing track, in milliseconds from its start.
 *
 * @property prepareAtMs the next track is loaded on the idle deck, so it can start without a delay.
 * @property startAtMs the overlap starts: the next track plays, the current one fades out.
 * @property overlapMs length of the overlap; the current track ends at `startAtMs + overlapMs`.
 */
internal data class CrossfadeTiming(
    val prepareAtMs: Long,
    val startAtMs: Long,
    val overlapMs: Long,
) {
    companion object {
        /** The next track is loaded this long before the overlap starts. */
        const val PREPARE_AHEAD_MS = 3_000L

        /** Timing for a track of [durationMs] and an overlap of [overlapMs]; null when there is nothing to overlap. */
        fun of(
            durationMs: Long,
            overlapMs: Long,
        ): CrossfadeTiming? {
            if (overlapMs <= 0 || durationMs <= overlapMs) return null
            val start = durationMs - overlapMs
            return CrossfadeTiming((start - PREPARE_AHEAD_MS).coerceAtLeast(0), start, overlapMs)
        }
    }
}

/** How far the overlap has gone: 0.0 at its start, 1.0 at its end, by the position of the incoming track. */
internal fun fadeProgress(
    incomingPositionMs: Long,
    overlapMs: Long,
): Float = if (overlapMs <= 0) 1f else (incomingPositionMs.toFloat() / overlapMs).coerceIn(0f, 1f)
