package org.muzzzlo.player.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.RadioOptionRow
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * The list screen of the mockup, shared by the album list (15:3) and the artist list (23:403), which are variants
 * of one screen (DESIGN.md, section 3): back, the sort button, the title and the rows, with the mini player over
 * the bottom edge and the sort dialog (15:111).
 *
 * @param fields sort options in display order, [label] gives their text.
 * @param isEmpty the list is loaded and has nothing to show.
 * @param selectionBar shown under the title in the choice mode (the artist list), null elsewhere.
 */
@Composable
internal fun <F> SortableListScreen(
    @StringRes title: Int,
    sort: ListSort<F>,
    fields: List<F>,
    label: (F) -> Int,
    onSortChosen: (F) -> Unit,
    onBack: () -> Unit,
    isEmpty: Boolean,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    headerAction: @Composable RowScope.() -> Unit = {},
    selectionBar: (@Composable () -> Unit)? = null,
    rows: LazyListScope.() -> Unit,
) where F : Enum<F>, F : SortField {
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // A new order starts from the top. The list keeps the first visible row in place by its key,
    // which after a resort shows the middle of the list. Coming back to the screen keeps the position.
    var shownSort by rememberSaveable(stateSaver = sortSaver(fields)) { mutableStateOf(sort) }
    LaunchedEffect(sort) {
        if (sort != shownSort) {
            shownSort = sort
            listState.scrollToItem(0)
        }
    }

    ScreenFrame(modifier) {
        ScreenHeader(onBack = onBack) {
            headerAction()
            PillButton(
                text = stringResource(R.string.list_sort),
                onClick = { sortOpen = true },
                icon = sortIcon(sort.ascending),
            )
        }
        SectionTitle(stringResource(title))
        selectionBar?.invoke()
        ContentWithMiniPlayer(miniPlayer) {
            if (isEmpty) {
                Notice(stringResource(R.string.library_empty))
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = MiniPlayerClearance,
                    verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.TinyGap),
                    content = rows,
                )
            }
        }
    }
    if (sortOpen) {
        SortDialog(sort, fields, label, onSortChosen, onDismiss = { sortOpen = false })
    }
}

/**
 * Sort dialog (15:111). A tap applies the option at once and a second tap flips the direction, "Done" closes.
 * "New sort" (15:114) is a reserve for later and is not shown (DESIGN.md, section 3).
 */
@Composable
private fun <F> SortDialog(
    sort: ListSort<F>,
    fields: List<F>,
    label: (F) -> Int,
    onChoose: (F) -> Unit,
    onDismiss: () -> Unit,
) where F : Enum<F>, F : SortField {
    MuzzzloDialog(title = stringResource(R.string.list_sort_title), onDismiss = onDismiss) {
        fields.forEach { field ->
            RadioOptionRow(
                text = stringResource(label(field)),
                selected = field == sort.field,
                onClick = { onChoose(field) },
            )
        }
        PillButton(text = stringResource(R.string.list_sort_done), onClick = onDismiss, wide = true)
    }
}

/** Keeps a [ListSort] across configuration changes as the field name and the direction. */
private fun <F> sortSaver(fields: List<F>) where F : Enum<F>, F : SortField =
    listSaver<ListSort<F>, Any>(
        save = { listOf(it.field.name, it.ascending) },
        restore = { saved -> ListSort(fields.first { it.name == saved[0] }, saved[1] as Boolean) },
    )

/**
 * Icon of the sort button. The mockup draws the default order (A to Z) with "arrow-down-wide-narrow",
 * the opposite direction uses the mirrored "arrow-up-wide-narrow".
 */
private fun sortIcon(ascending: Boolean): Int =
    if (ascending) DesignR.drawable.ic_arrow_down_wide_narrow else DesignR.drawable.ic_arrow_up_wide_narrow
