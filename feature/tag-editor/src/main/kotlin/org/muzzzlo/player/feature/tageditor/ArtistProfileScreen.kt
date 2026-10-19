package org.muzzzlo.player.feature.tageditor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.component.ValueListField
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * Profile editor of an artist (no mockup, DESIGN.md, section 7): the name is one row of a value list. Changing
 * it renames the artist, typing the name of another artist merges them, adding rows splits the artist into
 * several (for example "Kanye West & JAY-Z" into two). The change is written into the files of all their tracks.
 *
 * @param onClose leaves the screen after discarding the changes or back without changes.
 * @param onSaved leaves the screen after a write; gets the id of the artist to show next, null after a split.
 */
@Composable
fun ArtistProfileScreen(
    onClose: () -> Unit,
    onSaved: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    LaunchedEffect(state.saved) { if (state.saved) onSaved(state.savedArtistId) }
    BackHandler(onBack = viewModel::onBack)
    PermissionPrompt(state.permissionRequest, viewModel::onPermissionResult)

    ScreenFrame(modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(
                icon = DesignR.drawable.ic_chevron_left,
                contentDescription = stringResource(R.string.tag_editor_back),
                onClick = viewModel::onBack,
            )
            SectionTitle(stringResource(R.string.profile_title), Modifier.weight(1f), maxLines = 2)
        }
        when {
            !state.loaded -> Notice(stringResource(R.string.tag_editor_loading), Modifier.weight(1f))
            state.notFound -> Notice(stringResource(R.string.profile_not_found), Modifier.weight(1f))
            else -> {
                ProfileFields(state, viewModel, Modifier.weight(1f))
                PillButton(
                    text = saveText(state),
                    onClick = viewModel::onSave,
                    wide = true,
                    enabled = state.canSave,
                )
            }
        }
    }

    when (val dialog = state.dialog) {
        null -> Unit
        ProfileDialog.ConfirmWrite -> ConfirmProfileDialog(state, viewModel::onConfirmWrite, viewModel::onDismissDialog)
        ProfileDialog.Discard -> DiscardDialog(viewModel::onDiscard, viewModel::onDismissDialog)
        is ProfileDialog.Failed ->
            AlbumFailedDialog(
                failure = dialog.failure,
                onDismiss = viewModel::onDismissDialog,
                title = stringResource(R.string.profile_failed_title),
                restoredText = stringResource(R.string.profile_restored),
            )
    }
}

@Composable
private fun ProfileFields(
    state: ArtistProfileState,
    viewModel: ArtistProfileViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap),
    ) {
        Text(
            text = pluralStringResource(R.plurals.profile_tracks, state.trackCount, state.trackCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MuzzzloColors.TextSecondary,
        )
        if (state.skipped > 0) {
            Text(
                text = stringResource(R.string.batch_editor_skipped, state.skipped),
                style = MaterialTheme.typography.bodySmall,
                color = MuzzzloColors.TextSecondary,
            )
        }
        ValueListField(
            label = stringResource(R.string.profile_field_names),
            values = state.values,
            onValuesChange = viewModel::onValuesChange,
            addLabel = stringResource(R.string.profile_add_name),
            hint = stringResource(R.string.profile_names_hint),
        )
        state.merging.forEach { name ->
            Text(
                text = stringResource(R.string.profile_merge_note, name),
                style = MaterialTheme.typography.bodySmall,
                color = MuzzzloColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun saveText(state: ArtistProfileState): String {
    val progress = state.progress
    return if (progress == null) {
        stringResource(R.string.tag_editor_save)
    } else {
        stringResource(R.string.batch_editor_saving, progress + 1, state.trackCount)
    }
}

@Composable
private fun ConfirmProfileDialog(
    state: ArtistProfileState,
    onWrite: () -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(
        title = pluralStringResource(R.plurals.profile_confirm_title, state.trackCount, state.trackCount),
        onDismiss = onDismiss,
    ) {
        Text(
            text =
                stringResource(
                    R.string.profile_confirm_change,
                    state.originalName,
                    state.cleanValues.joinToString(", "),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MuzzzloColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.profile_confirm_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MuzzzloColors.TextSecondary,
        )
        PillButton(stringResource(R.string.tag_editor_confirm_write), onClick = onWrite, wide = true)
    }
}
