package org.muzzzlo.player.core.library

/** One audio row of MediaStore, before it is split into artists and written to the database. */
data class ScannedFile(
    val mediaStoreId: Long,
    val uri: String,
    val relativePath: String?,
    /** File name with extension; the tag reader picks the format by it. */
    val displayName: String?,
    val title: String,
    val artist: String?,
    val album: String?,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long,
    val mimeType: String?,
    val fileSize: Long?,
    val dateAdded: Long?,
    val dateModified: Long?,
)
