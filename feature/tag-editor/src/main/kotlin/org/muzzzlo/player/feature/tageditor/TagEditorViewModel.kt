package org.muzzzlo.player.feature.tageditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.model.CoverEdit
import org.muzzzlo.player.core.model.EmbeddedPicture
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TagWriteResult
import org.muzzzlo.player.core.model.TrackTagRepository
import org.muzzzlo.player.core.model.TrackTags
import org.muzzzlo.player.core.tags.WritePermissionRequest
import javax.inject.Inject

/** Navigation argument: id of the track whose tags are edited. */
const val TRACK_ID_ARG = "trackId"

/** Why the editor cannot show the tags. */
enum class LoadError {
    /** The track or its file is gone, or the format is not supported. */
    Unreadable,

    /** The file crashed the tag library before and is left alone. */
    Quarantined,
}

/** A write that did not happen, shown in a dialog. */
sealed interface WriteProblem {
    /** The writer refused or failed; the original file is as [result] says. */
    data class Failed(val result: TagWriteResult) : WriteProblem

    /** The user did not let the app change the file. */
    data object PermissionDenied : WriteProblem

    /** The picked image is not a picture the app can read. */
    data object CoverUnreadable : WriteProblem
}

/** Dialog over the editor. */
sealed interface EditorDialog {
    /** Confirmation before the file is written, with the changes. */
    data object ConfirmWrite : EditorDialog

    /** Leaving with changes that are not written. */
    data object Discard : EditorDialog

    /** Choose or remove the cover. */
    data object CoverMenu : EditorDialog

    data class Problem(val problem: WriteProblem) : EditorDialog
}

/**
 * State of the tag editor.
 *
 * @property draft what the user has typed; null until the file is read.
 * @property cover the picture the file will have after the write; null when it will have none.
 * @property permissionRequest the screen shows this request to let the app change the file, then reports the
 *   answer to [TagEditorViewModel.onPermissionResult].
 * @property done the screen closes: the tags are written or the changes were discarded.
 */
data class TagEditorState(
    val trackTitle: String = "",
    val trackUri: String? = null,
    val loadError: LoadError? = null,
    val draft: TagDraft? = null,
    val errors: Map<TagField, FieldError> = emptyMap(),
    val changes: List<TagChange> = emptyList(),
    val cover: EmbeddedPicture? = null,
    val dialog: EditorDialog? = null,
    val writing: Boolean = false,
    val permissionRequest: WritePermissionRequest? = null,
    val done: Boolean = false,
) {
    val isLoading: Boolean get() = draft == null && loadError == null

    /** Save is offered when something changed and every field can be written. */
    val canSave: Boolean get() = changes.isNotEmpty() && errors.isEmpty() && !writing
}

/**
 * Reads the tags of one track from its file, keeps the user's draft and writes it after a confirmation
 * (the switch to skip it comes with the settings in 0.5.1). A write that needs the user's permission is asked for
 * by the screen and retried once the user allows it.
 */
@HiltViewModel
class TagEditorViewModel
    @Inject
    constructor(
        private val tags: TrackTagRepository,
        private val library: LibraryRepository,
        private val pictures: PictureReader,
        private val permissions: WritePermissions,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val trackId: Long = checkNotNull(savedState[TRACK_ID_ARG])
        private val mutableState = MutableStateFlow(TagEditorState())
        val state: StateFlow<TagEditorState> = mutableState.asStateFlow()

        /** Tags of the file when it was read; changes are counted against them. */
        private var original = TrackTags()
        private var originalCover: EmbeddedPicture? = null

        /** The user already allowed the write once: a second refusal is reported instead of asking again. */
        private var permissionGranted = false

        init {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            library.tracksByIds(listOf(trackId)).firstOrNull()?.let { track ->
                mutableState.update { it.copy(trackTitle = track.title, trackUri = track.uri) }
            }
            when (val read = tags.readTags(trackId)) {
                is TagReadResult.Success -> {
                    original = read.fileTags.tags
                    updateDraft(TagDraft.from(original))
                    if (original.hasCover) {
                        originalCover = tags.readCover(trackId)
                        mutableState.update {
                            if (it.draft?.cover == CoverEdit.Keep) {
                                it.copy(
                                    cover = originalCover,
                                )
                            } else {
                                it
                            }
                        }
                    }
                }
                TagReadResult.Unreadable -> mutableState.update { it.copy(loadError = LoadError.Unreadable) }
                TagReadResult.Quarantined -> mutableState.update { it.copy(loadError = LoadError.Quarantined) }
            }
        }

        fun onDraftChange(draft: TagDraft) = updateDraft(draft)

        fun onCoverClicked() = showDialog(EditorDialog.CoverMenu)

        fun onCoverPicked(uri: String) {
            dismissDialog()
            viewModelScope.launch {
                val picture = pictures.read(uri)
                if (picture == null) {
                    showDialog(EditorDialog.Problem(WriteProblem.CoverUnreadable))
                } else {
                    updateDraft(currentDraft()?.copy(cover = CoverEdit.Replace(picture)) ?: return@launch)
                }
            }
        }

        fun onCoverRemoved() {
            dismissDialog()
            updateDraft(currentDraft()?.copy(cover = CoverEdit.Remove) ?: return)
        }

        fun onSave() {
            if (state.value.canSave) showDialog(EditorDialog.ConfirmWrite)
        }

        fun onConfirmWrite() {
            dismissDialog()
            write()
        }

        /** Back with unwritten changes asks first. */
        fun onBack() {
            val current = state.value
            when {
                current.writing -> Unit
                // Text that is not a number yet is a change too.
                current.changes.isEmpty() && current.errors.isEmpty() -> mutableState.update { it.copy(done = true) }
                else -> showDialog(EditorDialog.Discard)
            }
        }

        fun onDiscard() = mutableState.update { it.copy(dialog = null, done = true) }

        fun onDismissDialog() = dismissDialog()

        /** The answer to the request the screen showed for [TagEditorState.permissionRequest]. */
        fun onPermissionResult(granted: Boolean) {
            mutableState.update { it.copy(permissionRequest = null) }
            if (granted) {
                permissionGranted = true
                write()
            } else {
                showDialog(EditorDialog.Problem(WriteProblem.PermissionDenied))
            }
        }

        private fun write() {
            val edit = currentDraft()?.toEdit() ?: return
            if (state.value.writing) return
            mutableState.update { it.copy(writing = true) }
            viewModelScope.launch {
                val result = tags.writeTags(trackId, edit)
                mutableState.update { it.copy(writing = false) }
                when {
                    result == TagWriteResult.Success -> mutableState.update { it.copy(done = true) }
                    result == TagWriteResult.NeedsPermission && !permissionGranted -> askPermission()
                    result == TagWriteResult.NeedsPermission ->
                        showDialog(EditorDialog.Problem(WriteProblem.PermissionDenied))
                    else -> showDialog(EditorDialog.Problem(WriteProblem.Failed(result)))
                }
            }
        }

        private suspend fun askPermission() {
            val uri = state.value.trackUri
            when (val request = uri?.let { permissions.requestFor(it) }) {
                null -> showDialog(EditorDialog.Problem(WriteProblem.PermissionDenied))
                // The system has nothing to ask: try once more, a second refusal is reported.
                WritePermissionRequest.NotNeeded -> onPermissionResult(granted = true)
                else -> mutableState.update { it.copy(permissionRequest = request) }
            }
        }

        private fun updateDraft(draft: TagDraft) {
            val edit = draft.toEdit()
            mutableState.update {
                it.copy(
                    draft = draft,
                    errors = draft.errors(),
                    // Counted only for a draft that can be written; an invalid one keeps the last list.
                    changes = edit?.let { valid -> changesOf(original, valid) } ?: it.changes,
                    cover = coverAfter(draft.cover),
                )
            }
        }

        private fun coverAfter(edit: CoverEdit): EmbeddedPicture? =
            when (edit) {
                CoverEdit.Keep -> originalCover
                CoverEdit.Remove -> null
                is CoverEdit.Replace -> edit.picture
            }

        private fun currentDraft(): TagDraft? = state.value.draft

        private fun showDialog(dialog: EditorDialog) = mutableState.update { it.copy(dialog = dialog) }

        private fun dismissDialog() = mutableState.update { it.copy(dialog = null) }
    }
