package org.muzzzlo.player.feature.playlists

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors

/** Unmatched entries listed by path in the import dialog; the rest are only counted. */
private const val SHOWN_UNMATCHED = 10

/** MIME types of the picker for an import. Many file managers report M3U as something generic, so all files go. */
internal val PLAYLIST_FILE_TYPES = arrayOf("*/*")

/** MIME type of an exported playlist (M3U8). */
internal const val PLAYLIST_EXPORT_TYPE = "audio/x-mpegurl"

/**
 * The result of an import or export of a playlist file (no mockup, DESIGN.md, section 7: dialog 15:111, text as in
 * the delete dialog, wide button 15:142).
 *
 * @param onOpenPlaylist shown as a button after an import that created a playlist; null hides it.
 */
@Composable
internal fun PlaylistFileDialog(
    message: PlaylistFileMessage,
    onDismiss: () -> Unit,
    onOpenPlaylist: ((Long) -> Unit)? = null,
) {
    val title =
        when (message) {
            is PlaylistFileMessage.Imported -> R.string.playlist_import_title
            PlaylistFileMessage.Exported -> R.string.playlist_export_title
            PlaylistFileMessage.FileError -> R.string.playlist_file_error_title
        }
    MuzzzloDialog(stringResource(title), onDismiss) {
        when (message) {
            is PlaylistFileMessage.Imported -> ImportResult(message, onDismiss, onOpenPlaylist)
            PlaylistFileMessage.Exported -> Body(stringResource(R.string.playlist_export_done))
            PlaylistFileMessage.FileError -> Body(stringResource(R.string.playlist_file_error))
        }
    }
}

@Composable
private fun ImportResult(
    message: PlaylistFileMessage.Imported,
    onDismiss: () -> Unit,
    onOpenPlaylist: ((Long) -> Unit)?,
) {
    val result = message.result
    if (result.playlistId == null) {
        Body(stringResource(R.string.playlist_import_nothing))
    } else {
        Body(pluralStringResource(R.plurals.playlist_import_added, result.added, result.name, result.added))
    }
    if (result.unmatched.isNotEmpty()) {
        Body(pluralStringResource(R.plurals.playlist_import_unmatched, result.unmatched.size, result.unmatched.size))
        result.unmatched.take(SHOWN_UNMATCHED).forEach { path ->
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = MuzzzloColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val more = result.unmatched.size - SHOWN_UNMATCHED
        if (more > 0) Body(pluralStringResource(R.plurals.playlist_import_more, more, more))
    }
    val id = result.playlistId
    if (id != null && onOpenPlaylist != null) {
        PillButton(
            text = stringResource(R.string.playlist_import_open),
            onClick = {
                onDismiss()
                onOpenPlaylist(id)
            },
            wide = true,
        )
    }
}

@Composable
private fun Body(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MuzzzloColors.TextPrimary)
}
