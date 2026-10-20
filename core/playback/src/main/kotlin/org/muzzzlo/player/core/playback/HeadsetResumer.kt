package org.muzzzlo.player.core.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.Player
import org.muzzzlo.player.core.model.HeadsetResume

/**
 * Starts the playback again when headphones or a Bluetooth device are connected after they stopped it by going
 * away. The decision is [HeadsetResume]; this class feeds it from the player and from the audio devices.
 * Everything runs on the thread of [handler], which is the thread of the player.
 */
internal class HeadsetResumer(
    context: Context,
    private val player: Player,
    private val handler: Handler,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) : AudioDeviceCallback(),
    Player.Listener {
    private val audioManager = checkNotNull(context.getSystemService(AudioManager::class.java))
    private val resume = HeadsetResume()

    /** The user's choice; when false a connected headset does nothing. */
    @Volatile var enabled: Boolean = false

    fun start() {
        player.addListener(this)
        audioManager.registerAudioDeviceCallback(this, handler)
    }

    fun stop() {
        player.removeListener(this)
        audioManager.unregisterAudioDeviceCallback(this)
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        resume.onPlayWhenReadyChanged(
            playWhenReady,
            becomingNoisy = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY,
            nowMs = now(),
        )
    }

    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
        if (addedDevices.none { it.isSink && it.isHeadset() }) return
        if (resume.shouldResume(enabled, now()) && player.mediaItemCount > 0) player.play()
    }

    private fun AudioDeviceInfo.isHeadset(): Boolean =
        when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            -> true
            else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
}
