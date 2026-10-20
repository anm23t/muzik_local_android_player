package org.muzzzlo.player.core.playback

import android.content.Context
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource
import org.muzzzlo.player.core.model.ReplayGainSettings
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

/** The same format carrying [stream] to the audio sink; unchanged when it already does. */
internal fun Format.withReplayGain(stream: ReplayGainStream): Format =
    if (customData == stream) this else buildUpon().setCustomData(stream).build()

/**
 * Swaps the audio renderer and the audio sink of ExoPlayer for ones that follow the playback settings:
 * - gapless: ExoPlayer trims the silence that encoders add at the start and end of a track. Media3 has no switch
 *   for it, so the renderer can ignore the trim data. [gapless] is read when a track starts, a change applies from
 *   the next track.
 * - ReplayGain: the renderer finds the queue item of each new stream and passes its ReplayGain values in the
 *   format to the sink, where [replayGain] applies them from the first sample of the track. [shuffle] tells which
 *   tracks are its neighbours for [org.muzzzlo.player.core.model.ReplayGainMode.AUTO].
 * - silence: [silenceTrim] drops the silence at the start and the end of a track; the sink adds the dropped frames to
 *   its position ([TrimChain]).
 */
@UnstableApi
internal class AudioRenderersFactory(
    context: Context,
    @Volatile var gapless: Boolean,
    replayGainSettings: ReplayGainSettings,
) : DefaultRenderersFactory(context) {
    @Volatile var shuffle: Boolean = false

    val replayGain = ReplayGainAudioProcessor(replayGainSettings)

    /** Drops the silence at the start and the end of each track while enabled. */
    val silenceTrim = SilenceTrimAudioProcessor()

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink =
        ReplayGainAudioSink(
            DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(TrimChain(silenceTrim, replayGain))
                .build(),
            replayGain,
            silenceTrim,
        )

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
            PlaybackAudioRenderer(
                context,
                MediaCodecAdapter.Factory.getDefault(context),
                mediaCodecSelector,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                audioSink,
                isGapless = { gapless },
                isShuffled = { shuffle },
            )
    }
}

/**
 * Tells the processors where a track starts, when the renderer configures the sink for it: [processor] gets the
 * ReplayGain values of the track, [silenceTrim] starts skipping the silence at its beginning.
 */
@UnstableApi
private class ReplayGainAudioSink(
    sink: AudioSink,
    private val processor: ReplayGainAudioProcessor,
    private val silenceTrim: SilenceTrimAudioProcessor,
) : ForwardingAudioSink(sink) {
    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?,
    ) {
        processor.stream = inputFormat.customData as? ReplayGainStream ?: ReplayGainStream.NONE
        silenceTrim.startOfTrack()
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }
}

@UnstableApi
private class PlaybackAudioRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: AudioRendererEventListener,
    audioSink: AudioSink,
    private val isGapless: () -> Boolean,
    private val isShuffled: () -> Boolean,
) : MediaCodecAudioRenderer(
        context,
        codecAdapterFactory,
        mediaCodecSelector,
        enableDecoderFallback,
        eventHandler,
        eventListener,
        audioSink,
    ) {
    /** ReplayGain values of the stream being read; every format read after a stream change belongs to it. */
    private var stream = ReplayGainStream.NONE

    override fun onStreamChanged(
        formats: Array<out Format>,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId,
    ) {
        stream = replayGainStreamOf(timeline, mediaPeriodId.periodUid, isShuffled())
        super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId)
    }

    override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
        formatHolder.format = formatHolder.format?.forGapless(isGapless())?.withReplayGain(stream)
        return super.onInputFormatChanged(formatHolder)
    }
}

/**
 * ReplayGain values of the queue item that plays the period [periodUid], and whether its album plays in order:
 * the neighbours are taken in play order, which depends on [shuffled].
 */
@UnstableApi
internal fun replayGainStreamOf(
    timeline: Timeline,
    periodUid: Any,
    shuffled: Boolean,
): ReplayGainStream {
    val periodIndex = timeline.getIndexOfPeriod(periodUid)
    if (periodIndex == C.INDEX_UNSET) return ReplayGainStream.NONE
    val windowIndex = timeline.getPeriod(periodIndex, Timeline.Period()).windowIndex
    val window = Timeline.Window()

    fun itemAt(index: Int): MediaItem? =
        if (index == C.INDEX_UNSET) null else timeline.getWindow(index, window).mediaItem
    val item = itemAt(windowIndex) ?: return ReplayGainStream.NONE
    val previous = itemAt(timeline.getPreviousWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, shuffled))
    val next = itemAt(timeline.getNextWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, shuffled))
    return ReplayGainStream(
        info = item.replayGainInfo(),
        albumInOrder = playsAlbumInOrder(item.albumId(), previous?.albumId(), next?.albumId()),
    )
}
