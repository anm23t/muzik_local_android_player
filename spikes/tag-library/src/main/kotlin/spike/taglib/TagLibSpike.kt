package spike.taglib

import spike.SpikeEdit
import spike.SpikeTags
import spike.TagSpike
import java.io.File

/** TagLib 2.x through the unified PropertyMap / complex properties API. */
class TagLibSpike(libraryPath: String) : TagSpike {
    override val name = "TagLib"

    init {
        System.load(libraryPath)
    }

    override fun read(file: File) = toTags(TagLibNative.read(file.path, -1), TagLibNative.readPicture(file.path, -1))

    /** Same read through a raw file descriptor, as on Android with ParcelFileDescriptor.detachFd(). */
    fun readByFd(fd: Int) = toTags(TagLibNative.read(null, fd), TagLibNative.readPicture(null, fd))

    override fun write(file: File, edit: SpikeEdit) {
        val v23 = TagLibNative.id3v2Version(file.path) == 3
        // ID3v2.3 cannot hold several values in one frame: one string with "; " (ARCHITECTURE.md, section 5).
        val artists = if (v23) listOf(edit.artists.joinToString("; ")) else edit.artists
        val entries = buildList {
            add("TITLE$SEP${edit.title}")
            artists.forEach { add("ARTIST$SEP$it") }
            add("LYRICS$SEP${edit.lyrics}")
        }
        val ok = TagLibNative.write(
            file.path, -1, arrayOf("TITLE", "ARTIST", "LYRICS"), entries.toTypedArray(),
            if (edit.cover == null) 2 else 1, edit.cover, "image/png",
        )
        check(ok) { "TagLib save failed" }
    }

    override fun readForScan(file: File): Int = TagLibNative.readAudio(file.path)?.get(0) ?: error("unreadable")

    private fun toTags(flat: Array<String>?, picture: ByteArray?): SpikeTags {
        requireNotNull(flat) { "TagLib could not open the file" }
        val map = flat.groupBy({ it.substringBefore(SEP) }, { it.substringAfter(SEP) })
        fun one(key: String) = map[key]?.firstOrNull()
        return SpikeTags(
            title = one("TITLE"),
            artists = map["ARTIST"].orEmpty(),
            album = one("ALBUM"),
            albumArtist = one("ALBUMARTIST"),
            year = one("DATE"),
            track = one("TRACKNUMBER"),
            disc = one("DISCNUMBER"),
            genre = one("GENRE"),
            lyrics = one("LYRICS"),
            cover = picture,
        )
    }

    private companion object {
        const val SEP = '\u0001'
    }
}
