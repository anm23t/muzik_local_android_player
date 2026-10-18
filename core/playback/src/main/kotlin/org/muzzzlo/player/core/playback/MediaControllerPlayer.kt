package org.muzzzlo.player.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton

/** Talks to [PlaybackService] through a [MediaController] and exposes the player as [PlayerState]. */
@Singleton
internal class MediaControllerPlayer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        dispatchers: DispatcherProvider,
    ) : PlayerController {
        private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
        private val _state = MutableStateFlow(PlayerState())
        override val state: StateFlow<PlayerState> = _state.asStateFlow()

        /** Tracks of the current queue by media item id, so the UI gets full [Track] objects back. */
        private var queue: Map<String, Track> = emptyMap()
        private var controller: MediaController? = null
        private var pending: (MediaController) -> Unit = {}
        private var positionJob: Job? = null

        private val listener =
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) = publish(player)

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    positionJob?.cancel()
                    if (isPlaying) positionJob = scope.launch { trackPosition() }
                }
            }

        private fun connect(action: (MediaController) -> Unit) {
            controller?.let(action) ?: run {
                pending = action
                if (connecting) return
                connecting = true
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val future = MediaController.Builder(context, token).buildAsync()
                future.addListener({
                    connecting = false
                    val connected = future.get()
                    controller = connected
                    connected.addListener(listener)
                    publish(connected)
                    pending(connected)
                    pending = {}
                }, Runnable::run)
            }
        }

        private var connecting = false

        override fun play(
            tracks: List<Track>,
            startIndex: Int,
        ) {
            queue = tracks.associateBy { it.id.toString() }
            connect { player ->
                player.setMediaItems(tracks.map(::mediaItem), startIndex, 0)
                player.prepare()
                player.play()
            }
        }

        override fun togglePlayPause() = connect { if (it.isPlaying) it.pause() else it.play() }

        override fun next() = connect { it.seekToNextMediaItem() }

        override fun previous() = connect { it.seekToPreviousMediaItem() }

        override fun seekTo(positionMs: Long) = connect { it.seekTo(positionMs) }

        private fun mediaItem(track: Track) =
            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artistsText.ifEmpty { null })
                        .setAlbumTitle(track.albumTitle)
                        .build(),
                )
                .build()

        private suspend fun trackPosition() {
            while (true) {
                controller?.let(::publish)
                delay(POSITION_INTERVAL_MS)
            }
        }

        private fun publish(player: Player) {
            val id = player.currentMediaItem?.mediaId
            _state.value =
                PlayerState(
                    currentTrack = id?.let(queue::get),
                    isPlaying = player.isPlaying,
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    durationMs = player.duration.takeIf { it > 0 } ?: queue[id]?.durationMs ?: 0,
                    hasNext = player.hasNextMediaItem(),
                    hasPrevious = player.hasPreviousMediaItem(),
                )
        }

        private companion object {
            const val POSITION_INTERVAL_MS = 500L
        }
    }
