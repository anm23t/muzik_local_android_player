package org.muzzzlo.player.feature.tageditor

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.component.ActionRow
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.TagWriteResult
import org.muzzzlo.player.core.designsystem.R as DesignR

@Composable
internal fun ConfirmWriteDialog(
    changes: List<TagChange>,
    coverRemoved: Boolean,
    onWrite: () -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.tag_editor_confirm_title),
    text: String = stringResource(R.string.tag_editor_confirm_text),
    showBefore: Boolean = true,
) {
    MuzzzloDialog(title = title, onDismiss = onDismiss) {
        DialogText(text)
        changes.forEach { change ->
            Column {
                Text(
                    text = fieldLabel(change.field),
                    style = MaterialTheme.typography.bodySmall,
                    color = MuzzzloColors.TextSecondary,
                )
                Text(
                    text = changeText(change, coverRemoved, showBefore),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MuzzzloColors.TextPrimary,
                )
            }
        }
        PillButton(stringResource(R.string.tag_editor_confirm_write), onClick = onWrite, wide = true)
    }
}

@Composable
internal fun DiscardDialog(
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(title = stringResource(R.string.tag_editor_discard_title), onDismiss = onDismiss) {
        DialogText(stringResource(R.string.tag_editor_discard_text))
        PillButton(stringResource(R.string.tag_editor_discard), onClick = onDiscard, wide = true)
    }
}

/** Menu of the cover, like the photo menu of the artist card (33:714, 15:124). */
@Composable
internal fun CoverMenu(
    hasCover: Boolean,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(title = stringResource(R.string.tag_editor_cover), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.TinyGap)) {
            ActionRow(DesignR.drawable.ic_pencil, stringResource(R.string.tag_editor_cover_choose), onChoose)
            if (hasCover) {
                ActionRow(DesignR.drawable.ic_close, stringResource(R.string.tag_editor_cover_remove), onRemove)
            }
        }
    }
}

@Composable
internal fun ProblemDialog(
    problem: WriteProblem,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(title = stringResource(R.string.tag_editor_problem_title), onDismiss = onDismiss) {
        DialogText(problemText(problem))
        PillButton(stringResource(R.string.tag_editor_ok), onClick = onDismiss, wide = true)
    }
}

@Composable
private fun DialogText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MuzzzloColors.TextSecondary)
}

@Composable
private fun fieldLabel(field: TagField): String =
    stringResource(
        when (field) {
            TagField.Cover -> R.string.tag_editor_cover
            TagField.Title -> R.string.tag_editor_field_title
            TagField.Artists -> R.string.tag_editor_field_artists
            TagField.Album -> R.string.tag_editor_field_album
            TagField.AlbumArtists -> R.string.tag_editor_field_album_artists
            TagField.Track -> R.string.tag_editor_field_track
            TagField.Disc -> R.string.tag_editor_field_disc
            TagField.Year -> R.string.tag_editor_field_year
            TagField.Genres -> R.string.tag_editor_field_genres
            TagField.Lyrics -> R.string.tag_editor_field_lyrics
        },
    )

@Composable
private fun changeText(
    change: TagChange,
    coverRemoved: Boolean,
    showBefore: Boolean,
): String =
    when (change.field) {
        TagField.Cover ->
            stringResource(
                if (coverRemoved) R.string.tag_editor_change_cover_removed else R.string.tag_editor_change_cover_new,
            )
        TagField.Lyrics -> stringResource(R.string.tag_editor_change_lyrics)
        else -> {
            val empty = stringResource(R.string.tag_editor_change_empty)
            if (!showBefore) {
                change.after.ifEmpty { empty }
            } else {
                stringResource(
                    R.string.tag_editor_change_value,
                    change.before.ifEmpty { empty },
                    change.after.ifEmpty { empty },
                )
            }
        }
    }

@Composable
private fun problemText(problem: WriteProblem): String {
    val context = LocalContext.current
    return when (problem) {
        WriteProblem.PermissionDenied -> stringResource(R.string.tag_editor_problem_permission)
        WriteProblem.CoverUnreadable -> stringResource(R.string.tag_editor_problem_cover)
        is WriteProblem.Failed -> writeResultText(problem.result) { Formatter.formatShortFileSize(context, it) }
    }
}

@Composable
internal fun writeResultText(
    result: TagWriteResult,
    formatBytes: (Long) -> String,
): String =
    when (result) {
        is TagWriteResult.NotEnoughSpace ->
            stringResource(R.string.tag_editor_problem_space, formatBytes(result.requiredBytes))
        TagWriteResult.Unreadable -> stringResource(R.string.tag_editor_problem_unreadable)
        TagWriteResult.Quarantined -> stringResource(R.string.tag_editor_problem_quarantined)
        TagWriteResult.WriteFailed -> stringResource(R.string.tag_editor_problem_write)
        is TagWriteResult.VerificationFailed ->
            stringResource(R.string.tag_editor_problem_verification, result.reason)
        TagWriteResult.FileChanged -> stringResource(R.string.tag_editor_problem_file_changed)
        is TagWriteResult.ReplaceFailed ->
            stringResource(
                if (result.restored) R.string.tag_editor_problem_restored else R.string.tag_editor_problem_not_restored,
            )
        // Success closes the screen and a missing permission is asked for; neither reaches a dialog.
        TagWriteResult.Success,
        TagWriteResult.NeedsPermission,
        -> stringResource(R.string.tag_editor_problem_write)
    }

/** What went wrong with an album write and what became of the files written before it. */
@Composable
internal fun AlbumFailedDialog(
    failure: AlbumWriteFailure,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.batch_editor_failed_title),
    restoredText: String = stringResource(R.string.batch_editor_restored),
) {
    val context = LocalContext.current
    MuzzzloDialog(title = title, onDismiss = onDismiss) {
        val reason =
            failure.result?.let { result -> writeResultText(result) { Formatter.formatShortFileSize(context, it) } }
        if (failure.failedTitle != null && reason != null) {
            DialogText(stringResource(R.string.batch_editor_failed_track, failure.failedTitle, reason))
        } else {
            DialogText(stringResource(R.string.tag_editor_problem_permission))
        }
        if (failure.restored) {
            DialogText(restoredText)
        } else {
            DialogText(stringResource(R.string.batch_editor_not_restored, failure.notRestored.joinToString(", ")))
        }
        PillButton(stringResource(R.string.tag_editor_ok), onClick = onDismiss, wide = true)
    }
}
