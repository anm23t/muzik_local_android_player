package org.muzzzlo.player.core.playback

import java.nio.ByteBuffer

/**
 * The magic cookie and packets of the first ALAC track of an MP4 file: just enough of the format (`stsd`, `stsz`,
 * `stsc`, `stco`) to feed [AlacStreamDecoder] in tests, without the Media3 extractor and Android.
 */
class Mp4AlacTrack(
    val cookie: ByteArray,
    val packets: List<ByteArray>,
) {
    companion object {
        private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl")
        private const val BOX_HEADER = 8
        private const val FULL_BOX_HEADER = 12

        /** Size of the audio sample entry fields between its header and its child boxes. */
        private const val AUDIO_SAMPLE_ENTRY_FIELDS = 28

        fun read(file: ByteArray): Mp4AlacTrack {
            val boxes = mutableMapOf<String, ByteBuffer>()
            collectBoxes(ByteBuffer.wrap(file), boxes)
            val sizes = sampleSizes(boxes.getValue("stsz"))
            val offsets = packetOffsets(sizes, chunkOffsets(boxes.getValue("stco")), boxes.getValue("stsc"))
            val packets = sizes.indices.map { file.copyOfRange(offsets[it], offsets[it] + sizes[it]) }
            return Mp4AlacTrack(cookie(boxes.getValue("stsd")), packets)
        }

        /** The first box of each type, searched through the containers. */
        private fun collectBoxes(
            buffer: ByteBuffer,
            out: MutableMap<String, ByteBuffer>,
        ) {
            while (buffer.remaining() >= BOX_HEADER) {
                val start = buffer.position()
                val size = buffer.int
                val type = fourCc(buffer)
                val body = buffer.duplicate().position(start + BOX_HEADER).limit(start + size) as ByteBuffer
                if (type in CONTAINERS) collectBoxes(body.slice(), out) else out.putIfAbsent(type, body.slice())
                buffer.position(start + size)
            }
        }

        private fun cookie(stsd: ByteBuffer): ByteArray {
            // version and flags, entry count, then the 'alac' sample entry with an 'alac' full box inside it.
            val entry = stsd.duplicate().position(8) as ByteBuffer
            val entrySize = entry.int
            check(fourCc(entry) == "alac") { "Not an ALAC track" }
            entry.position(entry.position() + AUDIO_SAMPLE_ENTRY_FIELDS)
            val childSize = entry.int
            check(fourCc(entry) == "alac" && childSize <= entrySize) { "No ALAC magic cookie" }
            entry.position(entry.position() + FULL_BOX_HEADER - BOX_HEADER)
            return ByteArray(childSize - FULL_BOX_HEADER).also { entry.get(it) }
        }

        private fun sampleSizes(stsz: ByteBuffer): List<Int> {
            val box = stsz.duplicate().position(4) as ByteBuffer
            val fixedSize = box.int
            val count = box.int
            return List(count) { if (fixedSize != 0) fixedSize else box.int }
        }

        private fun chunkOffsets(stco: ByteBuffer): List<Int> {
            val box = stco.duplicate().position(4) as ByteBuffer
            return List(box.int) { box.int }
        }

        /** Places the packets into their chunks as `stsc` says (runs of chunks with the same packet count). */
        private fun packetOffsets(
            sizes: List<Int>,
            chunks: List<Int>,
            stsc: ByteBuffer,
        ): List<Int> {
            val box = stsc.duplicate().position(4) as ByteBuffer
            val runs = List(box.int) { Pair(box.int, box.int).also { box.int } }
            val offsets = ArrayList<Int>(sizes.size)
            chunks.forEachIndexed { chunkIndex, chunkOffset ->
                val packetsInChunk = runs.last { it.first <= chunkIndex + 1 }.second
                var offset = chunkOffset
                repeat(packetsInChunk) {
                    if (offsets.size < sizes.size) {
                        offsets += offset
                        offset += sizes[offsets.size - 1]
                    }
                }
            }
            return offsets
        }

        private fun fourCc(buffer: ByteBuffer): String = String(ByteArray(4).also { buffer.get(it) }, Charsets.US_ASCII)
    }
}
