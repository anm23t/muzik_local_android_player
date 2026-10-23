package org.muzzzlo.player.feature.settings

import org.muzzzlo.player.core.model.InterfaceTheme
import org.muzzzlo.player.core.model.SettingsRepository

/**
 * Changes of the look of the app since 1.2 (theme, waveform) for [SettingsViewModel], kept apart so that the view
 * model does not grow. [save] runs a repository call off the screen, [closeDialog] closes the choice dialog.
 */
class InterfaceSettingsEditor(
    private val save: (suspend SettingsRepository.() -> Unit) -> Unit,
    private val closeDialog: () -> Unit,
) {
    fun onThemeChosen(theme: InterfaceTheme) {
        closeDialog()
        save { setTheme(theme) }
    }
}
