package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Pause before a long line starts to scroll and between the repeats. */
private const val MARQUEE_DELAY_MS = 2_000

/** Scroll speed of a long line, slow enough to read. */
private val MarqueeSpeed = 30.dp

/**
 * Scrolling-line modifier for texts that must stay on one line (DESIGN.md, section 5): after a pause a line that does
 * not fit slowly scrolls, a short one stays still. Use with `maxLines = 1` and [TextOverflow.Clip].
 */
fun Modifier.slowMarquee(): Modifier =
    basicMarquee(
        iterations = Int.MAX_VALUE,
        initialDelayMillis = MARQUEE_DELAY_MS,
        repeatDelayMillis = MARQUEE_DELAY_MS,
        velocity = MarqueeSpeed,
    )

/** One-line [Text] that scrolls when it is longer than its place (the player title, 1:3). */
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier.slowMarquee(),
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}
