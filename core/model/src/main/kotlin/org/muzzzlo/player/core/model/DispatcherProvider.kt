package org.muzzzlo.player.core.model

import kotlinx.coroutines.CoroutineDispatcher

/** Dispatchers are injected so tests can replace them. */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
}
