package org.muzzzlo.player.core.model

/** A single audio file of the library as the UI and the player see it. */
data class Track(
    val id: Long,
    val uri: String,
    val title: String,
    /** Artists in tag order. Empty when the file has no artist tag. */
    val artists: List<String>,
    val albumId: Long?,
    val albumTitle: String?,
    val durationMs: Long,
    val trackNumber: Int?,
    /** ReplayGain values from the file's tags; [ReplayGainInfo.NONE] when it has none. */
    val replayGain: ReplayGainInfo = ReplayGainInfo.NONE,
) {
    /** Artists joined for display, for example "A, B". */
    val artistsText: String get() = artists.joinToString(", ")
}
