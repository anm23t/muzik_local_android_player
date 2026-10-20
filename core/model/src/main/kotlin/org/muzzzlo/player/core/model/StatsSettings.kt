package org.muzzzlo.player.core.model

/**
 * Statistics settings (ARCHITECTURE.md, sections 7 and 9).
 *
 * A listen is counted when it played [thresholdPercent] of the track or [thresholdMinutes], whichever comes first.
 *
 * @property thresholdPercent from [MIN_PERCENT] to [MAX_PERCENT], in steps of [PERCENT_STEP].
 * @property thresholdMinutes from [MIN_MINUTES] to [MAX_MINUTES].
 * @property privateMode listens are not recorded at all while it is on.
 */
data class StatsSettings(
    val thresholdPercent: Int = DEFAULT_PERCENT,
    val thresholdMinutes: Int = DEFAULT_MINUTES,
    val privateMode: Boolean = false,
) {
    /** The same settings with the threshold brought into its range and onto its step. */
    fun clamped(): StatsSettings =
        copy(thresholdPercent = clampPercent(thresholdPercent), thresholdMinutes = clampMinutes(thresholdMinutes))

    companion object {
        const val DEFAULT_PERCENT = 50
        const val MIN_PERCENT = 10
        const val MAX_PERCENT = 90
        const val PERCENT_STEP = 10
        const val DEFAULT_MINUTES = 4
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 10

        /** Brings [percent] into [MIN_PERCENT]..[MAX_PERCENT], rounded to the nearest [PERCENT_STEP]. */
        fun clampPercent(percent: Int): Int {
            val rounded = (percent + PERCENT_STEP / 2).floorDiv(PERCENT_STEP) * PERCENT_STEP
            return rounded.coerceIn(MIN_PERCENT, MAX_PERCENT)
        }

        /** Brings [minutes] into [MIN_MINUTES]..[MAX_MINUTES]. */
        fun clampMinutes(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
    }
}

/** Turns what the player reports into a stored listen. */
object ListenRules {
    /** Shorter listens, such as tracks passed by while skipping through the queue, are not recorded. */
    const val MIN_RECORDED_MS = 1000L

    private const val MS_PER_MINUTE = 60_000L
    private const val PERCENT = 100

    /**
     * How long a track of [durationMs] has to play to count: the share of the track or the minutes, whichever is
     * shorter. A track without a known length needs the minutes.
     */
    fun thresholdMs(
        settings: StatsSettings,
        durationMs: Long,
    ): Long {
        val clamped = settings.clamped()
        val byMinutes = clamped.thresholdMinutes * MS_PER_MINUTE
        if (durationMs <= 0) return byMinutes
        return minOf(durationMs * clamped.thresholdPercent / PERCENT, byMinutes)
    }

    /**
     * The event to store for [listen], or null when nothing is stored: in private mode and for listens shorter than
     * [MIN_RECORDED_MS]. A listen ended by the user is a skip only when it was not counted.
     */
    fun eventOf(
        listen: FinishedListen,
        settings: StatsSettings,
    ): ListenEvent? {
        if (settings.privateMode || listen.playedMs < MIN_RECORDED_MS) return null
        val track = listen.track
        val counted = listen.playedMs >= thresholdMs(settings, track.durationMs)
        return ListenEvent(
            trackId = track.id,
            startedAtMs = listen.startedAtMs,
            playedMs = listen.playedMs,
            durationMs = track.durationMs,
            counted = counted,
            skipped = listen.end == ListenEnd.MANUAL && !counted,
            titleSnapshot = track.title,
            artistsSnapshot = track.artists,
        )
    }
}
