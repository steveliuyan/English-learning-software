package com.example.englishlearning.di

import android.content.Context
import com.example.englishlearning.export.WorksheetPdfRenderer
import com.example.englishlearning.export.WorksheetPdfWriter
import androidx.room.Room
import com.example.englishlearning.ai.AiProfileIdFactory
import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.RoomAiProfileRepository
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.security.AndroidKeyStoreSecretStore
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.core.time.SystemClockProvider
import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.GetOrCreateTodayPlanUseCase
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.PlaceholderWordCardSource
import com.example.englishlearning.learning.PlanCardSource
import com.example.englishlearning.learning.RoomLearningEventRepository
import com.example.englishlearning.learning.StoredPlanCardSource
import com.example.englishlearning.learning.SubmitCardFeedbackUseCase
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.worksheet.BuildWorksheetContentUseCase
import com.example.englishlearning.learning.worksheet.WorksheetDocumentBuilder
import com.example.englishlearning.learning.worksheet.WorksheetPaginator
import com.example.englishlearning.learning.domain.FsrsReviewScheduler
import com.example.englishlearning.learning.RoomTodayPlanRepository
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.ui.TodayPlanUseCaseContract
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.LearningSettingsRepository
import com.example.englishlearning.learning.RoomLearningProfileRepository
import com.example.englishlearning.learning.RoomLearningSettingsRepository
import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.WordBookMetadataAssetSource
import com.example.englishlearning.profile.LocalProfileRepository
import com.example.englishlearning.reading.ArticleRepository
import com.example.englishlearning.reading.RoomArticleRepository
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.RoomLocalProfileRepository
import com.example.englishlearning.reading.ReadingPreferenceRepository
import com.example.englishlearning.reading.RoomReadingPreferenceRepository
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "english-learning.db")
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
    @Provides @Singleton fun provideSecretStore(@ApplicationContext context: Context): SecretStore = AndroidKeyStoreSecretStore(context)
    @Provides @Singleton fun provideClock(): ClockProvider = SystemClockProvider()
    @Provides @Named("io") fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    @Provides @Singleton fun provideProfileRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): LocalProfileRepository = RoomLocalProfileRepository(database, dispatcher)
    @Provides fun provideCreateUseCase(repository: LocalProfileRepository, clock: ClockProvider): CreateLocalProfileUseCase = CreateLocalProfileUseCase(repository, clock)
    @Provides @Singleton fun provideLearningProfileRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): LearningProfileRepository = RoomLearningProfileRepository(database, dispatcher)
    @Provides @Singleton fun provideLearningSettingsRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): LearningSettingsRepository = RoomLearningSettingsRepository(database, dispatcher)
    @Provides fun provideGetLearningSettingsUseCase(repository: LearningSettingsRepository): GetLearningSettingsUseCase = GetLearningSettingsUseCase(repository)
    @Provides fun provideSaveLearningSettingsUseCase(repository: LearningSettingsRepository): SaveLearningSettingsUseCase = SaveLearningSettingsUseCase(repository)
    @Provides fun provideSelectLearningSetupUseCase(repository: LearningProfileRepository): SelectWordBookAndSetDailyTargetUseCase = SelectWordBookAndSetDailyTargetUseCase(repository)
    @Provides fun provideWordBookMetadataAssetSource(@ApplicationContext context: Context): WordBookMetadataAssetSource = WordBookMetadataAssetSource { context.assets.open("wordbooks/metadata.json").bufferedReader().use { it.readText() } }
    @Provides fun provideSeedWordBooksUseCase(source: WordBookMetadataAssetSource, repository: LearningProfileRepository): SeedWordBooksUseCase = SeedWordBooksUseCase(source, repository)
    @Provides @Singleton fun provideTodayPlanRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): TodayPlanRepository = RoomTodayPlanRepository(database, dispatcher)
    @Provides @Singleton fun provideArticleRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): ArticleRepository = RoomArticleRepository(database, dispatcher)
    @Provides @Singleton fun provideLearningEventRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): LearningEventRepository = RoomLearningEventRepository(database, dispatcher)
    @Provides @Singleton fun provideFsrsReviewScheduler(): FsrsReviewScheduler = FsrsReviewScheduler()
    @Provides @Singleton fun provideSubmitCardFeedbackUseCase(events: LearningEventRepository, clock: ClockProvider, scheduler: FsrsReviewScheduler): SubmitCardFeedbackUseCase = SubmitCardFeedbackUseCase(repository = events, clock = clock, scheduler = scheduler)
    @Provides @Singleton fun provideEventIdFactory(): EventIdFactory = EventIdFactory.Random
    @Provides @Singleton fun provideWordCardSource(): WordCardSource = PlaceholderWordCardSource()
    @Provides @Singleton fun providePlanCardSource(content: WordCardSource, events: LearningEventRepository): PlanCardSource = StoredPlanCardSource(content, events)
    @Provides fun provideBuildWorksheetContentUseCase(plans: TodayPlanRepository, events: LearningEventRepository, content: WordCardSource): BuildWorksheetContentUseCase = BuildWorksheetContentUseCase(plans, events, content)
    @Provides fun provideWorksheetDocumentBuilder(): WorksheetDocumentBuilder = WorksheetDocumentBuilder()
    @Provides fun provideWorksheetPaginator(): WorksheetPaginator = WorksheetPaginator()
    @Provides fun provideWorksheetPdfWriter(@ApplicationContext context: Context): WorksheetPdfWriter = WorksheetPdfRenderer(context)
    @Provides fun provideTodayPlanUseCase(learning: LearningProfileRepository, plans: TodayPlanRepository, cards: PlanCardSource, clock: ClockProvider): GetOrCreateTodayPlanUseCase = GetOrCreateTodayPlanUseCase(learning, plans, cards, clock)
    @Provides fun provideTodayPlanUseCaseContract(useCase: GetOrCreateTodayPlanUseCase): TodayPlanUseCaseContract = TodayPlanUseCaseContract { profileId -> useCase(profileId) }
    @Provides @Singleton fun provideReadingPreferenceRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): ReadingPreferenceRepository = RoomReadingPreferenceRepository(database, dispatcher)
    @Provides @Singleton fun provideAiProfileRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): AiProfileRepository = RoomAiProfileRepository(database, dispatcher)
    @Provides fun provideAiProfileSecretUseCase(secretStore: SecretStore): AiProfileSecretUseCase = AiProfileSecretUseCase(secretStore)
    @Provides @Singleton fun provideAiProfileIdFactory(): AiProfileIdFactory = AiProfileIdFactory.Random
}
