package org.muzzzlo.player.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.designsystem.component.ArtistChoice
import org.muzzzlo.player.core.designsystem.component.ArtistLinks
import org.muzzzlo.player.core.designsystem.component.ArtistPickerMenu
import org.muzzzlo.player.core.designsystem.component.ArtistTap
import org.muzzzlo.player.core.designsystem.component.BottomNav
import org.muzzzlo.player.core.designsystem.component.BottomNavItem
import org.muzzzlo.player.core.designsystem.component.CoverCard
import org.muzzzlo.player.core.designsystem.component.CoverImage
import org.muzzzlo.player.core.designsystem.component.CoverSource
import org.muzzzlo.player.core.designsystem.component.MarqueeText
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.component.RoundIconButtonSize
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.designsystem.component.TrackActionsMenu
import org.muzzzlo.player.core.designsystem.component.WaveformBar
import org.muzzzlo.player.core.designsystem.gesture.AxisSwipe
import org.muzzzlo.player.core.designsystem.gesture.swipeGestures
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloMotion
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme
import org.muzzzlo.player.core.model.PlaybackProblem
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.RepeatMode
import org.muzzzlo.player.core.model.Waveform
import org.muzzzlo.player.core.model.WaveformPeaks
import java.util.Locale
import org.muzzzlo.player.core.designsystem.R as DesignR
import org.muzzzlo.player.core.designsystem.component.MiniPlayer as MiniPlayerView

/** The cover is one shared element in the cover and the lyrics layouts, so it flows from one place to the other. */
private const val SHARED_COVER_KEY = "player cover"

/** The cover card of the player: 316 x 319, a 284 x 288 cover with a 16 inset (1:3). */
private const val PLAYER_COVER_ASPECT = 316f / 319f

/**
 * Full screen player (1:3): cover, title, artists, progress, controls and the bottom menu
 * (artists, albums, queue, home). Each artist of the track opens their card.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onHome: () -> Unit,
    onEditTags: (Long) -> Unit,
    onSheetDrag: (Float) -> Unit,
    onSheetRelease: (Float) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
    waveformViewModel: WaveformViewModel = hiltViewModel(),
    sleepTimerViewModel: SleepTimerViewModel = hiltViewModel(),
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lyricsState by lyricsViewModel.state.collectAsStateWithLifecycle()
    val peaks by waveformViewModel.peaks.collectAsStateWithLifecycle()
    val sleepTimer by sleepTimerViewModel.state.collectAsStateWithLifecycle()
    var sleepDialogOpen by remember { mutableStateOf(false) }
    val track = state.currentTrack
    var menuOpen by remember { mutableStateOf(false) }
    val coverSwipe = rememberCoverSwipe(viewModel) { state }
    val closeSwipe =
        remember {
            AxisSwipe(onDrag = { onSheetDrag(it) }, onEnd = {
                    velocity,
                    _,
                ->
                onSheetRelease(velocity)
            })
        }

    val coverLift = rememberCoverLift(lyricsViewModel, onSheetDrag, onSheetRelease)
    val lyrics = lyricsState.lyrics

    ScreenFrame(modifier.swipeGestures(vertical = closeSwipe), spacing = MuzzzloDimens.SmallGap) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(
                icon = DesignR.drawable.ic_chevron_left,
                contentDescription = stringResource(R.string.player_back),
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            // Without a mockup (task 2.0.7): the lyrics button left of the menu, lit while the lyrics are shown.
            RoundIconButton(
                icon = DesignR.drawable.ic_text,
                contentDescription = stringResource(R.string.player_lyrics),
                onClick = lyricsViewModel::onToggle,
                accented = lyricsState.showLyrics,
                enabled = lyricsState.available || lyricsState.showLyrics,
            )
            Spacer(Modifier.width(MuzzzloDimens.ContentGap))
            RoundIconButton(
                icon = DesignR.drawable.ic_more_horizontal,
                contentDescription = stringResource(R.string.player_track_menu),
                onClick = { menuOpen = true },
                enabled = track != null,
            )
        }
        val sleepBadge: @Composable ColumnScope.() -> Unit = {
            val timer = sleepTimer
            if (timer == null) {
                Spacer(Modifier.height(MuzzzloDimens.RowGap))
            } else {
                SleepTimerBadge(
                    timer,
                    onClick = { sleepDialogOpen = true },
                    Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
        // The cover flows into the miniature next to the title and back (owner's remark after the phone check).
        SharedTransitionLayout(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = lyricsState.showLyrics,
                transitionSpec = {
                    fadeIn(MuzzzloMotion.timed(MuzzzloMotion.MEDIUM_MS)) togetherWith
                        fadeOut(MuzzzloMotion.timed(MuzzzloMotion.SHORT_MS))
                },
                label = "lyrics mode",
            ) { showLyrics ->
                val sharedCover =
                    Modifier.sharedElement(
                        rememberSharedContentState(SHARED_COVER_KEY),
                        animatedVisibilityScope = this,
                        boundsTransform = { _, _ -> MuzzzloMotion.gesture() },
                    )
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap)) {
                    if (showLyrics) {
                        // The lyrics take the place of the cover and the title; the cover shrinks next to the title.
                        Spacer(Modifier.height(MuzzzloDimens.SmallGap))
                        CompactTitle(
                            state,
                            viewModel::artistChoices,
                            onOpenArtist,
                            onCoverClick = lyricsViewModel::onToggle,
                            coverModifier = sharedCover,
                        )
                        Box(Modifier.weight(1f).fillMaxWidth().swipeGestures(horizontal = coverSwipe.axis)) {
                            if (lyrics != null) {
                                LyricsPane(
                                    lyrics = lyrics,
                                    positionMs = state.positionMs,
                                    onLineTap = lyricsViewModel::onLineTapped,
                                    modifier = Modifier.graphicsLayer { translationX = coverSwipe.offsetPx },
                                )
                            }
                        }
                        sleepBadge()
                    } else {
                        // The cover takes the space left by the fixed parts and keeps its proportions.
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .swipeGestures(horizontal = coverSwipe.axis, vertical = coverLift.axis),
                            contentAlignment = Alignment.Center,
                        ) {
                            CoverCard(
                                Modifier
                                    .graphicsLayer {
                                        translationX = coverSwipe.offsetPx
                                        translationY = coverLift.offsetPx
                                    }.fillMaxWidth()
                                    .aspectRatio(PLAYER_COVER_ASPECT, matchHeightConstraintsFirst = true),
                                cover = {
                                    CoverImage(
                                        track?.let { CoverSource.Track(it.id) },
                                        sharedCover.then(it),
                                        MuzzzloDimens.MiniPlayerShape,
                                        fadeIn = true,
                                    )
                                },
                            )
                        }
                        sleepBadge()
                        TrackTitle(state, viewModel::artistChoices, onOpenArtist)
                    }
                }
            }
        }
        Spacer(Modifier.height(MuzzzloDimens.ContentGap))
        Progress(state, peaks, viewModel::onSeek)
        Spacer(Modifier.height(MuzzzloDimens.SectionGap))
        Controls(state, viewModel)
        Spacer(Modifier.height(MuzzzloDimens.FrameInnerPadding))
        BottomNav(playerNavItems(onOpenArtists, onOpenAlbums, onOpenQueue, onHome))
    }
    if (sleepDialogOpen) {
        SleepTimerDialog(
            timer = sleepTimer,
            onMinutes = sleepTimerViewModel::onMinutesChosen,
            onEndOfTrack = sleepTimerViewModel::onEndOfTrackChosen,
            onCancel = sleepTimerViewModel::onCancel,
            onDismiss = { sleepDialogOpen = false },
        )
    }
    if (menuOpen && track != null) {
        TrackActionsMenu(
            title = track.title,
            artists = track.artistsText,
            onAction = { action ->
                viewModel.onTrackAction(track, action)
                when (action) {
                    TrackAction.EditTags -> onEditTags(track.id)
                    TrackAction.SleepTimer -> sleepDialogOpen = true
                    else -> Unit
                }
            },
            onDismiss = { menuOpen = false },
            actions = TrackAction.Player,
            loadArtists = { viewModel.artistChoices(track.id) },
            onOpenArtist = onOpenArtist,
            addToPlaylist = remember(track.id) { viewModel.addToPlaylist(track.id) },
            marks = remember(track.id) { viewModel.trackMarks(track.id) },
            shareUri = track.uri,
            albumId = track.albumId,
            onOpenAlbum = onOpenAlbum,
            trackId = track.id,
        )
    }
}

/** What the title shows of a track: a new rating of the same track is not a change to fade. */
private data class ShownTrack(
    val id: Long,
    val title: String,
    val artists: List<String>,
)

@Composable
internal fun TrackTitle(
    state: PlayerState,
    loadArtists: suspend (Long) -> List<ArtistChoice>,
    onOpenArtist: (Long) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val track = state.currentTrack
    val scope = rememberCoroutineScope()
    var choices by remember { mutableStateOf<List<ArtistChoice>?>(null) }
    choices?.let { artists ->
        ArtistPickerMenu(artists = artists, onPick = { onOpenArtist(it.id) }, onDismiss = { choices = null })
    }
    val textAlign = if (compact) TextAlign.Start else TextAlign.Center
    val titleStyle = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium
    val alignment = if (compact) Alignment.Start else Alignment.CenterHorizontally
    Column(modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        // A new track fades in over the old one (task 2.0.9); a new rating of the same track does not.
        Crossfade(
            targetState = track?.let { ShownTrack(it.id, it.title, it.artists) },
            animationSpec = MuzzzloMotion.timed<Float>(MuzzzloMotion.SHORT_MS),
            label = "player track",
        ) { shown ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
                // One line each, so the cover keeps its size whatever the title length is; long lines scroll.
                MarqueeText(
                    text = shown?.title ?: stringResource(R.string.player_nothing_playing),
                    style = titleStyle,
                    color = MuzzzloColors.TextPrimary,
                    textAlign = textAlign,
                )
                Spacer(Modifier.height(MuzzzloDimens.SmallGap))
                // A tap on the artists opens the card of the only artist or the picker of several (DESIGN.md, 3).
                ArtistLinks(
                    names = shown?.artists.orEmpty(),
                    onClick = {
                        if (shown != null) {
                            scope.launch {
                                when (val tap = ArtistTap.of(loadArtists(shown.id))) {
                                    is ArtistTap.Open -> onOpenArtist(tap.artist.id)
                                    is ArtistTap.Choose -> choices = tap.artists
                                    ArtistTap.Nothing -> Unit
                                }
                            }
                        }
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MuzzzloColors.TextSecondary,
                    textAlign = textAlign,
                    marquee = true,
                )
            }
        }
        state.problem?.let { problem ->
            Spacer(Modifier.height(MuzzzloDimens.SmallGap))
            Text(
                text = stringResource(problem.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MuzzzloTheme.accent.accent,
                textAlign = textAlign,
            )
        }
    }
}

private fun PlaybackProblem.messageRes(): Int =
    when (this) {
        PlaybackProblem.FILE_MISSING -> R.string.player_problem_missing
        PlaybackProblem.UNSUPPORTED -> R.string.player_problem_unsupported
        PlaybackProblem.OTHER -> R.string.player_problem_other
    }

@Composable
private fun Progress(
    state: PlayerState,
    peaks: WaveformPeaks?,
    onSeek: (Long) -> Unit,
) {
    val duration = state.durationMs
    val levels = remember(peaks) { peaks?.let { p -> { count: Int -> Waveform.bars(p, count) } } }
    Column(Modifier.fillMaxWidth()) {
        WaveformBar(
            progress = progressOf(state.positionMs, duration),
            levels = levels,
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
 * a long press opens the track menu, a swipe sideways switches the track and a swipe up pulls the
 * player up ([onSheetDrag] and [onSheetRelease] drive it).
 */
@Composable
fun MiniPlayer(
    onOpen: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onEditTags: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onSheetDrag: (Float) -> Unit,
    onSheetRelease: (Float) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val coverSwipe = rememberCoverSwipe(viewModel) { state }
    val openSwipe =
        remember {
            AxisSwipe(onDrag = { onSheetDrag(it) }, onEnd = {
                    velocity,
                    _,
                ->
                onSheetRelease(velocity)
            })
        }
    val track = state.currentTrack ?: return
    var menuOpen by remember { mutableStateOf(false) }
    if (menuOpen) {
        TrackActionsMenu(
            title = track.title,
            artists = track.artistsText,
            onAction = { action ->
                viewModel.onTrackAction(track, action)
                when (action) {
                    TrackAction.EditTags -> onEditTags(track.id)
                    else -> Unit
                }
            },
            onDismiss = { menuOpen = false },
            loadArtists = { viewModel.artistChoices(track.id) },
            onOpenArtist = onOpenArtist,
            addToPlaylist = remember(track.id) { viewModel.addToPlaylist(track.id) },
            marks = remember(track.id) { viewModel.trackMarks(track.id) },
            shareUri = track.uri,
            albumId = track.albumId,
            onOpenAlbum = onOpenAlbum,
            trackId = track.id,
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
        cover = { CoverImage(CoverSource.Track(track.id), it, fadeIn = true) },
        gestures = Modifier.swipeGestures(horizontal = coverSwipe.axis, vertical = openSwipe),
        coverOffsetPx = { coverSwipe.offsetPx },
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
