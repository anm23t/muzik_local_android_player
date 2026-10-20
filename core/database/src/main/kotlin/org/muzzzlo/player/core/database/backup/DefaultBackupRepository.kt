package org.muzzzlo.player.core.database.backup

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.database.MuzzzloDatabase
import org.muzzzlo.player.core.database.dao.BackupDao
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.dao.ListenEventDao
import org.muzzzlo.player.core.database.dao.TrackIdentity
import org.muzzzlo.player.core.database.entity.ArtistAliasEntity
import org.muzzzlo.player.core.database.entity.ArtistEntity
import org.muzzzlo.player.core.database.entity.ListenEventEntity
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.BackupAlias
import org.muzzzlo.player.core.model.BackupCompound
import org.muzzzlo.player.core.model.BackupData
import org.muzzzlo.player.core.model.BackupEvent
import org.muzzzlo.player.core.model.BackupImportResult
import org.muzzzlo.player.core.model.BackupJson
import org.muzzzlo.player.core.model.BackupRepository
import org.muzzzlo.player.core.model.BackupTrackMark
import org.muzzzlo.player.core.model.CompoundRepository
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.Json
import org.muzzzlo.player.core.model.ListenCsv
import org.muzzzlo.player.core.model.NameNormalizer
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.TrackMatcher
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * [BackupRepository] over the database, the compound names and the settings (ARCHITECTURE.md, section 8).
 * The database part of an import is one transaction; compound names and settings follow it, because they have
 * their own storage. Nothing is written when the text is not a usable backup.
 */
class DefaultBackupRepository
    @Inject
    constructor(
        private val database: MuzzzloDatabase,
        private val library: LibraryDao,
        private val events: ListenEventDao,
        private val backup: BackupDao,
        private val compounds: CompoundRepository,
        private val settings: SettingsRepository,
        private val dispatchers: DispatcherProvider,
        private val clock: Clock,
    ) : BackupRepository {
        override suspend fun exportJson(): String = withContext(dispatchers.io) { BackupJson.encode(collect()) }

        override suspend fun exportEventsCsv(): String =
            withContext(dispatchers.io) {
                ListenCsv.encode(eventsOf()) { Instant.ofEpochMilli(it).toString() }
            }

        override suspend fun importJson(text: String): BackupImportResult =
            withContext(dispatchers.io) {
                val data = BackupJson.decode(text)
                val result = database.withTransaction { mergeDatabase(data) }
                val compoundCount = mergeCompounds(data.compounds)
                applySettings(data.settings)
                result.copy(compounds = compoundCount)
            }

        private suspend fun collect(): BackupData {
            val marked = backup.trackIdentities().filter { it.ratingTenths != null || it.isFavorite }
            return BackupData(
                createdAtMs = clock.millis(),
                settings = settings.settings.first(),
                aliases = backup.aliases().map { BackupAlias(it.aliasKey, it.aliasDisplay, it.artistName) },
                compounds =
                    compounds.observeRules().first()
                        .filter { it.builtinId == null || !it.enabled }
                        .map {
                            BackupCompound(
                                it.displayName,
                                NameNormalizer.key(it.displayName),
                                it.builtinId,
                                it.enabled,
                            )
                        },
                marks =
                    marked.map {
                        BackupTrackMark(it.title, tagValues(it), it.durationMs, it.ratingTenths, it.isFavorite)
                    },
                events = eventsOf(),
            )
        }

        private suspend fun eventsOf(): List<BackupEvent> =
            events.all().map {
                BackupEvent(
                    it.startedAt,
                    it.playedMs,
                    it.durationMs,
                    it.counted,
                    it.skipped,
                    it.titleSnapshot,
                    stringList(it.artistsSnapshot),
                )
            }

        private suspend fun mergeDatabase(data: BackupData): BackupImportResult {
            val aliases = mergeAliases(data.aliases)
            val tracks = backup.trackIdentities()
            val matcher = TrackMatcher(tracks, { it.title }, ::tagValues, { it.durationMs })
            var matchedMarks = 0
            for (mark in data.marks) {
                val track = matcher.find(mark.title, mark.artistTags, mark.durationMs)
                if (track != null && mergeMark(track, mark)) matchedMarks++
            }
            val newEvents = mergeEvents(data.events, matcher)
            return BackupImportResult(aliases, 0, matchedMarks, newEvents, data.marks.size - matchedMarks)
        }

        private suspend fun mergeAliases(aliases: List<BackupAlias>): Int {
            var added = 0
            for (alias in aliases) {
                if (library.aliasArtistId(alias.aliasKey) != null) continue
                val artistKey = NameNormalizer.key(alias.artistName)
                val artistId =
                    library.artistIdByKey(artistKey)
                        ?: library.insertArtist(ArtistEntity(name = alias.artistName, nameKey = artistKey))
                library.insertAlias(
                    ArtistAliasEntity(
                        aliasKey = alias.aliasKey,
                        aliasDisplay = alias.aliasDisplay,
                        artistId = artistId,
                    ),
                )
                added++
            }
            return added
        }

        /** A rating fills an empty one and never replaces the user's newer choice; favorite is added, never removed. */
        private suspend fun mergeMark(
            track: TrackIdentity,
            mark: BackupTrackMark,
        ): Boolean {
            val rating = track.ratingTenths ?: mark.ratingTenths
            val favorite = track.isFavorite || mark.favorite
            if (rating == track.ratingTenths && favorite == track.isFavorite) return false
            backup.setMark(track.id, rating, favorite)
            return true
        }

        private suspend fun mergeEvents(
            incoming: List<BackupEvent>,
            matcher: TrackMatcher<TrackIdentity>,
        ): Int {
            val known = eventsOf().mapTo(HashSet()) { it.key }
            var added = 0
            for (event in incoming) {
                if (!known.add(event.key)) continue
                val trackId = matcher.findUnique(event.title, event.durationMs)?.id
                events.insert(
                    ListenEventEntity(
                        trackId = trackId,
                        startedAt = event.startedAtMs,
                        playedMs = event.playedMs,
                        durationMs = event.durationMs,
                        counted = event.counted,
                        skipped = event.skipped,
                        titleSnapshot = event.title,
                        artistsSnapshot = Json.write(event.artists),
                    ),
                )
                added++
            }
            return added
        }

        private suspend fun mergeCompounds(incoming: List<BackupCompound>): Int {
            var changed = 0
            for (rule in incoming) {
                if (rule.builtinId == null) {
                    if (compounds.remember(rule.displayName) >= 0) changed++
                } else if (!rule.enabled) {
                    val existing = compounds.observeRules().first().firstOrNull { it.builtinId == rule.builtinId }
                    if (existing != null && existing.enabled) {
                        compounds.remove(existing.id)
                        changed++
                    }
                }
            }
            return changed
        }

        private suspend fun applySettings(incoming: AppSettings) {
            val current = settings.settings.first()
            val folders = current.excludedFolders + incoming.excludedFolders
            folders.forEach { settings.setFolderIncluded(it, it !in incoming.excludedFolders) }
            settings.setMinTrackSeconds(incoming.minTrackSeconds)
            settings.setId3v2VersionForNewTags(incoming.id3v2VersionForNewTags)
            settings.setConfirmBeforeWrite(incoming.confirmBeforeWrite)
            settings.setLanguage(incoming.language)
            settings.setDynamicAccent(incoming.dynamicAccent)
            settings.setGapless(incoming.gapless)
            settings.setReplayGain(incoming.replayGain)
            settings.setCrossfade(incoming.crossfade)
            settings.setPlaybackBehavior(incoming.playbackBehavior)
            settings.setStats(incoming.stats)
        }

        private fun tagValues(track: TrackIdentity) = stringList(track.artistTagValues)

        private fun stringList(json: String): List<String> =
            runCatching {
                (
                    Json.parse(
                        json,
                    ) as? List<*>
                ).orEmpty().filterIsInstance<String>()
            }.getOrDefault(emptyList())
    }
