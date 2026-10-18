package org.muzzzlo.player.core.tags

import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.EmbeddedPicture
import org.muzzzlo.player.core.model.FileTags
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TagReader
import org.muzzzlo.player.core.model.TagSource
import javax.inject.Inject

/**
 * [TagReader] on top of TagLib.
 *
 * Every native call runs behind [NativeCallGuard], so a file that crashes TagLib is quarantined after the restart
 * instead of crashing every scan. Crash recovery runs once, before the first read of the process.
 */
internal class TagLibTagReader
    @Inject
    constructor(
        private val bridge: TagLibBridge,
        private val guard: NativeCallGuard,
        private val dispatchers: DispatcherProvider,
    ) : TagReader {
        private val crashRecovery = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { guard.recoverAfterCrash() }

        override suspend fun readTags(
            source: TagSource,
            withAudioInfo: Boolean,
        ): TagReadResult =
            withContext(dispatchers.io) {
                if (isQuarantined(source)) return@withContext TagReadResult.Quarantined
                val native = guard.runGuarded(source.key) { bridge.readTags(source, withAudioInfo) }
                val format = native?.format ?: return@withContext TagReadResult.Unreadable
                TagReadResult.Success(
                    FileTags(
                        format = format,
                        id3v2Version = native.id3v2Version,
                        tags = PropertyMapper.toTrackTags(native.properties, native.pictureCount),
                        audio = native.audio,
                    ),
                )
            }

        override suspend fun readCover(source: TagSource): EmbeddedPicture? =
            withContext(dispatchers.io) {
                if (isQuarantined(source)) return@withContext null
                guard.runGuarded(source.key) { bridge.readCover(source) }
            }

        private fun isQuarantined(source: TagSource): Boolean {
            crashRecovery.value
            return guard.isQuarantined(source.key)
        }
    }
