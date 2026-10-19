package org.muzzzlo.player.feature.tageditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.model.CoverEdit
import org.muzzzlo.player.core.model.EmbeddedPicture
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.TagEdit
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TagWriteResult
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.TrackTagRepository
import org.muzzzlo.player.core.model.TrackTags
import org.muzzzlo.player.core.tags.WritePermissionRequest
import javax.inject.Inject

/** Navigation argument: id of the album whose tracks are edited together. */
const val BATCH_ALBUM_ID_ARG = "albumId"

/** A track of the album with the tags read from its file. */
internal data class AlbumTrack(
    val track: Track,
    val tags: TrackTags,
)

/**
 * How a write of the album ended badly.
 *
 * @property failedTitle the track that was not written, null when the permission was refused.
 * @property result why it was not written; null for a refused permission.
 * @property restored true when every track written before it got its old tags back.
 * @property notRestored titles of the tracks that could not be restored; their backups are handled by the writer.
 */
data class AlbumWriteFailure(
    val failedTitle: String?,
    val result: TagWriteResult?,
    val restored: Boolean,
    val notRestored: List<String>,
)

/** Dialog over the album editor. */
sealed interface BatchDialog {
    data object ConfirmWrite : BatchDialog

    data object Discard : BatchDialog

    data object CoverMenu : BatchDialog

    data object CoverUnreadable : BatchDialog

    /** The write stopped and the written tracks were restored; shown after [BatchTagEditorState.progress] ends. */
    data class Failed(val failure: AlbumWriteFailure) : BatchDialog
}

/**
 * State of the album editor.
 *
 * @property albumTitle title of the album.
 * @property trackCount tracks that will be written.
 * @property skipped tracks whose files cannot be read; they are left alone.
 * @property progress tracks written so far while a write runs, null otherwise.
 */
data class BatchTagEditorState(
    val albumTitle: String = "",
    val trackCount: Int = 0,
    val skipped: Int = 0,
    val loadError: LoadError? = null,
    val draft: BatchDraft? = null,
    val errors: Map<TagField, FieldError> = emptyMap(),
    val changes: List<TagChange> = emptyList(),
    val cover: EmbeddedPicture? = null,
    val dialog: BatchDialog? = null,
    val progress: Int? = null,
    val permissionRequest: WritePermissionRequest? = null,
    val done: Boolean = false,
) {
    val isLoading: Boolean get() = draft == null && loadError == null
    val writing: Boolean get() = progress != null

    val canSave: Boolean get() = changes.isNotEmpty() && errors.isEmpty() && !writing
}

/**
 * Edits the fields shared by the tracks of one album and writes them into every file, one file at a time.
 * One permission request covers all files. When a file fails, the files already written get their old tags back
 * (the cover too), so an album is never left half changed; the failure is shown in a dialog.
 */
@HiltViewModel
class BatchTagEditorViewModel
    @Inject
    constructor(
        private val tags: TrackTagRepository,
        private val library: LibraryRepository,
        private val permissions: WritePermissions,
        private val pictures: PictureReader,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val albumId: Long = checkNotNull(savedState[BATCH_ALBUM_ID_ARG])
        private val mutableState = MutableStateFlow(BatchTagEditorState())
        val state: StateFlow<BatchTagEditorState> = mutableState.asStateFlow()

        private var tracks: List<AlbumTrack> = emptyList()
        private var originalCover: EmbeddedPicture? = null

        /** Where the write continues once the user answers the permission request. */
        private var pending: Pending? = null

        /** The track a permission request was already made for in the middle of a write; a second refusal fails. */
        private var askedAt = -1

        init {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            val albumTracks = library.observeAlbumTracks(albumId).first()
            val read = albumTracks.map { it to tags.readTags(it.id) }
            tracks =
                read.mapNotNull { (track, result) ->
                    (result as? TagReadResult.Success)?.let { AlbumTrack(track, it.fileTags.tags) }
                }
            if (tracks.isEmpty()) {
                val quarantined = read.isNotEmpty() && read.all { it.second == TagReadResult.Quarantined }
                mutableState.update {
                    it.copy(loadError = if (quarantined) LoadError.Quarantined else LoadError.Unreadable)
                }
                return
            }
            originalCover = tracks.firstOrNull { it.tags.hasCover }?.let { tags.readCover(it.track.id) }
            mutableState.update {
                it.copy(
                    albumTitle = albumTracks.firstNotNullOfOrNull { track -> track.albumTitle }.orEmpty(),
                    trackCount = tracks.size,
                    skipped = albumTracks.size - tracks.size,
                    cover = originalCover,
                )
            }
            updateDraft(BatchDraft.from(tracks.map { it.tags }))
        }

        fun onDraftChange(draft: BatchDraft) = updateDraft(draft)

        fun onCoverClicked() = showDialog(BatchDialog.CoverMenu)

        fun onCoverPicked(uri: String) {
            dismissDialog()
            viewModelScope.launch {
                val picture = pictures.read(uri)
                if (picture == null) {
                    showDialog(BatchDialog.CoverUnreadable)
                } else {
                    updateDraft(state.value.draft?.copy(cover = CoverEdit.Replace(picture)) ?: return@launch)
                }
            }
        }

        fun onSave() {
            if (state.value.canSave) showDialog(BatchDialog.ConfirmWrite)
        }

        fun onConfirmWrite() {
            dismissDialog()
            viewModelScope.launch { askPermission(Pending(0, emptyList())) }
        }

        fun onBack() {
            val current = state.value
            when {
                current.writing -> Unit
                current.changes.isEmpty() && current.errors.isEmpty() -> mutableState.update { it.copy(done = true) }
                else -> showDialog(BatchDialog.Discard)
            }
        }

        fun onDiscard() = mutableState.update { it.copy(dialog = null, done = true) }

        fun onDismissDialog() = dismissDialog()

        /** The answer to the request the screen showed for [BatchTagEditorState.permissionRequest]. */
        fun onPermissionResult(granted: Boolean) {
            mutableState.update { it.copy(permissionRequest = null) }
            val waiting = pending ?: return
            pending = null
            viewModelScope.launch {
                if (granted) writeFrom(waiting.index, waiting.written) else fail(null, null, waiting.written)
            }
        }

        private suspend fun askPermission(waiting: Pending) {
            pending = waiting
            mutableState.update { it.copy(progress = waiting.index) }
            when (val request = permissions.requestFor(tracks.drop(waiting.index).map { it.track.uri })) {
                WritePermissionRequest.NotNeeded -> onPermissionResult(granted = true)
                else -> mutableState.update { it.copy(permissionRequest = request) }
            }
        }

        /**
         * Writes the tracks from [start] on, one at a time. A track that needs the permission again (Android 10 asks
         * per file) pauses the write for a new request. The first failure stops it and restores [written] and the
         * tracks written here.
         */
        private suspend fun writeFrom(
            start: Int,
            written: List<Written>,
        ) {
            val draft = state.value.draft ?: return
            val done = written.toMutableList()
            for (index in start until tracks.size) {
                mutableState.update { it.copy(progress = index) }
                val item = tracks[index]
                val edit = draft.applyTo(item.tags)
                if (changesOf(item.tags, edit).isEmpty()) continue
                val previousCover = if (edit.cover is CoverEdit.Replace) coverOf(item) else null
                val result = tags.writeTags(item.track.id, edit)
                when {
                    result == TagWriteResult.Success -> done += Written(item, previousCover)
                    result == TagWriteResult.NeedsPermission && index != askedAt -> {
                        askedAt = index
                        askPermission(Pending(index, done))
                        return
                    }
                    else -> {
                        fail(item.track.title, result, done)
                        return
                    }
                }
            }
            mutableState.update { it.copy(progress = null, done = true) }
        }

        private suspend fun coverOf(item: AlbumTrack): EmbeddedPicture? =
            if (item.tags.hasCover) tags.readCover(item.track.id) else null

        /** Gives the [written] tracks their old tags back, newest first, and shows what happened. */
        private suspend fun fail(
            title: String?,
            result: TagWriteResult?,
            written: List<Written>,
        ) {
            val notRestored = written.asReversed().filterNot { restore(it) }.map { it.item.track.title }
            val failure = AlbumWriteFailure(title, result, restored = notRestored.isEmpty(), notRestored = notRestored)
            mutableState.update { it.copy(progress = null, dialog = BatchDialog.Failed(failure)) }
        }

        private suspend fun restore(written: Written): Boolean {
            val replaced = state.value.draft?.cover is CoverEdit.Replace
            val previous = written.previousCover
            val cover =
                when {
                    !replaced -> CoverEdit.Keep
                    previous != null -> CoverEdit.Replace(previous)
                    written.item.tags.hasCover -> CoverEdit.Keep
                    else -> CoverEdit.Remove
                }
            val edit = TagEdit.from(written.item.tags).copy(cover = cover)
            return tags.writeTags(written.item.track.id, edit) == TagWriteResult.Success
        }

        private fun updateDraft(draft: BatchDraft) {
            mutableState.update {
                it.copy(
                    draft = draft,
                    errors = draft.errors(),
                    changes = draft.changes(),
                    cover = (draft.cover as? CoverEdit.Replace)?.picture ?: originalCover,
                )
            }
        }

        private fun showDialog(dialog: BatchDialog) = mutableState.update { it.copy(dialog = dialog) }

        private fun dismissDialog() = mutableState.update { it.copy(dialog = null) }

        /** A track written in this run and the front cover it had before, to put back on failure. */
        private data class Written(val item: AlbumTrack, val previousCover: EmbeddedPicture?)

        private data class Pending(val index: Int, val written: List<Written>)
    }
