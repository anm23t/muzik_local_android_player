package org.muzzzlo.player.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.R
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens

/**
 * Dialog that asks which of the track's artists to open. Not in the mockup: built from the track menu
 * (15:111, 15:124), see DESIGN.md, section 7. Each row starts with the artist's photo, or the empty circle
 * when there is none, as in the album card (23:478). The dialog closes itself after a choice through [onDismiss].
 */
@Composable
fun ArtistPickerMenu(
    artists: List<ArtistChoice>,
    onPick: (ArtistChoice) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MuzzzloDialog(title = stringResource(R.string.artist_picker_title), onDismiss = onDismiss, modifier = modifier) {
        artists.forEach { artist ->
            ActionRow(
                text = artist.name,
                onClick = {
                    onDismiss()
                    onPick(artist)
                },
                leading = { ArtistPhoto(artist.photoPath, MuzzzloDimens.InlineArtistPhoto) },
            )
        }
    }
}
