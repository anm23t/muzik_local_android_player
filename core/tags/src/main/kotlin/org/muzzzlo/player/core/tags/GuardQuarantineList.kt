package org.muzzzlo.player.core.tags

import org.muzzzlo.player.core.model.QuarantineList
import javax.inject.Inject

/** [QuarantineList] over the [NativeCallGuard] list. */
internal class GuardQuarantineList
    @Inject
    constructor(private val guard: NativeCallGuard) : QuarantineList {
        override fun keys(): Set<String> = guard.quarantined()

        override fun release(key: String) = guard.release(key)
    }
