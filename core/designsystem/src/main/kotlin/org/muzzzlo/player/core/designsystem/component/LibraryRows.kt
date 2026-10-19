package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.muzzzlo.player.core.designsystem.R
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme

/**
 * Album row of the album list (15:18).
 *
 * @param cover cover image; null shows [CoverPlaceholder].
 */
@Composable
fun AlbumRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cover: (@Composable (Modifier) -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MuzzzloDimens.RowShape)
            .clickable(onClick = onClick)
            .padding(MuzzzloDimens.RowPadding),
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val coverModifier = Modifier.size(MuzzzloDimens.ListCover).clip(MuzzzloDimens.RowShape)
        if (cover != null) cover(coverModifier) else CoverPlaceholder(coverModifier)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.TinyGap)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MuzzzloColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MuzzzloColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Artist row of the artist list (23:415).
 *
 * @param photo photo added by the user; null shows the empty circle of the mockup.
 * @param selected the row is ticked in the selection mode: the accent container behind it, as in a chosen option
 * of the sort dialog (15:117; not in the mockup, DESIGN.md, section 7).
 */
@Composable
fun ArtistRow(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    photo: (@Composable (Modifier) -> Unit)? = null,
    selected: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MuzzzloDimens.RowShape)
            .background(if (selected) MuzzzloTheme.accent.container else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(MuzzzloDimens.RowPadding),
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val photoModifier = Modifier.size(MuzzzloDimens.ArtistPhoto).clip(CircleShape)
        if (photo != null) photo(photoModifier) else ArtistPhotoPlaceholder(MuzzzloDimens.ArtistPhoto)
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MuzzzloColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Shown where a cover is missing: the elevated surface with the disc icon.
 *
 * @param shape corners of the border, the same as the clip of the cover it replaces.
 */
@Composable
fun CoverPlaceholder(
    modifier: Modifier = Modifier,
    shape: Shape = MuzzzloDimens.RowShape,
) {
    Box(
        modifier
            .background(MuzzzloColors.SurfaceElevated)
            .border(MuzzzloDimens.BorderWidth, MuzzzloColors.Outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_disc), null, tint = MuzzzloColors.TextSecondary)
    }
}

/** Empty artist photo: a black circle with a faint border (23:478, 28:712). */
@Composable
fun ArtistPhotoPlaceholder(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MuzzzloColors.Background)
            .border(MuzzzloDimens.BorderWidth, MuzzzloColors.PhotoPlaceholderBorder, CircleShape),
    )
}
