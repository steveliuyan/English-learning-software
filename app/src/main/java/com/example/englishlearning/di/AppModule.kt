package com.example.englishlearning.di

import android.content.Context
import java.io.File
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
import com.example.englishlearning.learning.FavoriteWordRepository
import com.example.englishlearning.learning.RoomFavoriteWordRepository
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
import com.example.englishlearning.language.domain.PronunciationAudioCache
import com.example.englishlearning.language.domain.PronunciationProvider
import com.example.englishlearning.language.domain.PronunciationRouter
import com.example.englishlearning.language.infrastructure.AndroidTextToSpeechProvider
import com.example.englishlearning.language.infrastructure.AndroidAudioPlayer
import com.example.englishlearning.language.infrastructure.AudioPlayer
import com.example.englishlearning.language.infrastructure.FilePronunciationAudioCache
import com.example.englishlearning.language.infrastructure.MiMoPronunciationProvider
import com.example.englishlearning.language.infrastructure.KokoroAndroidEngine
import com.example.englishlearning.language.infrastructure.KokoroEngine
import com.example.englishlearning.language.infrastructure.KokoroPronunciationProvider
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
        AppDatabase.create(context, context.getDatabasePath("english-learning.db"))
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
    @Provides @Singleton fun provideFavoriteWordRepository(database: AppDatabase, clock: ClockProvider, @Named("io") dispatcher: CoroutineDispatcher): FavoriteWordRepository = RoomFavoriteWordRepository(database, clock, dispatcher)
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
    fun providePronunciationAudioCache(@ApplicationContext context: Context): PronunciationAudioCache =
        FilePronunciationAudioCache(context)

    @Provides @Singleton
    fun provideMiMoPronunciationProviderFactory(
        profiles: AiProfileRepository,
        secrets: AiProfileSecretUseCase,
        transport: AudioHttpTransport,
        player: AudioPlayer,
        cache: PronunciationAudioCache,
    ): MiMoPronunciationProviderFactory = MiMoPronunciationProviderFactory { profileId ->
        MiMoPronunciationProvider(profiles, secrets, transport, player, profileId, cache)
    }

    @Provides @Singleton
    fun provideKokoroEngine(@ApplicationContext context: Context): KokoroEngine = KokoroAndroidEngine(context)

    @Provides @Singleton
    fun provideKokoroPronunciationProvider(
        engine: KokoroEngine,
        player: AudioPlayer,
        @ApplicationContext context: Context,
    ): KokoroPronunciationProvider = KokoroPronunciationProvider(
        engine,
        player,
        com.example.englishlearning.language.infrastructure.FileSynthesizedAudioCache(
            java.io.File(context.filesDir, "kokoro-audio-cache"),
        ),
    )

    @Provides @Singleton
    fun providePronunciationProvider(
        kokoro: KokoroPronunciationProvider,
        system: AndroidTextToSpeechProvider,
        miMoFactory: MiMoPronunciationProviderFactory,
        preferences: SpeechPreferenceRepository,
    ): PronunciationProvider = PronunciationRouter(kokoro, system, miMoFactory, preferences)
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
    // F3-03 拍照 OCR：ML Kit bundled 主引擎、Tesseract 无 GMS 降级；模型资产在 filesDir，
    // 临时采集区在 cacheDir 子目录（AC3-05：任何出口零残留，purgeAll 只扫该子目录）。
    @Provides @Singleton fun provideTemporaryImageWorkspace(@ApplicationContext context: Context): com.example.englishlearning.language.domain.TemporaryImageWorkspace = com.example.englishlearning.language.domain.TemporaryImageWorkspace(java.io.File(context.cacheDir, "ocr-captures"))
    @Provides @Singleton fun provideTraineddataFetcher(): com.example.englishlearning.language.infrastructure.TraineddataFetcher = com.example.englishlearning.language.infrastructure.HttpsTraineddataFetcher()
    @Provides @Singleton fun provideEnglishOcrModelAssets(@ApplicationContext context: Context, fetcher: com.example.englishlearning.language.infrastructure.TraineddataFetcher): com.example.englishlearning.language.domain.EnglishOcrModelAssets = com.example.englishlearning.language.infrastructure.FileEnglishOcrModelAssets(java.io.File(context.filesDir, "ocr-models"), fetcher)
    @Provides @Singleton fun provideMlTextRecognitionClient(): com.example.englishlearning.language.infrastructure.MlTextRecognitionClient = com.example.englishlearning.language.infrastructure.MlKitTextRecognizerClient()
    @Provides @Singleton fun provideMlKitTextRecognitionProvider(client: com.example.englishlearning.language.infrastructure.MlTextRecognitionClient): com.example.englishlearning.language.infrastructure.MlKitTextRecognitionProvider = com.example.englishlearning.language.infrastructure.MlKitTextRecognitionProvider(client)
    @Provides @Singleton fun provideTesseractEngineRunner(): com.example.englishlearning.language.infrastructure.TesseractEngineRunner = com.example.englishlearning.language.infrastructure.Tesseract4AndroidEngineRunner()
    @Provides @Singleton fun provideTesseractOcrProvider(assets: com.example.englishlearning.language.domain.EnglishOcrModelAssets, runner: com.example.englishlearning.language.infrastructure.TesseractEngineRunner): com.example.englishlearning.language.infrastructure.TesseractOcrProvider = com.example.englishlearning.language.infrastructure.TesseractOcrProvider(assets, runner)
    @Provides @Singleton fun provideOcrProviderChain(mlKit: com.example.englishlearning.language.infrastructure.MlKitTextRecognitionProvider, tesseract: com.example.englishlearning.language.infrastructure.TesseractOcrProvider): com.example.englishlearning.language.domain.OcrProviderChain = com.example.englishlearning.language.domain.OcrProviderChain(listOf(mlKit, tesseract))
    @Provides @Singleton fun providePhotoTranslationUseCase(defaultTextProfile: DefaultTextProfileResolver, secrets: AiProfileSecretUseCase, transport: AiHttpTransport): com.example.englishlearning.phototranslation.PhotoTranslationUseCase = com.example.englishlearning.phototranslation.PhotoTranslationUseCase(defaultTextProfile, secrets, transport)
    // F3-03 历史保存：原图字节 → PrivateMediaStore（filesDir/assets，SHA 校验后原子发布）+ asset_records 登记；
    // 文本记录 → photo_translation_records。与 F4-03 备份媒体导出共用同一资产区。
    @Provides @Singleton fun providePrivateMediaStore(fileOps: com.example.englishlearning.core.storage.FileOps, @Named("io") dispatcher: CoroutineDispatcher): com.example.englishlearning.core.storage.PrivateMediaStore = com.example.englishlearning.core.storage.PrivateMediaStore(fileOps, dispatcher)
    @Provides @Singleton fun providePhotoTranslationHistoryRepository(database: AppDatabase, media: com.example.englishlearning.core.storage.PrivateMediaStore, fileOps: com.example.englishlearning.core.storage.FileOps, clock: ClockProvider, @Named("io") dispatcher: CoroutineDispatcher): com.example.englishlearning.phototranslation.PhotoTranslationHistoryRepository = com.example.englishlearning.phototranslation.RoomPhotoTranslationHistoryRepository(database, media, fileOps, clock, dispatcher)
    @Provides @Singleton fun provideSpeechPreferenceRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): SpeechPreferenceRepository = RoomSpeechPreferenceRepository(database, dispatcher)
    @Provides @Singleton fun provideReminderPreferenceRepository(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): com.example.englishlearning.notification.domain.ReminderPreferenceRepository = com.example.englishlearning.notification.RoomReminderPreferenceRepository(database, dispatcher)
    @Provides @Singleton fun provideReminderScheduler(@ApplicationContext context: Context, preferences: com.example.englishlearning.notification.domain.ReminderPreferenceRepository, plans: TodayPlanRepository, events: LearningEventRepository, clock: ClockProvider): com.example.englishlearning.notification.ReminderScheduler = com.example.englishlearning.notification.WorkManagerReminderScheduler(context, preferences, plans, events, clock)
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

    // ---- F4-03 备份导出（无认证 Salvage 包）：只读源 + 媒体校验 + 打包 + SAF 落盘 ----
    // 导出链路对 Room 严格只读（源契约测试守护），媒体缺失/校验不符一律失败关闭，绝不写业务表。
    @Provides @Singleton
    fun provideBackupFileOps(@ApplicationContext context: Context): com.example.englishlearning.core.storage.FileOps =
        com.example.englishlearning.core.storage.AndroidFileOps(context.filesDir)

    @Provides @Singleton
    fun provideBackupRowReader(database: AppDatabase, @Named("io") dispatcher: CoroutineDispatcher): com.example.englishlearning.backup.domain.BackupRowReader =
        com.example.englishlearning.backup.infrastructure.RoomBackupRowReader(database, dispatcher)

    @Provides @Singleton
    fun provideBackupMediaGateway(fileOps: com.example.englishlearning.core.storage.FileOps): com.example.englishlearning.backup.domain.BackupMediaGateway =
        com.example.englishlearning.backup.infrastructure.FileOpsBackupMediaGateway(fileOps)

    @Provides @Singleton
    fun provideBackupPackager(): com.example.englishlearning.backup.domain.PackagePackager =
        com.example.englishlearning.backup.infrastructure.ZipPackagePackager()

    @Provides @Singleton
    fun provideBackupExportCoordinator(
        rowReader: com.example.englishlearning.backup.domain.BackupRowReader,
        mediaGateway: com.example.englishlearning.backup.domain.BackupMediaGateway,
        packager: com.example.englishlearning.backup.domain.PackagePackager,
    ): com.example.englishlearning.backup.domain.BackupExportCoordinator =
        com.example.englishlearning.backup.domain.BackupExportCoordinator(rowReader, mediaGateway, packager) { System.currentTimeMillis() }

    @Provides @Singleton
    fun provideBackupDestinationWriter(writer: com.example.englishlearning.backup.infrastructure.ContentResolverBackupDestinationWriter): com.example.englishlearning.ui.BackupDestinationWriter =
        writer

    // ---- F4-04 认证恢复（决策文档 §9 端口绑定） ----

    @Provides @Singleton
    fun provideBackupRestoreCoordinator(
        @ApplicationContext context: Context,
        database: com.example.englishlearning.core.storage.AppDatabase,
        cards: WordCardSource,
        bundledIds: com.example.englishlearning.learning.BundledWordBookIdSource,
        importedIds: com.example.englishlearning.learning.ImportedWordBookIdSource,
        clock: ClockProvider,
        aiSecrets: com.example.englishlearning.ai.AiProfileSecretUseCase,
        @Named("io") io: CoroutineDispatcher,
    ): com.example.englishlearning.backup.restore.BackupRestoreCoordinator =
        com.example.englishlearning.backup.restore.BackupRestoreCoordinator(
            validator = com.example.englishlearning.backup.domain.BackupPackageValidator(),
            pairingVerifier = com.example.englishlearning.backup.pairing.PairedMigrationChallengeVerifier(
                com.example.englishlearning.backup.pairing.JavaSecurityEcSignatureVerifier(),
            ),
            kdf = com.example.englishlearning.backup.infrastructure.BouncyCastleArgon2idKdf(),
            aead = com.example.englishlearning.backup.infrastructure.TinkAes256GcmAead(),
            spaceChecker = com.example.englishlearning.backup.device.AndroidRestoreSpaceChecker(context),
            staging = com.example.englishlearning.backup.infrastructure.FileRestoreStagingWorkspace(
                File(context.cacheDir, "restore-scratch"),
            ),
            commitGateway = com.example.englishlearning.backup.restore.SnapshotSwapRestoreCommitGateway(
                rollbackRoot = File(context.filesDir, "rollback"),
                scratchRoot = File(context.cacheDir, "restore-scratch"),
                targets = com.example.englishlearning.backup.device.RoomRestoreDataWriter.swapTargets(context),
                database = com.example.englishlearning.backup.device.RoomFormalDataHandle(database),
                writer = com.example.englishlearning.backup.device.RoomRestoreDataWriter(
                    context,
                    com.example.englishlearning.backup.device.RestoreEntityDecoder(),
                ),
                // D-12：维护写入用临时新实例，不注入进程级 AppDatabase（close 后为僵尸）
                postActions = com.example.englishlearning.backup.device.AndroidPostRestoreActions(
                    context,
                    cards,
                    bundledIds,
                    importedIds,
                    clock,
                    aiSecrets,
                    io,
                ),
                clockMillis = { System.currentTimeMillis() },
            ),
            sessionIdProvider = { java.util.UUID.randomUUID().toString() },
            clock = { System.currentTimeMillis() / 1000 },
        )

    @Provides @Singleton
    fun provideProcessRestarter(restarter: com.example.englishlearning.backup.device.AndroidProcessRestarter):
        com.example.englishlearning.backup.restore.ProcessRestarter = restarter

    @Provides @Singleton
    fun provideProtectedExportExecutor(
        delegate: com.example.englishlearning.backup.domain.BackupExportCoordinator,
    ): com.example.englishlearning.ui.ProtectedExportExecutor =
        com.example.englishlearning.ui.ProtectedExportExecutor(
            signerFactory = { com.example.englishlearning.backup.device.AndroidMigrationSigner() },
            keySpki = {
                if (com.example.englishlearning.backup.device.AndroidMigrationKey.ensureKey()) {
                    com.example.englishlearning.backup.device.AndroidMigrationKey.publicKeySpki()
                } else {
                    null
                }
            },
            delegate = delegate,
            entropy = com.example.englishlearning.backup.infrastructure.SecureRandomBackupEntropy(),
            clock = { System.currentTimeMillis() / 1000 },
        )

    /**
     * AC4-05「已使用配对凭据被拒」消费台账：只持久化 nonce 的 SHA-256 摘要，
     * 表 `migration_pairing_ledger` 不入备份包（新设备空台账起步）。注入到
     * BackupProtectedExportViewModel，成功产包后登记、下次导出前先核验。
     */
    @Provides @Singleton
    fun provideConsumedChallengeLedger(
        database: AppDatabase,
        @Named("io") dispatcher: CoroutineDispatcher,
    ): com.example.englishlearning.backup.pairing.ConsumedChallengeLedger =
        com.example.englishlearning.backup.infrastructure.RoomConsumedChallengeLedger(database, dispatcher)
}
