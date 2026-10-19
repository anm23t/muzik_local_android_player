package org.muzzzlo.player.feature.tageditor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.component.TagTextField
import org.muzzzlo.player.core.designsystem.component.ValueListField
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * Tag editor of a whole album (no mockup, DESIGN.md, section 7): the cover, album, album artists, year and genres
 * are set for every track at once. A field whose tracks differ says so and is applied only when something is typed.
 *
 * @param onClose leaves the screen: after a write, after discarding the changes, or back without changes.
 */
@Composable
fun BatchTagEditorScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BatchTagEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    BackHandler(onBack = viewModel::onBack)
    PermissionPrompt(state.permissionRequest, viewModel::onPermissionResult)
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { viewModel.onCoverPicked(it.toString()) } ?: viewModel.onDismissDialog()
        }

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
            SectionTitle(stringResource(R.string.batch_editor_title), Modifier.weight(1f), maxLines = 2)
        }
        val draft = state.draft
        val loadError = state.loadError
        when {
            loadError != null -> Notice(loadErrorText(loadError), Modifier.weight(1f))
            draft == null -> Notice(stringResource(R.string.tag_editor_loading), Modifier.weight(1f))
            else -> {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap),
                ) {
                    CoverHeader(state.cover, state.albumTitle, onClick = viewModel::onCoverClicked)
                    Text(
                        text = stringResource(R.string.batch_editor_tracks, state.trackCount),
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
                    BatchFields(draft, state.errors, viewModel::onDraftChange)
                }
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
        BatchDialog.ConfirmWrite ->
            ConfirmWriteDialog(
                changes = state.changes,
                coverRemoved = false,
                onWrite = viewModel::onConfirmWrite,
                onDismiss = viewModel::onDismissDialog,
                title = stringResource(R.string.batch_editor_confirm_title, state.trackCount),
                text = stringResource(R.string.batch_editor_confirm_text),
                showBefore = false,
            )
        BatchDialog.Discard -> DiscardDialog(viewModel::onDiscard, viewModel::onDismissDialog)
        BatchDialog.CoverMenu ->
            CoverMenu(
                hasCover = false,
                onChoose = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemove = {},
                onDismiss = viewModel::onDismissDialog,
            )
        BatchDialog.CoverUnreadable -> ProblemDialog(WriteProblem.CoverUnreadable, viewModel::onDismissDialog)
        is BatchDialog.Failed -> AlbumFailedDialog(dialog.failure, viewModel::onDismissDialog)
    }
}

@Composable
private fun saveText(state: BatchTagEditorState): String {
    val progress = state.progress
    return if (progress == null) {
        stringResource(R.string.tag_editor_save)
    } else {
        stringResource(R.string.batch_editor_saving, progress + 1, state.trackCount)
    }
}

@Composable
private fun BatchFields(
    draft: BatchDraft,
    errors: Map<TagField, FieldError>,
    onChange: (BatchDraft) -> Unit,
) {
    val mixed = stringResource(R.string.batch_editor_mixed)
    TagTextField(
        label = stringResource(R.string.tag_editor_field_album),
        value = draft.album.value,
        onValueChange = { onChange(draft.copy(album = draft.album.edit(it))) },
        hint = mixed.takeIf { draft.album.mixed },
    )
    ValueListField(
        label = stringResource(R.string.tag_editor_field_album_artists),
        values = draft.albumArtists.value,
        onValuesChange = { onChange(draft.copy(albumArtists = draft.albumArtists.edit(it))) },
        addLabel = stringResource(R.string.tag_editor_add_album_artist),
        hint = mixed.takeIf { draft.albumArtists.mixed },
    )
    TagTextField(
        label = stringResource(R.string.tag_editor_field_year),
        value = draft.year.value,
        onValueChange = { onChange(draft.copy(year = draft.year.edit(it))) },
        numeric = true,
        error = errors[TagField.Year]?.let { fieldErrorText(it) },
        hint = mixed.takeIf { draft.year.mixed },
    )
    ValueListField(
        label = stringResource(R.string.tag_editor_field_genres),
        values = draft.genres.value,
        onValuesChange = { onChange(draft.copy(genres = draft.genres.edit(it))) },
        addLabel = stringResource(R.string.tag_editor_add_genre),
        hint = mixed.takeIf { draft.genres.mixed },
    )
}
