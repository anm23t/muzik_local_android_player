package org.muzzzlo.player.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.OutlinedPanel
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens

/**
 * Files in quarantine (no mockup, DESIGN.md, section 7): the explanation, then a framed row per file with a
 * "Try again" button.
 */
@Composable
fun QuarantineScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuarantineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenFrame(modifier) {
        SettingsHeader(onBack)
        SectionTitle(stringResource(R.string.settings_quarantine))
        when {
            !state.isLoaded -> Notice(stringResource(R.string.settings_loading))
            state.files.isEmpty() -> Notice(stringResource(R.string.settings_quarantine_empty))
            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap)) {
                    item {
                        Text(
                            text = stringResource(R.string.settings_quarantine_explanation),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MuzzzloColors.TextSecondary,
                        )
                    }
                    items(state.files, key = { it.uri }) { file ->
                        OutlinedPanel {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ItemGap),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MuzzzloColors.TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                PillButton(
                                    text = stringResource(R.string.settings_quarantine_retry),
                                    onClick = { viewModel.onRetryClicked(file) },
                                    enabled = state.retrying == null,
                                )
                            }
                        }
                    }
                }
        }
    }
}
