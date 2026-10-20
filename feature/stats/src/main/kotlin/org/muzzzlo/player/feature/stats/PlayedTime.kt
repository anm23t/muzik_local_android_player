package org.muzzzlo.player.feature.stats

private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

/** Listened time split for display: whole [hours] and the [minutes] left over (0..59). Seconds are dropped. */
data class PlayedTime(val hours: Long, val minutes: Long)

/** Splits [playedMs] into hours and minutes; a negative value counts as zero. */
fun playedTimeOf(playedMs: Long): PlayedTime {
    val totalMinutes = playedMs.coerceAtLeast(0) / MS_PER_MINUTE
    return PlayedTime(totalMinutes / MINUTES_PER_HOUR, totalMinutes % MINUTES_PER_HOUR)
}
