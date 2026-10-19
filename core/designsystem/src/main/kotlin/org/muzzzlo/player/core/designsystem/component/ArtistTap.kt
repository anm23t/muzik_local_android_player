package org.muzzzlo.player.core.designsystem.component

/** What a tap on the artists of a track does (DESIGN.md, section 3, "Поведение"). */
sealed interface ArtistTap {
    /** The track has no artist to open. */
    data object Nothing : ArtistTap

    /** One artist: the card opens at once. */
    data class Open(
        val artist: ArtistChoice,
    ) : ArtistTap

    /** Several artists: the picker asks which card to open. */
    data class Choose(
        val artists: List<ArtistChoice>,
    ) : ArtistTap

    companion object {
        /** Decides the result of a tap on a track with these artists. */
        fun of(artists: List<ArtistChoice>): ArtistTap =
            when (artists.size) {
                0 -> Nothing
                1 -> Open(artists.single())
                else -> Choose(artists)
            }
    }
}
