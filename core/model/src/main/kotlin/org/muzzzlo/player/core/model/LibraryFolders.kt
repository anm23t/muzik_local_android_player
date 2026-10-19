package org.muzzzlo.player.core.model

/** A folder with music as MediaStore knows it. [relativePath] is empty for files in the storage root. */
data class LibraryFolder(
    val relativePath: String,
    val trackCount: Int,
)

/** A file the native tag reader crashed on; it is skipped until the user retries it. */
data class QuarantinedFile(
    val uri: String,
    val name: String,
)

/** The folders of the device and the quarantine list, for the settings screen. Implemented in `core:library`. */
interface LibraryMaintenance {
    /** Every folder with music, including the excluded ones; sorted by path. */
    suspend fun folders(): List<LibraryFolder>

    suspend fun quarantined(): List<QuarantinedFile>

    /** Takes [uri] out of quarantine and reads the library again, so the file is tried once more. */
    suspend fun retryQuarantined(uri: String)
}

/** The quarantine list of the tag reader (`NativeCallGuard`); implemented in `core:tags`. */
interface QuarantineList {
    fun keys(): Set<String>

    fun release(key: String)
}
