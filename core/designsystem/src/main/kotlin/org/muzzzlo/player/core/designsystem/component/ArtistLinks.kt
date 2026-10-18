package org.muzzzlo.player.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink

/** Separator between artists everywhere in the app (DESIGN.md, section 6). */
private const val SEPARATOR = ", "

/**
 * Artists as separate tappable names joined by ", ": each name opens the card of its artist
 * (DESIGN.md, section 3, "Поведение"; player 1:3, album card 4:311). The names look like plain text, as in the
 * mockup, and wrap like one line of text.
 *
 * @param onClick receives the index of the tapped name in [names].
 */
@Composable
fun ArtistLinks(
    names: List<String>,
    onClick: (Int) -> Unit,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val text =
        remember(names) {
            buildAnnotatedString {
                names.forEachIndexed { index, name ->
                    if (index > 0) append(SEPARATOR)
                    withLink(LinkAnnotation.Clickable(tag = name) { currentOnClick(index) }) { append(name) }
                }
            }
        }
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
