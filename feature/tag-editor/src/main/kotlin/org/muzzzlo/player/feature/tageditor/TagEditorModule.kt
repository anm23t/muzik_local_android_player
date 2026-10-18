package org.muzzzlo.player.feature.tageditor

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TagEditorModule {
    @Binds
    abstract fun pictureReader(impl: ContentPictureReader): PictureReader

    @Binds
    abstract fun writePermissions(impl: SystemWritePermissions): WritePermissions
}
