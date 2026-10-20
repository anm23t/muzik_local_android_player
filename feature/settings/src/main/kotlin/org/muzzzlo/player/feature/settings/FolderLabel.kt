package org.muzzzlo.player.feature.settings

import java.net.URLDecoder

/**
 * A readable name for a folder tree URI: `content://.../tree/primary%3ADocuments%2FBackups` gives
 * `Documents/Backups`. The volume (`primary:`) is dropped; text that does not look like a tree URI is returned as is.
 */
internal fun folderLabel(treeUri: String): String {
    val last = treeUri.substringAfterLast('/')
    val decoded = runCatching { URLDecoder.decode(last, Charsets.UTF_8.name()) }.getOrDefault(last)
    return decoded.substringAfter(':').ifEmpty { decoded.trimEnd(':') }
}
