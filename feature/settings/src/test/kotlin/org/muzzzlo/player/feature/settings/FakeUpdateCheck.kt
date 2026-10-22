package org.muzzzlo.player.feature.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.muzzzlo.player.core.model.ReleaseLookup
import org.muzzzlo.player.core.model.ReleaseSource
import org.muzzzlo.player.core.model.UpdateCheckState
import org.muzzzlo.player.core.model.UpdateCheckStore

/** The update check state in memory. */
class FakeUpdateStore(initial: UpdateCheckState = UpdateCheckState()) : UpdateCheckStore {
    val current = MutableStateFlow(initial)
    override val state: Flow<UpdateCheckState> = current

    override suspend fun setEnabled(enabled: Boolean) {
        current.value = current.value.copy(enabled = enabled)
    }

    override suspend fun setChecked(atMs: Long) {
        current.value = current.value.copy(lastCheckMs = atMs)
    }

    override suspend fun setAnnounced(tag: String) {
        current.value = current.value.copy(announcedTag = tag)
    }
}

/** Answers with [lookup] and counts the requests. */
class FakeReleaseSource(var lookup: ReleaseLookup = ReleaseLookup.NoRelease) : ReleaseSource {
    var calls = 0

    override suspend fun latest(): ReleaseLookup {
        calls++
        return lookup
    }
}
