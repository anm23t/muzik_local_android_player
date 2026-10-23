package org.muzzzlo.player.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.RadioOptionRow
import org.muzzzlo.player.core.designsystem.component.ValueRow
import org.muzzzlo.player.core.model.InterfaceTheme

/** "Theme" of the "Interface" group (task 1.2.6): the row with the chosen theme. */
@Composable
internal fun ThemeRow(
    theme: InterfaceTheme,
    onClick: () -> Unit,
) {
    ValueRow(
        text = stringResource(R.string.settings_theme),
        onClick = onClick,
        value = themeLabel(theme),
    )
}

/** The choice of the theme, each option with a line on what it changes. */
@Composable
internal fun ThemeDialog(
    theme: InterfaceTheme,
    onChosen: (InterfaceTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(stringResource(R.string.settings_theme), onDismiss) {
        InterfaceTheme.entries.forEach { option ->
            RadioOptionRow(
                themeLabel(option),
                selected = option == theme,
                onClick = { onChosen(option) },
                secondary = stringResource(themeHint(option)),
                secondaryMaxLines = 2,
            )
        }
    }
}

@Composable
private fun themeLabel(theme: InterfaceTheme): String =
    when (theme) {
        InterfaceTheme.WINDOW -> stringResource(R.string.settings_theme_window)
        InterfaceTheme.HUGE -> stringResource(R.string.settings_theme_huge)
    }

private fun themeHint(theme: InterfaceTheme): Int =
    when (theme) {
        InterfaceTheme.WINDOW -> R.string.settings_theme_window_hint
        InterfaceTheme.HUGE -> R.string.settings_theme_huge_hint
    }
