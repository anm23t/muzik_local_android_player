package org.muzzzlo.player.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.R as DesignR

/** The back button (3:6) at the top left, like the headers of the library screens. */
@Composable
internal fun SettingsHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        RoundIconButton(
            icon = DesignR.drawable.ic_chevron_left,
            contentDescription = stringResource(R.string.settings_back),
            onClick = onBack,
        )
    }
}

/** Centered secondary text for empty lists and short notices. */
@Composable
internal fun Notice(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = MuzzzloColors.TextSecondary)
    }
}
