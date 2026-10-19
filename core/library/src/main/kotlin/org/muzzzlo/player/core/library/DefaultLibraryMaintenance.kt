package org.muzzzlo.player.core.library

import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.LibraryFolder
import org.muzzzlo.player.core.model.LibraryMaintenance
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.QuarantineList
import org.muzzzlo.player.core.model.QuarantinedFile
import javax.inject.Inject

/**
 * [LibraryMaintenance] for the settings screen. A quarantined file is retried by taking it off the list and
 * scanning: the scanner reads damaged files again (see [LibraryIndexer]).
 */
internal class DefaultLibraryMaintenance
    @Inject
    constructor(
        private val source: MediaStoreSource,
        private val dao: LibraryDao,
        private val quarantine: QuarantineList,
        private val library: LibraryRepository,
        private val dispatchers: DispatcherProvider,
    ) : LibraryMaintenance {
        override suspend fun folders(): List<LibraryFolder> = withContext(dispatchers.io) { source.folders() }

        override suspend fun quarantined(): List<QuarantinedFile> =
            withContext(dispatchers.io) {
                val uris = quarantine.keys().toList()
                val titles =
                    uris.chunked(
                        SQL_VARIABLE_CHUNK,
                    ).flatMap { dao.titlesByUris(it) }.associate { it.uri to it.title }
                uris.map {
                    QuarantinedFile(
                        it,
                        titles[it] ?: it.substringAfterLast('/'),
                    )
                }.sortedBy { it.name.lowercase() }
            }

        override suspend fun retryQuarantined(uri: String) {
            withContext(dispatchers.io) { quarantine.release(uri) }
            library.scan()
        }

        private companion object {
            const val SQL_VARIABLE_CHUNK = 500
        }
    }
