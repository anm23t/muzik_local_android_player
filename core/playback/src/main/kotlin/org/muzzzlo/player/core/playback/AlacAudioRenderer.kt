package org.muzzzlo.player.core.playback

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.decoder.CryptoConfig
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DecoderAudioRenderer
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.source.MediaSource

/**
 * Plays ALAC with the bundled Apple decoder ([AlacDecoder]) instead of `MediaCodec`: the vendor ALAC decoders of
 * some phones fail on every file (docs/decisions/012-alac-decoder.md). Other formats are left to the next renderer.
 *
 * Gapless and ReplayGain follow the settings as in the `MediaCodec` renderer of [AudioRenderersFactory]: the
 * format of each track reaches the sink through [TrackFormatSink], which drops the trim data when gapless is off
 * and adds the ReplayGain values of the queue item. The renderer creates a new decoder for every stream (the
 * default of [DecoderAudioRenderer]), so the sink is configured again at the start of each track.
 */
@UnstableApi
internal class AlacAudioRenderer private constructor(
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    private val trackSink: TrackFormatSink,
    private val isShuffled: () -> Boolean,
) : DecoderAudioRenderer<AlacDecoder>(eventHandler, eventListener, trackSink) {
    constructor(
        eventHandler: Handler?,
        eventListener: AudioRendererEventListener?,
        audioSink: AudioSink,
        isGapless: () -> Boolean,
        isShuffled: () -> Boolean,
    ) : this(eventHandler, eventListener, TrackFormatSink(audioSink, isGapless), isShuffled)

    override fun getName(): String = NAME

    override fun supportsFormatInternal(format: Format): Int {
        if (!MimeTypes.AUDIO_ALAC.equals(format.sampleMimeType, ignoreCase = true) || !AlacNative.isAvailable) {
            return C.FORMAT_UNSUPPORTED_TYPE
        }
        val config = AlacConfig.parse(format.initializationData) ?: return C.FORMAT_UNSUPPORTED_SUBTYPE
        return when {
            !sinkSupportsFormat(config.outputFormat()) -> C.FORMAT_UNSUPPORTED_SUBTYPE
            format.cryptoType != C.CRYPTO_TYPE_NONE -> C.FORMAT_UNSUPPORTED_DRM
            else -> C.FORMAT_HANDLED
        }
    }

    override fun createDecoder(
        format: Format,
        cryptoConfig: CryptoConfig?,
    ): AlacDecoder = AlacDecoder.create(format)

    override fun getOutputFormat(decoder: AlacDecoder): Format = decoder.config.outputFormat()

    override fun onStreamChanged(
        formats: Array<out Format>,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId,
    ) {
        trackSink.stream = replayGainStreamOf(timeline, mediaPeriodId.periodUid, isShuffled())
        super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId)
    }

    private fun AlacConfig.outputFormat(): Format = Util.getPcmFormat(pcmEncoding, channelCount, sampleRate)

    private companion object {
        const val NAME = "AlacAudioRenderer"
    }
}

/**
 * Hands the sink the format of the track as the settings want it: without trim data when [isGapless] is off, with
 * the ReplayGain values of [stream], the queue item the renderer is reading.
 */
@UnstableApi
internal class TrackFormatSink(
    sink: AudioSink,
    private val isGapless: () -> Boolean,
) : ForwardingAudioSink(sink) {
    @Volatile var stream: ReplayGainStream = ReplayGainStream.NONE

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?,
    ) {
        super.configure(inputFormat.forGapless(isGapless()).withReplayGain(stream), specifiedBufferSize, outputChannels)
    }
}
