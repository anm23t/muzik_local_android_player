package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens

/**
 * Square cover with a caption under it: the tiles of the home screen (28:620) and the albums of the artist card
 * (33:714). The tile takes the width it is given, three of them fill a row.
 *
 * @param subtitle second line under the title (artists on the artist card); null hides it.
 * @param cover cover image; null shows [CoverPlaceholder].
 */
@Composable
fun CoverTile(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    cover: (@Composable (Modifier) -> Unit)? = null,
) {
    Column(
        modifier.clip(MuzzzloDimens.RowShape).clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap),
    ) {
        val coverModifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MuzzzloDimens.TileShape)
        if (cover != null) cover(coverModifier) else CoverPlaceholder(coverModifier, MuzzzloDimens.TileShape)
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MuzzzloColors.TextPrimary,
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
    }
}

/**
 * Cover inside an elevated card with a border: the big cover of the player (1:3, card 24, cover 284x288)
 * and the cover of the album card (4:311).
 *
 * @param shape corners of the card; the cover inside is rounded by [coverShape].
 * @param inset space between the card edge and the cover.
 * @param cover cover image; null shows [CoverPlaceholder].
 */
@Composable
fun CoverCard(
    modifier: Modifier = Modifier,
    shape: Shape = MuzzzloDimens.CardShape,
    coverShape: Shape = MuzzzloDimens.MiniPlayerShape,
    inset: Dp = MuzzzloDimens.SectionGap,
    cover: (@Composable (Modifier) -> Unit)? = null,
) {
    Box(
        modifier
            .clip(shape)
            .background(MuzzzloColors.SurfaceElevated)
            .border(MuzzzloDimens.BorderWidth, MuzzzloColors.Outline, shape)
            .padding(inset),
    ) {
        val coverModifier = Modifier.fillMaxSize().clip(coverShape)
        if (cover != null) cover(coverModifier) else CoverPlaceholder(coverModifier, coverShape)
    }
}
