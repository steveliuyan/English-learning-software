package com.example.englishlearning.ui

import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.LearningProfile
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.LearningSettings
import com.example.englishlearning.learning.LearningSettingsRepository
import com.example.englishlearning.learning.LearningSettingsRepositoryResult
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.WordBook
import com.example.englishlearning.learning.WordBookDeletionService
import com.example.englishlearning.learning.WordBookProgress
import com.example.englishlearning.learning.WordBookProgressMigrationCoordinator
import com.example.englishlearning.learning.WordBookProgressMigrationService
import com.example.englishlearning.learning.BundledWordBookIdSource
import com.example.englishlearning.learning.ImportedWordBookIdSource
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.AppendEventResult
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.WordCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LearningSetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loaded progress exposes learned and unlearned counts`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("cet4", "大学英语四级", "CET-4", 3, "v1", "cefr-j-1.5")
        }
        val cards = FakeCards(mapOf("cet4" to listOf("cet4:a", "cet4:b", "cet4:c")))
        val events = FakeEvents(setOf("cet4:a"))
        val viewModel = setupViewModel(repository, cards, events)

        viewModel.load("default")
        advanceUntilIdle()

        assertEquals(WordBookProgress(learned = 1, total = 3), viewModel.uiState.value.progressByBook["cet4"])
        assertEquals(2, viewModel.uiState.value.progressByBook["cet4"]?.unlearned)
    }

    @Test
    fun `loads existing learning settings for the profile`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            profile = LearningProfile("default", "cet4", 20)
            books += WordBook("cet4", "大学英语四级", "CET-4", 0, "v1", "cefr-j-1.5")
        }
        val viewModel = setupViewModel(repository)

        viewModel.load("default")
        advanceUntilIdle()

        assertEquals("cet4", viewModel.uiState.value.selectedWordBookId)
        assertEquals(20, viewModel.uiState.value.dailyNewTarget)
        assertEquals("大学英语四级", viewModel.uiState.value.savedWordBookName)
    }

    @Test
    fun `saves selected wordbook and daily target`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("cet4", "大学英语四级", "CET-4", 0, "v1", "cefr-j-1.5")
        }
        val viewModel = setupViewModel(repository)

        viewModel.load("default")
        advanceUntilIdle()
        viewModel.selectWordBook("cet4")
        viewModel.updateDailyNewTarget(20)
        viewModel.save("default")
        advanceUntilIdle()

        assertEquals(LearningProfile("default", "cet4", 20), repository.profile)
        assertEquals("大学英语四级", viewModel.uiState.value.savedWordBookName)
        assertEquals(20, viewModel.uiState.value.dailyNewTarget)
    }

    @Test
    fun `save emits one Saved effect only when setup succeeds`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("cet4", "大学英语四级", "CET-4", 0, "v1", "cefr-j-1.5")
        }
        val viewModel = setupViewModel(repository)
        viewModel.load("default")
        advanceUntilIdle()
        val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effects.first() }

        viewModel.save("default")
        advanceUntilIdle()

        assertEquals(LearningSetupEffect.Saved, effect.await())
        assertEquals("大学英语四级", viewModel.uiState.value.savedWordBookName)
    }

    @Test
    fun `loads detail toggles with defaults when no settings are stored`() = runTest(dispatcher) {
        val viewModel = setupViewModel()

        viewModel.load("profile-x")
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.openDetailOnKnown)
        assertEquals(true, viewModel.uiState.value.openDetailOnFuzzy)
        assertEquals(true, viewModel.uiState.value.openDetailOnForgotten)
        assertEquals(true, viewModel.uiState.value.showVocabularySearchCount)
    }

    @Test
    fun `loads stored search count visibility for the profile`() = runTest(dispatcher) {
        val settingsRepo = FakeSettingsRepository().apply {
            stored["profile-x"] = LearningSettings(
                "profile-x",
                openDetailOnKnown = true,
                openDetailOnFuzzy = false,
                openDetailOnForgotten = false,
                showVocabularySearchCount = false,
            )
        }
        val viewModel = setupViewModel(settingsRepo = settingsRepo)

        viewModel.load("profile-x")
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.showVocabularySearchCount)
    }

    @Test
    fun `toggling search count visibility persists immediately`() = runTest(dispatcher) {
        val settingsRepo = FakeSettingsRepository()
        val viewModel = setupViewModel(settingsRepo = settingsRepo)

        viewModel.load("profile-x")
        advanceUntilIdle()
        viewModel.setShowVocabularySearchCount(false)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.showVocabularySearchCount)
        assertEquals(false, settingsRepo.stored["profile-x"]?.showVocabularySearchCount)
    }

    @Test
    fun `loads stored detail toggles for the profile`() = runTest(dispatcher) {
        val settingsRepo = FakeSettingsRepository().apply {
            stored["profile-x"] = LearningSettings(
                "profile-x",
                openDetailOnKnown = true,
                openDetailOnFuzzy = false,
                openDetailOnForgotten = false,
            )
        }
        val viewModel = setupViewModel(settingsRepo = settingsRepo)

        viewModel.load("profile-x")
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.openDetailOnKnown)
        assertEquals(false, viewModel.uiState.value.openDetailOnFuzzy)
        assertEquals(false, viewModel.uiState.value.openDetailOnForgotten)
    }

    @Test
    fun `toggling a detail switch persists immediately and keeps the value`() = runTest(dispatcher) {
        val settingsRepo = FakeSettingsRepository()
        val viewModel = setupViewModel(settingsRepo = settingsRepo)

        viewModel.load("profile-x")
        advanceUntilIdle()
        viewModel.setOpenDetailOnKnown(true)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.openDetailOnKnown)
        assertEquals(
            LearningSettings("profile-x", openDetailOnKnown = true, openDetailOnFuzzy = true, openDetailOnForgotten = true),
            settingsRepo.stored["profile-x"],
        )
    }

    @Test
    fun `detail settings are isolated per profile`() = runTest(dispatcher) {
        val settingsRepo = FakeSettingsRepository()
        val vm1 = setupViewModel(settingsRepo = settingsRepo)
        vm1.load("profile-1")
        advanceUntilIdle()
        vm1.setOpenDetailOnKnown(true)
        advanceUntilIdle()

        val vm2 = setupViewModel(settingsRepo = settingsRepo)
        vm2.load("profile-2")
        advanceUntilIdle()
        vm2.setOpenDetailOnForgotten(false)
        advanceUntilIdle()

        assertEquals(
            LearningSettings("profile-1", openDetailOnKnown = true, openDetailOnFuzzy = true, openDetailOnForgotten = true),
            settingsRepo.stored["profile-1"],
        )
        assertEquals(
            LearningSettings("profile-2", openDetailOnKnown = false, openDetailOnFuzzy = true, openDetailOnForgotten = false),
            settingsRepo.stored["profile-2"],
        )
    }

    @Test
    fun `hides books that are neither bundled nor imported`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("cet4", "四级", "CET-4", 1, "v1", "ngsl-nawl-1.2")
            books += WordBook("postgraduate-entrance-exam", "旧占位册", "考研", 1, "v1", "cefr-j-1.5")
        }
        val viewModel = setupViewModel(repository, bundledIds = setOf("cet4"))

        viewModel.load("default")
        advanceUntilIdle()

        assertEquals(listOf("cet4"), viewModel.uiState.value.wordBooks.map(WordBook::id))
    }

    @Test
    fun `only imported books are deletable`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("cet4", "四级", "CET-4", 1, "v1", "ngsl-nawl-1.2")
            books += WordBook("my-pack", "我的词书", "自定义", 1, "v1", "ngsl-nawl-1.2")
        }
        val viewModel = setupViewModel(
            repository,
            bundledIds = setOf("cet4"),
            importedIds = setOf("my-pack"),
        )

        viewModel.load("default")
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.canDelete("cet4"))
        assertEquals(true, viewModel.uiState.value.canDelete("my-pack"))
    }

    @Test
    fun `requesting delete on a bundled book is ignored`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("cet4", "四级", "CET-4", 1, "v1", "ngsl-nawl-1.2")
        }
        val viewModel = setupViewModel(repository, bundledIds = setOf("cet4"))
        viewModel.load("default")
        advanceUntilIdle()

        viewModel.requestDelete(WordBook("cet4", "四级", "CET-4", 1, "v1", "ngsl-nawl-1.2"))
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.pendingDelete)
    }

    @Test
    fun `deleting an imported book drops it from the list and storage`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("cet4", "四级", "CET-4", 1, "v1", "ngsl-nawl-1.2")
            books += WordBook("my-pack", "我的词书", "自定义", 1, "v1", "ngsl-nawl-1.2")
        }
        val viewModel = setupViewModel(
            repository,
            bundledIds = setOf("cet4"),
            importedIds = setOf("my-pack"),
        )
        viewModel.load("default")
        advanceUntilIdle()

        viewModel.requestDelete(WordBook("my-pack", "我的词书", "自定义", 1, "v1", "ngsl-nawl-1.2"))
        viewModel.confirmDelete("default")
        advanceUntilIdle()

        assertEquals(listOf("my-pack"), repository.deleted)
        assertEquals(listOf("cet4"), viewModel.uiState.value.wordBooks.map(WordBook::id))
    }

    @Test
    fun `switching to a book with learned duplicates waits for confirmation before saving`() =
        runTest(dispatcher) {
            val repository = FakeRepository().apply {
                profile = LearningProfile("default", "cet4", 10)
                books += WordBook("cet4", "四级", "CET-4", 1, "v1", "ngsl-nawl-1.2")
                books += WordBook("cet6", "六级", "CET-6", 1, "v1", "ngsl-nawl-1.2")
            }
            val cards = MapCards(
                listOf(
                    card("cet4", "ability"),
                    card("cet6", "ability"),
                ),
            )
            val events = FakeEvents(setOf("cet4:ability"))
            val viewModel = setupViewModel(
                repository = repository,
                cards = cards,
                events = events,
                bundledIds = setOf("cet4", "cet6"),
            )
            viewModel.load("default")
            advanceUntilIdle()

            viewModel.selectWordBook("cet6")
            viewModel.save("default")
            advanceUntilIdle()

            // 关键：还没落库，也没静默迁移。
            assertEquals("cet4", repository.profile?.activeWordBookId)
            assertEquals(1, viewModel.uiState.value.pendingMigration?.candidates?.size)
            assertEquals(emptyList<String>(), events.migrated)
        }

    @Test
    fun `confirming migration marks duplicates then switches the active book`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            profile = LearningProfile("default", "cet4", 10)
            books += WordBook("cet4", "四级", "CET-4", 1, "v1", "ngsl-nawl-1.2")
            books += WordBook("cet6", "六级", "CET-6", 1, "v1", "ngsl-nawl-1.2")
        }
        val cards = MapCards(
            listOf(
                card("cet4", "ability"),
                card("cet6", "ability"),
            ),
        )
        val events = FakeEvents(setOf("cet4:ability"))
        val viewModel = setupViewModel(
            repository = repository,
            cards = cards,
            events = events,
            bundledIds = setOf("cet4", "cet6"),
        )
        viewModel.load("default")
        advanceUntilIdle()
        viewModel.selectWordBook("cet6")
        viewModel.save("default")
        advanceUntilIdle()

        viewModel.resolveMigration("default", apply = true)
        advanceUntilIdle()

        assertEquals(listOf("default|cet4|cet6"), events.migrated)
        assertEquals("cet6", repository.profile?.activeWordBookId)
        assertEquals(null, viewModel.uiState.value.pendingMigration)
    }

    @Test
    fun `declining migration still switches the book without marking anything`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            profile = LearningProfile("default", "cet4", 10)
            books += WordBook("cet4", "四级", "CET-4", 1, "v1", "ngsl-nawl-1.2")
            books += WordBook("cet6", "六级", "CET-6", 1, "v1", "ngsl-nawl-1.2")
        }
        val cards = MapCards(listOf(card("cet4", "ability"), card("cet6", "ability")))
        val events = FakeEvents(setOf("cet4:ability"))
        val viewModel = setupViewModel(
            repository = repository,
            cards = cards,
            events = events,
            bundledIds = setOf("cet4", "cet6"),
        )
        viewModel.load("default")
        advanceUntilIdle()
        viewModel.selectWordBook("cet6")
        viewModel.save("default")
        advanceUntilIdle()

        viewModel.resolveMigration("default", apply = false)
        advanceUntilIdle()

        assertEquals(emptyList<String>(), events.migrated)
        assertEquals("cet6", repository.profile?.activeWordBookId)
    }

    @Test
    fun `switching to a book without duplicates saves straight away`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            profile = LearningProfile("default", "cet4", 10)
            books += WordBook("cet4", "四级", "CET-4", 1, "v1", "ngsl-nawl-1.2")
            books += WordBook("cet6", "六级", "CET-6", 1, "v1", "ngsl-nawl-1.2")
        }
        val cards = MapCards(listOf(card("cet4", "ability"), card("cet6", "different")))
        val events = FakeEvents(setOf("cet4:ability"))
        val viewModel = setupViewModel(
            repository = repository,
            cards = cards,
            events = events,
            bundledIds = setOf("cet4", "cet6"),
        )
        viewModel.load("default")
        advanceUntilIdle()

        viewModel.selectWordBook("cet6")
        viewModel.save("default")
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.pendingMigration)
        assertEquals("cet6", repository.profile?.activeWordBookId)
    }

    private fun card(book: String, lemma: String) = WordCard(
        cardId = "$book:$lemma",
        wordBookId = book,
        lemma = lemma,
        ipa = "",
        partOfSpeech = "n.",
        meaningZh = "释义",
    )

    private class MapCards(private val all: List<WordCard>) : WordCardSource {
        override suspend fun cardIds(wordBookId: String) =
            all.filter { it.wordBookId == wordBookId }.map(WordCard::cardId)

        override suspend fun cards(cardIds: List<String>) = all.filter { it.cardId in cardIds }
    }

    private fun seed(repository: LearningProfileRepository) =
        SeedWordBooksUseCase({ "[]" }, repository)

    private fun select(repository: LearningProfileRepository) =
        SelectWordBookAndSetDailyTargetUseCase(repository)

    private fun setupViewModel(
        repository: LearningProfileRepository = FakeRepository(),
        cards: WordCardSource = EmptyCards,
        events: LearningEventRepository = EmptyEvents,
        settingsRepo: LearningSettingsRepository = FakeSettingsRepository(),
        // 默认把 fake 仓库里的册当作「内置」全部可见；要测过滤就显式传更小的集合。
        bundledIds: Set<String> = (repository as? FakeRepository)?.books?.map(WordBook::id)?.toSet().orEmpty(),
        importedIds: Set<String> = emptySet(),
        deletion: WordBookDeletionService? = null,
        migration: WordBookProgressMigrationCoordinator? = null,
    ) = LearningSetupViewModel(
        repository,
        seed(repository),
        select(repository),
        GetLearningSettingsUseCase(settingsRepo),
        SaveLearningSettingsUseCase(settingsRepo),
        cards,
        events,
        BundledWordBookIdSource { bundledIds },
        ImportedWordBookIdSource { importedIds },
        deletion ?: WordBookDeletionService(repository, java.io.File("")) { emptySet() },
        migration ?: WordBookProgressMigrationCoordinator(WordBookProgressMigrationService(cards, events)),
    )

    private class FakeCards(private val idsByBook: Map<String, List<String>>) : WordCardSource {
        override suspend fun cardIds(wordBookId: String) = idsByBook[wordBookId].orEmpty()
        override suspend fun cards(cardIds: List<String>) = emptyList<WordCard>()
    }

    private class FakeEvents(private val reviewed: Set<String>) : LearningEventRepository {
        val migrated = mutableListOf<String>()

        override suspend fun append(event: LearningEvent, nextState: CardReviewState) = AppendEventResult.Appended(false)

        override suspend fun findEvent(eventId: String) = RepositoryResult.Success<LearningEvent?>(null)

        override suspend fun findCardState(cardId: String) = RepositoryResult.Success<CardReviewState?>(null)

        override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)

        override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(emptyList<String>())

        override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(reviewed.toList())

        override suspend fun dueCardIds(wordBookId: String, now: java.time.Instant) =
            RepositoryResult.Success(emptyList<String>())

        override suspend fun reviewedStates(wordBookId: String): RepositoryResult<List<CardReviewState>> =
            RepositoryResult.Success(
                reviewed.filter { it.startsWith("$wordBookId:") }.map { cardId ->
                    CardReviewState(
                        cardId = cardId,
                        wordBookId = wordBookId,
                        lastFeedback = com.example.englishlearning.learning.domain.ReviewFeedback.Good,
                        lastReviewedAt = java.time.Instant.EPOCH,
                        nextReviewAt = java.time.Instant.EPOCH,
                    )
                },
            )

        override suspend fun migrateReviewStates(
            profileId: String,
            sourceBookId: String,
            targetBookId: String,
            candidates: List<com.example.englishlearning.learning.ProgressMigrationCandidate>,
        ): RepositoryResult<Int> {
            migrated += "$profileId|$sourceBookId|$targetBookId"
            return RepositoryResult.Success(candidates.size)
        }
    }

    private object EmptyCards : WordCardSource {
        override suspend fun cardIds(wordBookId: String) = emptyList<String>()
        override suspend fun cards(cardIds: List<String>) = emptyList<WordCard>()
    }

    private object EmptyEvents : LearningEventRepository {
        override suspend fun append(event: LearningEvent, nextState: CardReviewState) = AppendEventResult.Appended(false)
        override suspend fun findEvent(eventId: String) = RepositoryResult.Success<LearningEvent?>(null)
        override suspend fun findCardState(cardId: String) = RepositoryResult.Success<CardReviewState?>(null)
        override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)
        override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(emptyList<String>())
        override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())
        override suspend fun dueCardIds(wordBookId: String, now: java.time.Instant) = RepositoryResult.Success(emptyList<String>())
    }

    private class FakeRepository : LearningProfileRepository {
        var profile: LearningProfile? = null
        val books = mutableListOf<WordBook>()
        val deleted = mutableListOf<String>()

        override suspend fun current(profileId: String) = RepositoryResult.Success(profile)

        override suspend fun save(profile: LearningProfile): RepositoryResult<Unit> {
            this.profile = profile
            return RepositoryResult.Success(Unit)
        }

        override suspend fun listWordBooks() = RepositoryResult.Success(books)

        override suspend fun findWordBook(id: String) =
            RepositoryResult.Success(books.find { it.id == id })

        override suspend fun upsertWordBook(wordBook: WordBook) = RepositoryResult.Success(Unit)

        override suspend fun deleteWordBook(profileId: String, wordBookId: String): RepositoryResult<Unit> {
            deleted += wordBookId
            books.removeAll { it.id == wordBookId }
            return RepositoryResult.Success(Unit)
        }
    }

    private class FakeSettingsRepository : LearningSettingsRepository {
        val stored = mutableMapOf<String, LearningSettings>()

        override suspend fun find(profileId: String): LearningSettingsRepositoryResult<LearningSettings?> =
            LearningSettingsRepositoryResult.Success(stored[profileId])

        override suspend fun save(settings: LearningSettings): LearningSettingsRepositoryResult<Unit> {
            stored[settings.profileId] = settings
            return LearningSettingsRepositoryResult.Success(Unit)
        }
    }
}
