package org.muzzzlo.player.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.BandSlider
import org.muzzzlo.player.core.designsystem.component.ChoiceChip
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.component.StepperRow
import org.muzzzlo.player.core.designsystem.component.SwitchRow
import org.muzzzlo.player.core.designsystem.component.ValueRow
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.Equalizer
import org.muzzzlo.player.core.model.EqualizerPreset
import org.muzzzlo.player.core.model.EqualizerSettings

private const val HERTZ_PER_KILOHERTZ = 1000

/**
 * The equalizer (task 1.2.8; no mockup, DESIGN.md, section 7): the switch, the row of curves (chips as the periods
 * of the statistics), ten band sliders, the preamp with −/+ and "Reset". Opened from the "Playback" group.
 */
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    ScreenFrame(modifier) {
        SettingsHeader(onBack)
        SectionTitle(stringResource(R.string.settings_equalizer))
        if (!state.isLoaded) {
            Notice(stringResource(R.string.settings_loading))
            return@ScreenFrame
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap),
        ) {
            SwitchRow(
                text = stringResource(R.string.settings_equalizer_enabled),
                checked = settings.enabled,
                onCheckedChange = viewModel::onEnabledChanged,
                secondary = stringResource(R.string.settings_equalizer_hint),
            )
            PresetChips(settings.preset, viewModel::onPresetChosen)
            Bands(settings, viewModel::onBandChanged)
            GroupTitle(R.string.settings_equalizer_preamp)
            StepperRow(
                value = decibelLabel(settings.preampDb),
                onDecrease = { viewModel.onPreampStepped(-1) },
                onIncrease = { viewModel.onPreampStepped(1) },
                decreaseDescription = stringResource(R.string.settings_decrease),
                increaseDescription = stringResource(R.string.settings_increase),
                canDecrease = settings.preampDb > Equalizer.MIN_DB,
                canIncrease = settings.preampDb < Equalizer.MAX_DB,
            )
            Text(
                text =
                    stringResource(
                        if (state.clipProtection) {
                            R.string.settings_equalizer_clipping_on
                        } else {
                            R.string.settings_equalizer_clipping_off
                        },
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MuzzzloColors.TextSecondary,
            )
            ValueRow(stringResource(R.string.settings_equalizer_reset), viewModel::onReset, chevron = false)
        }
    }
}

@Composable
private fun PresetChips(
    chosen: EqualizerPreset,
    onChosen: (EqualizerPreset) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap),
    ) {
        EqualizerPreset.entries.forEach { preset ->
            ChoiceChip(
                text = stringResource(equalizerPresetLabel(preset)),
                selected = preset == chosen,
                onClick = { onChosen(preset) },
            )
        }
    }
}

@Composable
private fun Bands(
    settings: EqualizerSettings,
    onChange: (Int, Float) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.TinyGap)) {
        Equalizer.FREQUENCIES.forEachIndexed { index, frequency ->
            val label = frequencyLabel(frequency)
            BandSlider(
                value = settings.bandsDb[index],
                min = Equalizer.MIN_DB,
                max = Equalizer.MAX_DB,
                valueLabel = formatDecibels(settings.bandsDb[index], locale),
                frequencyLabel = label,
                description = stringResource(R.string.settings_equalizer_band, label),
                onChange = { onChange(index, it) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun frequencyLabel(hertz: Int): String =
    if (hertz < HERTZ_PER_KILOHERTZ) {
        hertz.toString()
    } else {
        stringResource(R.string.settings_equalizer_kilohertz, hertz / HERTZ_PER_KILOHERTZ)
    }

internal fun equalizerPresetLabel(preset: EqualizerPreset): Int =
    when (preset) {
        EqualizerPreset.FLAT -> R.string.equalizer_preset_flat
        EqualizerPreset.BASS -> R.string.equalizer_preset_bass
        EqualizerPreset.TREBLE -> R.string.equalizer_preset_treble
        EqualizerPreset.VOCAL -> R.string.equalizer_preset_vocal
        EqualizerPreset.ROCK -> R.string.equalizer_preset_rock
        EqualizerPreset.POP -> R.string.equalizer_preset_pop
        EqualizerPreset.JAZZ -> R.string.equalizer_preset_jazz
        EqualizerPreset.CLASSICAL -> R.string.equalizer_preset_classical
        EqualizerPreset.ELECTRONIC -> R.string.equalizer_preset_electronic
        EqualizerPreset.CUSTOM -> R.string.equalizer_preset_custom
    }
