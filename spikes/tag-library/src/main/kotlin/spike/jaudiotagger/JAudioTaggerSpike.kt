package spike.jaudiotagger

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import spike.SpikeEdit
import spike.SpikeTags
import spike.TagSpike
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/** JAudioTagger 3.0.1 (net.jthink) through its generic Tag / FieldKey API. */
class JAudioTaggerSpike : TagSpike {
    override val name = "JAudioTagger"

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
        // Android mode: avoids javax.imageio / java.awt when handling artwork.
        TagOptionSingleton.getInstance().isAndroid = true
    }

    override fun read(file: File): SpikeTags {
        val tag = AudioFileIO.read(file).tag ?: error("no tag")
        fun one(key: FieldKey) = tag.getFirst(key).ifEmpty { null }
        return SpikeTags(
            title = one(FieldKey.TITLE),
            artists = tag.getAll(FieldKey.ARTIST),
            album = one(FieldKey.ALBUM),
            albumArtist = one(FieldKey.ALBUM_ARTIST),
            year = one(FieldKey.YEAR),
            track = one(FieldKey.TRACK),
            disc = one(FieldKey.DISC_NO),
            genre = one(FieldKey.GENRE),
            lyrics = one(FieldKey.LYRICS),
            cover = tag.firstArtwork?.binaryData,
        )
    }

    override fun write(file: File, edit: SpikeEdit) {
        val audio = AudioFileIO.read(file)
        val tag = audio.tagOrCreateAndSetDefault
        tag.setField(FieldKey.TITLE, edit.title)
        tag.deleteField(FieldKey.ARTIST)
        if (tag is ID3v23Tag) {
            // ID3v2.3 cannot hold several values in one frame: one string with "; ".
            tag.setField(FieldKey.ARTIST, edit.artists.joinToString("; "))
        } else {
            edit.artists.forEach { tag.addField(FieldKey.ARTIST, it) }
        }
        tag.setField(FieldKey.LYRICS, edit.lyrics)
        tag.deleteArtworkField()
        if (edit.cover != null) setCover(tag, edit.cover)
        audio.commit()
    }

    /**
     * In Android mode the generic `setField(Artwork)` throws UnsupportedOperationException for FLAC and
     * Vorbis (AndroidArtwork.setImageFromData), so those formats need their format-specific calls.
     */
    private fun setCover(tag: Tag, cover: ByteArray) {
        when (tag) {
            is FlacTag -> tag.setField(
                tag.createArtworkField(cover, PictureTypes.DEFAULT_ID, "image/png", "", 64, 64, 24, 0),
            )
            is VorbisCommentTag -> tag.setArtworkField(cover, "image/png")
            else -> {
                val art = ArtworkFactory.getNew().apply {
                    binaryData = cover
                    mimeType = "image/png"
                    pictureType = PictureTypes.DEFAULT_ID
                    width = 64
                    height = 64
                }
                tag.setField(art)
            }
        }
    }

    override fun readForScan(file: File): Int {
        val audio = AudioFileIO.read(file)
        audio.tag?.getFirst(FieldKey.TITLE)
        return audio.audioHeader.trackLength * 1000
    }
}
