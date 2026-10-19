package org.muzzzlo.player.core.designsystem.component

/**
 * One artist offered by [ArtistPickerMenu]. The design system does not know `core:model`, so screens map their
 * artists to this.
 *
 * @property photoPath copy of the artist's photo; null shows the empty circle.
 */
data class ArtistChoice(
    val id: Long,
    val name: String,
    val photoPath: String? = null,
)
