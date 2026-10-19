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
import org.muzzzlo.player.core.model.ArtistEdit
import org.muzzzlo.player.core.model.ArtistEditToken
import org.muzzzlo.player.core.model.ArtistProfileRepository
import org.muzzzlo.player.core.model.ArtistSplitter
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.NameNormalizer
import org.muzzzlo.player.core.model.TagEdit
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TagWriteResult
import org.muzzzlo.player.core.model.TrackTagRepository
import org.muzzzlo.player.core.model.TrackTags
import org.muzzzlo.player.core.tags.WritePermissionRequest
import javax.inject.Inject

/** Navigation argument: id of the artist whose profile is edited. */
const val PROFILE_ARTIST_ID_ARG = "artistId"

/** A track that mentions the artist, with the tags read from its file and the tags it should get. */
internal data class ProfileTrack(
    val id: Long,
    val title: String,
    val uri: String,
    val tags: TrackTags,
    val edit: TagEdit,
)

/** Dialog over the profile editor. */
sealed interface ProfileDialog {
    data object ConfirmWrite : ProfileDialog

    data object Discard : ProfileDialog

    /** The write stopped and the written tracks were restored; shown after [ArtistProfileState.progress] ends. */
    data class Failed(val failure: AlbumWriteFailure) : ProfileDialog
}

/**
 * State of the artist profile editor.
 *
 * @property originalName the name the artist has now.
 * @property values the names the artist should have after saving: one is a rename (or a merge when the artist
 *   exists), several split the artist.
 * @property merging names in [values] that belong to other artists already; their tracks are combined.
 * @property trackCount files that will be written.
 * @property skipped tracks whose files cannot be read; they are left alone.
 * @property progress files written so far while a write runs, null otherwise.
 */
data class ArtistProfileState(
    val originalName: String = "",
    val values: List<String> = emptyList(),
    val merging: List<String> = emptyList(),
    val trackCount: Int = 0,
    val skipped: Int = 0,
    val loaded: Boolean = false,
    val saved: Boolean = false,
    val savedArtistId: Long? = null,
    val notFound: Boolean = false,
    val dialog: ProfileDialog? = null,
    val progress: Int? = null,
    val permissionRequest: WritePermissionRequest? = null,
    val done: Boolean = false,
) {
    /** Names to save: trimmed, without blanks and repeats. */
    val cleanValues: List<String>
        get() = values.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy(NameNormalizer::key)

    val isSplit: Boolean get() = cleanValues.size > 1
    val writing: Boolean get() = progress != null
    val changed: Boolean get() = cleanValues.isNotEmpty() && cleanValues != listOf(originalName)
    val canSave: Boolean get() = loaded && changed && trackCount > 0 && !writing
}

/**
 * Edits an artist profile: renames the artist, merges them into another artist or splits them into several, by
 * writing the new names into the artist tags of every file that mentions them (ARCHITECTURE.md, section 4). The
 * files are the source of truth; the database follows through [ArtistProfileRepository]. One permission request
 * covers all files. When a file fails, the files already written get their old tags back, like the album editor.
 */
@HiltViewModel
class ArtistProfileViewModel
    @Inject
    constructor(
        private val tags: TrackTagRepository,
        private val library: LibraryRepository,
        private val profiles: ArtistProfileRepository,
        private val permissions: WritePermissions,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val artistId: Long = checkNotNull(savedState[PROFILE_ARTIST_ID_ARG])
        private val mutableState = MutableStateFlow(ArtistProfileState())
        val state: StateFlow<ArtistProfileState> = mutableState.asStateFlow()

        private var items: List<ProfileTrack> = emptyList()
        private var token: ArtistEditToken? = null
        private var pending: Pending? = null
        private var askedAt = -1

        init {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            val name = profiles.artistName(artistId)
            if (name == null) {
                mutableState.update { it.copy(loaded = true, notFound = true) }
                return
            }
            val ids = profiles.affectedTrackIds(artistId)
            val known = library.tracksByIds(ids).associateBy { it.id }
            val readable =
                ids.mapNotNull { id ->
                    (tags.readTags(id) as? TagReadResult.Success)?.let { id to it.fileTags.tags }
                }
            mutableState.update {
                it.copy(
                    originalName = name,
                    values = listOf(name),
                    skipped = ids.size - readable.size,
                    loaded = true,
                )
            }
            items =
                readable.map { (id, trackTags) ->
                    val track = known[id]
                    ProfileTrack(id, track?.title.orEmpty(), track?.uri.orEmpty(), trackTags, TagEdit.from(trackTags))
                }
            recount()
        }

        fun onValuesChange(values: List<String>) {
            mutableState.update { it.copy(values = values) }
            recount()
            viewModelScope.launch { updateMerging() }
        }

        fun onSave() {
            if (state.value.canSave) showDialog(ProfileDialog.ConfirmWrite)
        }

        fun onConfirmWrite() {
            dismissDialog()
            viewModelScope.launch {
                token = profiles.begin(artistId, currentEdit())
                askPermission(Pending(0, emptyList()))
            }
        }

        fun onBack() {
            val current = state.value
            when {
                current.writing -> Unit
                !current.changed -> mutableState.update { it.copy(done = true) }
                else -> showDialog(ProfileDialog.Discard)
            }
        }

        fun onDiscard() = mutableState.update { it.copy(dialog = null, done = true) }

        fun onDismissDialog() = dismissDialog()

        /** The answer to the request the screen showed for [ArtistProfileState.permissionRequest]. */
        fun onPermissionResult(granted: Boolean) {
            mutableState.update { it.copy(permissionRequest = null) }
            val waiting = pending ?: return
            pending = null
            viewModelScope.launch {
                if (granted) writeFrom(waiting.index, waiting.written) else fail(null, null, waiting.written)
            }
        }

        private fun currentEdit() = ArtistEdit(state.value.originalName, state.value.cleanValues)

        /** Works out which files change for the current names and how many there are. */
        private fun recount() {
            val values = state.value.cleanValues
            if (values.isEmpty()) {
                mutableState.update { it.copy(trackCount = 0) }
                return
            }
            val edit = ArtistEdit(state.value.originalName, values)
            items = items.map { it.copy(edit = editFor(it.tags, edit)) }
            mutableState.update { it.copy(trackCount = items.count { item -> item.edit != TagEdit.from(item.tags) }) }
        }

        private fun editFor(
            current: TrackTags,
            edit: ArtistEdit,
        ): TagEdit {
            val base = TagEdit.from(current)
            val artists = ArtistSplitter.split(current.artists)
            val albumArtists = ArtistSplitter.split(current.albumArtists)
            return base.copy(
                artists = if (edit.changes(artists)) edit.applyTo(artists) else current.artists,
                albumArtists = if (edit.changes(albumArtists)) edit.applyTo(albumArtists) else current.albumArtists,
            )
        }

        private suspend fun updateMerging() {
            val own = NameNormalizer.key(state.value.originalName)
            val merging =
                state.value.cleanValues.filter { name ->
                    NameNormalizer.key(name) != own && library.artistId(name)?.let { it != artistId } == true
                }
            mutableState.update { it.copy(merging = merging) }
        }

        private fun toWrite() = items.filter { it.edit != TagEdit.from(it.tags) }

        private suspend fun askPermission(waiting: Pending) {
            pending = waiting
            mutableState.update { it.copy(progress = waiting.index) }
            val uris = toWrite().drop(waiting.index).map { it.uri }
            when (val request = permissions.requestFor(uris)) {
                WritePermissionRequest.NotNeeded -> onPermissionResult(granted = true)
                else -> mutableState.update { it.copy(permissionRequest = request) }
            }
        }

        /**
         * Writes the files from [start] on, one at a time. A file that needs the permission again (Android 10 asks
         * per file) pauses the write for a new request. The first failure stops it and restores the files written.
         */
        private suspend fun writeFrom(
            start: Int,
            written: List<ProfileTrack>,
        ) {
            val done = written.toMutableList()
            val list = toWrite()
            for (index in start until list.size) {
                mutableState.update { it.copy(progress = index) }
                val item = list[index]
                val result = tags.writeTags(item.id, item.edit)
                when {
                    result == TagWriteResult.Success -> done += item
                    result == TagWriteResult.NeedsPermission && index != askedAt -> {
                        askedAt = index
                        askPermission(Pending(index, done))
                        return
                    }
                    else -> {
                        fail(item.title, result, done)
                        return
                    }
                }
            }
            token?.let { profiles.commit(it) }
            // A rename or merge leaves one artist to open; a split has none.
            val openId = state.value.cleanValues.singleOrNull()?.let { library.artistId(it) }
            mutableState.update { it.copy(progress = null, saved = true, savedArtistId = openId) }
        }

        /** Gives the [written] files their old tags back, newest first, and shows what happened. */
        private suspend fun fail(
            title: String?,
            result: TagWriteResult?,
            written: List<ProfileTrack>,
        ) {
            val notRestored =
                written.asReversed()
                    .filterNot { tags.writeTags(it.id, TagEdit.from(it.tags)) == TagWriteResult.Success }
                    .map { it.title }
            token?.let { profiles.rollback(it) }
            val failure = AlbumWriteFailure(title, result, restored = notRestored.isEmpty(), notRestored = notRestored)
            mutableState.update { it.copy(progress = null, dialog = ProfileDialog.Failed(failure)) }
        }

        private fun showDialog(dialog: ProfileDialog) = mutableState.update { it.copy(dialog = dialog) }

        private fun dismissDialog() = mutableState.update { it.copy(dialog = null) }

        private data class Pending(val index: Int, val written: List<ProfileTrack>)
    }
