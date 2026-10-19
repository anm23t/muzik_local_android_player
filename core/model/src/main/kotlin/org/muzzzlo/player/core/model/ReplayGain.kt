package org.muzzzlo.player.core.model

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * ReplayGain values of one file, as read from its tags (`REPLAYGAIN_*`, or `R128_*` in Opus converted to the
 * ReplayGain reference level). A missing or broken value is null.
 *
 * @property trackGainDb gain that brings this track to the reference loudness, in decibels.
 * @property trackPeak highest sample of the track, 1.0 = full scale.
 * @property albumGainDb gain that brings the whole album to the reference loudness, in decibels.
 * @property albumPeak highest sample of the album, 1.0 = full scale.
 */
data class ReplayGainInfo(
    val trackGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumGainDb: Float? = null,
    val albumPeak: Float? = null,
) {
    /** True when the file has no gain at all, so the gain for untagged files applies. */
    val hasGain: Boolean get() = trackGainDb != null || albumGainDb != null

    companion object {
        val NONE = ReplayGainInfo()
    }
}

/** Which ReplayGain value the player applies. */
enum class ReplayGainMode {
    /** No gain is applied. */
    OFF,

    /** Track gain: every track plays at the same loudness. */
    TRACK,

    /** Album gain: an album keeps the loudness differences between its tracks. */
    ALBUM,

    /** Album gain while an album plays in order, track gain otherwise. */
    AUTO,
}

/**
 * ReplayGain settings (ARCHITECTURE.md, section 9). Decibel values are kept in steps of [STEP_DB].
 *
 * @property preampDb added to the gain from the tags, from [PREAMP_MIN_DB] to [PREAMP_MAX_DB].
 * @property untaggedGainDb gain for files without ReplayGain tags, from [UNTAGGED_MIN_DB] to 0; the preamp does not
 *   apply to it.
 * @property clipProtection the gain is lowered so the peak from the tags does not go over full scale.
 */
data class ReplayGainSettings(
    val mode: ReplayGainMode = ReplayGainMode.AUTO,
    val preampDb: Float = 0f,
    val untaggedGainDb: Float = DEFAULT_UNTAGGED_GAIN_DB,
    val clipProtection: Boolean = true,
) {
    /** The same settings with both decibel values brought into their ranges and steps. */
    fun clamped(): ReplayGainSettings =
        copy(
            preampDb = clampDb(preampDb, PREAMP_MIN_DB, PREAMP_MAX_DB),
            untaggedGainDb = clampDb(untaggedGainDb, UNTAGGED_MIN_DB, UNTAGGED_MAX_DB),
        )

    companion object {
        const val STEP_DB = 0.5f
        const val PREAMP_MIN_DB = -15f
        const val PREAMP_MAX_DB = 15f
        const val UNTAGGED_MIN_DB = -15f
        const val UNTAGGED_MAX_DB = 0f
        const val DEFAULT_UNTAGGED_GAIN_DB = -6f

        /** Rounds [db] to the nearest [STEP_DB] and keeps it within [min]..[max]; a non-number becomes 0 or [max]. */
        fun clampDb(
            db: Float,
            min: Float,
            max: Float,
        ): Float {
            val value = if (db.isNaN()) 0f else db
            val stepped = (value / STEP_DB).roundToInt() * STEP_DB
            return stepped.coerceIn(min, max)
        }
    }
}

/** Turns ReplayGain values and settings into the factor the audio samples are multiplied by. */
object ReplayGain {
    /**
     * The linear factor for a track: 1.0 leaves the sound as it is.
     *
     * @param albumInOrder the track plays as part of its album in order; used by [ReplayGainMode.AUTO].
     */
    fun factor(
        settings: ReplayGainSettings,
        info: ReplayGainInfo,
        albumInOrder: Boolean,
    ): Float {
        if (settings.mode == ReplayGainMode.OFF) return 1f
        if (!info.hasGain) return dbToLinear(settings.untaggedGainDb)
        val album = settings.mode == ReplayGainMode.ALBUM || (settings.mode == ReplayGainMode.AUTO && albumInOrder)
        val (gainDb, peak) =
            // Either value stands in for the other when the file has only one of them.
            if ((album && info.albumGainDb != null) || info.trackGainDb == null) {
                info.albumGainDb to info.albumPeak
            } else {
                info.trackGainDb to info.trackPeak
            }
        val factor = dbToLinear(checkNotNull(gainDb) + settings.preampDb)
        return if (settings.clipProtection && peak != null && peak > 0f) minOf(factor, 1f / peak) else factor
    }

    /** Decibels to a linear amplitude factor: 0 dB is 1.0, −6 dB is about 0.5. */
    fun dbToLinear(db: Float): Float = 10f.pow(db / DB_PER_DECADE)

    private const val DB_PER_DECADE = 20f
}
