package org.muzzzlo.player.feature.playlists

import org.muzzzlo.player.core.model.PlaylistImport
import org.muzzzlo.player.core.model.PlaylistTransfer
import java.io.IOException

/** Answers imports with [importResult] and remembers exports; [failing] throws like an unreadable file. */
class FakePlaylistTransfer(
    var importResult: PlaylistImport = PlaylistImport(7, "Mix", 2, listOf("gone.mp3")),
) : PlaylistTransfer {
    var failing = false
    val imports = mutableListOf<Pair<String, String>>()
    val exports = mutableListOf<Pair<Long, String>>()

    override suspend fun import(
        uri: String,
        fallbackName: String,
    ): PlaylistImport {
        if (failing) throw IOException("unreadable")
        imports += uri to fallbackName
        return importResult
    }

    override suspend fun export(
        playlistId: Long,
        uri: String,
    ) {
        if (failing) throw SecurityException("no access")
        exports += playlistId to uri
    }
}
