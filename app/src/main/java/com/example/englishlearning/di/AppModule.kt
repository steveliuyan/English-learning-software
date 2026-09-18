package com.example.englishlearning.di

import android.content.Context
import androidx.room.Room
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.core.time.SystemClockProvider
import com.example.englishlearning.profile.LocalProfileRepository
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.RoomLocalProfileRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "english-learning.db").addMigrations(*AppDatabase.MIGRATIONS).build()
    @Provides @Singleton fun provideClock(): ClockProvider = SystemClockProvider()
    @Provides @Named("io") fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    @Provides @Singleton fun provideProfileRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): LocalProfileRepository = RoomLocalProfileRepository(database, dispatcher)
    @Provides fun provideCreateUseCase(repository: LocalProfileRepository, clock: ClockProvider): CreateLocalProfileUseCase = CreateLocalProfileUseCase(repository, clock)
}
