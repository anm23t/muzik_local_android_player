package org.muzzzlo.player.feature.stats

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.designsystem.component.LiveBackground
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme
import org.muzzzlo.player.core.model.PeriodSummary
import org.muzzzlo.player.core.designsystem.R as DesignR

private const val NANOS_PER_SECOND = 1_000_000_000f

/** A tap in this share of the width on the left goes back a slide; anywhere else goes forward. */
private const val BACK_ZONE = 0.3f

/**
 * The slides of a period summary (task 2.5.12, no mockup, DESIGN.md, section 7): full screen over a live background
 * of the colors of the top track; the user goes on with a swipe or a tap (no automatic turning, owner's decision).
 * Segments on top show the place, a round close button leaves, "Share" hands a picture of the current slide to the
 * system share sheet.
 */
@Composable
fun SummaryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.summary_share_chooser)
    LaunchedEffect(state.shareUri) {
        val uri = state.shareUri ?: return@LaunchedEffect
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, chooserTitle))
        viewModel.onShareOpened()
    }
    val time = rememberSummaryClock()
    Box(modifier.fillMaxSize()) {
        LiveBackground(
            colors = state.palette.map(::Color),
            timeSec = { time() },
            colorAmount = { 1f },
            dim = SUMMARY_DIM,
            modifier = Modifier.fillMaxSize(),
        )
        val summary = state.summary
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(MuzzzloDimens.FrameOuterPadding),
            verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap),
        ) {
            if (summary != null) {
                SummaryPager(state, summary, time, onBack, viewModel::onShare)
            } else if (state.isLoaded) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.summary_not_found),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MuzzzloColors.TextSecondary,
                    )
                    CloseButton(onBack)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SummaryPager(
    state: SummaryUiState,
    summary: PeriodSummary,
    time: () -> Float,
    onBack: () -> Unit,
    onShare: (SummarySlide, ImageBitmap) -> Unit,
) {
    val slides = state.slides
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    // Not state: the layers only serve "Share" and must not recompose anything.
    val layers = remember { HashMap<Int, GraphicsLayer>() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SlideSegments(pager, Modifier.weight(1f))
        CloseButton(onBack)
    }
    HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f)) { page ->
        val layer = rememberGraphicsLayer()
        layers[page] = layer
        SummarySlideContent(
            slide = slides[page],
            summary = summary,
            timeSec = time,
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(slides.size) {
                        detectTapGestures { offset ->
                            val target = if (offset.x < size.width * BACK_ZONE) page - 1 else page + 1
                            if (target in slides.indices) scope.launch { pager.animateScrollToPage(target) }
                        }
                    }.drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
        )
    }
    PillButton(
        text = stringResource(R.string.summary_share),
        onClick = {
            val page = pager.currentPage
            val layer = layers[page] ?: return@PillButton
            scope.launch { onShare(slides[page], layer.toImageBitmap()) }
        },
        modifier = Modifier.align(Alignment.CenterHorizontally),
        icon = DesignR.drawable.ic_share,
        enabled = !state.sharing,
    )
}

/** The place among the slides as stories show it: one thin segment per slide, the passed ones in the accent. */
@Composable
private fun SlideSegments(
    pager: PagerState,
    modifier: Modifier = Modifier,
) {
    val accent = MuzzzloTheme.accent.accent
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap)) {
        repeat(pager.pageCount) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(MuzzzloDimens.ProgressThin)
                    .clip(MuzzzloDimens.PillShape)
                    .background(if (index <= pager.currentPage) accent else MuzzzloColors.Outline),
            )
        }
    }
}

@Composable
private fun CloseButton(onBack: () -> Unit) {
    RoundIconButton(
        icon = DesignR.drawable.ic_close,
        contentDescription = stringResource(R.string.summary_close),
        onClick = onBack,
    )
}

/** The clock of the live background and the glints; it runs while the screen is shown. */
@Composable
private fun rememberSummaryClock(): () -> Float {
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0) seconds += (now - last) / NANOS_PER_SECOND
                last = now
            }
        }
    }
    return { seconds }
}
