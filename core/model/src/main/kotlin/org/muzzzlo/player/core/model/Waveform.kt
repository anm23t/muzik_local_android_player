package org.muzzzlo.player.core.model

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The loudest sample of each of [Waveform.PEAK_COUNT] equal parts of a track, 0 (silence) to 1 (full scale), as
 * computed once by decoding the file (ARCHITECTURE.md, section 6; `docs/decisions/013-waveform.md`). The player
 * folds them into as many bars as fit its width ([Waveform.bars]).
 */
class WaveformPeaks(
    val values: FloatArray,
) {
    override fun equals(other: Any?): Boolean = other is WaveformPeaks && values.contentEquals(other.values)

    override fun hashCode(): Int = values.contentHashCode()

    override fun toString(): String = "WaveformPeaks(${values.size})"
}

/**
 * Collects the peaks of a track while it is decoded. Frames are given in order with the loudest absolute sample over
 * their channels; frame `i` goes to part `i * count / totalFrames`. [totalFrames] comes from the duration in the
 * file header and may be off a little: frames past it go to the last part, parts never reached stay silent.
 */
class PeakCollector(
    private val totalFrames: Long,
    private val count: Int = Waveform.PEAK_COUNT,
) {
    private val peaks = FloatArray(count)
    private var frame = 0L

    init {
        require(totalFrames > 0) { "A track without frames has no waveform" }
        require(count > 0) { "At least one peak" }
    }

    /** The next frame; [peak] is the largest absolute sample of its channels, 1 is full scale. */
    fun add(peak: Float) {
        val part = ((frame * count) / totalFrames).toInt().coerceAtMost(count - 1)
        val value = abs(peak).coerceAtMost(1f)
        if (value > peaks[part]) peaks[part] = value
        frame++
    }

    /** Frames given so far. */
    val frames: Long get() = frame

    fun result(): WaveformPeaks = WaveformPeaks(peaks.copyOf())
}

/** Folding peaks into bars and the cache file format. Pure Kotlin, covered by unit tests. */
object Waveform {
    /** Peaks per track: enough for about 400 bars, small enough for the cache (about 2 KB per track). */
    const val PEAK_COUNT = 2048

    /** The quietest bar is this tall (share of the full height), so that silence still shows as a line. */
    const val MIN_BAR = 0.06f

    private val MAGIC = byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 'W'.code.toByte(), 'F'.code.toByte())
    private const val VERSION: Byte = 1
    private const val HEADER_SIZE = 8
    private const val BYTE_MAX = 255
    private const val BYTE_MASK = 0xFF
    private const val SHIFT_BYTE = 8
    private const val VERSION_OFFSET = 4
    private const val COUNT_OFFSET = 6

    /**
     * [barCount] bar heights in [MIN_BAR, 1]. A bar takes the loudest peak of its share of the track, then the bars
     * are scaled so that the loudest is 1: a quiet recording fills the height like a loud one. With more bars
     * than peaks a peak covers several bars. A silent track gives bars of [MIN_BAR].
     */
    fun bars(
        peaks: WaveformPeaks,
        barCount: Int,
    ): FloatArray {
        val raw = if (barCount <= 0) FloatArray(0) else foldPeaks(peaks.values, barCount)
        val loudest = raw.maxOrNull() ?: 0f
        return if (loudest <= 0f) {
            FloatArray(raw.size) { MIN_BAR }
        } else {
            FloatArray(barCount) { MIN_BAR + (1f - MIN_BAR) * raw[it] / loudest }
        }
    }

    /** The loudest peak of each of [barCount] equal shares of [values]; silent bars for no peaks. */
    private fun foldPeaks(
        values: FloatArray,
        barCount: Int,
    ): FloatArray {
        if (values.isEmpty()) return FloatArray(barCount)
        return FloatArray(barCount) { bar ->
            val from = floor(bar.toDouble() * values.size / barCount).toInt()
            val to = ceil((bar + 1).toDouble() * values.size / barCount).toInt().coerceAtMost(values.size)
            var max = 0f
            for (i in from until maxOf(to, from + 1)) max = maxOf(max, values[i])
            max
        }
    }

    /** The cache file: `MZWF`, version, number of peaks (2 bytes) and one byte (0 to 255) per peak. */
    fun encode(peaks: WaveformPeaks): ByteArray {
        val values = peaks.values
        val bytes = ByteArray(HEADER_SIZE + values.size)
        MAGIC.copyInto(bytes)
        bytes[VERSION_OFFSET] = VERSION
        bytes[COUNT_OFFSET] = (values.size shr SHIFT_BYTE).toByte()
        bytes[COUNT_OFFSET + 1] = values.size.toByte()
        values.forEachIndexed { i, v -> bytes[HEADER_SIZE + i] = Math.round(v.coerceIn(0f, 1f) * BYTE_MAX).toByte() }
        return bytes
    }

    /** Peaks of a cache file written by [encode]; null for a damaged, cut or other file. */
    fun decode(bytes: ByteArray): WaveformPeaks? {
        val valid =
            bytes.size >= HEADER_SIZE &&
                MAGIC.indices.all { bytes[it] == MAGIC[it] } &&
                bytes[VERSION_OFFSET] == VERSION
        val count = if (valid) (bytes.unsigned(COUNT_OFFSET) shl SHIFT_BYTE) or bytes.unsigned(COUNT_OFFSET + 1) else 0
        if (count == 0 || bytes.size != HEADER_SIZE + count) return null
        return WaveformPeaks(FloatArray(count) { bytes.unsigned(HEADER_SIZE + it) / BYTE_MAX.toFloat() })
    }

    private fun ByteArray.unsigned(index: Int): Int = this[index].toInt() and BYTE_MASK
}

/**
 * Peaks of the tracks for the waveform of the big player. Implemented in `core:playback`, which has the decoders.
 */
interface WaveformRepository {
    /**
     * The peaks of [track]: from the cache, or by decoding the file once (one track at a time, off the main
     * thread). Null when the file cannot be decoded; then the player keeps the plain progress bar. Cancelling the
     * caller stops the decoding.
     */
    suspend fun peaks(track: Track): WaveformPeaks?
}
