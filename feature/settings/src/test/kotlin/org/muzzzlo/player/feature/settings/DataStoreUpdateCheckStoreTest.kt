package org.muzzzlo.player.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.muzzzlo.player.core.model.UpdateCheckState
import java.io.File

class DataStoreUpdateCheckStoreTest {
    @TempDir lateinit var directory: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { File(directory, "settings.preferences_pb") }
    }
    private val store by lazy { DataStoreUpdateCheckStore(dataStore) }

    @AfterEach
    fun tearDown() = scope.cancel()

    @Test
    fun `an empty store means the user was not asked yet`() =
        runBlocking {
            assertEquals(UpdateCheckState(), store.state.first())
            assertEquals(null, store.state.first().enabled)
        }

    @Test
    fun `every part is saved and read back`() =
        runBlocking {
            store.setEnabled(true)
            store.setChecked(123_456)
            store.setAnnounced("v1.1")

            assertEquals(
                UpdateCheckState(enabled = true, lastCheckMs = 123_456, announcedTag = "v1.1"),
                store.state.first(),
            )

            store.setEnabled(false)
            assertEquals(false, store.state.first().enabled)
        }

    @Test
    fun `the answer does not touch the app settings`() =
        runBlocking {
            store.setEnabled(true)

            assertEquals(
                org.muzzzlo.player.core.model.AppSettings(),
                DataStoreSettingsRepository(dataStore).settings.first(),
            )
        }
}
