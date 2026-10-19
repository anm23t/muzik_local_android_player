package org.muzzzlo.player.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.OutlinedPanel
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.RadioOptionRow
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens

/**
 * Artist check (no mockup, DESIGN.md, section 7): every group is a panel with one radio row per spelling and the
 * number of its tracks, "Merge" and "Skip" under it.
 */
@Composable
fun ArtistCheckScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistCheckViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenFrame(modifier) {
        ScreenHeader(onBack = onBack)
        SectionTitle(stringResource(R.string.check_title))
        when {
            !state.isLoaded -> Notice(stringResource(R.string.check_loading))
            state.groups.isEmpty() ->
                Notice(
                    stringResource(if (state.appliedCount > 0) R.string.check_done else R.string.check_empty),
                )
            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap)) {
                    item {
                        Text(
                            text = stringResource(R.string.check_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MuzzzloColors.TextSecondary,
                        )
                    }
                    itemsIndexed(state.groups, key = { _, g -> g.group.variants.first().artistId }) { index, item ->
                        GroupPanel(
                            item = item,
                            enabled = !state.isApplying,
                            onChoose = { viewModel.onKeepChosen(index, it) },
                            onApply = { viewModel.onApply(index) },
                            onSkip = { viewModel.onSkip(index) },
                        )
                    }
                }
        }
    }
}

@Composable
private fun GroupPanel(
    item: CheckGroup,
    enabled: Boolean,
    onChoose: (Long) -> Unit,
    onApply: () -> Unit,
    onSkip: () -> Unit,
) {
    OutlinedPanel {
        item.group.variants.forEach { variant ->
            val tracks = pluralStringResource(R.plurals.library_track_count, variant.trackCount, variant.trackCount)
            RadioOptionRow(
                text = variant.name,
                secondary = tracks,
                maxLines = 2,
                selected = variant.artistId == item.keepId,
                onClick = { onChoose(variant.artistId) },
            )
        }
        PillButton(stringResource(R.string.check_merge), onClick = onApply, wide = true, enabled = enabled)
        PillButton(stringResource(R.string.check_skip), onClick = onSkip, wide = true, enabled = enabled)
    }
}
