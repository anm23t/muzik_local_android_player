package org.muzzzlo.player.core.playback

import android.content.ComponentName
import android.content.Context
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.RepeatMode
import org.muzzzlo.player.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton

/** Talks to [PlaybackService] through a [MediaController] and exposes the player as [PlayerState]. */
@Singleton
internal class MediaControllerPlayer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val library: LibraryRepository,
        private val store: PlaybackStateStore,
        private val dispatchers: DispatcherProvider,
    ) : PlayerController {
        private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
        private val _state = MutableStateFlow(PlayerState())
        override val state: StateFlow<PlayerState> = _state.asStateFlow()

        private var controller: MediaController? = null
        private var connecting = false
        private val pending = ArrayDeque<(MediaController) -> Unit>()
        private var positionJob: Job? = null

        /** Saving starts only after the previous run was restored, otherwise an empty queue would overwrite it. */
        private var restored = false

        private val listener =
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    publish(player, queueChanged = events.contains(Player.EVENT_TIMELINE_CHANGED))
                    if (restored && PERSISTED_EVENTS.any(events::contains)) save(player)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    positionJob?.cancel()
                    if (isPlaying) positionJob = scope.launch { trackPosition() }
                }
            }

        override fun connect() = withController { }

        private fun withController(action: (MediaController) -> Unit) {
            controller?.let(action) ?: run {
                pending += action
                if (connecting) return
                connecting = true
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val future = MediaController.Builder(context, token).buildAsync()
                future.addListener({
                    val connected = future.get()
                    controller = connected
                    connected.addListener(listener)
                    publish(connected, queueChanged = true)
                    if (connected.isPlaying) positionJob = scope.launch { trackPosition() }
                    scope.launch { restoreIfEmpty(connected) }
                }, Runnable::run)
            }
        }

        private suspend fun restoreIfEmpty(player: MediaController) {
            if (player.mediaItemCount == 0) restore(player)
            restored = true
            while (pending.isNotEmpty()) pending.removeFirst()(player)
        }

        private suspend fun restore(player: MediaController) {
            val saved = store.load() ?: return
            val tracks = library.tracksByIds(saved.trackIds)
            if (tracks.isEmpty()) return
            val index = restoredIndex(saved, tracks.map { it.id })
            player.shuffleModeEnabled = saved.shuffleEnabled
            player.repeatMode = saved.repeatMode.toPlayerMode()
            player.setMediaItems(tracks.map(Track::toMediaItem), index ?: 0, if (index != null) saved.positionMs else 0)
            player.prepare()
        }

        override fun play(
            tracks: List<Track>,
            startIndex: Int,
        ) = withController { player ->
            player.setMediaItems(tracks.map(Track::toMediaItem), startIndex, 0)
            player.prepare()
            player.play()
        }

        override fun togglePlayPause() = withController { if (it.isPlaying) it.pause() else it.play() }

        override fun next() = withController { it.seekToNextMediaItem() }

        override fun previous() = withController { it.seekToPreviousMediaItem() }

        override fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

        override fun playNext(track: Track) =
            withController { player ->
                if (player.mediaItemCount == 0) {
                    play(listOf(track), 0)
                } else {
                    player.addMediaItem(player.currentMediaItemIndex + 1, track.toMediaItem())
                }
            }

        override fun addToQueue(track: Track) =
            withController { player ->
                if (player.mediaItemCount == 0) play(listOf(track), 0) else player.addMediaItem(track.toMediaItem())
            }

        override fun skipTo(index: Int) =
            withController {
                if (index in 0 until it.mediaItemCount) {
                    it.seekTo(index, 0)
                    it.play()
                }
            }

        override fun moveQueueItem(
            from: Int,
            to: Int,
        ) = withController {
            if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to)
        }

        override fun removeQueueItem(index: Int) =
            withController {
                if (index in 0 until it.mediaItemCount) it.removeMediaItem(index)
            }

        override fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

        override fun cycleRepeatMode() =
            withController {
                it.repeatMode =
                    when (it.repeatMode.toRepeatMode()) {
                        RepeatMode.OFF -> Player.REPEAT_MODE_ALL
                        RepeatMode.ALL -> Player.REPEAT_MODE_ONE
                        RepeatMode.ONE -> Player.REPEAT_MODE_OFF
                    }
            }

        private suspend fun trackPosition() {
            var ticks = 0
            while (true) {
                controller?.let { player ->
                    updatePosition(player)
                    if (restored && ++ticks % SAVE_EVERY_TICKS == 0) save(player)
                }
                delay(POSITION_INTERVAL_MS)
            }
        }

        private fun updatePosition(player: Player) =
            _state.update {
                it.copy(
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    durationMs = durationOf(player, it.currentTrack),
                )
            }

        private fun publish(
            player: Player,
            queueChanged: Boolean,
        ) {
            val previous = _state.value
            val queue =
                if (queueChanged) {
                    (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).toTrack() }
                } else {
                    previous.queue
                }
            val current = player.currentMediaItem?.toTrack()
            _state.value =
                PlayerState(
                    currentTrack = current,
                    isPlaying = player.isPlaying,
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    durationMs = durationOf(player, current),
                    hasNext = player.hasNextMediaItem(),
                    hasPrevious = player.hasPreviousMediaItem(),
                    queue = queue,
                    currentIndex = if (current == null) -1 else player.currentMediaItemIndex,
                    shuffleEnabled = player.shuffleModeEnabled,
                    repeatMode = player.repeatMode.toRepeatMode(),
                )
        }

        private fun durationOf(
            player: Player,
            track: Track?,
        ): Long = player.duration.takeIf { it > 0 } ?: track?.durationMs ?: 0

        private fun save(player: Player) {
            val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).toTrack()?.id }
            val snapshot =
                SavedPlayback(
                    trackIds = ids,
                    index = player.currentMediaItemIndex,
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    shuffleEnabled = player.shuffleModeEnabled,
                    repeatMode = player.repeatMode.toRepeatMode(),
                )
            scope.launch { withContext(dispatchers.io) { store.save(snapshot) } }
        }

        private fun Int.toRepeatMode() =
            when (this) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }

        private fun RepeatMode.toPlayerMode() =
            when (this) {
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            }

        private companion object {
            const val POSITION_INTERVAL_MS = 500L
            const val SAVE_EVERY_TICKS = 10
            val PERSISTED_EVENTS =
                intArrayOf(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                )
        }
    }
