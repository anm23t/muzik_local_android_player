package org.muzzzlo.player.core.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.muzzzlo.player.core.model.Crossfade
import org.muzzzlo.player.core.model.CrossfadeSettings
import org.muzzzlo.player.core.model.TransientFocusAction

/**
 * The player of the media session: two ExoPlayers ("decks") behind one [Player], so the end of a track can overlap
 * the start of the next one (docs/decisions/011-crossfade.md).
 *
 * Both decks hold the same queue; every queue change goes to both. The active deck plays and is what the session
 * sees. Without crossfade it moves to the next track by itself, gapless when the files allow. Before a crossfade
 * the idle deck loads the next track; at the overlap it starts and becomes the active deck, while the outgoing deck
 * fades out, pauses at the end of its track and stops. The decks have no audio focus handling of their own, the
 * player keeps the focus for both ([AudioFocus]).
 *
 * All calls come on [looper], the looper the decks were built on.
 */
@UnstableApi
internal class CrossfadePlayer(
    context: Context,
    private val decks: List<ExoPlayer>,
    looper: Looper,
) : SimpleBasePlayer(looper) {
    private val handler = Handler(looper)
    private val focus = AudioFocus(context, handler, ::onFocusChange)
    private var activeIndex = 0
    private val active: ExoPlayer get() = decks[activeIndex]
    private val idle: ExoPlayer get() = decks[1 - activeIndex]

    private var playWhenReady = false
    private var playWhenReadyReason = USER_REQUEST
    private var focusPaused = false
    private var duck = 1f
    private var volume = 1f
    private var discontinuity: Pair<Int, Long>? = null

    /** The crossfade planned for the track on the active deck, with the messages that carry it out. */
    private var plan: Plan? = null

    /** What a short sound of another app does to the playback; read when the sound starts. */
    var transientFocus: TransientFocusAction = TransientFocusAction.DUCK

    /** The overlap in progress. */
    private var fade: Fade? = null

    private val fadeTick = Runnable { onFadeTick() }

    /** Share of the volume left by the fade of the sleep timer ([SleepTimerDriver]); 1 when no timer fades. */
    var sleepVolume: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyVolumes()
        }

    /**
     * Where the current track stops being heard alone, in ms from its start: the start of its planned crossfade,
     * otherwise its end; null while the length is unknown. The sleep timer fades out before it.
     */
    val trackEndMs: Long?
        get() =
            plan?.key?.timing?.startAtMs?.takeIf { plan?.key?.deck === active }
                ?: active.duration.takeIf { it != C.TIME_UNSET && it > 0 }

    /** Crossfade settings; a change applies to the track that plays now if its overlap has not started yet. */
    var crossfade: CrossfadeSettings = CrossfadeSettings()
        set(value) {
            field = value
            reschedule()
        }

    init {
        require(decks.size == DECKS) { "CrossfadePlayer needs two decks" }
        decks.forEach { deck -> deck.addListener(DeckListener(deck)) }
    }

    override fun getState(): State {
        val deck = active
        val state =
            State.Builder()
                .setAvailableCommands(commandsOf(deck))
                .setPlayWhenReady(playWhenReady, playWhenReadyReason)
                .setPlaybackState(deck.playbackState)
                .setPlaybackSuppressionReason(
                    if (focusPaused) TRANSIENT_FOCUS_LOSS else deck.playbackSuppressionReason,
                )
                .setPlayerError(deck.playerError)
                .setRepeatMode(deck.repeatMode)
                .setShuffleModeEnabled(deck.shuffleModeEnabled)
                .setIsLoading(deck.isLoading)
                .setAudioAttributes(deck.audioAttributes)
                .setVolume(volume)
                .setPlaylist(deck.currentTimeline, deck.currentTracks, deck.mediaMetadata)
                .setPlaylistMetadata(deck.playlistMetadata)
                .setCurrentMediaItemIndex(deck.currentMediaItemIndex)
                .setContentPositionMs { deck.contentPosition }
                .setContentBufferedPositionMs { deck.contentBufferedPosition }
                .setTotalBufferedDurationMs { deck.totalBufferedDuration }
        discontinuity?.let { (reason, positionMs) -> state.setPositionDiscontinuity(reason, positionMs) }
        discontinuity = null
        return state.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val allowed = !playWhenReady || focus.request()
        this.playWhenReady = playWhenReady && allowed
        playWhenReadyReason = if (allowed) USER_REQUEST else FOCUS_LOSS
        applyPlayWhenReady()
        return done()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        active.prepare()
        return done()
    }

    override fun handleStop(): ListenableFuture<*> {
        stopCrossfade()
        active.stop()
        focus.abandon()
        return done()
    }

    override fun handleRelease(): ListenableFuture<*> {
        handler.removeCallbacksAndMessages(null)
        focus.abandon()
        decks.forEach(ExoPlayer::release)
        return done()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        idleFirst.forEach { it.repeatMode = repeatMode }
        return done()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        idleFirst.forEach { it.shuffleModeEnabled = shuffleModeEnabled }
        return done()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        this.volume = volume
        applyVolumes()
        return done()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        stopCrossfade()
        idleFirst.forEach {
            if (startIndex == C.INDEX_UNSET) {
                it.setMediaItems(mediaItems, true)
            } else {
                it.setMediaItems(mediaItems, startIndex, startPositionMs)
            }
        }
        return done()
    }

    override fun handleAddMediaItems(
        index: Int,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<*> {
        idleFirst.forEach { it.addMediaItems(index, mediaItems) }
        return done()
    }

    override fun handleMoveMediaItems(
        fromIndex: Int,
        toIndex: Int,
        newIndex: Int,
    ): ListenableFuture<*> {
        idleFirst.forEach { it.moveMediaItems(fromIndex, toIndex, newIndex) }
        return done()
    }

    override fun handleReplaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<*> {
        stopCrossfade()
        idleFirst.forEach { it.replaceMediaItems(fromIndex, toIndex, mediaItems) }
        return done()
    }

    override fun handleRemoveMediaItems(
        fromIndex: Int,
        toIndex: Int,
    ): ListenableFuture<*> {
        stopCrossfade()
        idleFirst.forEach { it.removeMediaItems(fromIndex, toIndex) }
        return done()
    }

    /** A seek or a skip is a manual change: the overlap in progress ends at once, the next change is planned anew. */
    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        stopCrossfade()
        if (positionMs == C.TIME_UNSET) {
            active.seekToDefaultPosition(mediaItemIndex)
        } else {
            active.seekTo(mediaItemIndex, positionMs)
        }
        return done()
    }

    private fun onFocusChange(received: FocusChange) {
        when (received.under(transientFocus)) {
            FocusChange.GAIN -> {
                duck = 1f
                focusPaused = false
            }
            FocusChange.LOSS -> {
                focusPaused = false
                playWhenReady = false
                playWhenReadyReason = FOCUS_LOSS
                focus.abandon()
            }
            FocusChange.PAUSE -> focusPaused = true
            FocusChange.DUCK -> duck = DUCK_VOLUME
        }
        applyVolumes()
        applyPlayWhenReady()
        invalidateState()
    }

    /**
     * Queue changes go to the idle deck first: events of the active deck may already plan the next crossfade, which
     * then sees both decks with the new queue.
     */
    private val idleFirst: List<ExoPlayer> get() = listOf(idle, active)

    private val shouldPlay: Boolean get() = playWhenReady && !focusPaused

    /** Both sounding decks follow the player: the active one and, during an overlap, the outgoing one. */
    private fun applyPlayWhenReady() {
        active.playWhenReady = shouldPlay
        fade?.outgoing?.playWhenReady = shouldPlay
        handler.removeCallbacks(fadeTick)
        if (fade != null && shouldPlay) handler.post(fadeTick)
    }

    private fun applyVolumes() {
        val base = volume * duck * sleepVolume
        val current = fade
        if (current == null) {
            active.volume = base
        } else {
            val progress = fadeProgress(active.currentPosition, current.overlapMs)
            active.volume = base * Crossfade.fadeInVolume(progress)
            current.outgoing.volume = base * Crossfade.fadeOutVolume(progress)
        }
    }

    /**
     * Plans the crossfade at the end of the track on the active deck, or drops the plan when the track, its next
     * track, the settings or the length changed so that the plan no longer holds.
     */
    private fun reschedule() {
        if (fade != null) return
        val deck = active
        val key = plannedCrossfadeOf(deck, crossfade)
        if (key == plan?.key) return
        cancelPlan()
        val position = deck.currentPosition
        if (key == null || position >= key.timing.startAtMs) return
        val prepare =
            if (position >= key.timing.prepareAtMs) {
                prepareNext(key.nextIndex)
                null
            } else {
                deck.messageAt(key.index, key.timing.prepareAtMs) { prepareNext(key.nextIndex) }
            }
        val start = deck.messageAt(key.index, key.timing.startAtMs) { startCrossfade(key) }
        plan = Plan(key, listOfNotNull(prepare, start))
    }

    private fun cancelPlan() {
        val current = plan ?: return
        current.messages.forEach(PlayerMessage::cancel)
        plan = null
        if (fade == null) idle.stop()
    }

    /** Loads the next track on the idle deck, silent and paused at its start. */
    private fun prepareNext(nextIndex: Int) {
        val deck = idle
        deck.playWhenReady = false
        deck.volume = 0f
        deck.pauseAtEndOfMediaItems = false
        deck.seekTo(nextIndex, 0)
        deck.prepare()
    }

    private fun startCrossfade(key: PlannedCrossfade) {
        if (key.deck !== active || fade != null) return
        val incoming = idle
        if (incoming.currentMediaItemIndex != key.nextIndex || incoming.playbackState == Player.STATE_IDLE) {
            prepareNext(key.nextIndex)
        }
        val outgoing = active
        outgoing.pauseAtEndOfMediaItems = true
        plan = null
        activeIndex = 1 - activeIndex
        fade = Fade(outgoing, key.timing.overlapMs)
        discontinuity = Player.DISCONTINUITY_REASON_AUTO_TRANSITION to 0L
        applyVolumes()
        applyPlayWhenReady()
        invalidateState()
    }

    private fun onFadeTick() {
        val current = fade ?: return
        applyVolumes()
        val outgoingDone = current.outgoing.playbackState == Player.STATE_ENDED || !current.outgoing.playWhenReady
        if (fadeProgress(active.currentPosition, current.overlapMs) >= 1f || (outgoingDone && shouldPlay)) {
            finishFade()
        } else if (shouldPlay) {
            handler.postDelayed(fadeTick, FADE_TICK_MS)
        }
    }

    /** Ends the overlap: the outgoing deck stops and becomes the idle deck. */
    private fun finishFade() {
        val current = fade ?: return
        fade = null
        handler.removeCallbacks(fadeTick)
        current.outgoing.stop()
        current.outgoing.pauseAtEndOfMediaItems = false
        current.outgoing.volume = 0f
        applyVolumes()
        reschedule()
    }

    /** A manual change or a queue change: the overlap in progress ends and the planned crossfade is dropped. */
    private fun stopCrossfade() {
        finishFade()
        cancelPlan()
    }

    /** Passes the events of the active deck on to the session; the outgoing deck only ends the overlap. */
    private inner class DeckListener(
        private val deck: ExoPlayer,
    ) : Player.Listener {
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (deck === active) discontinuity = reason to newPosition.positionMs
        }

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
        ) {
            if (deck === active && !playWhenReady && reason == BECOMING_NOISY) {
                this@CrossfadePlayer.playWhenReady = false
                playWhenReadyReason = reason
                applyPlayWhenReady()
            }
        }

        override fun onEvents(
            player: Player,
            events: Player.Events,
        ) {
            if (deck === fade?.outgoing) {
                if (deck.playbackState == Player.STATE_ENDED || (!deck.playWhenReady && shouldPlay)) finishFade()
                return
            }
            if (deck !== active) return
            reschedule()
            invalidateState()
        }
    }

    private class Plan(
        val key: PlannedCrossfade,
        val messages: List<PlayerMessage>,
    )

    private class Fade(
        val outgoing: ExoPlayer,
        val overlapMs: Long,
    )

    private companion object {
        const val DECKS = 2
        const val DUCK_VOLUME = 0.2f
        const val FADE_TICK_MS = 20L
        const val USER_REQUEST = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
        const val FOCUS_LOSS = Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
        const val BECOMING_NOISY = Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
        const val TRANSIENT_FOCUS_LOSS = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
    }
}

/**
 * The crossfade at the end of the track on [deck].
 *
 * @property windowUid the queue entry of the track, so a moved or replaced entry gets a new plan.
 */
internal data class PlannedCrossfade(
    val deck: Player,
    val windowUid: Any,
    val index: Int,
    val nextIndex: Int,
    val timing: CrossfadeTiming,
)

/**
 * Applies the crossfade rules to the track that plays on [deck] and the next one in play order; null when the next
 * track starts without a crossfade.
 */
@UnstableApi
internal fun plannedCrossfadeOf(
    deck: Player,
    settings: CrossfadeSettings,
): PlannedCrossfade? {
    val item = deck.currentMediaItem
    val state = deck.playbackState
    if (item == null || state == Player.STATE_IDLE || state == Player.STATE_ENDED) return null
    val index = deck.currentMediaItemIndex
    val nextIndex = deck.nextMediaItemIndex
    val durationMs = deck.duration.takeIf { it != C.TIME_UNSET } ?: item.crossfadeTrack().durationMs
    val current = item.crossfadeTrack().copy(durationMs = durationMs)
    val next = if (nextIndex == C.INDEX_UNSET) null else deck.getMediaItemAt(nextIndex).crossfadeTrack()
    val overlapMs = Crossfade.overlapMs(settings, current, next, deck.repeatMode == Player.REPEAT_MODE_ONE)
    val timing = CrossfadeTiming.of(durationMs, overlapMs) ?: return null
    val uid = deck.currentTimeline.getWindow(index, Timeline.Window()).uid
    return PlannedCrossfade(deck, uid, index, nextIndex, timing)
}

/** Runs [action] on the application looper when this deck plays item [index] at [positionMs]. */
@UnstableApi
private fun ExoPlayer.messageAt(
    index: Int,
    positionMs: Long,
    action: () -> Unit,
): PlayerMessage =
    createMessage { _, _ -> action() }
        .setLooper(applicationLooper)
        .setPosition(index, positionMs)
        .setDeleteAfterDelivery(true)
        .send()

private fun done(): ListenableFuture<*> = Futures.immediateVoidFuture()

/** The session commands the player supports, as far as the deck allows them now. */
@UnstableApi
private fun commandsOf(deck: Player): Player.Commands =
    Player.Commands.Builder()
        .apply { SUPPORTED_COMMANDS.filter(deck::isCommandAvailable).forEach(::add) }
        .build()

private val SUPPORTED_COMMANDS =
    listOf(
        Player.COMMAND_PLAY_PAUSE,
        Player.COMMAND_PREPARE,
        Player.COMMAND_STOP,
        Player.COMMAND_RELEASE,
        Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_MEDIA_ITEM,
        Player.COMMAND_SEEK_BACK,
        Player.COMMAND_SEEK_FORWARD,
        Player.COMMAND_SET_SHUFFLE_MODE,
        Player.COMMAND_SET_REPEAT_MODE,
        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
        Player.COMMAND_GET_TIMELINE,
        Player.COMMAND_GET_METADATA,
        Player.COMMAND_SET_MEDIA_ITEM,
        Player.COMMAND_CHANGE_MEDIA_ITEMS,
        Player.COMMAND_GET_AUDIO_ATTRIBUTES,
        Player.COMMAND_GET_VOLUME,
        Player.COMMAND_SET_VOLUME,
        Player.COMMAND_GET_TRACKS,
    )
