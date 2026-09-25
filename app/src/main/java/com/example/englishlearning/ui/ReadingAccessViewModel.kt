package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.ArticleContext
import com.example.englishlearning.reading.ArticleGenerationRequest
import com.example.englishlearning.reading.ArticleSourceRegistry
import com.example.englishlearning.reading.FetchArticleResult
import com.example.englishlearning.reading.FetchArticleUseCase
import com.example.englishlearning.reading.FetchFailure
import com.example.englishlearning.reading.FeedItem
import com.example.englishlearning.reading.GenerateArticleResult
import com.example.englishlearning.reading.GenerateArticleUseCase
import com.example.englishlearning.reading.ImportArticleResult
import com.example.englishlearning.reading.ImportArticleUseCase
import com.example.englishlearning.reading.ImportRejection
import com.example.englishlearning.reading.TranslationLanguage
import com.example.englishlearning.reading.ArticleRepository
import com.example.englishlearning.reading.ReadingPreferenceRepository
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
import com.example.englishlearning.reading.resolveArticleLength
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** AI 生成在「阅读」页的界面状态。Cancelled 不在这里：取消就是回到 Idle，不弹失败。 */
sealed interface GenerationUiState {
    data object Idle : GenerationUiState
    data object Generating : GenerationUiState

    /** 需要用户对 [host] 给出站确认；确认与否由 [ReadingAccessViewModel.confirmOutbound] 收口。 */
    data class NeedsConfirmation(val host: String) : GenerationUiState
    data class Failed(val failure: AiFailure) : GenerationUiState

    /** 校验与请求都成功但本地存储失败——处置是「稍后再试」，与 AI 失败分开，不得混用。 */
    data object StorageFailed : GenerationUiState
}

/** 粘贴导入的表单与结果状态。全程不出网，所以没有确认态。 */
data class ImportUiState(
    val title: String = "",
    val body: String = "",
    val submitting: Boolean = false,
    val rejection: ImportRejection? = null,
    val storageFailed: Boolean = false,
)

sealed interface FeedUiState {
    data object Idle : FeedUiState
    data object Loading : FeedUiState

    /** [items] 非空；空列表单独给 [Empty]，空态必须显式而不是一块白屏。 */
    data class Ready(val items: List<FeedItem>, val sourceDisplayName: String) : FeedUiState
    data object Empty : FeedUiState

    /** 索引拿不到（网络不可用 / 站点不可达 / 未注册来源）。 */
    data object Unreachable : FeedUiState

    /** 索引成功但具体文章页拿不到，原因具体到检查项。 */
    data class FetchFailed(val failure: FetchFailure) : FeedUiState
}

/** 当前正在阅读的文章与其关联词卡；null 表示没有打开的文章。 */
data class ReadingTarget(val article: Article, val cards: List<WordCard>)

/**
 * 「阅读」栏的编排 ViewModel：三种来源（AI 生成 / 外刊选取 / 粘贴导入）共用一个
 * Ready 状态与一个 [readingTarget] 出口。阅读解锁的前提是今日计划已完成，所以
 * 所有需要 `wordBookId` 与今日词卡的编排都从今日计划取数；计划缺失时静默回到
 * 空闲——入口按钮本来就不该出现在未完成计划的状态里。
 */
@HiltViewModel
class ReadingAccessViewModel @Inject constructor(
    private val articles: ArticleRepository,
    private val preferences: ReadingPreferenceRepository,
    private val plans: TodayPlanRepository,
    private val events: LearningEventRepository,
    private val cardSource: WordCardSource,
    private val generateArticles: GenerateArticleUseCase,
    private val fetchArticles: FetchArticleUseCase,
    private val importArticles: ImportArticleUseCase,
    private val clock: ClockProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ReadingAccessUiState>(ReadingAccessUiState.Loading)
    val uiState: StateFlow<ReadingAccessUiState> = _uiState

    private val _readingTarget = MutableStateFlow<ReadingTarget?>(null)
    val readingTarget: StateFlow<ReadingTarget?> = _readingTarget

    private var profileId: String? = null

    /** 出站确认后重放生成时要带上「是不是换一篇」，否则确认会把重放变回复用。 */
    private var pendingRegenerate = false

    fun load(profileId: String, isUnlocked: Boolean, unlockReason: String) {
        this.profileId = profileId
        _uiState.value = ReadingAccessUiState.Loading
        viewModelScope.launch {
            val preference = preferences.getPreference(profileId)
            val history = articles.findHistory(profileId)
            _uiState.value = when {
                preference.isFailure || history.isFailure -> ReadingAccessUiState.Unavailable
                !isUnlocked -> ReadingAccessUiState.Locked(unlockReason)
                else -> {
                    val pref = preference.getOrThrow()
                    ReadingAccessUiState.Ready(pref, history.getOrThrow(), todayArticle = todayArticle(profileId, pref))
                }
            }
        }
    }

    fun selectType(type: ArticleType) {
        val current = _uiState.value as? ReadingAccessUiState.Ready ?: return
        val updatedPreference = current.preference.copy(defaultArticleType = type)
        _uiState.value = current.copy(preference = updatedPreference)
        viewModelScope.launch { preferences.savePreference(updatedPreference) }
    }

    fun generate(regenerate: Boolean = false) {
        runGeneration(regenerate, confirmedTextHost = null)
    }

    fun confirmOutbound(confirmed: Boolean) {
        val host = generationState().let { it as? GenerationUiState.NeedsConfirmation }?.host
        if (!confirmed || host == null) {
            setGeneration(GenerationUiState.Idle)
            return
        }
        runGeneration(regenerate = pendingRegenerate, confirmedTextHost = host)
    }

    // ---------- 粘贴导入 ----------

    fun importTitleChange(value: String) {
        val current = importState() ?: return
        setImport(current.copy(title = value, rejection = null))
    }

    fun importBodyChange(value: String) {
        val current = importState() ?: return
        setImport(current.copy(body = value, rejection = null))
    }

    fun submitImport() {
        val id = profileId ?: return
        val draft = importState() ?: return
        viewModelScope.launch {
            setImport(draft.copy(submitting = true, rejection = null, storageFailed = false))
            val plan = planOf(id) ?: run {
                setImport(draft.copy(submitting = false))
                return@launch
            }
            val context = ArticleContext(
                profileId = id,
                localDate = today().toString(),
                activeWordBookId = plan.activeWordBookId,
            )
            when (val result = importArticles.import(draft.title, draft.body, context)) {
                is ImportArticleResult.Imported -> {
                    setImport(ImportUiState())
                    _readingTarget.value = ReadingTarget(result.article, completedCards(plan))
                }
                is ImportArticleResult.Rejected ->
                    setImport(draft.copy(submitting = false, rejection = result.reason))
                is ImportArticleResult.Failed ->
                    setImport(draft.copy(submitting = false, storageFailed = true))
            }
        }
    }

    // ---------- 外刊 ----------

    fun openFeed() {
        viewModelScope.launch {
            setFeed(FeedUiState.Loading)
            when (val result = fetchArticles.list(FEED_SOURCE_ID)) {
                is FetchArticleResult.Listed -> setFeed(
                    if (result.items.isEmpty()) {
                        FeedUiState.Empty
                    } else {
                        FeedUiState.Ready(result.items, ArticleSourceRegistry.byId(FEED_SOURCE_ID)?.displayName ?: "")
                    },
                )
                // 索引拿不到只说「来源此刻不可用」；RejectedTarget 对注册表常量不可达，同样归入。
                is FetchArticleResult.Failed -> setFeed(FeedUiState.Unreachable)
                is FetchArticleResult.RejectedTarget -> setFeed(FeedUiState.Unreachable)
                is FetchArticleResult.Fetched -> Unit
            }
        }
    }

    fun fetchFeedItem(item: FeedItem) {
        val id = profileId ?: return
        viewModelScope.launch {
            val plan = planOf(id)
            val context = ArticleContext(
                profileId = id,
                localDate = today().toString(),
                activeWordBookId = plan?.activeWordBookId ?: "",
            )
            when (val result = fetchArticles.fetch(FEED_SOURCE_ID, item, context)) {
                is FetchArticleResult.Fetched ->
                    _readingTarget.value = ReadingTarget(result.article, plan?.let { completedCards(it) } ?: emptyList())
                is FetchArticleResult.Failed -> setFeed(FeedUiState.FetchFailed(result.failure))
                // 白名单拒绝意味着链接被注入或来源改版：一个字节没发出去，按来源不可用呈现。
                is FetchArticleResult.RejectedTarget -> setFeed(FeedUiState.FetchFailed(FetchFailure.SourceUnreachable))
                is FetchArticleResult.Listed -> Unit
            }
        }
    }

    fun closeArticle() {
        _readingTarget.value = null
    }

    /** 「阅读今天的文章」：把 load() 已发现的今日文章作为阅读目标打开。 */
    fun openTodayArticle() {
        val id = profileId ?: return
        viewModelScope.launch {
            val ready = _uiState.value as? ReadingAccessUiState.Ready ?: return@launch
            val article = ready.todayArticle ?: return@launch
            val plan = planOf(id)
            _readingTarget.value = ReadingTarget(article, plan?.let { completedCards(it) } ?: emptyList())
        }
    }

    // ---------- 内部 ----------

    private fun runGeneration(regenerate: Boolean, confirmedTextHost: String?) {
        val id = profileId ?: return
        pendingRegenerate = regenerate
        viewModelScope.launch {
            setGeneration(GenerationUiState.Generating)
            try {
                val ready = _uiState.value as? ReadingAccessUiState.Ready ?: return@launch
                val plan = planOf(id) ?: run {
                    setGeneration(GenerationUiState.Idle)
                    return@launch
                }
                val targetCards = completedCards(plan)
                val request = ArticleGenerationRequest(
                    profileId = id,
                    localDate = today().toString(),
                    wordBookId = plan.activeWordBookId,
                    articleType = ready.preference.defaultArticleType,
                    length = resolveArticleLength(plan.activeWordBookId, ready.preference.explicitLengthTier),
                    language = TranslationLanguage.ZH,
                    targetCards = targetCards,
                    // 换一篇时把已覆盖的词交给模型避开；确认重放沿用同一份，保证两次请求一致。
                    excludedLemmas = if (regenerate) ready.todayArticle?.coveredLemmas.orEmpty() else emptyList(),
                )
                when (val result = generateArticles.generate(request, confirmedTextHost, regenerate)) {
                    is GenerateArticleResult.Reused -> openGenerated(result.article, targetCards)
                    is GenerateArticleResult.Generated -> openGenerated(result.article, targetCards)
                    is GenerateArticleResult.NeedsConfirmation ->
                        setGeneration(GenerationUiState.NeedsConfirmation(result.host))
                    is GenerateArticleResult.Failed -> setGeneration(GenerationUiState.Failed(result.failure))
                    is GenerateArticleResult.NotConfigured ->
                        // 配置类问题的处置也是「去配置」，界面动作与 NotConfigured 一致。
                        setGeneration(GenerationUiState.Failed(AiFailure.NotConfigured))
                    GenerateArticleResult.StorageFailed -> setGeneration(GenerationUiState.StorageFailed)
                }
            } catch (e: CancellationException) {
                // 用户取消不是失败：回到 Idle 让按钮恢复，取消本身继续传播。
                setGeneration(GenerationUiState.Idle)
                throw e
            }
        }
    }

    private fun openGenerated(article: Article, cards: List<WordCard>) {
        val current = _uiState.value as? ReadingAccessUiState.Ready ?: return
        _uiState.value = current.copy(todayArticle = article, generation = GenerationUiState.Idle)
        _readingTarget.value = ReadingTarget(article, cards)
    }

    private fun generationState(): GenerationUiState =
        (_uiState.value as? ReadingAccessUiState.Ready)?.generation ?: GenerationUiState.Idle

    private fun importState(): ImportUiState? =
        (_uiState.value as? ReadingAccessUiState.Ready)?.import

    private fun setGeneration(state: GenerationUiState) {
        val current = _uiState.value as? ReadingAccessUiState.Ready ?: return
        _uiState.value = current.copy(generation = state)
    }

    private fun setImport(state: ImportUiState) {
        val current = _uiState.value as? ReadingAccessUiState.Ready ?: return
        _uiState.value = current.copy(import = state)
    }

    private fun setFeed(state: FeedUiState) {
        val current = _uiState.value as? ReadingAccessUiState.Ready ?: return
        _uiState.value = current.copy(feed = state)
    }

    // 不用 LocalDate.ofInstant：那是 API 34+ 的方法，Android 13 真机直接 NoSuchMethodError。
    private fun today(): LocalDate = clock.instant().atZone(clock.zoneId()).toLocalDate()

    private suspend fun planOf(profileId: String): TodayPlan? =
        (plans.find(profileId, today()) as? TodayPlanResult.Ready)?.plan

    /** 今日文章 = 与偏好类型/档位同复用键的最新一版；没有计划就没有复用键。 */
    private suspend fun todayArticle(profileId: String, preference: ReadingPreference): Article? {
        val plan = planOf(profileId) ?: return null
        val length = resolveArticleLength(plan.activeWordBookId, preference.explicitLengthTier)
        return articles.findLatest(
            profileId,
            today().toString(),
            plan.activeWordBookId,
            preference.defaultArticleType,
            length.tier,
        ).getOrNull()
    }

    private suspend fun completedCards(plan: TodayPlan): List<WordCard> {
        val cardIds = when (val completed = events.completedCardIds(plan.planId)) {
            is RepositoryResult.Success -> completed.value
            is RepositoryResult.Failure -> emptyList()
        }
        return cardSource.cards(cardIds)
    }

    private companion object {
        const val FEED_SOURCE_ID = "voa-learning-english"
    }
}
