package org.muzzzlo.player.core.model

/**
 * An album with the artists it is credited to.
 *
 * @property artists album artists from the album artist tag (the track artists when the files have none).
 * @property dateAdded when the first of its tracks appeared on the device, seconds since the epoch.
 */
data class Album(
    val id: Long,
    val title: String,
    val artists: List<String>,
    val year: Int?,
    val trackCount: Int,
    val dateAdded: Long? = null,
) {
    val artistsText: String get() = artists.joinToString(", ")
}
