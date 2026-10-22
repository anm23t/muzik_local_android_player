package org.muzzzlo.player.feature.playlists

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.designsystem.component.TrackMarksMenu
import org.muzzzlo.player.core.designsystem.component.TrackMarksState
import org.muzzzlo.player.core.model.TrackMarksRepository

/** "Add to favorites" and "Rate" of the track menu for the track with [trackId]; writes run in [scope]. */
internal fun TrackMarksRepository.menuFor(
    scope: CoroutineScope,
    trackId: Long,
): TrackMarksMenu =
    TrackMarksMenu(
        load = { marks(trackId).let { TrackMarksState(it.isFavorite, it.ratingTenths) } },
        onFavorite = { favorite -> scope.launch { setFavorite(trackId, favorite) } },
        onRate = { tenths -> scope.launch { setRating(trackId, tenths) } },
    )
