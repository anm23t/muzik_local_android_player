package org.muzzzlo.player.core.stats

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.muzzzlo.player.core.database.dao.ListenEventDao
import org.muzzzlo.player.core.model.RecentAlbum
import org.muzzzlo.player.core.model.StatsPeriod
import org.muzzzlo.player.core.model.StatsRepository
import org.muzzzlo.player.core.model.StatsTotals
import org.muzzzlo.player.core.model.TopAlbum
import org.muzzzlo.player.core.model.TopArtist
import org.muzzzlo.player.core.model.TopTrack
import org.muzzzlo.player.core.model.rangeAt
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

/**
 * Statistics over `listen_event` (ARCHITECTURE.md, section 7). The period turns into a time range when a Flow is
 * created, so a Flow made for "today" keeps the day it was made on.
 */
internal class DefaultStatsRepository
    @Inject
    constructor(
        private val dao: ListenEventDao,
        private val clock: Clock,
    ) : StatsRepository {
        private fun StatsPeriod.range() = rangeAt(clock.millis(), ZoneId.systemDefault())

        override fun totals(period: StatsPeriod): Flow<StatsTotals> {
            val range = period.range()
            return dao.observeTotals(range.fromMs, range.toMs).map { StatsTotals(it.plays, it.skips, it.playedMs) }
        }

        override fun topTracks(
            period: StatsPeriod,
            limit: Int,
        ): Flow<List<TopTrack>> {
            val range = period.range()
            return dao.observeTopTracks(range.fromMs, range.toMs, limit).map { rows ->
                rows.map { TopTrack(it.trackId, it.title, snapshotNames(it.artistsSnapshot), it.plays, it.playedMs) }
            }
        }

        override fun topAlbums(
            period: StatsPeriod,
            limit: Int,
        ): Flow<List<TopAlbum>> {
            val range = period.range()
            return dao.observeTopAlbums(range.fromMs, range.toMs, limit).map { rows ->
                rows.map { TopAlbum(it.albumId, it.title, it.plays, it.playedMs) }
            }
        }

        override fun topArtists(
            period: StatsPeriod,
            limit: Int,
        ): Flow<List<TopArtist>> {
            val range = period.range()
            return combine(
                dao.observeArtistPlays(range.fromMs, range.toMs),
                dao.observeOrphanArtistPlays(range.fromMs, range.toMs),
            ) { linked, orphans -> mergeTopArtists(linked, orphans, limit) }
        }

        override fun artistTopTracks(
            artistId: Long,
            limit: Int,
        ): Flow<List<TopTrack>> =
            dao.observeArtistTopTracks(artistId, limit).map { rows ->
                rows.map { TopTrack(it.trackId, it.title, snapshotNames(it.artistsSnapshot), it.plays, it.playedMs) }
            }

        override fun artistPlays(artistId: Long): Flow<Int> = dao.observeArtistPlayCount(artistId)

        override fun recentAlbums(limit: Int): Flow<List<RecentAlbum>> =
            dao.observeRecentAlbums(limit).map { rows -> rows.map { RecentAlbum(it.albumId, it.lastListenedAt) } }
    }
