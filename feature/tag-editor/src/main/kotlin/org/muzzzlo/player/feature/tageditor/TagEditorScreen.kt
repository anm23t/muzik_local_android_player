package org.muzzzlo.player.feature.tageditor

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.designsystem.component.CoverCard
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.component.TagTextField
import org.muzzzlo.player.core.designsystem.component.ValueListField
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.EmbeddedPicture
import org.muzzzlo.player.core.tags.WritePermissionRequest
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * Tag editor of one track (no mockup, DESIGN.md, section 7): cover, title, artists, album, album artists,
 * track and disc, year, genres and lyrics, written into the file after a confirmation.
 *
 * @param onClose leaves the screen: after a write, after discarding the changes, or back without changes.
 */
@Composable
fun TagEditorScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TagEditorViewModel = hiltViewModel(),
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
            SectionTitle(stringResource(R.string.tag_editor_title), Modifier.weight(1f), maxLines = 2)
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
                    CoverHeader(state.cover, state.trackTitle, onClick = viewModel::onCoverClicked)
                    TagFields(draft, state.errors, viewModel::onDraftChange)
                }
                PillButton(
                    text = stringResource(if (state.writing) R.string.tag_editor_saving else R.string.tag_editor_save),
                    onClick = viewModel::onSave,
                    wide = true,
                    enabled = state.canSave,
                )
            }
        }
    }

    when (val dialog = state.dialog) {
        null -> Unit
        EditorDialog.ConfirmWrite ->
            ConfirmWriteDialog(
                changes = state.changes,
                coverRemoved = state.cover == null,
                onWrite = viewModel::onConfirmWrite,
                onDismiss = viewModel::onDismissDialog,
            )
        EditorDialog.Discard -> DiscardDialog(viewModel::onDiscard, viewModel::onDismissDialog)
        EditorDialog.CoverMenu ->
            CoverMenu(
                hasCover = state.cover != null,
                onChoose = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemove = viewModel::onCoverRemoved,
                onDismiss = viewModel::onDismissDialog,
            )
        is EditorDialog.Problem -> ProblemDialog(dialog.problem, viewModel::onDismissDialog)
    }
}

/** Shows the system request that lets the app change the file and reports the answer. */
@Composable
internal fun PermissionPrompt(
    request: WritePermissionRequest?,
    onResult: (Boolean) -> Unit,
) {
    val systemDialog =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            onResult(it.resultCode == Activity.RESULT_OK)
        }
    val runtimePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    LaunchedEffect(request) {
        when (request) {
            null -> Unit
            WritePermissionRequest.NotNeeded -> onResult(true)
            is WritePermissionRequest.SystemDialog ->
                systemDialog.launch(IntentSenderRequest.Builder(request.intentSender).build())
            is WritePermissionRequest.RuntimePermission -> runtimePermission.launch(request.permission)
        }
    }
}

/** The cover card of the album card (4:311) and the track title; a tap or a long press opens the cover menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CoverHeader(
    cover: EmbeddedPicture?,
    trackTitle: String,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val image = rememberPicture(cover)
        CoverCard(
            modifier =
                Modifier
                    .size(MuzzzloDimens.AlbumCardCover)
                    .combinedClickable(
                        onClickLabel = stringResource(R.string.tag_editor_cover_change),
                        onClick = onClick,
                        onLongClick = onClick,
                    ),
            shape = MuzzzloDimens.MiniPlayerShape,
            coverShape = MuzzzloDimens.TileShape,
            inset = MuzzzloDimens.AlbumCardInset,
            cover =
                image?.let { bitmap ->
                    { coverModifier ->
                        Image(
                            bitmap,
                            contentDescription = null,
                            modifier = coverModifier,
                            contentScale = ContentScale.Crop,
                        )
                    }
                },
        )
        Text(
            text = trackTitle,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MuzzzloColors.TextPrimary,
            maxLines = 3,
        )
    }
}

/** Covers are decoded off the main thread, at most the size the reader embeds. */
@Composable
private fun rememberPicture(picture: EmbeddedPicture?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, picture) {
        value =
            picture?.let {
                withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(it.data, 0, it.data.size)?.asImageBitmap()
                }
            }
    }
    return image
}

@Composable
private fun TagFields(
    draft: TagDraft,
    errors: Map<TagField, FieldError>,
    onChange: (TagDraft) -> Unit,
) {
    TagTextField(stringResource(R.string.tag_editor_field_title), draft.title, { onChange(draft.copy(title = it)) })
    ValueListField(
        label = stringResource(R.string.tag_editor_field_artists),
        values = draft.artists,
        onValuesChange = { onChange(draft.copy(artists = it)) },
        addLabel = stringResource(R.string.tag_editor_add_artist),
        canSplit = ::canSplit,
        onSplit = { onChange(draft.copy(artists = draft.artists.splitAt(it))) },
        onMerge = { onChange(draft.copy(artists = draft.artists.mergeWithNext(it))) },
    )
    TagTextField(stringResource(R.string.tag_editor_field_album), draft.album, { onChange(draft.copy(album = it)) })
    ValueListField(
        label = stringResource(R.string.tag_editor_field_album_artists),
        values = draft.albumArtists,
        onValuesChange = { onChange(draft.copy(albumArtists = it)) },
        addLabel = stringResource(R.string.tag_editor_add_album_artist),
        canSplit = ::canSplit,
        onSplit = { onChange(draft.copy(albumArtists = draft.albumArtists.splitAt(it))) },
        onMerge = { onChange(draft.copy(albumArtists = draft.albumArtists.mergeWithNext(it))) },
    )
    NumberPair(
        numberLabel = stringResource(R.string.tag_editor_field_track),
        number = draft.trackNumber,
        onNumberChange = { onChange(draft.copy(trackNumber = it)) },
        totalLabel = stringResource(R.string.tag_editor_field_track_total),
        total = draft.trackTotal,
        onTotalChange = { onChange(draft.copy(trackTotal = it)) },
        error = errors[TagField.Track],
    )
    NumberPair(
        numberLabel = stringResource(R.string.tag_editor_field_disc),
        number = draft.discNumber,
        onNumberChange = { onChange(draft.copy(discNumber = it)) },
        totalLabel = stringResource(R.string.tag_editor_field_disc_total),
        total = draft.discTotal,
        onTotalChange = { onChange(draft.copy(discTotal = it)) },
        error = errors[TagField.Disc],
    )
    TagTextField(
        label = stringResource(R.string.tag_editor_field_year),
        value = draft.year,
        onValueChange = { onChange(draft.copy(year = it)) },
        numeric = true,
        error = errors[TagField.Year]?.let { fieldErrorText(it) },
    )
    ValueListField(
        label = stringResource(R.string.tag_editor_field_genres),
        values = draft.genres,
        onValuesChange = { onChange(draft.copy(genres = it)) },
        addLabel = stringResource(R.string.tag_editor_add_genre),
    )
    TagTextField(
        label = stringResource(R.string.tag_editor_field_lyrics),
        value = draft.lyrics,
        onValueChange = { onChange(draft.copy(lyrics = it)) },
        singleLine = false,
    )
}

/** Number and total side by side; the error of the pair goes under the number. */
@Composable
private fun NumberPair(
    numberLabel: String,
    number: String,
    onNumberChange: (String) -> Unit,
    totalLabel: String,
    total: String,
    onTotalChange: (String) -> Unit,
    error: FieldError?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap)) {
            TagTextField(numberLabel, number, onNumberChange, Modifier.weight(1f), numeric = true)
            TagTextField(totalLabel, total, onTotalChange, Modifier.weight(1f), numeric = true)
        }
        if (error != null) {
            Text(fieldErrorText(error), style = MaterialTheme.typography.bodySmall, color = MuzzzloColors.TextPrimary)
        }
    }
}

/** Centered secondary text while the file is read or when it cannot be. */
@Composable
internal fun Notice(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MuzzzloColors.TextSecondary)
    }
}

@Composable
internal fun loadErrorText(error: LoadError): String =
    stringResource(
        when (error) {
            LoadError.Unreadable -> R.string.tag_editor_load_unreadable
            LoadError.Quarantined -> R.string.tag_editor_load_quarantined
        },
    )

@Composable
internal fun fieldErrorText(error: FieldError): String =
    when (error) {
        FieldError.NotANumber -> stringResource(R.string.tag_editor_error_number, MAX_NUMBER)
        FieldError.TotalWithoutNumber -> stringResource(R.string.tag_editor_error_total)
    }
