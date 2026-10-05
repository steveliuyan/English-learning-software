package com.example.englishlearning.di

import android.content.Context
import com.example.englishlearning.export.WorksheetPdfRenderer
import com.example.englishlearning.export.WorksheetPdfWriter
import androidx.room.Room
import com.example.englishlearning.ai.AiPreferenceRepository
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.DefaultTextProfileSelector
import com.example.englishlearning.ai.AiProfileIdFactory
import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.RoomAiPreferenceRepository
import com.example.englishlearning.ai.RoomAiProfileRepository
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.ai.net.AudioHttpTransport
import com.example.englishlearning.ai.net.UrlConnectionAiHttpTransport
import com.example.englishlearning.ai.net.UrlConnectionAudioHttpTransport
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.security.AndroidKeyStoreSecretStore
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.core.time.SystemClockProvider
import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.GetOrCreateTodayPlanUseCase
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.LearningRecordRepository
import com.example.englishlearning.learning.RoomLearningRecordRepository
import com.example.englishlearning.learning.RoomVocabularyRepository
import com.example.englishlearning.learning.VocabularyRepository
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
import com.example.englishlearning.language.RoomSpeechPreferenceRepository
import com.example.englishlearning.language.SpeechPreferenceRepository
import com.example.englishlearning.language.domain.MiMoPronunciationProviderFactory
import com.example.englishlearning.language.domain.OpenAiPronunciationProviderFactory
import com.example.englishlearning.language.domain.PronunciationProvider
import com.example.englishlearning.language.domain.PronunciationRouter
import com.example.englishlearning.language.infrastructure.AndroidTextToSpeechProvider
import com.example.englishlearning.language.infrastructure.AndroidAudioPlayer
import com.example.englishlearning.language.infrastructure.AudioPlayer
import com.example.englishlearning.language.infrastructure.MiMoPronunciationProvider
import com.example.englishlearning.language.infrastructure.OpenAiCompatiblePronunciationProvider
import com.example.englishlearning.learning.RoomTodayPlanRepository
import com.example.englishlearning.learning.LearningStatsRepository
import com.example.englishlearning.learning.RoomLearningStatsRepository
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.ui.TodayPlanUseCaseContract
import com.example.englishlearning.wordbook.CompositeWordCardSource
import com.example.englishlearning.wordbook.ImportedWordBookSource
import com.example.englishlearning.wordbook.BundledWordBookSource
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.SearchVocabularyOperator
import com.example.englishlearning.learning.SearchVocabularyUseCase
import com.example.englishlearning.learning.VocabularySearchHistoryRepository
import com.example.englishlearning.learning.RoomVocabularySearchHistoryRepository
import com.example.englishlearning.learning.VocabularySearchIndexRepository
import com.example.englishlearning.learning.RoomVocabularySearchIndexRepository
import com.example.englishlearning.learning.LearningSettingsRepository
import com.example.englishlearning.learning.RoomLearningProfileRepository
import com.example.englishlearning.learning.RoomLearningSettingsRepository
import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.WordBookMetadataAssetSource
import com.example.englishlearning.profile.LocalProfileRepository
import com.example.englishlearning.reading.ArticleIdFactory
import com.example.englishlearning.reading.ArticleRepository
import com.example.englishlearning.reading.RoomArticleRepository
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.RoomLocalProfileRepository
import com.example.englishlearning.reading.FetchArticleUseCase
import com.example.englishlearning.reading.GenerateArticleUseCase
import com.example.englishlearning.reading.ImportArticleUseCase
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
    @Provides @Singleton fun provideVocabularySearchHistoryRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): VocabularySearchHistoryRepository = RoomVocabularySearchHistoryRepository(database, dispatcher)
    @Provides @Singleton fun provideVocabularySearchIndexRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): VocabularySearchIndexRepository = RoomVocabularySearchIndexRepository(database, dispatcher)
    @Provides fun provideSearchVocabularyUseCase(history: VocabularySearchHistoryRepository, index: VocabularySearchIndexRepository, clock: ClockProvider): SearchVocabularyOperator = SearchVocabularyUseCase(history = history, index = index, clock = clock)
    /** 索引只存结果列表要显示的列，详情所需的完整词卡由这个用例在点开时回源取。 */
    @Provides fun provideOpenVocabularySearchResultUseCase(cards: WordCardSource): com.example.englishlearning.learning.OpenVocabularySearchResultUseCase = com.example.englishlearning.learning.OpenVocabularySearchResultUseCase(cards)
    @Provides fun provideRefreshVocabularySearchIndexUseCase(profiles: LearningProfileRepository, cards: WordCardSource, index: VocabularySearchIndexRepository, bundled: com.example.englishlearning.learning.BundledWordBookIdSource, imported: com.example.englishlearning.learning.ImportedWordBookIdSource): com.example.englishlearning.learning.RefreshVocabularySearchIndexUseCase = com.example.englishlearning.learning.RefreshVocabularySearchIndexUseCase(profiles, cards, index, bundled, imported)
    @Provides fun provideGetLearningSettingsUseCase(repository: LearningSettingsRepository): GetLearningSettingsUseCase = GetLearningSettingsUseCase(repository)
    @Provides fun provideSaveLearningSettingsUseCase(repository: LearningSettingsRepository): SaveLearningSettingsUseCase = SaveLearningSettingsUseCase(repository)
    @Provides fun provideSelectLearningSetupUseCase(repository: LearningProfileRepository): SelectWordBookAndSetDailyTargetUseCase = SelectWordBookAndSetDailyTargetUseCase(repository)
    @Provides fun provideWordBookMetadataAssetSource(@ApplicationContext context: Context): WordBookMetadataAssetSource = WordBookMetadataAssetSource { context.assets.open("wordbooks/metadata.json").bufferedReader().use { it.readText() } }
    @Provides fun provideSeedWordBooksUseCase(source: WordBookMetadataAssetSource, repository: LearningProfileRepository): SeedWordBooksUseCase = SeedWordBooksUseCase(source, repository)
    /** 内置词书 id 以 `assets/wordbooks/metadata.json` 为唯一事实来源，不再在代码里重复维护一份清单。 */
    @Provides @Singleton fun provideBundledWordBookIdSource(source: WordBookMetadataAssetSource): com.example.englishlearning.learning.BundledWordBookIdSource = com.example.englishlearning.learning.BundledWordBookIdSource { com.example.englishlearning.learning.bundledWordBookIds(source.read()) }
    @Provides @Singleton fun provideImportedWordBookIdSource(@ApplicationContext context: Context): com.example.englishlearning.learning.ImportedWordBookIdSource = com.example.englishlearning.learning.ImportedWordBookIdSource { com.example.englishlearning.wordbook.importedBookDirectories(java.io.File(context.filesDir, "wordbooks")).map { it.name }.toSet() }
    @Provides @Singleton fun provideWordBookDeletionService(repository: LearningProfileRepository, @ApplicationContext context: Context, bundled: com.example.englishlearning.learning.BundledWordBookIdSource): com.example.englishlearning.learning.WordBookDeletionService = com.example.englishlearning.learning.WordBookDeletionService(repository, java.io.File(context.filesDir, "wordbooks")) { bundled.ids() }
    @Provides @Singleton fun provideWordBookProgressMigrationService(cards: WordCardSource, events: LearningEventRepository): com.example.englishlearning.learning.WordBookProgressMigrationService = com.example.englishlearning.learning.WordBookProgressMigrationService(cards, events)
    @Provides @Singleton fun provideWordBookProgressMigrationCoordinator(service: com.example.englishlearning.learning.WordBookProgressMigrationService): com.example.englishlearning.learning.WordBookProgressMigrationCoordinator = com.example.englishlearning.learning.WordBookProgressMigrationCoordinator(service)
    @Provides @Singleton fun provideTodayPlanRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): TodayPlanRepository = RoomTodayPlanRepository(database, dispatcher)
    @Provides @Singleton fun provideLearningStatsRepository(database: AppDatabase, clock: ClockProvider, @Named("io") dispatcher: CoroutineDispatcher): LearningStatsRepository = RoomLearningStatsRepository(database, clock, dispatcher)
    @Provides @Singleton fun provideArticleRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): ArticleRepository = RoomArticleRepository(database, dispatcher)
    @Provides @Singleton fun provideLearningEventRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): LearningEventRepository = RoomLearningEventRepository(database, dispatcher)
    @Provides @Singleton fun provideLearningRecordRepository(database: AppDatabase, cards: WordCardSource, clock: ClockProvider, @Named("io") dispatcher: CoroutineDispatcher): LearningRecordRepository = RoomLearningRecordRepository(database, cards, clock, dispatcher)
    @Provides @Singleton fun provideVocabularyRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): VocabularyRepository = RoomVocabularyRepository(database, dispatcher)
    @Provides @Singleton fun provideFsrsReviewScheduler(): FsrsReviewScheduler = FsrsReviewScheduler()
    @Provides @Singleton fun provideSubmitCardFeedbackUseCase(events: LearningEventRepository, clock: ClockProvider, scheduler: FsrsReviewScheduler): SubmitCardFeedbackUseCase = SubmitCardFeedbackUseCase(repository = events, clock = clock, scheduler = scheduler)
    @Provides @Singleton fun provideEventIdFactory(): EventIdFactory = EventIdFactory.Random
    /**
     * 词卡内容端口：**导入册优先、内置正式册兜底**。
     *
     * 绑定合成实现而不是单个来源，导入的 `.wbpack` 才能真正参与学习流程；只绑
     * 没有合成来源的话，导入成功也只会在设置页多出一个名字。
     */
    @Provides @Singleton
    fun provideWordCardSource(
        @ApplicationContext context: Context,
        @Named("io") dispatcher: CoroutineDispatcher,
    ): WordCardSource =
        CompositeWordCardSource(
            imported = ImportedWordBookSource(java.io.File(context.filesDir, "wordbooks"), dispatcher),
            bundled = BundledWordBookSource(
                assets = context.assets,
                root = java.io.File(context.filesDir, "bundled-wordbooks"),
                io = dispatcher,
            ),
        )
    @Provides @Singleton
    fun provideSystemPronunciationProvider(@ApplicationContext context: Context): AndroidTextToSpeechProvider = AndroidTextToSpeechProvider(context)

    @Provides @Singleton
    fun provideAudioPlayer(@ApplicationContext context: Context): AudioPlayer = AndroidAudioPlayer(context)

    @Provides @Singleton
    fun provideMiMoPronunciationProviderFactory(
        profiles: AiProfileRepository,
        secrets: AiProfileSecretUseCase,
        transport: AudioHttpTransport,
        player: AudioPlayer,
    ): MiMoPronunciationProviderFactory = MiMoPronunciationProviderFactory { profileId ->
        MiMoPronunciationProvider(profiles, secrets, transport, player, profileId)
    }

    @Provides @Singleton
    fun provideOpenAiPronunciationProviderFactory(
        profiles: AiProfileRepository,
        secrets: AiProfileSecretUseCase,
        transport: AudioHttpTransport,
        player: AudioPlayer,
    ): OpenAiPronunciationProviderFactory = OpenAiPronunciationProviderFactory { profileId ->
        OpenAiCompatiblePronunciationProvider(
            profileId = profileId,
            voice = "alloy",
            responseFormat = "mp3",
            profiles = profiles,
            secrets = secrets,
            transport = transport,
            player = player,
        )
    }

    @Provides @Singleton
    fun providePronunciationProvider(
        system: AndroidTextToSpeechProvider,
        miMoFactory: MiMoPronunciationProviderFactory,
        openAiFactory: OpenAiPronunciationProviderFactory,
        preferences: SpeechPreferenceRepository,
    ): PronunciationProvider = PronunciationRouter(system, miMoFactory, openAiFactory, preferences)
    @Provides @Singleton fun providePlanCardSource(content: WordCardSource, events: LearningEventRepository): PlanCardSource = StoredPlanCardSource(content, events)
    @Provides fun provideBuildWorksheetContentUseCase(plans: TodayPlanRepository, events: LearningEventRepository, content: WordCardSource): BuildWorksheetContentUseCase = BuildWorksheetContentUseCase(plans, events, content)
    @Provides fun provideWorksheetDocumentBuilder(): WorksheetDocumentBuilder = WorksheetDocumentBuilder()
    @Provides fun provideWorksheetPaginator(): WorksheetPaginator = WorksheetPaginator()
    @Provides fun provideWorksheetPdfWriter(@ApplicationContext context: Context): WorksheetPdfWriter = WorksheetPdfRenderer(context)
    @Provides fun provideTodayPlanUseCase(learning: LearningProfileRepository, plans: TodayPlanRepository, cards: PlanCardSource, clock: ClockProvider): GetOrCreateTodayPlanUseCase = GetOrCreateTodayPlanUseCase(learning, plans, cards, clock)
    @Provides fun provideTodayPlanUseCaseContract(useCase: GetOrCreateTodayPlanUseCase): TodayPlanUseCaseContract = TodayPlanUseCaseContract { profileId -> useCase(profileId) }
    @Provides @Singleton fun provideReadingPreferenceRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): ReadingPreferenceRepository = RoomReadingPreferenceRepository(database, dispatcher)

    @Provides @Singleton fun provideReadingCompletionRepository(database: AppDatabase, clock: com.example.englishlearning.core.time.ClockProvider, @Named("io") dispatcher: CoroutineDispatcher): com.example.englishlearning.reading.ReadingCompletionRepository = com.example.englishlearning.reading.RoomReadingCompletionRepository(database, clock, dispatcher)
    @Provides @Singleton fun provideAiProfileRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): AiProfileRepository = RoomAiProfileRepository(database, dispatcher)
    @Provides @Singleton fun provideAiPreferenceRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): AiPreferenceRepository = RoomAiPreferenceRepository(database, dispatcher)
    @Provides @Singleton fun provideDefaultTextProfileSelector(preferences: AiPreferenceRepository, profiles: AiProfileRepository, secrets: AiProfileSecretUseCase): DefaultTextProfileResolver = DefaultTextProfileSelector(preferences, profiles, secrets)
    @Provides @Singleton fun provideDefaultImageProfileSelector(preferences: AiPreferenceRepository, profiles: AiProfileRepository, secrets: AiProfileSecretUseCase): com.example.englishlearning.ai.DefaultImageProfileResolver = com.example.englishlearning.ai.DefaultImageProfileSelector(preferences, profiles, secrets)
    @Provides @Singleton fun provideWordAiNoteRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): com.example.englishlearning.wordqa.WordAiNoteRepository = com.example.englishlearning.wordqa.RoomWordAiNoteRepository(database, dispatcher)
    @Provides @Singleton fun provideWordQaUseCase(defaultTextProfile: DefaultTextProfileResolver, secrets: AiProfileSecretUseCase, transport: AiHttpTransport): com.example.englishlearning.wordqa.WordQaUseCase = com.example.englishlearning.wordqa.WordQaUseCase(defaultTextProfile, secrets, transport)
    @Provides @Singleton fun provideWordNoteIdFactory(): com.example.englishlearning.ui.WordNoteIdFactory = com.example.englishlearning.ui.WordNoteIdFactory.Random
    @Provides @Singleton fun provideSentenceAnalysisUseCase(defaultTextProfile: DefaultTextProfileResolver, secrets: AiProfileSecretUseCase, transport: AiHttpTransport): com.example.englishlearning.sentence.SentenceAnalysisUseCase = com.example.englishlearning.sentence.SentenceAnalysisUseCase(defaultTextProfile, secrets, transport)
    @Provides @Singleton fun provideSpeechPreferenceRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): SpeechPreferenceRepository = RoomSpeechPreferenceRepository(database, dispatcher)
    @Provides fun provideAiProfileSecretUseCase(secretStore: SecretStore): AiProfileSecretUseCase = AiProfileSecretUseCase(secretStore)
    @Provides @Singleton fun provideAiProfileIdFactory(): AiProfileIdFactory = AiProfileIdFactory.Random
    @Provides @Singleton fun provideAiHttpTransport(@Named("io") dispatcher: CoroutineDispatcher): AiHttpTransport = UrlConnectionAiHttpTransport(dispatcher)
    /** 生图响应（1024×1024 b64）可达 ~14MB，文本传输的 512KB 上限会把它整条路堵死。 */
    @Provides @Singleton @Named("imageAi") fun provideImageAiHttpTransport(@Named("io") dispatcher: CoroutineDispatcher): AiHttpTransport = UrlConnectionAiHttpTransport(dispatcher, maxResponseBytes = 16 * 1024 * 1024)
    @Provides @Singleton fun provideDrawingPromptUseCase(defaultTextProfile: DefaultTextProfileResolver, secrets: AiProfileSecretUseCase, transport: AiHttpTransport): com.example.englishlearning.imagegen.DrawingPromptUseCase = com.example.englishlearning.imagegen.DrawingPromptUseCase(defaultTextProfile, secrets, transport)
    @Provides @Singleton fun provideImageGenerationUseCase(defaultImageProfile: com.example.englishlearning.ai.DefaultImageProfileResolver, secrets: AiProfileSecretUseCase, @Named("imageAi") transport: AiHttpTransport): com.example.englishlearning.imagegen.ImageGenerationUseCase = com.example.englishlearning.imagegen.ImageGenerationUseCase(defaultImageProfile, secrets, transport)
    @Provides @Singleton fun provideAudioHttpTransport(@Named("io") dispatcher: CoroutineDispatcher): AudioHttpTransport = UrlConnectionAudioHttpTransport(dispatcher)
    /** 生图产物只写应用 cache 目录（`generated-images/`），不进相册、不进 Room、不备份。 */
    @Provides @Singleton fun provideGeneratedImageStore(@ApplicationContext context: Context, transport: AudioHttpTransport): com.example.englishlearning.imagegen.GeneratedImageStore = com.example.englishlearning.imagegen.GeneratedImageStore(transport, java.io.File(context.cacheDir, "generated-images"))
    @Provides @Singleton fun provideArticleIdFactory(): ArticleIdFactory = ArticleIdFactory.Random
    @Provides @Singleton     fun provideGenerateArticleUseCase(
        defaultTextProfile: DefaultTextProfileResolver,
        secrets: AiProfileSecretUseCase,
        transport: AiHttpTransport,
        articles: ArticleRepository,
        ids: ArticleIdFactory,
        clock: ClockProvider,
    ): GenerateArticleUseCase = GenerateArticleUseCase(defaultTextProfile, secrets, transport, articles, ids, { clock.instant() })
    @Provides @Singleton fun provideFetchArticleUseCase(
        transport: AiHttpTransport,
        articles: ArticleRepository,
        ids: ArticleIdFactory,
        clock: ClockProvider,
    ): FetchArticleUseCase = FetchArticleUseCase(transport, articles, ids, { clock.instant() })
    @Provides @Singleton fun provideImportArticleUseCase(
        articles: ArticleRepository,
        plans: TodayPlanRepository,
        events: LearningEventRepository,
        cards: WordCardSource,
        ids: ArticleIdFactory,
        clock: ClockProvider,
    ): ImportArticleUseCase = ImportArticleUseCase(articles, plans, events, cards, ids, { clock.instant() })
}
