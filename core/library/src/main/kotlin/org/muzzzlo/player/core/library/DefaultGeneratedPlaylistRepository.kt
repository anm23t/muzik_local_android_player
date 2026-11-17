package org.muzzzlo.player.core.library

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.database.MuzzzloDatabase
import org.muzzzlo.player.core.database.dao.GeneratedPlaylistDao
import org.muzzzlo.player.core.database.dao.GeneratedPlaylistRow
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.dao.ListenEventDao
import org.muzzzlo.player.core.database.dao.MixListenRow
import org.muzzzlo.player.core.database.dao.PlaylistDao
import org.muzzzlo.player.core.database.entity.GeneratedPlaylistEntity
import org.muzzzlo.player.core.database.entity.PlaylistEntity
import org.muzzzlo.player.core.model.CalendarPeriod
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.GeneratedInput
import org.muzzzlo.player.core.model.GeneratedKind
import org.muzzzlo.player.core.model.GeneratedPlaylist
import org.muzzzlo.player.core.model.GeneratedPlaylistRepository
import org.muzzzlo.player.core.model.Json
import org.muzzzlo.player.core.model.MixListen
import org.muzzzlo.player.core.model.PlaylistNames
import org.muzzzlo.player.core.model.SavedSnapshot
import org.muzzzlo.player.core.model.SeasonalWindow
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.TrackFactsSource
import org.muzzzlo.player.core.model.rangeIn
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [GeneratedPlaylistRepository] over `generated_playlist` (task 2.5.7). The rules of the sets are in `core:model`
 * ([GeneratedSetBuilder]); this class reads their input, stores the result and keeps a set as a playlist.
 */
@Singleton
internal class DefaultGeneratedPlaylistRepository
    @Inject
    constructor(
        private val database: MuzzzloDatabase,
        private val dao: GeneratedPlaylistDao,
        private val library: LibraryDao,
        private val listens: ListenEventDao,
        private val playlists: PlaylistDao,
        private val facts: TrackFactsSource,
        private val builder: GeneratedSetBuilder,
        private val clock: Clock,
        private val dispatchers: DispatcherProvider,
    ) : GeneratedPlaylistRepository {
        /** Two screens opening at once must not build the day twice. */
        private val building = Mutex()

        override suspend fun ensureToday() =
            withContext(dispatchers.io) {
                building.withLock {
                    val today = LocalDate.now(clock).toEpochDay()
                    val stored = dao.all()
                    val empty = stored.filter { dao.trackIds(it.kind).isEmpty() }.mapTo(HashSet()) { it.kind }
                    val stale = builder.staleKinds(stored.associate { it.kind to it.day }, empty, today)
                    if (stale.isNotEmpty()) rebuild(stale, today)
                    val seasonal =
                        builder.seasonalToBuild(
                            stored.associate { it.kind to StoredSeasonal(it.period, it.day) },
                            LocalDate.ofEpochDay(today),
                        )
                    seasonal.forEach { (kind, period) -> rebuildSeasonal(kind, period, today) }
                }
            }

        /** The most played tracks of [period] (task 2.5.9); listens of the whole period, also after its end. */
        private suspend fun rebuildSeasonal(
            kind: GeneratedKind,
            period: CalendarPeriod,
            today: Long,
        ) {
            val now = clock.millis()
            val range = period.rangeIn(clock.zone)
            val input =
                GeneratedInput(
                    facts = facts.observeFacts().first(),
                    recentPlays = emptyMap(),
                    nowMs = now,
                    epochDay = today,
                    periodPlays =
                        listens.trackPlayStatsBetween(range.fromMs, range.toMs).associate { it.trackId to it.plays },
                )
            val set = builder.build(kind, input, period)
            val entity =
                GeneratedPlaylistEntity(
                    kind = kind.key,
                    day = today,
                    gradient = set.gradient,
                    createdAt = now,
                    period = period.key,
                )
            dao.replace(entity, set.trackIds)
        }

        private suspend fun rebuild(
            kinds: List<GeneratedKind>,
            today: Long,
        ) {
            val now = clock.millis()
            val input =
                GeneratedInput(
                    facts = facts.observeFacts().first(),
                    recentPlays = recentPlays(now),
                    nowMs = now,
                    epochDay = today,
                    listens = listens.countedListens().map { mixListenOf(it, clock.zone) },
                    yesterdayMix = yesterdayMix(today),
                )
            val sets = kinds.associateWith { builder.build(it, input) }
            database.withTransaction {
                for ((kind, set) in sets) {
                    dao.replace(
                        GeneratedPlaylistEntity(kind = kind.key, day = today, gradient = set.gradient, createdAt = now),
                        set.trackIds,
                    )
                }
            }
        }

        /** Yesterday's daily mix, read before it is replaced; empty when the last mix is older. */
        private suspend fun yesterdayMix(today: Long): Set<Long> {
            val mix = dao.byKind(GeneratedKind.DAILY_MIX.key)?.takeIf { it.day == today - 1 } ?: return emptySet()
            return dao.trackIds(mix.kind).toSet()
        }

        private suspend fun recentPlays(nowMs: Long): Map<Long, Int> {
            val from = nowMs - GeneratedSetBuilder.ON_REPEAT_MS
            return listens.trackPlayStatsSince(from).associate { it.trackId to it.plays }
        }

        override fun observePlaylists(): Flow<List<GeneratedPlaylist>> =
            dao.observeAll()
                .map {
                        rows ->
                    rows.mapNotNull { it.toModel() }.filter { it.isShown && it.isVisibleToday() }
                        .sortedBy { it.kind.ordinal }
                }
                .flowOn(dispatchers.default)

        override fun observePlaylist(kind: GeneratedKind): Flow<GeneratedPlaylist?> =
            dao.observe(kind.key).map { it?.toModel() }.flowOn(dispatchers.default)

        override fun observeTracks(kind: GeneratedKind): Flow<List<Track>> =
            combine(
                dao.observeTracks(kind.key),
                library.observeTrackArtists(),
                library.observeAlbums(),
            ) { t, a, albums ->
                toTracks(t, a.groupBy({ it.trackId }, { it.name }), albums.associate { it.id to it.title })
            }.flowOn(dispatchers.default)

        override suspend fun saveSnapshot(
            kind: GeneratedKind,
            name: String,
        ): SavedSnapshot =
            withContext(dispatchers.io) {
                database.withTransaction {
                    val set = checkNotNull(dao.byKind(kind.key)) { "No generated playlist of kind $kind" }
                    val clean = requireNotNull(PlaylistNames.clean(name)) { "A playlist name must not be blank" }
                    val taken = playlists.observePlaylists().first().map { it.name }
                    val entity =
                        PlaylistEntity(
                            name = PlaylistNames.numbered(clean, taken),
                            createdAt = clock.millis(),
                            coverGradient = set.gradient,
                        )
                    val id = playlists.insertPlaylist(entity)
                    playlists.append(id, dao.trackIds(kind.key))
                    SavedSnapshot(id, entity.name)
                }
            }

        /**
         * A set made before midnight stays in the table until the next [ensureToday] but is not shown; a seasonal set
         * shows in the window of its period.
         */
        private fun GeneratedPlaylist.isVisibleToday(): Boolean {
            val today = LocalDate.now(clock)
            return if (kind.isSeasonal) {
                period?.let { SeasonalWindow.isShown(it, today) } == true
            } else {
                epochDay == today.toEpochDay()
            }
        }

        private fun GeneratedPlaylistRow.toModel(): GeneratedPlaylist? =
            GeneratedKind.fromKey(kind)?.let {
                GeneratedPlaylist(
                    it,
                    day,
                    gradient,
                    trackCount,
                    createdAt,
                    lastStartedAt,
                    CalendarPeriod.fromKey(period),
                )
            }
    }

/** A counted listen for the daily mix: the local day, the artists of the snapshot, whether it came from a set. */
internal fun mixListenOf(
    row: MixListenRow,
    zone: ZoneId,
): MixListen =
    MixListen(
        trackId = row.trackId,
        artists =
            runCatching { (Json.parse(row.artistsSnapshot) as? List<*>).orEmpty().filterIsInstance<String>() }
                .getOrDefault(emptyList()),
        startedAtMs = row.startedAt,
        epochDay = Instant.ofEpochMilli(row.startedAt).atZone(zone).toLocalDate().toEpochDay(),
        fromGenerated = row.sourceKind != null,
    )
