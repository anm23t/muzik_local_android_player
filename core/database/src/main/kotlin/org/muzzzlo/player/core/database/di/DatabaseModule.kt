package org.muzzzlo.player.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.muzzzlo.player.core.database.MuzzzloDatabase
import org.muzzzlo.player.core.database.dao.LibraryDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): MuzzzloDatabase = Room.databaseBuilder(context, MuzzzloDatabase::class.java, MuzzzloDatabase.NAME).build()

    @Provides
    fun libraryDao(database: MuzzzloDatabase): LibraryDao = database.libraryDao()
}
