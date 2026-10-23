package org.muzzzlo.player.feature.player

import kotlinx.coroutines.flow.MutableStateFlow
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.Track

/** Records the queue calls the ViewModel makes. */
class FakePlayerController : PlayerController {
    override val state = MutableStateFlow(PlayerState())
    val playedNext = mutableListOf<Track>()
    val addedToQueue = mutableListOf<Track>()

    override fun connect() = Unit

    override fun play(
        tracks: List<Track>,
        startIndex: Int,
        playlistId: Long?,
    ) = Unit

    override fun togglePlayPause() = Unit

    override fun next() = Unit

    override fun previous() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun playNext(track: Track) {
        playedNext += track
    }

    override fun addToQueue(track: Track) {
        addedToQueue += track
    }

    override fun skipTo(index: Int) = Unit

    override fun moveQueueItem(
        from: Int,
        to: Int,
    ) = Unit

    override fun removeQueueItem(index: Int) = Unit

    override fun toggleShuffle() = Unit

    override fun cycleRepeatMode() = Unit
}
