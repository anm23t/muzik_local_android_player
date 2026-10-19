package org.muzzzlo.player.core.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler

/** What the player does when another app takes or gives back the audio focus. */
internal enum class FocusChange {
    /** The focus is back: full volume, resume after a pause for [PAUSE]. */
    GAIN,

    /** Another app took the focus for good: pause. */
    LOSS,

    /** Another app took the focus for a while (a call, navigation): pause and resume on [GAIN]. */
    PAUSE,

    /** Another app plays a short sound over ours: lower the volume until [GAIN]. */
    DUCK,
}

/** Maps an `AudioManager.AUDIOFOCUS_*` change to what the player does; null for values it ignores. */
internal fun focusChangeOf(change: Int): FocusChange? =
    when (change) {
        AudioManager.AUDIOFOCUS_GAIN -> FocusChange.GAIN
        AudioManager.AUDIOFOCUS_LOSS -> FocusChange.LOSS
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusChange.PAUSE
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusChange.DUCK
        else -> null
    }

/**
 * Audio focus of the whole player. The two decks of [CrossfadePlayer] run without their own focus handling,
 * otherwise each request of one deck would take the focus from the other (docs/decisions/011-crossfade.md).
 * Changes arrive on [handler].
 */
internal class AudioFocus(
    context: Context,
    handler: Handler,
    private val onChange: (FocusChange) -> Unit,
) {
    private val audioManager = checkNotNull(context.getSystemService(AudioManager::class.java))
    private val request =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener({ change -> focusChangeOf(change)?.let(onChange) }, handler)
            .build()
    private var held = false

    /** Asks for the focus; true when the player may play. */
    fun request(): Boolean {
        if (!held) held = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return held
    }

    /** Gives the focus back, if held. */
    fun abandon() {
        if (!held) return
        audioManager.abandonAudioFocusRequest(request)
        held = false
    }
}
