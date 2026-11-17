package org.muzzzlo.player.feature.stats

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.BackgroundPaletteRepository
import org.muzzzlo.player.core.model.CalendarPeriod
import org.muzzzlo.player.core.model.PeriodSummary
import org.muzzzlo.player.core.model.Season
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.SummaryAlbum
import org.muzzzlo.player.core.model.SummaryArtist
import org.muzzzlo.player.core.model.SummaryRepository
import org.muzzzlo.player.core.model.SummaryTrack
import java.lang.reflect.Proxy
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModelTest {
    private val month = CalendarPeriod.Month(YearMonth.of(2026, 11))
    private val track = SummaryTrack(7, "Hyperballad", listOf("Björk"), 3, plays = 4, playedMs = 600_000)
    private val album = SummaryAlbum(3, "Post", 4, 600_000)
    private val artist = SummaryArtist(1, "Björk", 4, 600_000, image = null)
    private val full =
        PeriodSummary(month, true, 4, 600_000, listOf(track), listOf(album), listOf(artist), favoriteGenre = "Art pop")

    private val defaultPalette = listOf(1, 2, 3, 4)
    private val trackPalette = listOf(5, 6, 7, 8)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private class FakeSummaries(
        private val summaries: Map<CalendarPeriod, PeriodSummary>,
    ) : SummaryRepository {
        override suspend fun summary(period: CalendarPeriod) = summaries[period]

        override fun hasListens(period: CalendarPeriod): Flow<Boolean> = flowOf(period in summaries)
    }

    private val palettes =
        object : BackgroundPaletteRepository {
            override suspend fun paletteOf(trackId: Long) = trackPalette

            override val defaultPalette = this@SummaryViewModelTest.defaultPalette
        }

    private fun settings(dynamicAccent: Boolean): SettingsRepository =
        Proxy.newProxyInstance(
            SettingsRepository::class.java.classLoader,
            arrayOf(SettingsRepository::class.java),
        ) { _, method, _ ->
            if (method.name == "getSettings") {
                flowOf(
                    AppSettings(dynamicAccent = dynamicAccent),
                )
            } else {
                error("Not used: ${method.name}")
            }
        } as SettingsRepository

    private var writeFails = false

    private val writer =
        object : SlideImageWriter {
            override suspend fun write(
                slide: ImageBitmap,
                palette: List<Int>,
                fileName: String,
            ): String {
                check(!writeFails) { "Software rendering doesn't support hardware bitmaps" }
                return "content://share/$fileName"
            }
        }

    /** A stand-in for a captured slide: the fake writer never looks at it. */
    private val image =
        Proxy.newProxyInstance(ImageBitmap::class.java.classLoader, arrayOf(ImageBitmap::class.java)) { _, _, _ ->
            error("Not used")
        } as ImageBitmap

    private fun viewModel(
        summary: PeriodSummary?,
        key: String? = month.key,
        dynamicAccent: Boolean = true,
    ) = SummaryViewModel(
        FakeSummaries(listOfNotNull(summary).associateBy { it.period }),
        palettes,
        settings(dynamicAccent),
        writer,
        SavedStateHandle(mapOf(SUMMARY_PERIOD_ARG to key)),
    )

    @Test
    fun `a full summary has every slide in order and the colors of the top track`() =
        runTest {
            val state = viewModel(full).state.value
            assertTrue(state.isLoaded)
            assertEquals(month, state.period)
            assertEquals(SummarySlide.entries.toList(), state.slides)
            assertEquals(trackPalette, state.palette)
        }

    @Test
    fun `tops without entries are left out, the cover and the final slide stay`() =
        runTest {
            val gone = full.copy(tracks = listOf(track.copy(trackId = null, albumId = null)), albums = emptyList())
            val state = viewModel(gone).state.value
            assertEquals(
                listOf(SummarySlide.COVER, SummarySlide.TRACKS, SummarySlide.ARTISTS, SummarySlide.FINAL),
                state.slides,
            )
            assertEquals(defaultPalette, state.palette)
        }

    @Test
    fun `a switched off dynamic accent keeps the red palette`() =
        runTest {
            assertEquals(defaultPalette, viewModel(full, dynamicAccent = false).state.value.palette)
        }

    @Test
    fun `a period without listens or an unknown key has no slides`() =
        runTest {
            val empty = viewModel(null).state.value
            assertTrue(empty.isLoaded)
            assertNull(empty.summary)
            assertEquals(emptyList<SummarySlide>(), empty.slides)

            val unknown = viewModel(full, key = "nonsense").state.value
            assertNull(unknown.period)
            assertNull(unknown.summary)
        }

    @Test
    fun `share hands over the picture once and a failed picture does not break the screen`() =
        runTest {
            val viewModel = viewModel(full)
            viewModel.onShare(SummarySlide.TRACKS, image)
            assertEquals("content://share/muzzzlo-month-2026-11-tracks.png", viewModel.state.value.shareUri)
            viewModel.onShareOpened()
            assertNull(viewModel.state.value.shareUri)

            writeFails = true
            viewModel.onShare(SummarySlide.FINAL, image)
            assertNull(viewModel.state.value.shareUri)
            assertTrue(!viewModel.state.value.sharing)
        }

    @Test
    fun `the shared file is named after the period and the slide`() {
        assertEquals("muzzzlo-month-2026-11-tracks.png", ShareImage.fileName(month, SummarySlide.TRACKS))
        assertEquals(
            "muzzzlo-season-2026-winter-final.png",
            ShareImage.fileName(CalendarPeriod.SeasonOf(Season.WINTER, 2026), SummarySlide.FINAL),
        )
        assertEquals("muzzzlo-year-2026-cover.png", ShareImage.fileName(CalendarPeriod.Year(2026), SummarySlide.COVER))
    }

    @Test
    fun `the shared picture is 1080 by 1920 and the slide fits inside its margins`() {
        assertEquals(1080, ShareImage.WIDTH)
        assertEquals(1920, ShareImage.HEIGHT)
        val tall = ShareImage.fit(1000, 2200)
        assertEquals(1920f * 0.88f, tall.height, 0.5f)
        assertEquals((1080 - tall.width) / 2, tall.left, 0.5f)
        val wide = ShareImage.fit(1000, 1000)
        assertEquals(1080f * 0.88f, wide.width, 0.5f)
        assertEquals((1920 - wide.height) / 2, wide.top, 0.5f)
    }
}
