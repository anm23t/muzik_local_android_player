package org.muzzzlo.player.core.model

/** Database side of the "Check" button. Tags in the files are not touched. Implemented in `core:library`. */
interface ArtistCheckRepository {
    /**
     * Folds [variantIds] into [keepId]: their tracks and album credits move over, their photo goes along when
     * [keepId] has none, and each old spelling becomes an alias of [keepId], so a rescan sends it there again.
     * All of it is one transaction.
     */
    suspend fun merge(
        keepId: Long,
        variantIds: List<Long>,
    )
}
