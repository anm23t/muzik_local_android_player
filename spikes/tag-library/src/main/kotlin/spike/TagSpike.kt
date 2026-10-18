package spike

import java.io.File

/** Tag values as the app would need them; artists are kept exactly as stored (no splitting here). */
data class SpikeTags(
    val title: String?,
    val artists: List<String>,
    val album: String?,
    val albumArtist: String?,
    val year: String?,
    val track: String?,
    val disc: String?,
    val genre: String?,
    val lyrics: String?,
    val cover: ByteArray?,
)

/** What a write changes; everything else in the file must survive untouched. */
data class SpikeEdit(
    val title: String,
    val artists: List<String>,
    val lyrics: String,
    /** null = remove the cover. */
    val cover: ByteArray?,
)

/** Common surface both candidate libraries are tested through. */
interface TagSpike {
    val name: String

    fun read(file: File): SpikeTags

    fun write(file: File, edit: SpikeEdit)

    /** Reads tags and audio properties, as the scanner would. Returns duration in ms. */
    fun readForScan(file: File): Int
}
