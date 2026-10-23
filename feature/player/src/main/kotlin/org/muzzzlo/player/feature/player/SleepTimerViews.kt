package org.muzzzlo.player.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.component.ActionRow
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.RadioOptionRow
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme
import org.muzzzlo.player.core.model.SleepTimerMode
import org.muzzzlo.player.core.model.SleepTimerRules
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * The small sign of a running sleep timer under the cover (no mockup): the moon icon and the time left in the
 * accent, as the rating badge of the track rows (4:337). A tap opens the dialog.
 */
@Composable
internal fun SleepTimerBadge(
    timer: SleepTimerUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MuzzzloTheme.accent.accent
    val label = stringResource(DesignR.string.track_action_sleep_timer)
    Row(
        modifier
            .clip(MuzzzloDimens.RowShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = MuzzzloDimens.RowPadding, vertical = MuzzzloDimens.TinyGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap),
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_moon),
            contentDescription = label,
            tint = accent,
            modifier = Modifier.size(MuzzzloDimens.SmallIcon),
        )
        Text(text = remainingText(timer), style = MaterialTheme.typography.bodySmall, color = accent)
    }
}

/**
 * The sleep timer dialog (no mockup, as the choice dialogs of the settings, 15:111, 15:117): what the running
 * timer does and "Turn off timer", then the lengths and "End of current track". A choice starts the timer anew.
 */
@Composable
internal fun SleepTimerDialog(
    timer: SleepTimerUi?,
    onMinutes: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(stringResource(DesignR.string.track_action_sleep_timer), onDismiss) {
        if (timer != null) {
            Text(
                text = runningText(timer),
                style = MaterialTheme.typography.bodyLarge,
                color = MuzzzloColors.TextPrimary,
            )
            ActionRow(
                icon = DesignR.drawable.ic_close,
                text = stringResource(R.string.sleep_timer_off),
                onClick = {
                    onCancel()
                    onDismiss()
                },
            )
        }
        SleepTimerRules.MINUTE_OPTIONS.forEach { minutes ->
            RadioOptionRow(
                pluralStringResource(R.plurals.sleep_timer_minutes, minutes, minutes),
                selected = false,
                onClick = {
                    onMinutes(minutes)
                    onDismiss()
                },
            )
        }
        RadioOptionRow(
            stringResource(R.string.sleep_timer_end_of_track),
            selected = timer?.mode == SleepTimerMode.EndOfTrack,
            onClick = {
                onEndOfTrack()
                onDismiss()
            },
        )
    }
}

@Composable
private fun remainingText(timer: SleepTimerUi): String =
    timer.remainingMs?.let(::formatTime) ?: stringResource(R.string.sleep_timer_end_short)

@Composable
private fun runningText(timer: SleepTimerUi): String =
    when (timer.mode) {
        is SleepTimerMode.After -> stringResource(R.string.sleep_timer_running, remainingText(timer))
        SleepTimerMode.EndOfTrack -> stringResource(R.string.sleep_timer_running_end_of_track)
    }
