package org.muzzzlo.player.core.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.muzzzlo.player.core.model.PeakCollector
import org.muzzzlo.player.core.model.WaveformPeaks
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * Decodes a whole audio file once to find its peaks ([PeakCollector]). The platform extractor splits the file into
 * packets; ALAC goes to the bundled Apple decoder ([AlacStreamDecoder]) because vendor ALAC codecs crash on some
 * phones (`docs/decisions/012-alac-decoder.md`), everything else to `MediaCodec`. Runs on the calling thread and
 * stops when the coroutine is cancelled.
 */
@UnstableApi
internal class WaveformDecoder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** The peaks of the file at [uri]; null when it has no audio track, no length or cannot be decoded. */
        suspend fun decode(uri: String): WaveformPeaks? {
            val extractor = MediaExtractor()
            return try {
                runCatching { decodeWith(extractor, uri) }.getOrElse { error ->
                    // A broken or unsupported file only means no waveform; cancellation goes on up.
                    if (error is CancellationException) throw error
                    null
                }
            } finally {
                extractor.release()
            }
        }

        private suspend fun decodeWith(
            extractor: MediaExtractor,
            uri: String,
        ): WaveformPeaks? {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.mimeOf(it).startsWith("audio/") }
            val format = track?.let { extractor.selectTrack(it).let { _ -> extractor.getTrackFormat(it) } }
            val totalFrames =
                format?.let { it.longOr(MediaFormat.KEY_DURATION, 0L) * it.intOr(MediaFormat.KEY_SAMPLE_RATE, 0) }
                    ?.div(US_PER_SECOND) ?: 0L
            if (format == null || totalFrames <= 0) return null
            val collector = PeakCollector(totalFrames)
            val decoded =
                if (format.getString(MediaFormat.KEY_MIME) == MimeTypes.AUDIO_ALAC) {
                    decodeAlac(extractor, format, collector)
                } else {
                    decodeWithCodec(extractor, format, collector)
                }
            return collector.result().takeIf { decoded && collector.frames > 0 }
        }

        private suspend fun decodeAlac(
            extractor: MediaExtractor,
            format: MediaFormat,
            collector: PeakCollector,
        ): Boolean {
            val cookie =
                format.getByteBuffer(CSD_0)?.let(::bytesOf)?.let(::alacCookies)
                    ?.firstOrNull { AlacConfig.parse(listOf(it)) != null }
            if (cookie == null || !AlacNative.isAvailable) return false
            AlacStreamDecoder.create(listOf(cookie)).use { decoder ->
                val config = decoder.config
                val peaks = PcmPeaks(collector, config.pcmEncoding, config.channelCount)
                val inputSize = format.intOr(MediaFormat.KEY_MAX_INPUT_SIZE, config.maxOutputSize)
                val packet = ByteBuffer.allocateDirect(inputSize)
                val output = ByteBuffer.allocateDirect(config.maxOutputSize)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    packet.clear()
                    val size = extractor.readSampleData(packet, 0)
                    if (size < 0) return true
                    packet.position(0)
                    packet.limit(size)
                    decoder.decode(packet, output)
                    peaks.add(output)
                    extractor.advance()
                }
            }
        }

        private suspend fun decodeWithCodec(
            extractor: MediaExtractor,
            format: MediaFormat,
            collector: PeakCollector,
        ): Boolean {
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return false
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val output = CodecOutput(codec, collector)
                var input = CodecInput.QUEUED
                while (!output.ended) {
                    currentCoroutineContext().ensureActive()
                    if (input != CodecInput.ENDED) input = queueInput(extractor, codec)
                    // Wait for output only when there is nothing to feed: waiting on every packet made a
                    // 3-minute MP3 take seconds (found on the test phone).
                    output.drainOne(if (input == CodecInput.QUEUED) 0 else TIMEOUT_US)
                }
                return true
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        }

        /** Takes one output buffer of [codec] into the peaks, if one is ready. */
        private inner class CodecOutput(
            private val codec: MediaCodec,
            private val collector: PeakCollector,
        ) {
            private val info = MediaCodec.BufferInfo()
            private var peaks: PcmPeaks? = null

            var ended = false
                private set

            fun drainOne(timeoutUs: Long) {
                val index = codec.dequeueOutputBuffer(info, timeoutUs)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) peaks = peaksFor(codec.outputFormat, collector)
                if (index < 0) return
                val buffer = codec.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val target = peaks ?: peaksFor(codec.outputFormat, collector).also { peaks = it }
                    target.add(buffer)
                }
                codec.releaseOutputBuffer(index, false)
                ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            }
        }

        /** What [queueInput] did. */
        private enum class CodecInput { QUEUED, NO_BUFFER, ENDED }

        /** Gives the codec the next packet if it has a free input buffer, without waiting. */
        private fun queueInput(
            extractor: MediaExtractor,
            codec: MediaCodec,
        ): CodecInput {
            val index = codec.dequeueInputBuffer(0)
            val buffer = if (index >= 0) codec.getInputBuffer(index) else null
            val size = buffer?.let { extractor.readSampleData(it, 0) } ?: return CodecInput.NO_BUFFER
            return if (size < 0) {
                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                CodecInput.ENDED
            } else {
                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                extractor.advance()
                CodecInput.QUEUED
            }
        }

        private fun peaksFor(
            format: MediaFormat,
            collector: PeakCollector,
        ): PcmPeaks {
            val encoding =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    format.intOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                } else {
                    C.ENCODING_PCM_16BIT
                }
            return PcmPeaks(collector, encoding, format.intOr(MediaFormat.KEY_CHANNEL_COUNT, 1))
        }

        private fun MediaExtractor.mimeOf(track: Int): String =
            getTrackFormat(
                track,
            ).getString(MediaFormat.KEY_MIME).orEmpty()

        private fun MediaFormat.intOr(
            key: String,
            default: Int,
        ): Int = if (containsKey(key)) getInteger(key) else default

        private fun MediaFormat.longOr(
            key: String,
            default: Long,
        ): Long = if (containsKey(key)) getLong(key) else default

        private fun bytesOf(buffer: ByteBuffer): ByteArray {
            val copy = buffer.duplicate()
            return ByteArray(copy.remaining()).also { copy.get(it) }
        }

        private companion object {
            const val US_PER_SECOND = 1_000_000L
            const val TIMEOUT_US = 10_000L
            const val CSD_0 = "csd-0"
        }
    }

/**
 * The magic cookie as the platform extractor may give it: as it is, or with the 4 bytes of version and flags of the
 * `alac` box in front.
 */
internal fun alacCookies(csd: ByteArray): List<ByteArray> =
    buildList {
        add(csd)
        if (csd.size == AlacConfig.COOKIE_SIZE + BOX_VERSION_SIZE) add(csd.copyOfRange(BOX_VERSION_SIZE, csd.size))
    }

private const val BOX_VERSION_SIZE = 4
