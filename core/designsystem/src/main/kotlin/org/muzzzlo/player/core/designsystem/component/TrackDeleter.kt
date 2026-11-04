package org.muzzzlo.player.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.R
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors

/**
 * Starts deleting the file of a track (task 2.0.6). The app module provides one for every screen through
 * [LocalTrackDeleter]; it shows the warning and the system dialogs itself.
 */
fun interface TrackDeleter {
    fun delete(
        trackId: Long,
        title: String,
    )
}

/** The [TrackDeleter] of the app; without one the track menu has no "Delete file". */
val LocalTrackDeleter = staticCompositionLocalOf<TrackDeleter?> { null }

/**
 * Warns that the file of [title] goes for good (Android 10 and older have no system trash). Not in the mockup: the
 * dialog of the sort dialog (15:111) with a wide button (15:142), DESIGN.md, section 7. Closes itself through
 * [onDismiss] after [onConfirm].
 */
@Composable
fun DeleteFileDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MuzzzloDialog(title = stringResource(R.string.delete_file_title), onDismiss = onDismiss, modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MuzzzloColors.TextPrimary)
        Text(
            text = stringResource(R.string.delete_file_permanent),
            style = MaterialTheme.typography.bodyMedium,
            color = MuzzzloColors.TextSecondary,
        )
        PillButton(
            text = stringResource(R.string.delete_file_confirm),
            onClick = {
                onDismiss()
                onConfirm()
            },
            wide = true,
        )
    }
}
