package com.example.englishlearning.ui

import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.LearningProfile
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.LearningProfileRepositoryError
import com.example.englishlearning.learning.LearningSettings
import com.example.englishlearning.learning.LearningSettingsRepository
import com.example.englishlearning.learning.LearningSettingsRepositoryResult
import com.example.englishlearning.learning.RefreshVocabularySearchIndexUseCase
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.VocabularySearchIndexEntry
import com.example.englishlearning.learning.VocabularySearchIndexRepository
import com.example.englishlearning.learning.WordBook
import com.example.englishlearning.learning.WordBookDeletionService
import com.example.englishlearning.learning.WordBookDeletionPreview
import com.example.englishlearning.learning.WordBookDeletionPreviewRepository
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

    // ---- F4-01 Task10b：删除确认框的级联影响预览（保留面按决策 §6 计数） ----

    @Test
    fun `requestDelete fetches the cascade preview for the confirmation dialog`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("my-pack", "我的词书", "自定义", 1, "v1", "ngsl-nawl-1.2")
        }
        val previewRepository = FakeWordBookDeletionPreviewRepository(
            Result.success(WordBookDeletionPreview(entryCount = 3, keptEventCount = 10, keptArticleCount = 2)),
        )
        val viewModel = setupViewModel(
            repository,
            bundledIds = emptySet(),
            importedIds = setOf("my-pack"),
            deletePreview = previewRepository,
        )
        viewModel.load("default")
        advanceUntilIdle()

        viewModel.requestDelete(WordBook("my-pack", "我的词书", "自定义", 1, "v1", "ngsl-nawl-1.2"))
        advanceUntilIdle()

        assertEquals("my-pack", viewModel.uiState.value.pendingDelete?.id)
        assertEquals(
            WordBookDeletionPreview(entryCount = 3, keptEventCount = 10, keptArticleCount = 2),
            viewModel.uiState.value.deletePreview,
        )
        assertEquals(listOf("default" to "my-pack"), previewRepository.calls)
    }

    @Test
    fun `preview failure still opens the dialog without counts`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("my-pack", "我的词书", "自定义", 1, "v1", "ngsl-nawl-1.2")
        }
        val viewModel = setupViewModel(
            repository,
            bundledIds = emptySet(),
            importedIds = setOf("my-pack"),
            deletePreview = FakeWordBookDeletionPreviewRepository(Result.failure(IllegalStateException("storage"))),
        )
        viewModel.load("default")
        advanceUntilIdle()

        viewModel.requestDelete(WordBook("my-pack", "我的词书", "自定义", 1, "v1", "ngsl-nawl-1.2"))
        advanceUntilIdle()

        // 二次确认不能因为读不出条数就消失——无条数版文案仍然要能确认/取消。
        assertEquals("my-pack", viewModel.uiState.value.pendingDelete?.id)
        assertEquals(null, viewModel.uiState.value.deletePreview)
    }

    @Test
    fun `cancelDelete clears the pending preview too`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("my-pack", "我的词书", "自定义", 1, "v1", "ngsl-nawl-1.2")
        }
        val viewModel = setupViewModel(
            repository,
            bundledIds = emptySet(),
            importedIds = setOf("my-pack"),
            deletePreview = FakeWordBookDeletionPreviewRepository(
                Result.success(WordBookDeletionPreview(1, 1, 1)),
            ),
        )
        viewModel.load("default")
        advanceUntilIdle()

        viewModel.requestDelete(WordBook("my-pack", "我的词书", "自定义", 1, "v1", "ngsl-nawl-1.2"))
        advanceUntilIdle()
        viewModel.cancelDelete()

        assertEquals(null, viewModel.uiState.value.pendingDelete)
        assertEquals(null, viewModel.uiState.value.deletePreview)
    }

    // ---- F1-01 渐进发布：进入「选取词书」不得出现「卡一下然后整列蹦出来」 ----

    @Test
    fun `load marks loading on the first frame`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("cet4", "大学英语四级", "CET-4", 1, "v1", "ngsl-nawl-1.2")
        }
        val viewModel = setupViewModel(repository)
        viewModel.load("default")
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.wordBooksLoading)
        assertEquals(false, viewModel.uiState.value.progressLoading)

        // 再次进入该页：一帧都不推进，界面也必须已经处于加载态。
        viewModel.load("default")

        assertEquals(true, viewModel.uiState.value.wordBooksLoading)
        assertEquals(true, viewModel.uiState.value.progressLoading)
    }

    @Test
    fun `book list publishes before progress and backfills per book`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("cet4", "大学英语四级", "CET-4", 3, "v1", "ngsl-nawl-1.2")
            books += WordBook("cet6", "大学英语六级", "CET-6", 2, "v1", "ngsl-nawl-1.2")
        }
        val cards =
            GatingCards(
                mapOf(
                    "cet4" to listOf("cet4:a", "cet4:b", "cet4:c"),
                    "cet6" to listOf("cet6:x", "cet6:y"),
                ),
            )
        val viewModel = setupViewModel(repository, cards, FakeEvents(setOf("cet4:a")))

        viewModel.load("default")
        runCurrent()

        assertEquals(listOf("cet4", "cet6"), viewModel.uiState.value.wordBooks.map(WordBook::id))
        assertEquals(emptyMap(), viewModel.uiState.value.progressByBook)
        assertEquals(false, viewModel.uiState.value.wordBooksLoading)
        assertEquals(true, viewModel.uiState.value.progressLoading)

        cards.gates.getValue("cet4").complete(Unit)
        runCurrent()
        assertEquals(setOf("cet4"), viewModel.uiState.value.progressByBook.keys)
        assertEquals(true, viewModel.uiState.value.progressLoading)

        cards.gates.getValue("cet6").complete(Unit)
        runCurrent()
        assertEquals(setOf("cet4", "cet6"), viewModel.uiState.value.progressByBook.keys)
        assertEquals(false, viewModel.uiState.value.progressLoading)
        assertEquals(WordBookProgress(learned = 1, total = 3), viewModel.uiState.value.progressByBook["cet4"])
    }

    @Test
    fun `storage failure shows the load error without a half-filled list`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("cet4", "大学英语四级", "CET-4", 3, "v1", "ngsl-nawl-1.2")
            failBookList = true
        }
        val viewModel = setupViewModel(repository)

        viewModel.load("default")
        advanceUntilIdle()

        assertEquals("暂时无法加载学习设置", viewModel.uiState.value.message)
        assertEquals(emptyList<String>(), viewModel.uiState.value.wordBooks.map(WordBook::id))
        assertEquals(emptyMap(), viewModel.uiState.value.progressByBook)
        assertEquals(false, viewModel.uiState.value.wordBooksLoading)
        assertEquals(false, viewModel.uiState.value.progressLoading)
    }

    @Test
    fun `index refresh is optional and does not gate the list`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("cet4", "大学英语四级", "CET-4", 0, "v1", "ngsl-nawl-1.2")
        }

        // 没注入索引刷新（既有单测与旧装配点的默认）时，列表与进度照常发布。
        val withoutIndex = setupViewModel(repository)
        withoutIndex.load("default")
        advanceUntilIdle()
        assertEquals(listOf("cet4"), withoutIndex.uiState.value.wordBooks.map(WordBook::id))
        assertEquals(false, withoutIndex.uiState.value.progressLoading)

        val index = CountingIndex()
        // 进度回填被门住的那段时间里，索引刷新一次都不许被读到：它排在回填之后。
        val cards = GatingCards(mapOf("cet4" to listOf("cet4:a")))
        val withIndex =
            setupViewModel(
                repository,
                cards,
                refreshIndex =
                    RefreshVocabularySearchIndexUseCase(
                        repository,
                        EmptyCards,
                        index,
                        BundledWordBookIdSource { setOf("cet4") },
                        ImportedWordBookIdSource { emptySet() },
                    ),
            )

        withIndex.load("default")
        runCurrent()

        assertEquals(listOf("cet4"), withIndex.uiState.value.wordBooks.map(WordBook::id))
        assertEquals(true, withIndex.uiState.value.progressLoading)
        assertEquals(0, index.versionReads, "索引收敛不得挡在列表首帧前面")

        cards.gates.getValue("cet4").complete(Unit)
        advanceUntilIdle()

        assertEquals(1, index.versionReads)
        assertEquals(WordBookProgress(learned = 0, total = 0), withIndex.uiState.value.progressByBook["cet4"])
    }

    @Test
    fun `a superseded load stops writing once a newer load starts`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            profile = LearningProfile("default", "cet4", 10)
            books += WordBook("cet4", "大学英语四级", "CET-4", 1, "v1", "ngsl-nawl-1.2")
        }
        val settingsRepo = FakeSettingsRepository()
        val staleLoad = CompletableDeferred<Unit>()
        settingsRepo.gateFirstFind = staleLoad
        val viewModel = setupViewModel(repository, settingsRepo = settingsRepo)

        // 老任务已经读完册列表与 profile（目标 10），卡在第一次设置读取上。
        viewModel.load("default")
        runCurrent()

        // 页面重进（删除词书后也会再加载一次）：新任务照常跑完，读到的是目标 30。
        repository.profile = LearningProfile("default", "cet4", 30)
        viewModel.load("default")
        advanceUntilIdle()

        assertEquals(30, viewModel.uiState.value.dailyNewTarget)
        assertEquals(false, viewModel.uiState.value.progressLoading)

        // 老任务这时才被放行。它手里握着过期的 profile 与目标：渐进发布让它有 N+2 次落笔机会，
        // 不取消就会把用户刚看到的目标改回 10。
        staleLoad.complete(Unit)
        advanceUntilIdle()

        assertEquals(30, viewModel.uiState.value.dailyNewTarget)
        assertEquals(listOf("cet4"), viewModel.uiState.value.wordBooks.map(WordBook::id))
        assertEquals(false, viewModel.uiState.value.wordBooksLoading)
    }

    @Test
    fun `a word book whose cards cannot be read still lists with an unreadable progress`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("cet4", "大学英语四级", "CET-4", 1, "v1", "ngsl-nawl-1.2")
            books += WordBook("cet6", "大学英语六级", "CET-6", 2, "v1", "ngsl-nawl-1.2")
        }
        val viewModel = setupViewModel(
            repository,
            FlakyCards(mapOf("cet6" to listOf("cet6:x", "cet6:y"))),
            bundledIds = setOf("cet4", "cet6"),
        )

        viewModel.load("default")
        advanceUntilIdle()

        assertEquals(listOf("cet4", "cet6"), viewModel.uiState.value.wordBooks.map(WordBook::id))
        // 读不出词卡的那本不进进度表：界面据此显示「暂时读不到进度」，而不是编一个「已学 0 / 共 4308」。
        assertEquals(setOf("cet6"), viewModel.uiState.value.progressByBook.keys)
        assertEquals(false, viewModel.uiState.value.progressLoading)
    }

    /** 内置册的词卡要从 assets 拷到盘上，`assets.open` 失败是真实抛异常而不是返回 Failure。 */
    private class FlakyCards(private val idsByBook: Map<String, List<String>>) : WordCardSource {
        override suspend fun cardIds(wordBookId: String): List<String> {
            if (wordBookId !in idsByBook) throw java.io.IOException("asset missing: $wordBookId")
            return idsByBook.getValue(wordBookId)
        }

        override suspend fun cards(cardIds: List<String>) = emptyList<WordCard>()
    }

    private class GatingCards(private val idsByBook: Map<String, List<String>>) : WordCardSource {
        val gates: Map<String, CompletableDeferred<Unit>> = idsByBook.keys.associateWith { CompletableDeferred() }

        override suspend fun cardIds(wordBookId: String): List<String> {
            gates.getValue(wordBookId).await()
            return idsByBook.getValue(wordBookId)
        }

        override suspend fun cards(cardIds: List<String>) = emptyList<WordCard>()
    }

    private class CountingIndex : VocabularySearchIndexRepository {
        var versionReads = 0

        override suspend fun replaceWordBook(
            wordBookId: String,
            wordBookName: String,
            dataVersion: String,
            cards: List<WordCard>,
        ) = RepositoryResult.Success(Unit)

        override suspend fun deleteWordBook(wordBookId: String) = RepositoryResult.Success(Unit)

        override suspend fun search(
            normalizedQuery: String,
            limit: Int,
        ) = RepositoryResult.Success(emptyList<VocabularySearchIndexEntry>())

        override suspend fun indexedVersions(): RepositoryResult<Map<String, String>> {
            versionReads += 1
            return RepositoryResult.Success(mapOf("cet4" to "v1"))
        }

        override suspend fun deleteMissing(keepWordBookIds: Collection<String>) = RepositoryResult.Success(Unit)
    }

    private class FakeWordBookDeletionPreviewRepository(
        private val result: Result<WordBookDeletionPreview>,
    ) : WordBookDeletionPreviewRepository {
        val calls = mutableListOf<Pair<String, String>>()

        override suspend fun preview(profileId: String, wordBookId: String): Result<WordBookDeletionPreview> {
            calls += profileId to wordBookId
            return result
        }
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
        deletePreview: WordBookDeletionPreviewRepository =
            FakeWordBookDeletionPreviewRepository(Result.failure(IllegalStateException("unused"))),
        refreshIndex: RefreshVocabularySearchIndexUseCase? = null,
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
        deletePreview = deletePreview,
        refreshIndex = refreshIndex,
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

        /** 让册列表读取失败：加载路径必须报错而不是端出一份空列表。 */
        var failBookList = false

        override suspend fun current(profileId: String) = RepositoryResult.Success(profile)

        override suspend fun save(profile: LearningProfile): RepositoryResult<Unit> {
            this.profile = profile
            return RepositoryResult.Success(Unit)
        }

        override suspend fun listWordBooks(): RepositoryResult<List<WordBook>> =
            if (failBookList) {
                RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            } else {
                RepositoryResult.Success(books)
            }

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

        /**
         * 让**第一次**设置读取挂起：模拟慢盘，把「老任务还活着、新任务已经跑完」这段时间
         * 拉长到测试可以观测的程度（渐进发布之后一次加载有 N+2 次状态落笔）。
         */
        var gateFirstFind: CompletableDeferred<Unit>? = null
        private var finds = 0

        override suspend fun find(profileId: String): LearningSettingsRepositoryResult<LearningSettings?> {
            finds += 1
            if (finds == 1) gateFirstFind?.await()
            return LearningSettingsRepositoryResult.Success(stored[profileId])
        }

        override suspend fun save(settings: LearningSettings): LearningSettingsRepositoryResult<Unit> {
            stored[settings.profileId] = settings
            return LearningSettingsRepositoryResult.Success(Unit)
        }
    }
}
