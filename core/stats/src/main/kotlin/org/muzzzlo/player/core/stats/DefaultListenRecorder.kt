package org.muzzzlo.player.core.stats

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.database.dao.ListenEventDao
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.FinishedListen
import org.muzzzlo.player.core.model.ListenRecorder
import org.muzzzlo.player.core.model.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Takes the listens of the player and writes them off the player thread. The scope lives as long as the app, so a
 * listen reported while the playback service is being destroyed is still stored.
 */
@Singleton
internal class DefaultListenRecorder
    @Inject
    constructor(
        dao: ListenEventDao,
        settings: SettingsRepository,
        dispatchers: DispatcherProvider,
    ) : ListenRecorder {
        private val writer = ListenWriter(dao, settings.settings.map { it.stats })
        private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

        override fun record(listen: FinishedListen) {
            scope.launch { writer.write(listen) }
        }
    }
