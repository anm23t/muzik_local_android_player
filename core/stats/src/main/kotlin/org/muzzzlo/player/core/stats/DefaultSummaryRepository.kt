package org.muzzzlo.player.core.stats

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.database.dao.ListenEventDao
import org.muzzzlo.player.core.model.CalendarPeriod
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.PeriodSummary
import org.muzzzlo.player.core.model.SummaryRepository
import org.muzzzlo.player.core.model.rangeIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * [SummaryRepository] over `listen_event` (task 2.5.11): reads the counted listens of the period with the artists
 * and genres of their tracks and leaves the counting to [SummaryCalculator]. Periods are in the zone of [clock].
 */
internal class DefaultSummaryRepository
    @Inject
    constructor(
        private val dao: ListenEventDao,
        private val clock: Clock,
        private val dispatchers: DispatcherProvider,
    ) : SummaryRepository {
        override suspend fun summary(period: CalendarPeriod): PeriodSummary? =
            withContext(dispatchers.io) {
                val range = period.rangeIn(clock.zone)
                val listens =
                    dao.summaryListens(range.fromMs, range.toMs).map {
                        SummaryListen(
                            it.trackId,
                            it.title,
                            snapshotNames(it.artistsSnapshot),
                            it.albumId,
                            it.albumTitle,
                            it.playedMs,
                        )
                    }
                val artists =
                    dao.summaryTrackArtists(range.fromMs, range.toMs)
                        .groupBy({ it.trackId }, { ArtistLink(it.artistId, it.name, it.photoPath) })
                val genres = dao.summaryTrackGenres(range.fromMs, range.toMs).groupBy({ it.trackId }, { it.name })
                SummaryCalculator.calculate(period, period.isFinishedOn(LocalDate.now(clock)), listens, artists, genres)
            }

        override fun hasListens(period: CalendarPeriod): Flow<Boolean> {
            val range = period.rangeIn(clock.zone)
            return dao.observeHasListens(range.fromMs, range.toMs)
        }
    }
