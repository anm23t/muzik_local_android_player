package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens

/**
 * A black block with a rounded border and inner padding: the header with the photo and the top songs of the
 * artist card (33:714).
 */
@Composable
fun OutlinedPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MuzzzloDimens.PanelShape)
            .border(MuzzzloDimens.BorderWidth, MuzzzloColors.Outline, MuzzzloDimens.PanelShape)
            .padding(MuzzzloDimens.PanelPadding),
        verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap),
        content = content,
    )
}
