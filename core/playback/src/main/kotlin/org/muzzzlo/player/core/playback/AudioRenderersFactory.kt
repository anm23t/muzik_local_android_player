package org.muzzzlo.player.core.playback

import android.content.Context
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.util.ArrayList

/**
 * Format of a track as the audio renderer should see it: with gapless off, the encoder delay and padding (read
 * from LAME/iTunSMPB and similar headers) are dropped, so the decoder output is played whole and not trimmed.
 */
internal fun Format.forGapless(gapless: Boolean): Format =
    if (gapless || (encoderDelay == 0 && encoderPadding == 0)) {
        this
    } else {
        buildUpon().setEncoderDelay(0).setEncoderPadding(0).build()
    }

/**
 * ExoPlayer trims the silence that encoders add at the start and end of a track, which gives gapless playback.
 * Media3 has no switch for it, so this factory swaps the audio renderer for one that can ignore the trim data.
 * [gapless] is read when a track starts, a change applies from the next track.
 */
@UnstableApi
internal class GaplessRenderersFactory(
    context: Context,
    @Volatile var gapless: Boolean,
) : DefaultRenderersFactory(context) {
    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out,
        )
        val index = out.indexOfFirst { it is MediaCodecAudioRenderer }
        if (index < 0) return
        out[index] =
            SwitchableGaplessRenderer(
                context,
                MediaCodecAdapter.Factory.getDefault(context),
                mediaCodecSelector,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                audioSink,
            ) { gapless }
    }
}

@UnstableApi
private class SwitchableGaplessRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: AudioRendererEventListener,
    audioSink: AudioSink,
    private val isGapless: () -> Boolean,
) : MediaCodecAudioRenderer(
        context,
        codecAdapterFactory,
        mediaCodecSelector,
        enableDecoderFallback,
        eventHandler,
        eventListener,
        audioSink,
    ) {
    override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
        formatHolder.format = formatHolder.format?.forGapless(isGapless())
        return super.onInputFormatChanged(formatHolder)
    }
}
