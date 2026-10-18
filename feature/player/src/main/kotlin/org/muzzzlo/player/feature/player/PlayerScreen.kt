package org.muzzzlo.player.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.ArtistLinks
import org.muzzzlo.player.core.designsystem.component.BottomNav
import org.muzzzlo.player.core.designsystem.component.BottomNavItem
import org.muzzzlo.player.core.designsystem.component.CoverCard
import org.muzzzlo.player.core.designsystem.component.ProgressBar
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.component.RoundIconButtonSize
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.designsystem.component.TrackActionsMenu
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.RepeatMode
import java.util.Locale
import org.muzzzlo.player.core.designsystem.R as DesignR
import org.muzzzlo.player.core.designsystem.component.MiniPlayer as MiniPlayerView

/** The cover card of the player: 316 x 319, a 284 x 288 cover with a 16 inset (1:3). */
private const val PLAYER_COVER_ASPECT = 316f / 319f

/**
 * Full screen player (1:3): cover, title, artists, progress, controls and the bottom menu
 * (artists, albums, queue, home). Each artist of the track opens their card.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val track = state.currentTrack
    var menuOpen by remember { mutableStateOf(false) }

    ScreenFrame(modifier, spacing = MuzzzloDimens.SmallGap) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(
                icon = DesignR.drawable.ic_chevron_left,
                contentDescription = stringResource(R.string.player_back),
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            RoundIconButton(
                icon = DesignR.drawable.ic_more_horizontal,
                contentDescription = stringResource(R.string.player_track_menu),
                onClick = { menuOpen = true },
                enabled = track != null,
            )
        }
        // The cover takes the space left by the fixed parts and keeps its proportions.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CoverCard(Modifier.fillMaxWidth().aspectRatio(PLAYER_COVER_ASPECT, matchHeightConstraintsFirst = true))
        }
        Spacer(Modifier.height(MuzzzloDimens.RowGap))
        TrackTitle(state, onOpenArtist)
        Spacer(Modifier.height(MuzzzloDimens.ContentGap))
        Progress(state, viewModel::onSeek)
        Spacer(Modifier.height(MuzzzloDimens.SectionGap))
        Controls(state, viewModel)
        Spacer(Modifier.height(MuzzzloDimens.FrameInnerPadding))
        BottomNav(playerNavItems(onOpenArtists, onOpenAlbums, onOpenQueue, onHome))
    }
    if (menuOpen && track != null) {
        TrackActionsMenu(
            title = track.title,
            artists = track.artistsText,
            onAction = { action ->
                viewModel.onTrackAction(track, action)
                if (action == TrackAction.OpenQueue) onOpenQueue()
            },
            onDismiss = { menuOpen = false },
        )
    }
}

@Composable
private fun TrackTitle(
    state: PlayerState,
    onOpenArtist: (String) -> Unit,
) {
    val track = state.currentTrack
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = track?.title ?: stringResource(R.string.player_nothing_playing),
            style = MaterialTheme.typography.headlineMedium,
            color = MuzzzloColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(MuzzzloDimens.SmallGap))
        // Every artist is its own tappable name that opens their card (DESIGN.md, section 3).
        ArtistLinks(
            names = track?.artists.orEmpty(),
            onClick = { index -> track?.artists?.getOrNull(index)?.let(onOpenArtist) },
            style = MaterialTheme.typography.titleSmall,
            color = MuzzzloColors.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun Progress(
    state: PlayerState,
    onSeek: (Long) -> Unit,
) {
    val duration = state.durationMs
    Column(Modifier.fillMaxWidth()) {
        ProgressBar(
            progress = progressOf(state.positionMs, duration),
            onSeek = { share: Float -> onSeek((share * duration).toLong()) }.takeIf { duration > 0 },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimeText(formatTime(state.positionMs))
            TimeText(formatTime(duration))
        }
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MuzzzloColors.TextSecondary)
}

@Composable
private fun Controls(
    state: PlayerState,
    viewModel: PlayerViewModel,
) {
    val accent = MuzzzloTheme.accent.accent
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            icon = DesignR.drawable.ic_shuffle,
            contentDescription = stringResource(R.string.player_shuffle),
            onClick = viewModel::onToggleShuffle,
            size = RoundIconButtonSize.Large,
            iconTint = if (state.shuffleEnabled) accent else MuzzzloColors.TextSecondary,
        )
        RoundIconButton(
            icon = DesignR.drawable.ic_skip_back,
            contentDescription = stringResource(R.string.player_previous),
            onClick = viewModel::onPrevious,
            size = RoundIconButtonSize.Control,
            enabled = state.hasPrevious,
        )
        RoundIconButton(
            icon = if (state.isPlaying) DesignR.drawable.ic_pause else DesignR.drawable.ic_play,
            contentDescription = stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play),
            onClick = viewModel::onPlayPause,
            size = RoundIconButtonSize.Primary,
            accented = true,
            enabled = state.currentTrack != null,
        )
        RoundIconButton(
            icon = DesignR.drawable.ic_skip_forward,
            contentDescription = stringResource(R.string.player_next),
            onClick = viewModel::onNext,
            size = RoundIconButtonSize.Control,
            enabled = state.hasNext,
        )
        RoundIconButton(
            icon = if (state.repeatMode == RepeatMode.ONE) DesignR.drawable.ic_repeat_1 else DesignR.drawable.ic_repeat,
            contentDescription = stringResource(R.string.player_repeat),
            onClick = viewModel::onCycleRepeat,
            size = RoundIconButtonSize.Large,
            iconTint = if (state.repeatMode == RepeatMode.OFF) MuzzzloColors.TextSecondary else accent,
        )
    }
}

/** Artists, albums, queue, home, as in 27:3. */
@Composable
private fun playerNavItems(
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenQueue: () -> Unit,
    onHome: () -> Unit,
): List<BottomNavItem> =
    listOf(
        BottomNavItem(DesignR.drawable.ic_users, stringResource(R.string.player_artists), onOpenArtists),
        BottomNavItem(DesignR.drawable.ic_disc, stringResource(R.string.player_albums), onOpenAlbums),
        BottomNavItem(DesignR.drawable.ic_list_music, stringResource(R.string.player_queue), onOpenQueue),
        BottomNavItem(DesignR.drawable.ic_home, stringResource(R.string.player_home), onHome),
    )

/**
 * Mini player (15:45) under the lists, hidden when nothing is loaded. A tap opens the player,
 * a long press opens the track menu.
 */
@Composable
fun MiniPlayer(
    onOpen: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val track = state.currentTrack ?: return
    var menuOpen by remember { mutableStateOf(false) }
    if (menuOpen) {
        TrackActionsMenu(
            title = track.title,
            artists = track.artistsText,
            onAction = { action ->
                viewModel.onTrackAction(track, action)
                if (action == TrackAction.OpenQueue) onOpenQueue()
            },
            onDismiss = { menuOpen = false },
        )
    }
    MiniPlayerView(
        title = track.title,
        artists = track.artistsText,
        progress = progressOf(state.positionMs, state.durationMs),
        isPlaying = state.isPlaying,
        onClick = onOpen,
        onPlayPause = viewModel::onPlayPause,
        modifier = modifier,
        onLongClick = { menuOpen = true },
    )
}

/** Played share of the track in [0, 1]; 0 while the duration is unknown. */
internal fun progressOf(
    positionMs: Long,
    durationMs: Long,
): Float = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / MS_IN_SECOND).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / SECONDS_IN_MINUTE, totalSeconds % SECONDS_IN_MINUTE)
}

private const val MS_IN_SECOND = 1000L
private const val SECONDS_IN_MINUTE = 60L
