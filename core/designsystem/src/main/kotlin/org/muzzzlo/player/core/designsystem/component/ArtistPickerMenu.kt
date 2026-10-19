package org.muzzzlo.player.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.R

/**
 * Dialog that asks which of the track's artists to open. Not in the mockup: built from the track menu
 * (15:111, 15:124), see DESIGN.md, section 7. The dialog closes itself after a choice through [onDismiss].
 */
@Composable
fun ArtistPickerMenu(
    artists: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MuzzzloDialog(title = stringResource(R.string.artist_picker_title), onDismiss = onDismiss, modifier = modifier) {
        artists.forEach { name ->
            ActionRow(
                icon = R.drawable.ic_users,
                text = name,
                onClick = {
                    onDismiss()
                    onPick(name)
                },
            )
        }
    }
}
