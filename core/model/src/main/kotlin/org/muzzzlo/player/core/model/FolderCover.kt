package org.muzzzlo.player.core.model

/** A track of an album as the folder cover rules see it: where its file lies and its place on the album. */
data class AlbumTrackPlace(
    val relativePath: String,
    val discNumber: Int?,
    val trackNumber: Int?,
)

/** A picture of a folder that may be an album cover: its MediaStore folder, file name, URI and `DATE_MODIFIED`. */
data class FolderPicture(
    val relativePath: String,
    val displayName: String,
    val uri: String,
    val modified: Long,
)

/**
 * Rules of the covers that lie as picture files next to the tracks (task 2.0.5). The cover from the tags always wins;
 * these rules apply only to an album none of whose tracks has a cover of its own.
 *
 * Folders are MediaStore `RELATIVE_PATH` values: `Music/Artist/Album/`, with the trailing slash.
 */
object FolderCover {
    /** File names of a cover without the extension, in order of preference. */
    val NAMES = listOf("cover", "folder", "front", "album")

    /** Extensions of a cover file. */
    val EXTENSIONS = listOf("jpg", "jpeg", "png")

    /** Every display name a cover file may have, lower case; MediaStore can be asked for these. */
    val CANDIDATE_NAMES: List<String> = NAMES.flatMap { name -> EXTENSIONS.map { "$name.$it" } }

    /** `CD1`, `Disc 2`, `disk_03`, `CD 1 - Bonus`; not `CDs`, `Discography`, `CD1a`. */
    private val DISC_FOLDER = Regex("^(cd|disc|disk)[ ._-]*\\d{1,2}([ ._-].*)?$", RegexOption.IGNORE_CASE)

    /** Rank of [displayName] as a cover (0 best), null when it is not a cover name at all. */
    fun rank(displayName: String): Int? {
        val dot = displayName.lastIndexOf('.')
        if (dot <= 0) return null
        val extension = displayName.substring(dot + 1).lowercase()
        if (extension !in EXTENSIONS) return null
        return NAMES.indexOf(displayName.substring(0, dot).lowercase()).takeIf { it >= 0 }
    }

    /** The best cover among the file names of one folder, null when none is a cover. */
    fun pick(displayNames: Collection<String>): String? =
        displayNames
            .mapNotNull { name -> rank(name)?.let { name to it } }
            // Equal ranks (cover.jpg and cover.png) are settled by the name, so the choice does not depend on order.
            .minWithOrNull(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
            ?.first

    /** True for a folder of one disc of a set, such as `CD1` or `Disc 2`; [name] is the last part of the path. */
    fun isDiscFolder(name: String): Boolean = DISC_FOLDER.matches(name.trim())

    /**
     * Folders to look for the cover of an album in, in order: the folder of its first track by disc and number,
     * then, when that is a disc folder, its parent. Tracks in other folders do not count. Empty for no tracks.
     */
    fun albumFolders(tracks: List<AlbumTrackPlace>): List<String> {
        val first =
            tracks.minWithOrNull(
                compareBy<AlbumTrackPlace>(
                    { it.discNumber ?: Int.MAX_VALUE },
                    { it.trackNumber ?: Int.MAX_VALUE },
                    { it.relativePath },
                ),
            ) ?: return emptyList()
        val folder = first.relativePath
        val parts = folder.trimEnd('/').split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty() || !isDiscFolder(parts.last())) return listOf(folder)
        val parent = parts.dropLast(1).joinToString(separator = "") { "$it/" }
        return listOf(folder, parent)
    }

    /**
     * The cover picture of an album: the best picture of its first folder that has one ([albumFolders]).
     * [pictures] may hold pictures of any folder; null when no folder of the album has a cover.
     */
    fun coverOf(
        tracks: List<AlbumTrackPlace>,
        pictures: Map<String, List<FolderPicture>>,
    ): FolderPicture? {
        for (folder in albumFolders(tracks)) {
            val inFolder = pictures[folder].orEmpty()
            val name = pick(inFolder.map { it.displayName }) ?: continue
            return inFolder.first { it.displayName == name }
        }
        return null
    }
}
