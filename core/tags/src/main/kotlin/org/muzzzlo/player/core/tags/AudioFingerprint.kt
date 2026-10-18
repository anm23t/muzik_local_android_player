package org.muzzzlo.player.core.tags

import org.muzzzlo.player.core.model.AudioFormat
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Hash of the audio data of a file, without its tags: equal before and after a tag write when the audio is intact.
 *
 * Written in Kotlin on purpose, independently of TagLib, so a TagLib bug that damages audio cannot hide itself
 * (ARCHITECTURE.md, section 5, step 5):
 * - MP3: the bytes between the leading ID3v2 tags and the trailing APEv2 / ID3v1 tags;
 * - FLAC: the bytes after the last metadata block, without a trailing ID3v1 tag;
 * - M4A: the content of the `mdat` atoms; TagLib may move `mdat` (and fixes the chunk offsets) but not change it;
 * - Ogg Vorbis and Opus: the audio packets with their granule positions, not the bytes, because TagLib may
 *   repaginate the stream; the header packets (Vorbis: 3, Opus: 2), which hold the comments, are left out.
 */
internal object AudioFingerprint {
    private const val VORBIS_HEADER_PACKETS = 3
    private const val OPUS_HEADER_PACKETS = 2

    /** SHA-256 of the audio data of [file] in [format], or null when its structure cannot be parsed. */
    @Suppress("SwallowedException")
    fun of(
        file: File,
        format: AudioFormat,
    ): ByteArray? =
        try {
            RandomAccessFile(file, "r").use { input ->
                when (format) {
                    AudioFormat.MP3 -> mp3(input)
                    AudioFormat.FLAC -> flac(input)
                    AudioFormat.M4A -> mp4(input)
                    AudioFormat.OGG_VORBIS -> ogg(input, VORBIS_HEADER_PACKETS)
                    AudioFormat.OPUS -> ogg(input, OPUS_HEADER_PACKETS)
                }
            }
        } catch (e: IOException) {
            null
        }

    private fun mp3(input: RandomAccessFile): ByteArray? {
        val start = TagBlocks.skipId3v2(input, 0)
        val end = TagBlocks.endWithoutTrailingTags(input, start)
        return if (end > start) digestOf { input.digestRange(start, end, it) } else null
    }

    private fun flac(input: RandomAccessFile): ByteArray? {
        val start = FlacLayout.audioStart(input, TagBlocks.skipId3v2(input, 0)) ?: return null
        val end = TagBlocks.endWithoutTrailingTags(input, start)
        return if (end > start) digestOf { input.digestRange(start, end, it) } else null
    }

    private fun mp4(input: RandomAccessFile): ByteArray? {
        var found = false
        val hash =
            digestOf { digest ->
                Mp4Layout.forEachTopLevelAtom(input) { type, contentStart, end ->
                    if (type == "mdat") {
                        found = true
                        input.digestRange(contentStart, end, digest)
                    }
                }
            }
        return hash.takeIf { found }
    }

    private fun ogg(
        input: RandomAccessFile,
        headerPackets: Int,
    ): ByteArray? {
        var audioPackets = 0
        val hash =
            digestOf { digest ->
                val reader = OggPacketReader(input)
                generateSequence { reader.next() }.drop(headerPackets).forEach { packet ->
                    audioPackets++
                    digest.update(packet.granulePosition.toBigEndianBytes())
                    digest.update(packet.data.size.toLong().toBigEndianBytes())
                    digest.update(packet.data)
                }
            }
        return hash.takeIf { audioPackets > 0 }
    }

    private inline fun digestOf(block: (MessageDigest) -> Unit): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        block(digest)
        return digest.digest()
    }
}

/** Tags around MPEG audio: ID3v2 at the start, APEv2 and ID3v1 at the end (also found around FLAC). */
private object TagBlocks {
    private const val ID3V2_HEADER = 10L
    private const val ID3V2_FOOTER_FLAG = 0x10
    private const val ID3V1_SIZE = 128L
    private const val APE_FOOTER = 32L
    private const val APE_SIZE_OFFSET = 12L
    private const val APE_HAS_HEADER = 1 shl 31

    /** Offset after the ID3v2 tags at [start]; files sometimes carry several in a row. */
    fun skipId3v2(
        input: RandomAccessFile,
        start: Long,
    ): Long {
        var position = start
        while (position + ID3V2_HEADER <= input.length() && input.hasMagicAt(position, "ID3")) {
            input.skipBytes(2) // version
            val flags = input.readUnsignedByte()
            val size = input.readSynchsafeInt()
            val footer = if (flags and ID3V2_FOOTER_FLAG != 0) ID3V2_HEADER else 0L
            position += ID3V2_HEADER + size + footer
        }
        return position
    }

    /** End of the audio before an ID3v1 tag and an APEv2 tag (in either order) at the end of the file. */
    fun endWithoutTrailingTags(
        input: RandomAccessFile,
        start: Long,
    ): Long {
        var end = input.length()
        while (true) {
            end =
                when {
                    end - ID3V1_SIZE >= start && input.hasMagicAt(end - ID3V1_SIZE, "TAG") -> end - ID3V1_SIZE
                    end - APE_FOOTER >= start && input.hasMagicAt(end - APE_FOOTER, "APETAGEX") ->
                        end - apeSize(input, end)
                    else -> return end
                }
        }
    }

    /** Footer: "APETAGEX", version, tag size (items and footer), item count, flags, reserved. */
    private fun apeSize(
        input: RandomAccessFile,
        end: Long,
    ): Long {
        input.seek(end - APE_FOOTER + APE_SIZE_OFFSET)
        val size = input.readIntLittleEndian().toLong()
        input.skipBytes(Int.SIZE_BYTES) // item count
        val flags = input.readIntLittleEndian()
        return size + if (flags and APE_HAS_HEADER != 0) APE_FOOTER else 0L
    }
}

/** FLAC: `fLaC`, then metadata blocks with a 4-byte header (last-block flag, type, 24-bit length), then audio. */
private object FlacLayout {
    private const val BLOCK_HEADER = 4L
    private const val LAST_BLOCK_FLAG = 0x80

    /** Offset of the first audio frame, or null when there is no FLAC stream at [start]. */
    fun audioStart(
        input: RandomAccessFile,
        start: Long,
    ): Long? {
        if (start + BLOCK_HEADER > input.length() || !input.hasMagicAt(start, "fLaC")) return null
        var position = start + BLOCK_HEADER
        var last = false
        while (!last) {
            input.seek(position)
            last = input.readUnsignedByte() and LAST_BLOCK_FLAG != 0
            position += BLOCK_HEADER + input.readUnsignedMedium()
        }
        return position
    }
}

/** MP4: a sequence of atoms, each with a 32-bit size (1 = 64-bit size follows, 0 = to the end) and a type. */
private object Mp4Layout {
    private const val HEADER = 8L
    private const val LARGE_HEADER = 16L
    private const val UNSIGNED_INT_MASK = 0xFFFFFFFFL
    private const val ATOM_TYPE_LENGTH = 4

    /** Calls [action] with the type, content start and end of every top-level atom. */
    fun forEachTopLevelAtom(
        input: RandomAccessFile,
        action: (type: String, contentStart: Long, end: Long) -> Unit,
    ) {
        var position = 0L
        while (position + HEADER <= input.length()) {
            input.seek(position)
            val shortSize = input.readInt().toLong() and UNSIGNED_INT_MASK
            val type = input.readAscii(ATOM_TYPE_LENGTH)
            val (size, header) =
                when (shortSize) {
                    0L -> (input.length() - position) to HEADER
                    1L -> input.readLong() to LARGE_HEADER
                    else -> shortSize to HEADER
                }
            if (size < header || position + size > input.length()) throw IOException("Bad atom $type at $position")
            action(type, position + header, position + size)
            position += size
        }
    }
}

/**
 * Reads the packets of the first logical stream of an Ogg file, page by page.
 *
 * A page is `OggS`, version, header type, granule position (8 bytes, little endian), serial number, page sequence,
 * checksum, the number of segments and the segment table; a packet ends at the first segment shorter than 255.
 */
private class OggPacketReader(private val input: RandomAccessFile) {
    /** A packet and the granule position of the page where it ends. */
    class Packet(val data: ByteArray, val granulePosition: Long)

    private val pending = ArrayDeque<Packet>()
    private var partial = ByteArrayOutputStream()
    private var position = 0L
    private var serial: Int? = null

    fun next(): Packet? {
        while (pending.isEmpty()) {
            if (!readPage()) return null
        }
        return pending.removeFirst()
    }

    private fun readPage(): Boolean {
        if (position + PAGE_HEADER > input.length()) return false
        if (!input.hasMagicAt(position, "OggS")) throw IOException("No Ogg page at $position")
        input.skipBytes(2) // version, header type
        val granule = input.readLongLittleEndian()
        val pageSerial = input.readIntLittleEndian()
        input.skipBytes(SEQUENCE_AND_CHECKSUM)
        val segments = IntArray(input.readUnsignedByte()) { input.readUnsignedByte() }
        position = input.filePointer + segments.sum()
        if (position > input.length()) throw EOFException()
        if (serial == null) serial = pageSerial
        if (pageSerial == serial) collectPackets(segments, granule)
        return true
    }

    private fun collectPackets(
        segments: IntArray,
        granule: Long,
    ) {
        val body = ByteArray(segments.sum()).also { input.readFully(it) }
        var offset = 0
        for (length in segments) {
            partial.write(body, offset, length)
            offset += length
            if (length < MAX_SEGMENT) {
                pending.addLast(Packet(partial.toByteArray(), granule))
                partial = ByteArrayOutputStream()
            }
        }
    }

    private companion object {
        const val PAGE_HEADER = 27
        const val MAX_SEGMENT = 255
        const val SEQUENCE_AND_CHECKSUM = 8
    }
}

// Byte-level reading for the layouts above.

private const val BUFFER_SIZE = 64 * 1024
private const val BYTE_BITS = 8
private const val SYNCHSAFE_BITS = 7
private const val MEDIUM_BYTES = 3
private const val SYNCHSAFE_MASK = 0x7F

private fun RandomAccessFile.digestRange(
    start: Long,
    end: Long,
    digest: MessageDigest,
) {
    seek(start)
    val buffer = ByteArray(BUFFER_SIZE)
    var remaining = end - start
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read < 0) throw EOFException()
        digest.update(buffer, 0, read)
        remaining -= read
    }
}

private fun RandomAccessFile.readAscii(count: Int): String {
    val bytes = ByteArray(count)
    readFully(bytes)
    return String(bytes, Charsets.ISO_8859_1)
}

/** True when the bytes at [position] spell [magic]; the file pointer ends after them. */
private fun RandomAccessFile.hasMagicAt(
    position: Long,
    magic: String,
): Boolean {
    seek(position)
    return readAscii(magic.length) == magic
}

private fun RandomAccessFile.readUnsignedMedium(): Int =
    (0 until MEDIUM_BYTES).fold(0) { acc, _ -> (acc shl BYTE_BITS) or readUnsignedByte() }

private fun RandomAccessFile.readSynchsafeInt(): Long =
    (0 until Int.SIZE_BYTES).fold(0L) { acc, _ ->
        (acc shl SYNCHSAFE_BITS) or (readUnsignedByte() and SYNCHSAFE_MASK).toLong()
    }

private fun RandomAccessFile.readIntLittleEndian(): Int =
    (0 until Int.SIZE_BYTES).fold(0) { acc, i -> acc or (readUnsignedByte() shl (BYTE_BITS * i)) }

private fun RandomAccessFile.readLongLittleEndian(): Long =
    (0 until Long.SIZE_BYTES).fold(0L) { acc, i -> acc or (readUnsignedByte().toLong() shl (BYTE_BITS * i)) }

private fun Long.toBigEndianBytes(): ByteArray =
    ByteArray(Long.SIZE_BYTES) { (this ushr (BYTE_BITS * (Long.SIZE_BYTES - 1 - it))).toByte() }
