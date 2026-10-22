package org.muzzzlo.player.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.R

/** A playlist as the "Add to playlist" picker shows it. */
data class PlaylistChoice(
    val id: Long,
    val name: String,
)

/**
 * What the track menu needs for "Add to playlist" (task 1.1.5).
 *
 * @property load the playlists to choose from, loaded when the picker opens.
 * @property onAdd adds the track to the playlist with this id.
 * @property onCreate creates a playlist with this name and adds the track to it.
 */
class AddToPlaylist(
    val load: suspend () -> List<PlaylistChoice>,
    val onAdd: (Long) -> Unit,
    val onCreate: (String) -> Unit,
)

/**
 * Picker of "Add to playlist". Not in the mockup: built from the track menu (15:111, 15:124), DESIGN.md,
 * section 7. "New playlist" comes first, then the playlists. The dialog closes itself through [onDismiss] after
 * a playlist is picked; "New playlist" leaves the closing to the name dialog that follows.
 */
@Composable
fun PlaylistPickerMenu(
    playlists: List<PlaylistChoice>,
    onPick: (PlaylistChoice) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MuzzzloDialog(
        title = stringResource(R.string.track_action_add_to_playlist),
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        ActionRow(icon = R.drawable.ic_plus, text = stringResource(R.string.playlist_new), onClick = onNew)
        playlists.forEach { playlist ->
            ActionRow(
                icon = R.drawable.ic_list_music,
                text = playlist.name,
                onClick = {
                    onDismiss()
                    onPick(playlist)
                },
            )
        }
    }
}

/**
 * Asks for a playlist name, for a new playlist or a rename. Not in the mockup: the dialog of the sort dialog (15:111)
 * with the name field of the tag editor (15:13) and a wide button (15:142), DESIGN.md, section 7.
 * The button is off while the name is blank. The dialog closes itself through [onDismiss] after [onConfirm].
 */
@Composable
fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    MuzzzloDialog(title = title, onDismiss = onDismiss, modifier = modifier) {
        TagTextField(label = stringResource(R.string.playlist_name), value = name, onValueChange = { name = it })
        PillButton(
            text = confirmLabel,
            onClick = {
                onDismiss()
                onConfirm(name)
            },
            wide = true,
            enabled = name.isNotBlank(),
        )
    }
}
