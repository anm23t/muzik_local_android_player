package org.muzzzlo.player.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.Track

private val audioPermission: String
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by rememberSaveable {
        mutableStateOf(
            context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(granted) { if (granted) viewModel.onScanRequested() }
    BackHandler(enabled = state.openAlbum != null) { viewModel.onAlbumClosed() }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state.openAlbum?.title ?: stringResource(R.string.library_title)) },
            actions = {
                if (granted) {
                    IconButton(onClick = viewModel::onScanRequested) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.library_rescan))
                    }
                }
            },
        )
        if (state.isScanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            !granted -> PermissionRequest { launcher.launch(audioPermission) }
            state.openAlbum != null ->
                TrackList(
                    state.albumTracks,
                    viewModel,
                ) { viewModel.onTrackClicked(state.albumTracks, it) }
            else -> LibraryTabs(state, viewModel)
        }
    }
}

@Composable
private fun LibraryTabs(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    TabRow(selectedTabIndex = tab) {
        Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.library_tab_tracks)) })
        Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.library_tab_albums)) })
    }
    if (tab == 0) {
        TrackList(state.tracks, viewModel) { viewModel.onTrackClicked(state.tracks, it) }
    } else {
        AlbumList(state.albums, viewModel::onAlbumClicked)
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.library_permission_rationale), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRequest) { Text(stringResource(R.string.library_permission_grant)) }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    viewModel: LibraryViewModel,
    onClick: (Int) -> Unit,
) {
    if (tracks.isEmpty()) return EmptyState()
    LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
            ListItem(
                modifier = Modifier.clickable { onClick(index) },
                leadingContent = { Icon(Icons.Filled.MusicNote, null) },
                headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        track.artistsText.ifEmpty { stringResource(R.string.unknown_artist) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = { TrackMenu(track, viewModel) },
            )
        }
    }
}

@Composable
private fun TrackMenu(
    track: Track,
    viewModel: LibraryViewModel,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.library_track_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_play_next)) },
                onClick = {
                    expanded = false
                    viewModel.onPlayNext(track)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_add_to_queue)) },
                onClick = {
                    expanded = false
                    viewModel.onAddToQueue(track)
                },
            )
        }
    }
}

@Composable
private fun AlbumList(
    albums: List<Album>,
    onClick: (Album) -> Unit,
) {
    if (albums.isEmpty()) return EmptyState()
    LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
        itemsIndexed(albums, key = { _, a -> a.id }) { _, album ->
            ListItem(
                modifier = Modifier.clickable { onClick(album) },
                leadingContent = { Icon(Icons.Filled.Album, null) },
                headlineContent = { Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        listOf(
                            album.artistsText.ifEmpty { stringResource(R.string.unknown_artist) },
                            pluralStringResource(R.plurals.library_track_count, album.trackCount, album.trackCount),
                        ).joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
