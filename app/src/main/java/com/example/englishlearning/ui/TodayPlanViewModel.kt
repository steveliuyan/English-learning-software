package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.AppendEventResult
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.UnlockPolicy
import com.example.englishlearning.learning.WordBookProgress
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Compatibility [LearningEventRepository] backing the two-argument secondary constructor.
 *
 * It models a plan that has recorded no learning events yet: every completion read returns an
 * empty success, so [load] still emits the classic [TodayPlanUiState.Ready] (locked, zero
 * progress) the older two-argument callers rely on. This is an *inert* implementation, not an
 * "unavailable" one — returning a storage failure would route a Ready plan to
 * [TodayPlanUiState.Unavailable] and break the legacy UI semantics.
 *
 * The event repository is only consulted inside the `Ready` branch of [load]; any non-Ready
 * result (Loading, MissingSetup, NotFound, StorageUnavailable) never reaches it.
 */
private object NoLearningEventRepository : LearningEventRepository {
    override suspend fun append(event: LearningEvent, nextState: CardReviewState) =
        AppendEventResult.Appended(duplicate = false)

    override suspend fun findEvent(eventId: String) = RepositoryResult.Success<LearningEvent?>(null)
    override suspend fun findCardState(cardId: String) = RepositoryResult.Success<CardReviewState?>(null)
    override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)
    override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(emptyList<String>())
    override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())
    override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
    override suspend fun reviewedStates(wordBookId: String) = RepositoryResult.Success(emptyList<CardReviewState>())
    override suspend fun upsertReviewState(state: CardReviewState) = RepositoryResult.Success(Unit)
}

/**
 * 空的词书卡片来源：任何词书都没有卡片。
 *
 * 与 [NoLearningEventRepository] 同属「惰性」而非「不可用」——返回空列表会让进度算成
 * `已学 0 / 共 <词书声明词数>`，界面照常渲染，不会把 Ready 计划误判成读取失败。
 */
private object EmptyWordCardSource : com.example.englishlearning.learning.WordCardSource {
    override suspend fun cardIds(wordBookId: String) = emptyList<String>()

    override suspend fun cards(cardIds: List<String>) =
        emptyList<com.example.englishlearning.learning.domain.WordCard>()
}

sealed interface TodayPlanUiState {
    data object Loading : TodayPlanUiState
    data class Ready(
        val wordBookName: String,
        val localDateLabel: String,
        val newTarget: Int,
        val dueTarget: Int,
        val totalTasks: Int,
        val newDone: Int = 0,
        val dueDone: Int = 0,
        val isUnlocked: Boolean = false,
        val unlockReason: String = "等待完成今日计划",
        val planId: String? = null,
        val activeWordBookId: String? = null,
        /**
         * 当前词书的整体学习进度（已学 / 总词数）。
         *
         * `null` 表示尚未算出（仍在加载，或旧构造点未提供卡片来源）。界面据此显示占位文案，
         * 而不是把「0 / 0」当成真实进度画出来——0% 和「还不知道」是两件事。
         */
        val bookProgress: WordBookProgress? = null,
    ) : TodayPlanUiState
    data object MissingSetup : TodayPlanUiState
    data object Unavailable : TodayPlanUiState
}

fun interface TodayPlanUseCaseContract { suspend operator fun invoke(profileId: String): TodayPlanResult }

@HiltViewModel
class TodayPlanViewModel @Inject constructor(
    private val useCase: TodayPlanUseCaseContract,
    private val profiles: LearningProfileRepository,
    private val events: LearningEventRepository,
    /**
     * 词书可见性来源。两个都为 `null` 表示**未配置**，此时不做可见性判定。
     *
     * 这与本文件里 [NoLearningEventRepository] 是同一类兼容语义：给旧的两/三参构造点（测试、
     * 截图用例）保留既有行为。生产路径由 Hilt 注入真实来源——缺绑定会在编译期直接失败，
     * 所以「静默退化成不判定」不会发生在发布包里。
     */
    private val bundledIds: com.example.englishlearning.learning.BundledWordBookIdSource? = null,
    private val importedIds: com.example.englishlearning.learning.ImportedWordBookIdSource? = null,
    /**
     * 词书卡片来源，用来算「当前词书已学多少 / 共多少」。
     *
     * 默认给空实现，与 [NoLearningEventRepository] 同一类兼容语义：旧的两/三参构造点保持既有行为。
     * 生产路径由 Hilt 注入真实的 [com.example.englishlearning.wordbook.CompositeWordCardSource]，
     * 缺绑定会在编译期直接失败，不会静默退化成「永远 0 词」。
     */
    private val cards: com.example.englishlearning.learning.WordCardSource = EmptyWordCardSource,
) : ViewModel() {
    constructor(useCase: TodayPlanUseCaseContract, profiles: LearningProfileRepository) : this(useCase, profiles, NoLearningEventRepository)
    private val _uiState = MutableStateFlow<TodayPlanUiState>(TodayPlanUiState.Loading)
    val uiState: StateFlow<TodayPlanUiState> = _uiState

    fun load(profileId: String) {
        viewModelScope.launch {
            _uiState.value = TodayPlanUiState.Loading
            when (val result = useCase(profileId)) {
                is TodayPlanResult.Ready -> {
                    val plan = result.plan
                    // 活动词书可能已经不可见：被下线的旧占位分组册，或导入包被外部删掉。
                    // 此时今日计划虽然「可读」却是空壳（0 个新词、0 个复习），继续显示会让
                    // 用户以为今天没任务，所以按「还没选好词书」处理，把人送回选书页。
                    if (!isAvailable(plan.activeWordBookId)) {
                        _uiState.value = TodayPlanUiState.MissingSetup
                        return@launch
                    }
                    val book = when (val books = profiles.findWordBook(plan.activeWordBookId)) {
                        is RepositoryResult.Success -> books.value
                        is RepositoryResult.Failure -> null
                    }
                    val name = book?.displayName ?: plan.activeWordBookId
                    // 整册进度与今日任务无关：它回答的是「这本词书我啃掉多少了」。
                    // 卡片总数优先用词书自己声明的 totalWords——包解析失败时 cardIds 会是空的，
                    // 若用它当分母会把进度画成 100%，那是比不显示更有害的误导。
                    // 分母为 0 表示「还不知道」，此时给 null 让界面隐藏进度行，而不是画成 0%。
                    val cardIds = cards.cardIds(plan.activeWordBookId)
                    val reviewedCardIds = when (val reviewed = events.reviewedCardIds(plan.activeWordBookId)) {
                        is RepositoryResult.Success -> reviewed.value
                        is RepositoryResult.Failure -> emptyList()
                    }
                    val bookWordCount = book?.totalWords?.takeIf { it > 0 } ?: cardIds.size
                    val bookProgress = if (bookWordCount > 0) {
                        WordBookProgress.calculate(bookWordCount, cardIds, reviewedCardIds)
                    } else {
                        null
                    }
                    when (val completed = events.completedCardIds(plan.planId)) {
                        is RepositoryResult.Success -> {
                            val mode = UnlockPolicy.modeForRuleVersion(plan.ruleVersion)
                            val progress = UnlockPolicy.progress(plan, completed.value)
                            val unlocked = UnlockPolicy.isUnlocked(plan, completed.value, mode)
                            val reason = if (unlocked) "今日计划已完成" else UnlockPolicy.lockedReason(mode)
                            _uiState.value = TodayPlanUiState.Ready(
                                name, plan.localDate.toString(), plan.newTarget, plan.dueTarget,
                                plan.newTarget + plan.dueTarget, progress.newDone, progress.dueDone,
                                unlocked, reason, plan.planId, plan.activeWordBookId,
                                bookProgress = bookProgress,
                            )
                        }
                        is RepositoryResult.Failure -> _uiState.value = TodayPlanUiState.Unavailable
                    }
                }
                TodayPlanResult.MissingLearningSetup -> _uiState.value = TodayPlanUiState.MissingSetup
                TodayPlanResult.NotFound, TodayPlanResult.StorageUnavailable -> _uiState.value = TodayPlanUiState.Unavailable
            }
        }
    }

    /** 内置十册或已导入册才算「可选」；其余（旧占位分组册、已删除的导入包）视为不可用。 */
    private fun isAvailable(wordBookId: String): Boolean {
        val bundled = bundledIds ?: return true
        val imported = importedIds ?: return wordBookId in bundled.ids()
        return wordBookId in bundled.ids() || wordBookId in imported.ids()
    }
}
