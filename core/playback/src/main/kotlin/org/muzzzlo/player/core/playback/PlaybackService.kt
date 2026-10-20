package org.muzzzlo.player.core.playback

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.muzzzlo.player.core.model.ListenRecorder
import org.muzzzlo.player.core.model.PlaybackBehaviorSettings
import org.muzzzlo.player.core.model.SettingsRepository
import javax.inject.Inject

/**
 * Owns the player and the media session. The player is a [CrossfadePlayer] over two ExoPlayers; Media3 provides the
 * notification, lock screen controls and headset buttons.
 */
@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var listenRecorder: ListenRecorder

    private var session: MediaSession? = null
    private var headsetResumer: HeadsetResumer? = null
    private var listenTracker: ListenTracker? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // One quick DataStore read so the very first track already follows the settings.
        val initial = runBlocking { settings.settings.first() }
        val renderers = List(DECKS) { AudioRenderersFactory(this, initial.gapless, initial.replayGain) }
        scope.launch {
            settings.settings.map { it.gapless }.distinctUntilChanged().collect { gapless ->
                renderers.forEach { it.gapless = gapless }
            }
        }
        scope.launch {
            settings.settings
                .map { it.replayGain }
                .distinctUntilChanged()
                .collect { replayGain -> renderers.forEach { it.replayGain.settings = replayGain } }
        }
        // The same seed gives both decks the same shuffle order, so they agree on the next track.
        val shuffleSeed = System.nanoTime()
        val player = CrossfadePlayer(this, renderers.map { buildDeck(it, shuffleSeed) }, Looper.getMainLooper())
        player.crossfade = initial.crossfade
        scope.launch(Dispatchers.Main) {
            settings.settings.map { it.crossfade }.distinctUntilChanged().collect { player.crossfade = it }
        }
        val resumer = HeadsetResumer(this, player, Handler(Looper.getMainLooper()))
        resumer.start()
        headsetResumer = resumer
        listenTracker = ListenTracker(player, listenRecorder).also(ListenTracker::start)
        applyBehavior(initial.playbackBehavior, player, renderers, resumer)
        scope.launch(Dispatchers.Main) {
            settings.settings.map { it.playbackBehavior }.distinctUntilChanged().collect {
                applyBehavior(it, player, renderers, resumer)
            }
        }
        session = MediaSession.Builder(this, player).build()
    }

    private fun applyBehavior(
        behavior: PlaybackBehaviorSettings,
        player: CrossfadePlayer,
        renderers: List<AudioRenderersFactory>,
        resumer: HeadsetResumer,
    ) {
        player.transientFocus = behavior.transientFocus
        resumer.enabled = behavior.resumeOnHeadset
        renderers.forEach { it.silenceTrim.enabled = behavior.skipSilence }
    }

    /**
     * One deck of [CrossfadePlayer]. Audio focus is off here: the crossfade player holds it for both decks, otherwise
     * the decks would take it from each other.
     */
    private fun buildDeck(
        renderers: AudioRenderersFactory,
        shuffleSeed: Long,
    ): ExoPlayer {
        val deck =
            ExoPlayer.Builder(this, renderers)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    // handleAudioFocus =
                    false,
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
        deck.setShuffleOrder(DefaultShuffleOrder(0, shuffleSeed))
        renderers.shuffle = deck.shuffleModeEnabled
        deck.addListener(
            object : Player.Listener {
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    renderers.shuffle = shuffleModeEnabled
                }
            },
        )
        return deck
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        listenTracker?.stop()
        listenTracker = null
        headsetResumer?.stop()
        headsetResumer = null
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val DECKS = 2
    }
}
