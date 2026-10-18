package org.muzzzlo.player.core.model

/**
 * An artist of the library: a separate entity, a track can have several (CLAUDE.md, decision 3).
 *
 * @property trackCount tracks present on the device that credit this artist.
 * @property photoPath copy of the photo the user picked, in the app's private folder; null when there is none.
 * @property dateAdded when the first of their tracks appeared on the device, seconds since the epoch.
 */
data class Artist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val photoPath: String? = null,
    val dateAdded: Long? = null,
)
