package org.muzzzlo.player.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.component.ValueRow

/**
 * The "Lyrics files" row (task 2.0.7). A `.lrc` file is not media, so from Android 10 the app may read it only in a
 * folder the user opens in the system picker; the permission is kept across restarts. Android 8–9 read lyrics files
 * by their path and have no row.
 */
@Composable
internal fun LyricsFolderRow(
    folder: String?,
    onChosen: (String) -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val context = LocalContext.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                onChosen(uri.toString())
            }
        }
    ValueRow(
        text = stringResource(R.string.settings_lyrics_folder),
        onClick = { picker.launch(folder?.let(Uri::parse)) },
        value = folder?.let(::lyricsFolderLabel) ?: stringResource(R.string.settings_lyrics_folder_none),
        secondary = stringResource(R.string.settings_lyrics_folder_hint),
    )
}

/** `primary:Music/Rock` → `Music/Rock`; the root of a volume shows as `/`. */
internal fun lyricsFolderLabel(treeUri: String): String {
    val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)) }.getOrNull() ?: return treeUri
    return id.substringAfter(':').ifEmpty { "/" }
}
