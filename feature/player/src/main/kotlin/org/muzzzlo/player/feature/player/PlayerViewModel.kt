package org.muzzzlo.player.feature.player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.PlayerState
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val player: PlayerController,
    ) : ViewModel() {
        val state: StateFlow<PlayerState> = player.state

        fun onPlayPause() = player.togglePlayPause()

        fun onNext() = player.next()

        fun onPrevious() = player.previous()

        fun onSeek(positionMs: Long) = player.seekTo(positionMs)
    }
