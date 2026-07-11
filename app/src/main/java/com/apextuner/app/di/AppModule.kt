package com.apextuner.app.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.apextuner.data.db.ApexDatabase
import com.apextuner.data.db.GameDao
import com.apextuner.data.db.LogDao
import com.apextuner.data.db.ProfileDao
import com.apextuner.data.datastore.SettingsDataStore
import com.apextuner.data.repository.GameRepository
import com.apextuner.data.repository.LogRepository
import com.apextuner.data.repository.ProfileRepository
import com.apextuner.data.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): ApexDatabase =
        Room.databaseBuilder(ctx, ApexDatabase::class.java, ApexDatabase.DB_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideProfileDao(db: ApexDatabase): ProfileDao = db.profileDao()
    @Provides fun provideGameDao(db: ApexDatabase): GameDao = db.gameDao()
    @Provides fun provideLogDao(db: ApexDatabase): LogDao = db.logDao()

    @Provides @Singleton
    fun provideSettingsDataStore(@ApplicationContext ctx: Context): SettingsDataStore =
        SettingsDataStore(ctx)

    @Provides @Singleton
    fun provideProfileRepository(dao: ProfileDao): ProfileRepository = ProfileRepository(dao)

    @Provides @Singleton
    fun provideGameRepository(dao: GameDao): GameRepository = GameRepository(dao)

    @Provides @Singleton
    fun provideLogRepository(dao: LogDao): LogRepository = LogRepository(dao)

    @Provides @Singleton
    fun provideSettingsRepository(store: SettingsDataStore): SettingsRepository =
        SettingsRepository(store)

    @Provides @Singleton
    fun provideWorkManager(@ApplicationContext ctx: Context): WorkManager =
        WorkManager.getInstance(ctx)
}
