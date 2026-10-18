package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.muzzzlo.player.core.designsystem.R
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens

/**
 * Mini player under the lists (15:45). Stateless: the screen passes what is playing.
 *
 * @param onClick opens the player.
 * @param onLongClick opens the track menu (DESIGN.md, section 3, "Поведение").
 * @param cover cover of the playing track; null shows [CoverPlaceholder].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    title: String,
    artists: String,
    progress: Float,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    cover: (@Composable (Modifier) -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .height(MuzzzloDimens.MiniPlayerHeight)
            .clip(MuzzzloDimens.MiniPlayerShape)
            .background(MuzzzloColors.SurfaceElevated)
            .border(MuzzzloDimens.BorderWidth, MuzzzloColors.Outline, MuzzzloDimens.MiniPlayerShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.weight(1f).padding(horizontal = MuzzzloDimens.ItemGap),
            horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ItemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val coverModifier = Modifier.size(MuzzzloDimens.MiniPlayerCover).clip(MuzzzloDimens.RowShape)
            if (cover != null) cover(coverModifier) else CoverPlaceholder(coverModifier)
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MuzzzloColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artists,
                    style = MaterialTheme.typography.bodySmall,
                    color = MuzzzloColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RoundIconButton(
                icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                contentDescription =
                    stringResource(if (isPlaying) R.string.designsystem_pause else R.string.designsystem_play),
                onClick = onPlayPause,
                size = RoundIconButtonSize.Compact,
                accented = true,
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_small),
                contentDescription = stringResource(R.string.designsystem_open_player),
                tint = MuzzzloColors.TextSecondary,
            )
        }
        ProgressBar(progress = progress, thin = true)
    }
}
