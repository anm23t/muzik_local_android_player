package org.muzzzlo.player.core.model

/** An album with the artists it is credited to. */
data class Album(
    val id: Long,
    val title: String,
    val artists: List<String>,
    val year: Int?,
    val trackCount: Int,
) {
    val artistsText: String get() = artists.joinToString(", ")
}
