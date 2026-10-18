package org.muzzzlo.player.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.designsystem.component.TrackActionsMenu
import org.muzzzlo.player.core.designsystem.component.TrackMenuButton
import org.muzzzlo.player.core.model.Track

private val RowHeight = 64.dp

/** Playback queue: tap to jump, drag the handle to reorder, cross to remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onBack: () -> Unit,
    onEditTags: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.queue_title)) },
            navigationIcon = {
                IconButton(onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.player_back))
                }
            },
        )
        if (state.queue.isEmpty()) {
            Text(stringResource(R.string.queue_empty), Modifier.padding(24.dp))
        } else {
            QueueList(state.queue, state.currentIndex, viewModel, onEditTags)
        }
    }
}

@Composable
private fun QueueList(
    queue: List<Track>,
    currentIndex: Int,
    viewModel: PlayerViewModel,
    onEditTags: (Long) -> Unit,
) {
    val rowHeightPx = with(LocalDensity.current) { RowHeight.toPx() }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    menuTrack?.let { track ->
        TrackActionsMenu(
            title = track.title,
            artists = track.artistsText,
            onAction = { action ->
                viewModel.onTrackAction(track, action)
                if (action == TrackAction.EditTags) onEditTags(track.id)
            },
            onDismiss = { menuTrack = null },
            // The queue is already open.
            actions = TrackAction.entries - TrackAction.OpenQueue,
        )
    }

    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(queue, key = { index, track -> "$index-${track.id}" }) { index, track ->
            val isDragged = index == draggingIndex
            Surface(
                color = if (index == currentIndex) MaterialTheme.colorScheme.primaryContainer else Color.Unspecified,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(RowHeight)
                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                        .clickable { viewModel.onQueueItemClicked(index) },
            ) {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            track.artistsText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TrackMenuButton(onClick = { menuTrack = track })
                    IconButton({ viewModel.onQueueItemRemoved(index) }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.queue_remove))
                    }
                    Icon(
                        Icons.Filled.DragHandle,
                        stringResource(R.string.queue_reorder),
                        Modifier
                            .padding(horizontal = 12.dp)
                            .pointerInput(index, queue.size) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingIndex = index
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                    },
                                    onDragEnd = {
                                        val target = dragTargetIndex(index, dragOffset, rowHeightPx, queue.size)
                                        if (target != index) viewModel.onQueueItemMoved(index, target)
                                        draggingIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingIndex = -1
                                        dragOffset = 0f
                                    },
                                )
                            },
                    )
                }
            }
        }
    }
}
