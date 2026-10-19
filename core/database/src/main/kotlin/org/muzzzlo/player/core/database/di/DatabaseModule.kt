package org.muzzzlo.player.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.muzzzlo.player.core.database.MIGRATION_1_2
import org.muzzzlo.player.core.database.MIGRATION_2_3
import org.muzzzlo.player.core.database.MIGRATION_3_4
import org.muzzzlo.player.core.database.MIGRATION_4_5
import org.muzzzlo.player.core.database.MIGRATION_5_6
import org.muzzzlo.player.core.database.MIGRATION_6_7
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
    ): MuzzzloDatabase =
        Room.databaseBuilder(context, MuzzzloDatabase::class.java, MuzzzloDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()

    @Provides
    fun libraryDao(database: MuzzzloDatabase): LibraryDao = database.libraryDao()
}
