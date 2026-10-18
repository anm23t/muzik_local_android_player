package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.muzzzlo.player.core.designsystem.R
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme

/**
 * Track row of the album card (4:337), the current track is highlighted as in 4:361.
 *
 * @param number track number shown on the left ("01"), null hides the column.
 * @param subtitle artists under the title, null hides the line (album card shows only titles).
 * @param onMoreClick opens the track menu; null hides the "more" button.
 * @param trailing extra controls before the "more" button (for example the queue drag handle).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: String? = null,
    subtitle: String? = null,
    isCurrent: Boolean = false,
    onMoreClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val accent = MuzzzloTheme.accent
    val textColor = if (isCurrent) accent.accent else MuzzzloColors.TextPrimary
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MuzzzloDimens.TrackRowMinHeight)
            .clip(MuzzzloDimens.RowShape)
            .background(if (isCurrent) accent.container else MuzzzloColors.Surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = MuzzzloDimens.RowPadding, end = MuzzzloDimens.RowEndPadding),
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text(
                text = number,
                modifier = Modifier.width(MuzzzloDimens.TrackNumberWidth),
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent) accent.accent else MuzzzloColors.TextSecondary,
                maxLines = 1,
            )
        }
        Column(Modifier.weight(1f).padding(vertical = MuzzzloDimens.RowPadding)) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.bodyLarge.let {
                        if (isCurrent) it.copy(fontWeight = FontWeight.SemiBold) else it
                    },
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MuzzzloColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isCurrent) NowPlayingIndicator()
        trailing()
        if (onMoreClick != null) TrackMenuButton(onClick = onMoreClick)
    }
}

/** Three static level bars next to the current track (4:364). */
@Composable
fun NowPlayingIndicator(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.designsystem_now_playing)
    Row(
        modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.TinyGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MuzzzloDimens.LevelBarHeights.forEach { height ->
            Box(
                Modifier
                    .size(width = MuzzzloDimens.LevelBarWidth, height = height)
                    .clip(RoundedCornerShape(MuzzzloDimens.LevelBarRadius))
                    .background(MuzzzloTheme.accent.accent),
            )
        }
    }
}

/** The "more" button of a track row: opens [TrackActionsMenu]. Not in the mockup, see DESIGN.md, section 7. */
@Composable
fun TrackMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoundIconButton(
        icon = R.drawable.ic_more_horizontal,
        contentDescription = stringResource(R.string.designsystem_track_menu),
        onClick = onClick,
        modifier = modifier,
        size = RoundIconButtonSize.Small,
        containerColor = MuzzzloColors.Surface,
    )
}
