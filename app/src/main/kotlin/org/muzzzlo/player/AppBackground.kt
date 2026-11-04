package org.muzzzlo.player

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import org.muzzzlo.player.core.designsystem.component.BlobMotion
import org.muzzzlo.player.core.designsystem.component.LiveBackground
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloMotion

/** Track changes and the turn between colored and black take this long (task 2.0.8). */
private const val FADE_MS = MuzzzloMotion.BACKGROUND_MS

/** The veil over the spots: lists are darker than the player, where the cover is the main thing. */
private const val SCREEN_DIM = 0.55f
private const val PLAYER_DIM = 0.3f
private const val NANOS_PER_SECOND = 1_000_000_000f

/**
 * The shared moment of the live background, so the layer under the screens and the one under the player draw the
 * same spots and the player sheet slides over one picture.
 */
@Stable
internal class LiveBackgroundClock {
    var timeSec by mutableFloatStateOf(0f)
        internal set
}

/**
 * Runs the clock of the live background while [enabled]: not while the app is not visible (stopped) and not when the
 * system "Remove animations" is on, then the colored picture stands still.
 */
@Composable
internal fun rememberLiveBackgroundClock(enabled: Boolean): LiveBackgroundClock {
    val clock = remember { LiveBackgroundClock() }
    val context = LocalContext.current
    var animationsOn by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        // A setting of the developer options; 0 also follows the accessibility switch "Remove animations".
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        animationsOn = scale > 0f
        onPauseOrDispose {}
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = enabled && animationsOn && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0) clock.timeSec += (now - last) / NANOS_PER_SECOND
                last = now
            }
        }
    }
    return clock
}

/** The palette faded slot by slot, so a new track blends into the old colors instead of jumping. */
@Composable
internal fun animatedPalette(palette: List<Color>?): List<State<Color>> {
    val last = remember { mutableStateOf(List(BlobMotion.MAX_BLOBS) { MuzzzloColors.Background }) }
    if (palette != null && palette.isNotEmpty()) last.value = List(BlobMotion.MAX_BLOBS) { palette[it % palette.size] }
    return last.value.mapIndexed { i, color ->
        animateColorAsState(color, tween(FADE_MS), label = "background color $i")
    }
}

/** One layer of the live background: under the screens ([forPlayer] false) or under the player sheet. */
@Composable
internal fun AppBackgroundLayer(
    colored: Boolean,
    colors: List<State<Color>>,
    clock: LiveBackgroundClock,
    forPlayer: Boolean,
    modifier: Modifier = Modifier,
) {
    val amount by animateFloatAsState(if (colored) 1f else 0f, tween(FADE_MS), label = "background amount")
    LiveBackground(
        colors = colors.map { it.value },
        timeSec = { clock.timeSec },
        colorAmount = { amount },
        dim = if (forPlayer) PLAYER_DIM else SCREEN_DIM,
        modifier = modifier.fillMaxSize(),
    )
}
