package org.muzzzlo.player.core.tags.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.QuarantineList
import org.muzzzlo.player.core.model.TagReader
import org.muzzzlo.player.core.model.TagWriter
import org.muzzzlo.player.core.tags.GuardQuarantineList
import org.muzzzlo.player.core.tags.MediaStoreOriginalFiles
import org.muzzzlo.player.core.tags.NativeCallGuard
import org.muzzzlo.player.core.tags.OriginalFiles
import org.muzzzlo.player.core.tags.SafeTagWriter
import org.muzzzlo.player.core.tags.TagFileRecovery
import org.muzzzlo.player.core.tags.TagLibBridge
import org.muzzzlo.player.core.tags.TagLibTagReader
import org.muzzzlo.player.core.tags.WriteJournal
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TagsModule {
    @Binds
    @Singleton
    abstract fun tagReader(impl: TagLibTagReader): TagReader

    @Binds
    abstract fun quarantineList(impl: GuardQuarantineList): QuarantineList

    companion object {
        /** Markers and the quarantine list; `noBackupFilesDir`, because they describe this device's files only. */
        @Provides
        @Singleton
        fun nativeCallGuard(
            @ApplicationContext context: Context,
        ): NativeCallGuard = NativeCallGuard(File(context.noBackupFilesDir, "native-guard"))

        /** Working copies, backups and the journal of tag writes; device-only too. */
        @Provides
        @Singleton
        fun writeJournal(
            @ApplicationContext context: Context,
        ): WriteJournal = WriteJournal(File(context.noBackupFilesDir, "tag-write"))

        @Provides
        @Singleton
        fun originalFiles(
            @ApplicationContext context: Context,
        ): OriginalFiles = MediaStoreOriginalFiles(context)

        @Provides
        @Singleton
        fun tagFileRecovery(
            guard: NativeCallGuard,
            journal: WriteJournal,
            files: OriginalFiles,
        ): TagFileRecovery = TagFileRecovery(guard, journal, files)

        @Provides
        @Singleton
        fun tagWriter(
            bridge: TagLibBridge,
            guard: NativeCallGuard,
            journal: WriteJournal,
            files: OriginalFiles,
            recovery: TagFileRecovery,
            dispatchers: DispatcherProvider,
        ): TagWriter = SafeTagWriter(bridge, guard, journal, files, recovery, dispatchers)

        @Provides
        fun tagLibBridge(): TagLibBridge = TagLibBridge()
    }
}
