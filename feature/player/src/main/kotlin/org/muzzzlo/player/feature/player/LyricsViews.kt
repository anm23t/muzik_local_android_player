package org.muzzzlo.player.feature.player

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.designsystem.component.ArtistChoice
import org.muzzzlo.player.core.designsystem.component.CoverImage
import org.muzzzlo.player.core.designsystem.component.CoverSource
import org.muzzzlo.player.core.designsystem.gesture.AxisSwipe
import org.muzzzlo.player.core.designsystem.gesture.RUBBER_BAND
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloMotion
import org.muzzzlo.player.core.model.Lyrics
import org.muzzzlo.player.core.model.LyricsScroll
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.lineAt
import kotlin.math.abs

/** Lines that are not sung now are dimmed this much (task 2.0.7, no mockup, DESIGN.md, section 7). */
private const val DIMMED_ALPHA = 0.45f
private const val LINE_COLOR_MS = MuzzzloMotion.MEDIUM_MS

/** Share of the cover height a lift must cover to open the lyrics. */
private const val LIFT_SHARE = 0.15f

/** A lift faster than this many cover heights per second opens the lyrics on its own. */
private const val LIFT_FLING_SHARE_PER_SECOND = 1f

/** With lyrics to open the cover follows the finger more willingly than the rubber band of a track without them. */
private const val LIFT_FOLLOW = 0.5f

/**
 * Whether a lift of the cover by [rawOffsetPx] (up is negative) released at [velocityPxPerSec] opens the lyrics:
 * far enough, or flung up fast enough.
 */
internal fun liftOpensLyrics(
    rawOffsetPx: Float,
    velocityPxPerSec: Float,
    heightPx: Float,
): Boolean {
    if (heightPx <= 0f || rawOffsetPx >= 0f) return false
    val farEnough = -rawOffsetPx >= heightPx * LIFT_SHARE
    val flung = velocityPxPerSec < 0f && abs(velocityPxPerSec) >= heightPx * LIFT_FLING_SHARE_PER_SECOND
    return farEnough || flung
}

/**
 * The vertical swipe over the cover of the big player: down drags the player sheet as before, up lifts the cover to
 * open the lyrics ([onLift] says whether there are any). A track without lyrics only bounces, like the edge of the
 * queue. The direction is fixed by the first movement.
 */
@Stable
internal class CoverLiftState(
    private val scope: CoroutineScope,
    private val canLift: () -> Boolean,
    private val onLift: () -> Unit,
    private val onSheetDrag: (Float) -> Unit,
    private val onSheetRelease: (Float) -> Unit,
) {
    var offsetPx by mutableFloatStateOf(0f)
        private set
    private var rawPx = 0f
    private var dragsSheet: Boolean? = null

    val axis = AxisSwipe(onDrag = ::drag, onEnd = ::release)

    private fun drag(deltaPx: Float) {
        val sheet = dragsSheet ?: (deltaPx > 0f).also { dragsSheet = it }
        if (sheet) {
            onSheetDrag(deltaPx)
            return
        }
        rawPx = (rawPx + deltaPx).coerceAtMost(0f)
        offsetPx = rawPx * if (canLift()) LIFT_FOLLOW else RUBBER_BAND
    }

    private fun release(
        velocityPxPerSec: Float,
        heightPx: Float,
    ) {
        val sheet = dragsSheet
        dragsSheet = null
        if (sheet == true) {
            onSheetRelease(velocityPxPerSec)
            return
        }
        val opens = canLift() && liftOpensLyrics(rawPx, velocityPxPerSec, heightPx)
        rawPx = 0f
        if (opens) {
            offsetPx = 0f
            onLift()
        } else {
            scope.launch {
                animate(offsetPx, 0f, velocityPxPerSec, MuzzzloMotion.gesture()) { value, _ -> offsetPx = value }
            }
        }
    }
}

/**
 * The lyrics in the big player. Plain lyrics only scroll. Synchronised lyrics keep the sung line in the middle,
 * lit, with the others dimmed; a tap on a line jumps to it ([onLineTap]). When the user scrolls, the player waits
 * [LyricsScroll.USER_PAUSE_MS] before it brings the sung line back.
 */
@Composable
internal fun LyricsPane(
    lyrics: Lyrics,
    positionMs: Long,
    onLineTap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (lyrics) {
        is Lyrics.Plain -> PlainLyrics(lyrics, modifier)
        is Lyrics.Synced -> SyncedLyrics(lyrics, positionMs, onLineTap, modifier)
    }
}

@Composable
private fun PlainLyrics(
    lyrics: Lyrics.Plain,
    modifier: Modifier,
) {
    val lines = remember(lyrics) { lyrics.text.lines() }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = MuzzzloDimens.SectionGap)) {
        itemsIndexed(lines) { _, line ->
            Text(
                text = line,
                modifier = Modifier.fillMaxWidth().padding(vertical = LineGap),
                style = MaterialTheme.typography.titleMedium,
                color = MuzzzloColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun SyncedLyrics(
    lyrics: Lyrics.Synced,
    positionMs: Long,
    onLineTap: (Long) -> Unit,
    modifier: Modifier,
) {
    val current = lyrics.lineAt(positionMs)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))
    var userScrolling by remember { mutableStateOf(false) }
    var lastUserScrollEnd by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            when (it) {
                is DragInteraction.Start -> userScrolling = true
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    userScrolling = false
                    lastUserScrollEnd = SystemClock.uptimeMillis()
                }
            }
        }
    }
    LaunchedEffect(current, userScrolling, lastUserScrollEnd) {
        if (userScrolling) return@LaunchedEffect
        lastUserScrollEnd?.let { end ->
            val wait = LyricsScroll.USER_PAUSE_MS - (SystemClock.uptimeMillis() - end)
            if (wait > 0) delay(wait)
        }
        listState.animateScrollToItem(current.coerceAtLeast(0))
    }
    val lineHeight = with(LocalDensity.current) { MaterialTheme.typography.titleLarge.lineHeight.toDp() } + LineGap * 2
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Padding of half the height puts the line scrolled to the top of the content in the middle of the pane.
        val middle = (maxHeight / 2 - lineHeight / 2).coerceAtLeast(0.dp)
        LazyColumn(state = listState, contentPadding = PaddingValues(top = middle, bottom = middle)) {
            itemsIndexed(lyrics.lines) { index, line ->
                SyncedLine(
                    text = line.text.ifEmpty { stringResource(R.string.lyrics_pause) },
                    isCurrent = index == current,
                    onClick = { onLineTap(line.timeMs) },
                )
            }
        }
    }
}

@Composable
private fun SyncedLine(
    text: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val color by animateColorAsState(
        targetValue = if (isCurrent) MuzzzloColors.TextPrimary else MuzzzloColors.TextSecondary.copy(DIMMED_ALPHA),
        animationSpec = tween(LINE_COLOR_MS),
        label = "lyric line",
    )
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = LineGap),
        style = MaterialTheme.typography.titleLarge,
        color = color,
    )
}

private val LineGap = MuzzzloDimens.SmallGap

/**
 * The title row of the lyrics mode (task 2.0.7, no mockup): the cover as a miniature, a tap on it brings the big
 * cover back; the title and the artists start next to it.
 */
@Composable
internal fun CompactTitle(
    state: PlayerState,
    loadArtists: suspend (Long) -> List<ArtistChoice>,
    onOpenArtist: (Long) -> Unit,
    onCoverClick: () -> Unit,
) {
    val track = state.currentTrack
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val description = stringResource(R.string.player_show_cover)
        CoverImage(
            source = track?.let { CoverSource.Track(it.id) },
            modifier =
                Modifier
                    .size(MuzzzloDimens.ArtistPhoto)
                    .clip(MuzzzloDimens.TileShape)
                    .clickable(onClickLabel = description, onClick = onCoverClick),
            placeholderShape = MuzzzloDimens.TileShape,
        )
        TrackTitle(state, loadArtists, onOpenArtist, Modifier.weight(1f), compact = true)
    }
}
