package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.BundledWordBookIdSource
import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.ImportedWordBookIdSource
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.LearningSettings
import com.example.englishlearning.learning.LearningSettingsRepositoryResult
import com.example.englishlearning.learning.MigrationPreparation
import com.example.englishlearning.learning.ProgressMigrationPreview
import com.example.englishlearning.learning.ProgressMigrationResult
import com.example.englishlearning.learning.RefreshVocabularySearchIndexUseCase
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.SetupResult
import com.example.englishlearning.learning.WordBook
import com.example.englishlearning.learning.WordBookDeletionPreview
import com.example.englishlearning.learning.WordBookDeletionPreviewRepository
import com.example.englishlearning.learning.WordBookDeletionResult
import com.example.englishlearning.learning.WordBookDeletionService
import com.example.englishlearning.learning.WordBookProgress
import com.example.englishlearning.learning.WordBookProgressMigrationCoordinator
import com.example.englishlearning.learning.WordBookProgressMigrationService
import com.example.englishlearning.learning.WordBookVisibility
import com.example.englishlearning.learning.WordCardSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch

sealed interface LearningSetupEffect {
    data object Saved : LearningSetupEffect
}

data class LearningSetupUiState(
    val wordBooks: List<WordBook> = emptyList(),
    val progressByBook: Map<String, WordBookProgress> = emptyMap(),
    /** 用户导入、可删除的册。内置册绝不进这个集合。 */
    val importedBookIds: Set<String> = emptySet(),
    val selectedWordBookId: String? = null,
    val dailyNewTarget: Int = DEFAULT_DAILY_TARGET,
    /** 当前已生效的活动词书 id，用于判断是否真的发生了切换。 */
    val savedWordBookId: String? = null,
    val savedWordBookName: String? = null,
    val message: String? = null,
    val openDetailOnKnown: Boolean = false,
    val openDetailOnFuzzy: Boolean = true,
    val openDetailOnForgotten: Boolean = true,
    val showVocabularySearchCount: Boolean = true,
    /** 保存/迁移进行中：用于禁用按钮，避免重复提交。 */
    val saving: Boolean = false,
    /** 待确认删除的导入册。 */
    val pendingDelete: WordBook? = null,
    /** F4-01 Task10b：删除确认框的级联条数；null = 还没读到/读失败，对话框照常显示但不带数字。 */
    val deletePreview: WordBookDeletionPreview? = null,
    val deleting: Boolean = false,
    /** 非空表示正在等待用户决定是否迁移重复词的复习状态。 */
    val pendingMigration: ProgressMigrationPreview? = null,
    /**
     * 册列表尚未到位。默认 `true`：这一屏是先出骨架再填内容，而不是先空一下再整列蹦出来
     * （spec F1-01，决策 2026-10-17）。
     */
    val wordBooksLoading: Boolean = true,
    /** 逐册进度尚未算完；`false` 之前每本册显示「进度计算中」。 */
    val progressLoading: Boolean = true,
) {
    fun canDelete(bookId: String): Boolean = bookId in importedBookIds

    companion object {
        const val DEFAULT_DAILY_TARGET = 10
        const val MIN_DAILY_TARGET = 1
        const val MAX_DAILY_TARGET = 50
    }
}

@HiltViewModel
class LearningSetupViewModel @Inject constructor(
    private val repository: LearningProfileRepository,
    private val seedWordBooks: SeedWordBooksUseCase,
    private val selectWordBook: SelectWordBookAndSetDailyTargetUseCase,
    private val getSettings: GetLearningSettingsUseCase,
    private val saveSettings: SaveLearningSettingsUseCase,
    private val cards: WordCardSource =
        object : WordCardSource {
            override suspend fun cardIds(wordBookId: String) = emptyList<String>()

            override suspend fun cards(cardIds: List<String>) =
                emptyList<com.example.englishlearning.learning.domain.WordCard>()
        },
    private val events: LearningEventRepository =
        object : LearningEventRepository {
            override suspend fun append(
                event: com.example.englishlearning.learning.domain.LearningEvent,
                nextState: com.example.englishlearning.learning.domain.CardReviewState,
            ) = com.example.englishlearning.learning.AppendEventResult.Appended(false)

            override suspend fun findEvent(eventId: String) =
                RepositoryResult.Success<com.example.englishlearning.learning.domain.LearningEvent?>(null)

            override suspend fun findCardState(cardId: String) =
                RepositoryResult.Success<com.example.englishlearning.learning.domain.CardReviewState?>(null)

            override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)

            override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(emptyList<String>())

            override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())

            override suspend fun dueCardIds(wordBookId: String, now: java.time.Instant) =
                RepositoryResult.Success(emptyList<String>())
        },
    private val bundledIds: BundledWordBookIdSource = BundledWordBookIdSource { emptySet() },
    private val importedIds: ImportedWordBookIdSource = ImportedWordBookIdSource { emptySet() },
    private val deletion: WordBookDeletionService = WordBookDeletionService(
        repository = repository,
        importedRoot = java.io.File(""),
        builtInWordBookIds = { emptySet() },
    ),
    private val migration: WordBookProgressMigrationCoordinator = WordBookProgressMigrationCoordinator(
        WordBookProgressMigrationService(cards, events),
    ),
    /**
     * 词条索引刷新：内置词书注册、导入、删除之后都从这里收敛。
     *
     * 可空是为了让既有单测不必构造整套索引依赖；生产装配点 [com.example.englishlearning.di.AppModule] 一定传入。
     */
    private val refreshIndex: RefreshVocabularySearchIndexUseCase? = null,
    /**
     * 删除确认框的级联计数预览（F4-01 Task10b，口径见决策 §6）。可空是为了让既有单测
     * 不必构造计数依赖；生产装配点 [com.example.englishlearning.di.AppModule] 一定传入。
     * 预览失败不阻断二次确认——只是少了数字，不是少了护栏。
     */
    private val deletePreview: WordBookDeletionPreviewRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LearningSetupUiState())
    val uiState: StateFlow<LearningSetupUiState> = _uiState
    private val _effects = MutableSharedFlow<LearningSetupEffect>()
    val effects: SharedFlow<LearningSetupEffect> = _effects
    private var settingsProfileId: String? = null

    /** 当前这一次加载的任务。新的 `load()` 必须取消它，见 [load]。 */
    private var loadJob: Job? = null

    fun load(profileId: String) {
        // 渐进发布把一次加载变成 N+2 次状态写入和一串挂起点：老任务必须在此彻底收手，
        // 否则它会拿过期的册列表与设置覆盖新任务刚发布的结果（删除词书后页面会再加载一次）。
        loadJob?.cancel()
        settingsProfileId = profileId
        // 同步置加载态：组合完成的第一帧就有骨架可看，而不是「卡一下、然后整列蹦出来」（spec F1-01）。
        _uiState.update { it.copy(wordBooksLoading = true, progressLoading = true, message = null) }
        loadJob = viewModelScope.launch { loadInto(profileId) }
    }

    private suspend fun loadInto(profileId: String) {
        seedWordBooks()
        val wordBooksResult = repository.listWordBooks()
        val profileResult = repository.current(profileId)
        if (wordBooksResult is RepositoryResult.Failure || profileResult is RepositoryResult.Failure) {
            _uiState.value = LearningSetupUiState(
                message = "暂时无法加载学习设置",
                wordBooksLoading = false,
                progressLoading = false,
            )
            return
        }
        val bundled = bundledIds.ids()
        val imported = importedIds.ids()
        val wordBooks = WordBookVisibility.visible(
            all = (wordBooksResult as RepositoryResult.Success).value,
            bundledIds = bundled,
            importedIds = imported,
        )
        val profile = (profileResult as RepositoryResult.Success).value
        val settings =
            when (val result = getSettings(profileId)) {
                is LearningSettingsRepositoryResult.Success -> result.value
                LearningSettingsRepositoryResult.StorageUnavailable -> LearningSettings.defaults(profileId)
            }
        val activeId = profile?.activeWordBookId?.takeIf { id -> wordBooks.any { it.id == id } }
        // 落笔前查一次任务是否还作数：上面这几步读盘一旦开始就停不下来（真实盘上拷贝不可中断），
        // `loadJob?.cancel()` 只能让协作式挂起点抛异常，挡不住「已经读完、正要写出去」的过期结果。
        if (!coroutineContext.isActive) return
        // 第一段发布：册先到、进度还空着。进度要逐册解析词书包（cet4 有 4308 张卡），
        // 让它挡在列表前面就是把最慢的一步放到了用户最先等的位置。
        _uiState.value =
            LearningSetupUiState(
                wordBooks = wordBooks,
                progressByBook = emptyMap(),
                importedBookIds = imported.filterTo(mutableSetOf()) { it in wordBooks.map(WordBook::id) },
                selectedWordBookId = activeId ?: wordBooks.firstOrNull()?.id,
                dailyNewTarget = profile?.dailyNewTarget ?: LearningSetupUiState.DEFAULT_DAILY_TARGET,
                savedWordBookId = activeId,
                savedWordBookName = wordBooks.find { it.id == activeId }?.displayName,
                openDetailOnKnown = settings.openDetailOnKnown,
                openDetailOnFuzzy = settings.openDetailOnFuzzy,
                openDetailOnForgotten = settings.openDetailOnForgotten,
                showVocabularySearchCount = settings.showVocabularySearchCount,
                wordBooksLoading = false,
                progressLoading = true,
            )
        try {
            // 第二段：逐册回填，每算完一册就发布一次，用户看到的是进度条一本本长出来。
            val progress = LinkedHashMap<String, WordBookProgress>()
            wordBooks.forEach { book ->
                progressOf(book)?.let { progress[book.id] = it }
                // 同上：这一本读完了、正要发布，任务可能已经在这段读盘里被取代。
                // 过期任务既不该把新进度表换成旧数据，也不该继续读后面几本（白读盘）。
                if (!coroutineContext.isActive) return
                _uiState.update { it.copy(progressByBook = LinkedHashMap(progress)) }
            }
        } finally {
            // 被取代的旧任务不许翻掉新任务的加载态；但单册读盘抛异常时必须收口，
            // 否则那一本会永远停在「进度计算中」。
            if (coroutineContext.isActive) {
                _uiState.update { it.copy(progressLoading = false) }
                // 种子写入只登记元数据；索引在这里补建/收敛，之后查词走索引而不解析词书包。
                // 放在回填之后：它服务的是查词，不该挡学习设置的首帧（`dataVersion` 未变时本身几乎不花钱）。
                refreshIndex?.invoke()
            }
        }
    }

    /**
     * 单册进度。两类降级都不许把整页钉在加载态上（AGENTS.md「本地优先：存储与网络不得阻塞 UI」）：
     * 读不到已学词时按 0 计、册子照常列出；而 `cards.cardIds` 是**会抛**的（内置册要从 assets 拷盘，
     * `assets.open` 抛异常而不是返回 Failure），此时返回 null 让界面显示「暂时读不到进度」，
     * 而不是编一个「已学 0 / 共 4308」的假数字。
     */
    private suspend fun progressOf(book: WordBook): WordBookProgress? =
        try {
            val ids = cards.cardIds(book.id)
            val reviewed = when (val result = events.reviewedCardIds(book.id)) {
                is RepositoryResult.Success -> result.value
                is RepositoryResult.Failure -> emptyList()
            }
            WordBookProgress.calculate(book.totalWords, ids, reviewed)
        } catch (cancellation: CancellationException) {
            // 协作式取消必须原样上抛：被取代的加载在这里就该停止写状态，
            // 而不是把「我被取消了」当成一次普通的读盘失败继续往下落笔。
            throw cancellation
        } catch (error: Exception) {
            null
        }

    fun selectWordBook(wordBookId: String) {
        _uiState.value = _uiState.value.copy(selectedWordBookId = wordBookId, savedWordBookName = null)
    }

    fun updateDailyNewTarget(target: Int) {
        _uiState.value = _uiState.value.copy(
            dailyNewTarget = target.coerceIn(
                LearningSetupUiState.MIN_DAILY_TARGET,
                LearningSetupUiState.MAX_DAILY_TARGET,
            ),
            savedWordBookName = null,
        )
    }

    fun setOpenDetailOnKnown(value: Boolean) {
        _uiState.value = _uiState.value.copy(openDetailOnKnown = value)
        persistSettings()
    }

    fun setOpenDetailOnFuzzy(value: Boolean) {
        _uiState.value = _uiState.value.copy(openDetailOnFuzzy = value)
        persistSettings()
    }

    fun setOpenDetailOnForgotten(value: Boolean) {
        _uiState.value = _uiState.value.copy(openDetailOnForgotten = value)
        persistSettings()
    }

    fun setShowVocabularySearchCount(value: Boolean) {
        _uiState.value = _uiState.value.copy(showVocabularySearchCount = value)
        persistSettings()
    }

    private fun persistSettings() {
        val profileId = settingsProfileId ?: return
        val state = _uiState.value
        viewModelScope.launch {
            saveSettings(
                LearningSettings(
                    profileId = profileId,
                    openDetailOnKnown = state.openDetailOnKnown,
                    openDetailOnFuzzy = state.openDetailOnFuzzy,
                    openDetailOnForgotten = state.openDetailOnForgotten,
                    showVocabularySearchCount = state.showVocabularySearchCount,
                ),
            )
        }
    }

    /**
     * 保存活动词书与每日目标。
     *
     * 若从**别的**词书切过来且目标册里存在已学过的同 lemma 词，先进入 `pendingMigration`
     * 让用户决定是否把复习状态标记过去，而不是静默迁移或静默丢弃。
     */
    fun save(profileId: String) {
        val state = _uiState.value
        val target = state.selectedWordBookId ?: return
        if (state.saving) return
        val source = state.savedWordBookId
        _uiState.value = state.copy(saving = true, message = null)
        viewModelScope.launch {
            if (source != null && source != target) {
                when (val preparation = migration.prepare(profileId, source, target)) {
                    is MigrationPreparation.RequiresConfirmation -> {
                        _uiState.value = _uiState.value.copy(
                            saving = false,
                            pendingMigration = preparation.preview,
                        )
                        return@launch
                    }
                    MigrationPreparation.SaveWithoutMigration -> Unit
                    MigrationPreparation.StorageUnavailable -> {
                        _uiState.value = _uiState.value.copy(
                            saving = false,
                            message = "暂时无法读取已学单词，请稍后重试",
                        )
                        return@launch
                    }
                }
            }
            persistSelection(profileId, target)
        }
    }

    /** 用户在重复词弹窗上选择「标记为已学习」或「暂不标记」。 */
    fun resolveMigration(profileId: String, apply: Boolean) {
        val state = _uiState.value
        val target = state.selectedWordBookId ?: return
        val source = state.savedWordBookId
        val preview = state.pendingMigration
        if (state.saving) return
        _uiState.value = state.copy(saving = true, pendingMigration = null)
        viewModelScope.launch {
            if (apply && preview != null && source != null) {
                when (migration.confirm(profileId, source, target, preview)) {
                    is ProgressMigrationResult.StorageUnavailable -> {
                        _uiState.value = _uiState.value.copy(
                            saving = false,
                            message = "已学单词标记失败，请稍后重试",
                        )
                        return@launch
                    }
                    is ProgressMigrationResult.Applied -> Unit
                }
            }
            persistSelection(profileId, target)
        }
    }

    private suspend fun persistSelection(profileId: String, target: String) {
        val state = _uiState.value
        when (selectWordBook(profileId, target, state.dailyNewTarget)) {
            SetupResult.Saved -> {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    savedWordBookId = target,
                    savedWordBookName = state.wordBooks.find { it.id == target }?.displayName,
                    message = null,
                )
                _effects.emit(LearningSetupEffect.Saved)
            }
            SetupResult.InvalidDailyTarget ->
                _uiState.value = _uiState.value.copy(saving = false, message = "每日新词目标至少为 1")
            SetupResult.UnknownWordBook ->
                _uiState.value = _uiState.value.copy(saving = false, message = "词书不可用")
            SetupResult.StorageUnavailable ->
                _uiState.value = _uiState.value.copy(saving = false, message = "暂时无法保存学习设置")
        }
    }

    fun requestDelete(book: WordBook) {
        if (!_uiState.value.canDelete(book.id)) return
        // 对话框先出现（行为与加预览前一致），条数随后回填；读不出条数不影响二次确认本身。
        _uiState.value = _uiState.value.copy(pendingDelete = book, deletePreview = null)
        viewModelScope.launch {
            val profileId = settingsProfileId ?: return@launch
            val preview = this@LearningSetupViewModel.deletePreview?.preview(profileId, book.id)?.getOrNull()
            if (_uiState.value.pendingDelete?.id == book.id) {
                _uiState.value = _uiState.value.copy(deletePreview = preview)
            }
        }
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(pendingDelete = null, deletePreview = null)
    }

    fun confirmDelete(profileId: String) {
        val book = _uiState.value.pendingDelete ?: return
        if (_uiState.value.deleting) return
        _uiState.value = _uiState.value.copy(deleting = true)
        viewModelScope.launch {
            val message =
                when (deletion.delete(profileId, book.id)) {
                    WordBookDeletionResult.Deleted -> null
                    WordBookDeletionResult.ProtectedActive -> "《${book.displayName}》正在使用中，请先切换到其他词书"
                    WordBookDeletionResult.ProtectedBuiltIn -> "《${book.displayName}》是内置词书，不能删除"
                    WordBookDeletionResult.NotFound -> "《${book.displayName}》已不存在"
                    WordBookDeletionResult.StorageUnavailable -> "删除失败，请稍后重试"
                }
            _uiState.value = _uiState.value.copy(deleting = false, pendingDelete = null, message = message)
            if (message == null) load(profileId)
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
