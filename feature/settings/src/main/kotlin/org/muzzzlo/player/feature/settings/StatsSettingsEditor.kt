package org.muzzzlo.player.feature.settings

import org.muzzzlo.player.core.model.StatsSettings

/**
 * Changes of the statistics settings for [SettingsViewModel]. Like the other steppers, a change is applied to the
 * shown settings at once, so quick taps build on each other, and then saved.
 */
class StatsSettingsEditor(
    private val current: () -> StatsSettings,
    private val show: (StatsSettings) -> Unit,
    private val save: (StatsSettings) -> Unit,
) {
    /** Moves the threshold share by [steps] of [StatsSettings.PERCENT_STEP]; the dialog stays open. */
    fun onPercentStepped(steps: Int) =
        change { it.copy(thresholdPercent = it.thresholdPercent + steps * StatsSettings.PERCENT_STEP) }

    /** Moves the threshold time by [steps] minutes; the dialog stays open. */
    fun onMinutesStepped(steps: Int) = change { it.copy(thresholdMinutes = it.thresholdMinutes + steps) }

    fun onPrivateModeChanged(enabled: Boolean) = change { it.copy(privateMode = enabled) }

    private fun change(transform: (StatsSettings) -> StatsSettings) {
        val updated = transform(current()).clamped()
        show(updated)
        save(updated)
    }
}
