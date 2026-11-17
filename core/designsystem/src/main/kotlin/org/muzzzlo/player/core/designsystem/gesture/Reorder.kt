package org.muzzzlo.player.core.designsystem.gesture

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloMotion

/** The held row grows this much, so it looks lifted above the others. */
private const val LIFT_SCALE = 1.03f
private val LiftElevation = 8.dp

/** Within this distance of an edge the list scrolls under a held row, up to [MAX_AUTO_SCROLL_DP] per frame. */
private val AutoScrollEdge = 56.dp
private const val MAX_AUTO_SCROLL_DP = 12f

/** How long a dropped row waits for the list to show the move before it goes back. */
private const val DROP_WAIT_MS = 1_000L

/**
 * Drag to reorder in a lazy list (queue, playlist; task 2.0.9). While a row is held the rows between its old and its
 * new place step aside ([neighborShift]), the list scrolls near its edges ([autoScrollStep]) and the held row is
 * lifted. On release [onMove] is called; once the list shows the row at its new place, the row glides into its slot
 * on a spring. The list tells the state its [keys] on every composition.
 */
@Stable
class ReorderState internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: State<(Int, Int) -> Unit>,
) {
    /** Keys of the rows in their current order; set by the list on every composition. */
    var keys: List<Any> = emptyList()

    /** Distance between the tops of two rows, row height plus the gap. */
    var stepPx by mutableFloatStateOf(0f)

    internal var autoScrollEdgePx = 0f
    internal var maxAutoScrollPx = 0f

    var draggedKey by mutableStateOf<Any?>(null)
        private set
    private var fromIndex by mutableIntStateOf(-1)
    private var offsetPx by mutableFloatStateOf(0f)
    private var released = false
    private var job: Job? = null

    /** True from the start of a drag until the dropped row has settled: rows must not animate their places then. */
    val isActive: Boolean get() = draggedKey != null

    private val targetIndex by derivedStateOf {
        if (fromIndex < 0) -1 else dragTargetIndex(fromIndex, offsetPx, stepPx, keys.size)
    }

    /** Where the held row stands in the list now: its old place until the list shows the move. */
    private fun currentIndexOfDragged(): Int = draggedKey?.let { keys.indexOf(it) } ?: -1

    /** The list already shows the row at its new place. */
    internal val hasMoved: Boolean get() = isActive && currentIndexOfDragged() != fromIndex

    /** How far the row at [index] steps aside; read in composition, changes only when the target changes. */
    internal fun shiftOf(
        key: Any,
        index: Int,
    ): Float = if (key == draggedKey || hasMoved) 0f else neighborShift(index, fromIndex, targetIndex, stepPx)

    /** Offset of the held row from its layout place; read in the draw phase. */
    internal fun draggedOffset(): Float = offsetPx - (currentIndexOfDragged() - fromIndex) * stepPx

    internal fun start(key: Any) {
        val index = keys.indexOf(key)
        if (index < 0) return
        job?.cancel()
        draggedKey = key
        fromIndex = index
        offsetPx = 0f
        released = false
        job = scope.launch { autoScroll() }
    }

    internal fun dragBy(deltaPx: Float) {
        if (!released) offsetPx += deltaPx
    }

    internal fun release() {
        if (draggedKey == null || released) return
        released = true
        job?.cancel()
        val from = fromIndex
        val to = targetIndex
        val firstIndex = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        job =
            scope.launch {
                if (to != from && to >= 0) {
                    onMove.value(from, to)
                    // The row glides from where it was let go into its new slot once the list shows it there.
                    val shown = withTimeoutOrNull(DROP_WAIT_MS) { awaitFrameWhere { currentIndexOfDragged() == to } }
                    // A lazy list keeps its first visible row by key: when that row was the one moved, the list
                    // would scroll after it and hide the rows above. The list stays where it was instead.
                    if (listState.firstVisibleItemIndex != firstIndex) listState.scrollToItem(firstIndex, firstOffset)
                    settle(if (shown == null) 0f else (to - from) * stepPx)
                } else {
                    settle(0f)
                }
                draggedKey = null
                fromIndex = -1
                offsetPx = 0f
            }
    }

    private suspend fun settle(target: Float) {
        animate(offsetPx, target, animationSpec = MuzzzloMotion.gesture()) { value, _ -> offsetPx = value }
    }

    private suspend fun awaitFrameWhere(condition: () -> Boolean) {
        while (!condition()) withFrameNanos { }
    }

    private suspend fun autoScroll() {
        while (true) {
            withFrameNanos { }
            val layout = listState.layoutInfo
            val row = layout.visibleItemsInfo.firstOrNull { it.key == draggedKey } ?: continue
            val top = row.offset + draggedOffset()
            val step =
                autoScrollStep(
                    rowTopPx = top,
                    rowBottomPx = top + row.size,
                    viewportStartPx = layout.viewportStartOffset.toFloat(),
                    viewportEndPx = layout.viewportEndOffset.toFloat(),
                    edgePx = autoScrollEdgePx,
                    maxStepPx = maxAutoScrollPx,
                )
            // The row stays under the finger: what the list scrolled is added to the drag.
            if (step != 0f) offsetPx += listState.scrollBy(step)
        }
    }
}

/** A [ReorderState] for [listState]; [onMove] gets the old and the new index of a dropped row. */
@Composable
fun rememberReorderState(
    listState: LazyListState,
    onMove: (Int, Int) -> Unit,
): ReorderState {
    val scope = rememberCoroutineScope()
    val move = rememberUpdatedState(onMove)
    val density = LocalDensity.current
    return remember(listState) { ReorderState(listState, scope, move) }.apply {
        autoScrollEdgePx = with(density) { AutoScrollEdge.toPx() }
        maxAutoScrollPx = with(density) { MAX_AUTO_SCROLL_DP.dp.toPx() }
    }
}

/**
 * The modifier of a row of a reorderable list: it steps aside for a held row, is lifted while held itself, and
 * animates its place when rows come, go or move (`animateItem`), except during a drag, where the state moves it.
 * A held row stays composed even when it is scrolled out of sight, so the drag goes on.
 */
@Composable
fun LazyItemScope.reorderableItem(
    state: ReorderState,
    key: Any,
    index: Int,
): Modifier {
    val isDragged = key == state.draggedKey
    val pinnable = LocalPinnableContainer.current
    DisposableEffect(isDragged, pinnable) {
        val handle = if (isDragged) pinnable?.pin() else null
        onDispose { handle?.release() }
    }
    val shift by animateFloatAsState(
        targetValue = state.shiftOf(key, index),
        animationSpec = if (state.hasMoved) snap() else MuzzzloMotion.gesture(),
        label = "reorder shift",
    )
    val lift by animateFloatAsState(if (isDragged) 1f else 0f, MuzzzloMotion.gesture(), label = "reorder lift")
    val elevationPx = with(LocalDensity.current) { LiftElevation.toPx() }
    val radiusPx = with(LocalDensity.current) { MuzzzloDimens.RowRadius.toPx() }
    return Modifier
        .animateItem(
            fadeInSpec = MuzzzloMotion.ItemFade,
            placementSpec = if (state.isActive) null else MuzzzloMotion.ItemPlacement,
            fadeOutSpec = MuzzzloMotion.ItemFade,
        ).zIndex(if (isDragged) 1f else 0f)
        .graphicsLayer {
            translationY = if (isDragged) state.draggedOffset() else shift
            val scale = 1f + (LIFT_SCALE - 1f) * lift
            scaleX = scale
            scaleY = scale
            shadowElevation = elevationPx * lift
        }.drawBehind {
            // A transparent row (the queue over the live background) would show the rows it passes over.
            if (lift > 0f) {
                drawRoundRect(MuzzzloColors.SurfaceElevated.copy(alpha = lift), cornerRadius = CornerRadius(radiusPx))
            }
        }
}

/** The drag handle of the row with [key]: a drag on it holds the row. */
fun Modifier.reorderHandle(
    state: ReorderState,
    key: Any,
): Modifier =
    pointerInput(state, key) {
        detectDragGestures(
            onDragStart = { state.start(key) },
            onDrag = { change, amount ->
                change.consume()
                state.dragBy(amount.y)
            },
            onDragEnd = state::release,
            onDragCancel = state::release,
        )
    }
