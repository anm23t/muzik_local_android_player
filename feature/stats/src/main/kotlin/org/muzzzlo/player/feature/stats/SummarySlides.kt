package org.muzzzlo.player.feature.stats

import org.muzzzlo.player.core.model.CalendarPeriod
import org.muzzzlo.player.core.model.PeriodSummary

/** One slide of a period summary (task 2.5.12), in the order they are shown. */
enum class SummarySlide(val key: String) {
    /** The name of the period, the listens and minutes. */
    COVER("cover"),
    TRACKS("tracks"),
    ALBUMS("albums"),
    ARTISTS("artists"),

    /** The top track, album and artist, the minutes and the favorite genre. */
    FINAL("final"),
}

/** The slides of [summary]: a top without entries is left out, the cover and the final slide are always there. */
fun slidesOf(summary: PeriodSummary): List<SummarySlide> =
    buildList {
        add(SummarySlide.COVER)
        if (summary.tracks.isNotEmpty()) add(SummarySlide.TRACKS)
        if (summary.albums.isNotEmpty()) add(SummarySlide.ALBUMS)
        if (summary.artists.isNotEmpty()) add(SummarySlide.ARTISTS)
        add(SummarySlide.FINAL)
    }

/**
 * The picture "Share" makes of one slide (task 2.5.12): 9:16 for stories, the slide fitted inside with a margin over
 * the live background.
 */
object ShareImage {
    const val WIDTH = 1080
    const val HEIGHT = 1920

    /** The share of the width left free on each side of the slide. */
    private const val MARGIN = 0.06f

    /** The folder in the cache that the file provider serves (`res/xml/summary_share_paths.xml`). */
    const val FOLDER = "summary"

    /** "muzzzlo-month-2026-11-tracks.png": the period and the slide, letters, digits and dashes only. */
    fun fileName(
        period: CalendarPeriod,
        slide: SummarySlide,
    ): String {
        val periodPart = period.key.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return "muzzzlo-$periodPart-${slide.key}.png"
    }

    /** Where a slide of [width] x [height] goes in the picture: as large as fits inside the margins, centred. */
    fun fit(
        width: Int,
        height: Int,
    ): FitRect {
        val room = 1 - 2 * MARGIN
        val scale = minOf(WIDTH * room / width, HEIGHT * room / height)
        val w = width * scale
        val h = height * scale
        return FitRect((WIDTH - w) / 2, (HEIGHT - h) / 2, w, h)
    }
}

/** A rectangle in the pixels of the shared picture. */
data class FitRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)
