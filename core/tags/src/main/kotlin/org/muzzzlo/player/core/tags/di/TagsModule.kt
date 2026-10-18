package org.muzzzlo.player.core.tags.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.muzzzlo.player.core.model.TagReader
import org.muzzzlo.player.core.tags.NativeCallGuard
import org.muzzzlo.player.core.tags.TagLibBridge
import org.muzzzlo.player.core.tags.TagLibTagReader
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TagsModule {
    @Binds
    @Singleton
    abstract fun tagReader(impl: TagLibTagReader): TagReader

    companion object {
        /** Markers and the quarantine list; `noBackupFilesDir`, because they describe this device's files only. */
        @Provides
        @Singleton
        fun nativeCallGuard(
            @ApplicationContext context: Context,
        ): NativeCallGuard = NativeCallGuard(File(context.noBackupFilesDir, "native-guard"))

        @Provides
        fun tagLibBridge(): TagLibBridge = TagLibBridge()
    }
}
