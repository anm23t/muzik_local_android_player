package org.muzzzlo.player.core.model

/** What the player does while another app plays a short sound over ours (a navigation prompt, a message tone). */
enum class TransientFocusAction {
    /** The volume goes down for the length of the sound. */
    DUCK,

    /** The playback pauses and resumes when the other app is done. */
    PAUSE,
}

/**
 * Playback behaviour settings (ARCHITECTURE.md, section 9).
 *
 * @property transientFocus reaction to a short audio focus loss that allows ducking.
 * @property resumeOnHeadset playback continues when headphones or a Bluetooth device are connected again after
 *   they stopped it by being unplugged or disconnected, see [HeadsetResume].
 * @property skipSilence digital silence at the start and the end of a track is not played.
 */
data class PlaybackBehaviorSettings(
    val transientFocus: TransientFocusAction = TransientFocusAction.DUCK,
    val resumeOnHeadset: Boolean = false,
    val skipSilence: Boolean = false,
)

/**
 * Decides whether a connected headset should start the playback again. Only a pause caused by the headset going
 * away counts, and only for [windowMs] afterwards: a pause by the user, or an unplug that happened hours ago, must
 * not make the phone play when headphones come back.
 *
 * Not thread-safe; use it on the thread of the player.
 */
class HeadsetResume(
    private val windowMs: Long = WINDOW_MS,
) {
    private var unpluggedAtMs: Long? = null

    /**
     * Reports a change of the play state. [becomingNoisy] is true when the change came from the headset going away.
     */
    fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        becomingNoisy: Boolean,
        nowMs: Long,
    ) {
        unpluggedAtMs = if (!playWhenReady && becomingNoisy) nowMs else null
    }

    /** True when a headset was connected at [nowMs] and the playback should resume; the answer is given once. */
    fun shouldResume(
        enabled: Boolean,
        nowMs: Long,
    ): Boolean {
        val since = unpluggedAtMs ?: return false
        unpluggedAtMs = null
        return enabled && nowMs - since in 0..windowMs
    }

    companion object {
        /** How long after the unplug a reconnected headset resumes the playback. */
        const val WINDOW_MS = 10 * 60 * 1000L
    }
}
