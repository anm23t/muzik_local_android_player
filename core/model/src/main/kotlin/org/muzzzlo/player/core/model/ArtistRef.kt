package org.muzzzlo.player.core.model

/**
 * Short form of an artist for menus that pick one of a track's artists.
 *
 * @property photoPath copy of the photo the user picked; null when there is none.
 */
data class ArtistRef(
    val id: Long,
    val name: String,
    val photoPath: String? = null,
)
