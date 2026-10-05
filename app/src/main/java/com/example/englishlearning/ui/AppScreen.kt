package com.example.englishlearning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import kotlinx.coroutines.Job
import androidx.compose.runtime.rememberCoroutineScope
import com.example.englishlearning.language.domain.PronunciationProvider
import com.example.englishlearning.language.domain.PronunciationResult
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.englishlearning.export.WorksheetShareLauncher
import androidx.compose.ui.unit.dp
import com.example.englishlearning.R
import com.example.englishlearning.learning.WordBook
import com.example.englishlearning.ui.theme.AppleMintEnd
import com.example.englishlearning.ui.theme.AppleMintLight
import com.example.englishlearning.ui.theme.AppleMintMiddle
import com.example.englishlearning.ui.theme.AppleMintStart
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint
import kotlin.math.roundToInt
import java.time.Instant

private val AppleMintGradient = Brush.linearGradient(listOf(AppleMintStart, AppleMintMiddle, AppleMintEnd))

/**
 * 词卡详情当前是**从哪一份列表**打开的。
 *
 * 用枚举而不是裸字符串，是因为这个值只有一个用途：决定详情页「下一个」按哪份结果集推进。
 * 字符串写错（例如把主页搜索也写成 `DICTIONARY`）不会报错，只会静默地用另一份列表导航——
 * 之前 `detailSource = "search"` 就同时被旧词书搜索页和主页全量搜索页复用，
 * 从主页搜索结果点进去再按「下一个」，推进的是旧页面那份列表。
 */
private enum class DetailSource { VOCABULARY, RECORDS, ARTICLE, GLOBAL_SEARCH }

/**
 * `items` 里第一项满足 [matches] 的元素的**下一项**；没命中或已在末尾时返回 `null`。
 *
 * 单独抽出来是因为这里有个很容易写错的边界：`indexOfFirst` 未命中返回 `-1`，
 * 直接写 `items[index + 1]` 会取到**首项**——于是「下一个」在末页会悄悄跳回第一个词。
 */
private fun <T> nextAfter(items: List<T>, matches: (T) -> Boolean): T? {
    val index = items.indexOfFirst(matches)
    return if (index >= 0) items.getOrNull(index + 1) else null
}

@Composable
fun AppScreen(
    viewModel: AppViewModel,
    learningSetupViewModel: LearningSetupViewModel,
    todayPlanViewModel: TodayPlanViewModel,
    wordCardViewModel: WordCardViewModel,
    worksheetViewModel: WorksheetViewModel? = null,
    readingAccessViewModel: ReadingAccessViewModel? = null,
    articleReadingViewModel: ArticleReadingViewModel? = null,
    aiProfileViewModel: AiProfileSettingsViewModel? = null,
    speechSettingsViewModel: SpeechSettingsViewModel? = null,
    checkInViewModel: CheckInViewModel? = null,
    learningRecordsViewModel: LearningRecordsViewModel? = null,
    vocabularyViewModel: VocabularyViewModel? = null,
    vocabularyRelearnViewModel: VocabularyRelearnViewModel? = null,
    pronunciationProvider: PronunciationProvider? = null,
    wordQaViewModel: WordAiQaViewModel? = null,
    globalVocabularySearchViewModel: GlobalVocabularySearchViewModel? = null,
    refreshVocabularySearchIndex: com.example.englishlearning.learning.RefreshVocabularySearchIndexUseCase? = null,
    sentenceAnalysisViewModel: SentenceAnalysisViewModel? = null,
    imageStudioViewModel: ImageStudioViewModel? = null,
    wordBookTransferViewModel: com.example.englishlearning.wordbook.WordBookTransferViewModel? = null,
) {
    val pronunciationScope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    when (val state = viewModel.uiState.collectAsState().value) {
        AppUiState.Loading -> BrandLaunchSurface()
        AppUiState.NeedsProfile -> ProfileCreationScreen(
            name = name,
            onNameChange = { name = it },
            onCreate = { viewModel.createProfile(name) },
        )
        is AppUiState.Ready -> {
            var selectedTab by rememberSaveable(state.profile.id) { mutableStateOf(AppTab.LEARNING) }
            var showSetup by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showLearning by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showReadingHistory by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showWorksheetSettings by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showWorksheetPreview by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            // 选中的 AI 功能存 key 而不是枚举实例：String 进 Bundle 最省心，将来加功能也不用改存法。
            var selectedFeatureKey by rememberSaveable(state.profile.id) { mutableStateOf<String?>(null) }
            var showAiProfiles by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showSpeechSettings by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showCheckIn by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showLearningRecords by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showVocabulary by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showVocabularyRelearn by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var selectedRelearnCard by remember { mutableStateOf<com.example.englishlearning.learning.domain.WordCard?>(null) }
            var showGlobalVocabularySearch by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var selectedSearchCard by remember { mutableStateOf<com.example.englishlearning.learning.domain.WordCard?>(null) }
            var detailSource by remember { mutableStateOf<DetailSource?>(null) }
            var searchPronunciationRequest by remember { mutableStateOf(0L) }
            var searchPronunciationMessage by remember { mutableStateOf(PronunciationStatus.Idle) }
            var searchPronunciationJob by remember { mutableStateOf<Job?>(null) }
            DisposableEffect(state.profile.id) {
                onDispose {
                    searchPronunciationJob?.cancel()
                }
            }
            val closeSearchDetail: () -> Unit = {
                selectedSearchCard = null
                searchPronunciationMessage = PronunciationStatus.Idle
                searchPronunciationRequest++
                searchPronunciationJob?.cancel()
            }
            var selectedLearningRecordCard by remember { mutableStateOf<com.example.englishlearning.learning.domain.WordCard?>(null) }
            var learningRecordPronunciationRequest by remember { mutableStateOf(0L) }
            val learningRecordsState by learningRecordsViewModel?.state?.collectAsState() ?: remember { mutableStateOf(LearningRecordsUiState()) }
            val vocabularyState by vocabularyViewModel?.state?.collectAsState() ?: remember { mutableStateOf<VocabularyUiState>(VocabularyUiState.Loading) }
            val globalSearchState = globalVocabularySearchViewModel?.uiState?.collectAsState()?.value
                ?: GlobalVocabularySearchUiState.Idle
            val globalSearchQuery = globalVocabularySearchViewModel?.query?.collectAsState()?.value.orEmpty()
            // 搜索次数开关的唯一权威来源就是学习设置本身；这里只是把它同步给搜索 ViewModel，
            // 让「关闭显示」立刻生效，而不是等下次打开应用。
            val showVocabularySearchCount = learningSetupViewModel.uiState.collectAsState().value.showVocabularySearchCount
            LaunchedEffect(showVocabularySearchCount) {
                globalVocabularySearchViewModel?.setShowCount(showVocabularySearchCount)
            }
            // 启动即收敛派生数据：把词条索引对齐到当前可见词书。
            // 之前唯一的建索引入口是 LearningSetupViewModel.load，而它只在「学习设置页」被打开时才跑；
            // 老用户根本不会再进那一页，于是索引一直空着——主页查词任何词都查不到。
            // 2026-10-05 真机实证过这个缺口（迁移后 user_version=24 但 vocabulary_search_index 0 行）。
            LaunchedEffect(state.profile.id) {
                refreshVocabularySearchIndex?.invoke()
            }
            // 这里只对齐身份，**不**重置查询词与结果：两个入口（主页查词、阅读页点未知词）
            // 各自通过 `openSearch` 决定初始查询词。若在这里顺手清一次，
            // 阅读页刚带进来的那个词会被覆盖掉——用户点了一个词，看到的却是「最近搜索」。
            LaunchedEffect(state.profile.id) {
                globalVocabularySearchViewModel?.setProfile(state.profile.id)
            }
            // 点开主页搜索结果：ViewModel 回源取到完整词卡后发出一次性事件，这里把它交给详情页。
            // 「一次性」是关键——ViewModel 在界面消费后会把值置空，否则第二次点同一条时
            // 值没有变化，这个 effect 不会重跑，用户看到的就是「点了没反应」。
            //
            // 带 `showGlobalVocabularySearch` 作为 key：回源要解析一整册词书包，不是瞬时的。
            // 若用户在这段时间里按返回退出了搜索页，取回的卡片必须被丢弃，
            // 否则详情页会在主页之上凭空弹出来。
            val openedGlobalSearchCard = globalVocabularySearchViewModel?.openedCard?.collectAsState()?.value
            LaunchedEffect(openedGlobalSearchCard, showGlobalVocabularySearch) {
                val opened = openedGlobalSearchCard ?: return@LaunchedEffect
                globalVocabularySearchViewModel?.consumeOpenedCard()
                if (!showGlobalVocabularySearch) return@LaunchedEffect
                searchPronunciationMessage = PronunciationStatus.Idle
                searchPronunciationRequest++
                searchPronunciationJob?.cancel()
                detailSource = DetailSource.GLOBAL_SEARCH
                selectedSearchCard = opened
            }
            val vocabularyCards = (vocabularyState as? VocabularyUiState.Ready)?.items?.mapNotNull { it.card }.orEmpty()
            val recordCards = when (learningRecordsState.selectedTab) {
                LearningRecordsTab.TODAY -> learningRecordsState.today.mapNotNull { it.wordCard }
                LearningRecordsTab.HISTORY -> learningRecordsState.history.flatMap { it.records }.mapNotNull { it.wordCard }
                LearningRecordsTab.ALL_WORDS -> learningRecordsState.allWords.mapNotNull { it.card }
            }
            val vocabularyMembership by vocabularyViewModel?.membership?.collectAsState() ?: remember { mutableStateOf<VocabularyMembership?>(null) }
            val vocabularyMasteryState by vocabularyViewModel?.mastery?.collectAsState() ?: remember { mutableStateOf<VocabularyMasteryState>(VocabularyMasteryState.Idle) }
            var showWordBookExportPicker by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var exportWordBookId by rememberSaveable(state.profile.id) { mutableStateOf<String?>(null) }
            // 阅读来源的二级层：导入页是显式导航；文章页由 readingTarget 驱动；词卡详情是
            // 阅读页之上的本地覆盖层（WordCardViewModel 的详情只服务学习流，不复用）。
            var showArticleImport by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var articleScrollPosition by rememberSaveable(state.profile.id) { mutableStateOf(0) }
            var selectedArticleCard by remember { mutableStateOf<com.example.englishlearning.learning.domain.WordCard?>(null) }
            var wordCardPronunciationMessage by remember { mutableStateOf<String?>(null) }
            var wordCardPronunciationRequest by remember { mutableIntStateOf(0) }
            // 详情页只持有无字段状态枚举（安全边界），文案映射在 CardDetailScreen 内完成。
            var detailPronunciationMessage by remember { mutableStateOf<PronunciationStatus?>(null) }
            var detailPronunciationRequest by remember { mutableIntStateOf(0) }
            // F3-03 词 AI 问答覆盖层：从任一词卡详情的「问 AI」进入；WordCard 进不了
            // Bundle，所以和 selectedArticleCard 一样用 remember 而不是 rememberSaveable。
            var showWordQaCard by remember { mutableStateOf<com.example.englishlearning.learning.domain.WordCard?>(null) }
            // F3-01A：从历史列表点开的旧版文章。与 readingTarget 互斥展示；Article 进不了
            // Bundle，所以和 selectedArticleCard 一样用 remember 而不是 rememberSaveable。
            var historyArticle by remember { mutableStateOf<com.example.englishlearning.reading.domain.Article?>(null) }
            val selectedFeature = AiFeature.entries.firstOrNull { it.key == selectedFeatureKey }
            LaunchedEffect(state.profile.id) { todayPlanViewModel.load(state.profile.id) }
            // AI 配置是设备级的（与学习者无关），启动时读一次，好让「设置」栏的摘要和「AI 学」页
            // 的徽章说的是本机真实状态，而不是写死的假设。
            LaunchedEffect(state.profile.id) { aiProfileViewModel?.load() }
            LaunchedEffect(state.profile.id) { speechSettingsViewModel?.load() }
            val aiProfiles = aiProfileViewModel?.listState?.collectAsState()?.value as? AiProfileListUiState.Ready
            val speechState = speechSettingsViewModel?.state?.collectAsState()?.value ?: SpeechSettingsUiState()
            val aiConfigured = aiProfiles?.items?.any { it.hasKey } == true
            val todayState by todayPlanViewModel.uiState.collectAsState()
            val wordQaUiState = wordQaViewModel?.uiState?.collectAsState()?.value ?: WordAiQaUiState.Idle
            val personalNoteSaveStatus = wordQaViewModel?.personalNoteSaveStatus?.collectAsState()?.value
                ?: PersonalNoteSaveStatus.Idle
            val todayReady = todayState as? TodayPlanUiState.Ready
            val setupRequired = todayState == TodayPlanUiState.MissingSetup
            LaunchedEffect(showLearningRecords, state.profile.id, todayReady?.planId, todayReady?.activeWordBookId) {
                if (showLearningRecords) learningRecordsViewModel?.load(state.profile.id, todayReady?.planId, todayReady?.activeWordBookId)
            }
            // 进入阅读 tab 才算准入；今日计划未就绪时按「未解锁 + 说明原因」处理，不假装已解锁。
            LaunchedEffect(selectedTab, state.profile.id, todayReady?.isUnlocked) {
                if (selectedTab == AppTab.READING) {
                    readingAccessViewModel?.load(
                        profileId = state.profile.id,
                        isUnlocked = todayReady?.isUnlocked == true,
                        unlockReason = todayReady?.unlockReason ?: "今日计划尚未就绪",
                    )
                }
            }
            val readingState: ReadingAccessUiState =
                readingAccessViewModel?.uiState?.collectAsState()?.value ?: ReadingAccessUiState.Loading
            val readingTarget = readingAccessViewModel?.readingTarget?.collectAsState()?.value
            // 任一来源产出文章都会设置 readingTarget：此时导入页让位给文章页，
            // 并把文章与词卡交给阅读页 ViewModel 渲染。
            LaunchedEffect(readingTarget) {
                val target = readingTarget ?: return@LaunchedEffect
                showArticleImport = false
                articleReadingViewModel?.load(target.article, target.cards)
            }
            // 历史旧版没有「今日计划的词卡」，传空表：点词时 cardFor 返回 null，
            // 屏幕按既有占位路径处理（词典/发音入口本来就是后续版本）。
            LaunchedEffect(historyArticle) {
                val article = historyArticle ?: return@LaunchedEffect
                articleReadingViewModel?.load(article, emptyList())
            }
            // Only an user-opened setup screen may be dismissed; a mandatory setup (no active
            // word book yet) must stay until a word book is saved, so it gets no escape hatch.
            val cancelSetup: (() -> Unit)? = if (showSetup && !setupRequired) {
                { showSetup = false }
            } else {
                null
            }
            // Single exit for the learning flow. It always returns to the today page *and*
            // recomputes its state (F1-04: entering the today page recalculates progress), so
            // work reviewed in the cards shows immediately instead of only after a restart.
            val exitLearning: () -> Unit = {
                wordCardPronunciationRequest++
                wordCardPronunciationMessage = null
                showLearning = false
                todayPlanViewModel.load(state.profile.id)
            }
            LaunchedEffect(showCheckIn, state.profile.id) {
                if (showCheckIn) checkInViewModel?.load(state.profile.id)
            }
            val overlayOpen = setupRequired || showSetup || showLearning || showReadingHistory || showCheckIn || selectedRelearnCard != null ||
                showWorksheetSettings || showWorksheetPreview || selectedFeature != null || showAiProfiles || showSpeechSettings || showLearningRecords || selectedLearningRecordCard != null || showGlobalVocabularySearch || showVocabularyRelearn || selectedSearchCard != null ||
                showArticleImport || readingTarget != null || historyArticle != null || selectedArticleCard != null ||
                showWordQaCard != null
            // BackHandler 按「后声明者优先」分派，所以下面严格按优先级从低到高排列：层级越靠内
            // 越晚声明，越先拿到返回键。调整顺序会直接改变返回键行为，别随手重排。
            BackHandler(enabled = !overlayOpen && selectedTab != AppTab.LEARNING) {
                selectedTab = AppTab.LEARNING
            }
            BackHandler(enabled = cancelSetup != null) { cancelSetup?.invoke() }
            // Leaving the learning flow is always allowed; unsubmitted cards simply stay open.
            BackHandler(enabled = showLearning) { exitLearning() }
            BackHandler(enabled = showReadingHistory) { showReadingHistory = false }
            BackHandler(enabled = showCheckIn) { showCheckIn = false }
            BackHandler(enabled = selectedLearningRecordCard != null) {
                selectedLearningRecordCard = null
                detailPronunciationMessage = PronunciationStatus.Idle
                learningRecordPronunciationRequest++
                learningRecordsViewModel?.clearDetail()
            }
            BackHandler(enabled = selectedSearchCard != null) { closeSearchDetail() }
            BackHandler(enabled = showGlobalVocabularySearch && selectedSearchCard == null) {
                showGlobalVocabularySearch = false
            }
            BackHandler(enabled = showLearningRecords && selectedLearningRecordCard == null) { showLearningRecords = false }
            BackHandler(enabled = selectedRelearnCard != null) {
                selectedRelearnCard = null
                vocabularyRelearnViewModel?.load(state.profile.id, todayReady?.activeWordBookId)
            }
            BackHandler(enabled = showVocabularyRelearn) {
                showVocabularyRelearn = false
                vocabularyViewModel?.load(state.profile.id)
            }
            BackHandler(enabled = showVocabulary && selectedSearchCard == null) { showVocabulary = false }
            LaunchedEffect(showVocabularyRelearn, state.profile.id) {
                if (showVocabularyRelearn) vocabularyRelearnViewModel?.load(state.profile.id, todayReady?.activeWordBookId)
            }
            LaunchedEffect(showVocabulary, state.profile.id) {
                if (showVocabulary) vocabularyViewModel?.load(state.profile.id)
            }
            LaunchedEffect(selectedTab, state.profile.id) {
                if (selectedTab == AppTab.LEARNING) vocabularyViewModel?.load(state.profile.id)
            }
            LaunchedEffect(selectedSearchCard, selectedLearningRecordCard, selectedArticleCard) {
                wordQaViewModel?.reset()
                selectedSearchCard?.let {
                    vocabularyViewModel?.loadMembership(state.profile.id, it)
                    wordQaViewModel?.loadNotes(state.profile.id, it.lemma, it.wordBookId, it.cardId)
                }
                selectedLearningRecordCard?.let {
                    vocabularyViewModel?.loadMembership(state.profile.id, it)
                    wordQaViewModel?.loadNotes(state.profile.id, it.lemma, it.wordBookId, it.cardId)
                }
                selectedArticleCard?.let {
                    wordQaViewModel?.loadNotes(state.profile.id, it.lemma, it.wordBookId, it.cardId)
                }
            }
            // 历史旧版叠在历史列表之上：返回先关文章回列表，再按一次才退历史。
            BackHandler(enabled = historyArticle != null && !showGlobalVocabularySearch && selectedSearchCard == null) { historyArticle = null }
            // 阅读来源的层级从浅到深：导入页 → 文章页 → 点词的词卡详情。
            // 声明顺序即优先级（后声明者先拿返回键），别随手重排。
            BackHandler(enabled = showArticleImport && !showGlobalVocabularySearch && selectedSearchCard == null) { showArticleImport = false }
            BackHandler(enabled = readingTarget != null && !showGlobalVocabularySearch && selectedSearchCard == null) { readingAccessViewModel?.closeArticle() }
            BackHandler(enabled = selectedArticleCard != null && !showGlobalVocabularySearch && selectedSearchCard == null) { selectedArticleCard = null }
            // 词 AI 问答是最内层覆盖层：最后声明，返回键先关它，再按才轮到详情页与文章页。
            BackHandler(enabled = showWordQaCard != null) { showWordQaCard = null }
            // 设置页先声明、预览页后声明：两者同时为真时（从设置页点进预览）返回键要先关预览。
            BackHandler(enabled = showWorksheetSettings) { showWorksheetSettings = false }
            BackHandler(enabled = showWorksheetPreview) {
                worksheetViewModel?.dismissPreview()
                showWorksheetPreview = false
            }
            // 这一层内部还有「列表 / 编辑」两态，编辑态会自己再声明一条更靠后的 BackHandler，
            // 因此从编辑页按返回键先关编辑页，而不是直接退掉整层。
            BackHandler(enabled = showSpeechSettings) { showSpeechSettings = false }
            BackHandler(enabled = showAiProfiles) {
                showAiProfiles = false
                speechSettingsViewModel?.load()
            }
            BackHandler(enabled = selectedFeature != null) { selectedFeatureKey = null }
            if (selectedSearchCard != null) {
                val card = selectedSearchCard
                if (card != null) {
                    LaunchedEffect(card.cardId) {
                        val cardSnapshot = card
                        val request = ++searchPronunciationRequest
                        searchPronunciationJob?.cancel()
                        searchPronunciationMessage = PronunciationStatus.Playing
                        searchPronunciationJob = launch {
                            val result = pronunciationStatus(pronunciationProvider?.speak(cardSnapshot.lemma))
                            if (request == searchPronunciationRequest && selectedSearchCard === cardSnapshot) {
                                searchPronunciationMessage = result
                            }
                        }
                    }
                    // 「下一个」只能沿**打开详情的那份列表**推进：用来源枚举选列表，
                    // 而不是让所有来源共用一份判断。不同来源的结果集不同，
                    // 复用同一段判断会让「下一个」悄悄跳到另一份列表里去。
                    val globalSearchResults = (globalSearchState as? GlobalVocabularySearchUiState.Ready)?.results.orEmpty()
                    val nextFromSource: (() -> Unit)? = when (detailSource) {
                        DetailSource.VOCABULARY ->
                            nextAfter(vocabularyCards) { it.cardId == card.cardId }
                                ?.let { next -> { selectedSearchCard = next } }
                        // 全量查词的下一张同样要走回源：索引里没有可渲染的完整卡片。
                        DetailSource.GLOBAL_SEARCH ->
                            nextAfter(globalSearchResults) { it.result.cardId == card.cardId }
                                ?.let { next -> { globalVocabularySearchViewModel?.open(next.result) } }
                        else -> null
                    }
                    CardDetailScreen(
                        card = card,
                        onBack = closeSearchDetail,
                        onRelearn = {
                            selectedRelearnCard = card
                            vocabularyRelearnViewModel?.loadCard(state.profile.id, card)
                        },
                        onNext = nextFromSource,
                        onSpeak = {
                            val cardSnapshot = card
                            val request = ++searchPronunciationRequest
                            searchPronunciationJob?.cancel()
                            searchPronunciationMessage = PronunciationStatus.Idle
                            searchPronunciationJob = pronunciationScope.launch {
                                val result = pronunciationStatus(pronunciationProvider?.speak(cardSnapshot.lemma))
                                if (request == searchPronunciationRequest && selectedSearchCard === cardSnapshot) {
                                    searchPronunciationMessage = result
                                }
                            }
                        },
                        pronunciationStatus = searchPronunciationMessage,
                        notes = wordQaViewModel?.savedNotes?.collectAsState()?.value.orEmpty(),
                        onSavePersonalNote = { wordQaViewModel?.savePersonalNote(card.lemma, it, card.wordBookId, card.cardId) },
                        personalNoteSaveStatus = personalNoteSaveStatus,
                        vocabularyPresent = vocabularyMembership?.takeIf { it.cardId == card.cardId }?.present,
                        onToggleVocabulary = { vocabularyViewModel?.toggle(state.profile.id, card, Instant.now()) },
                        onAskAi = null,
                    )
                }
            } else if (showGlobalVocabularySearch) {
                val globalSearch = globalVocabularySearchViewModel
                GlobalVocabularySearchScreen(
                    state = globalSearchState,
                    query = globalSearchQuery,
                    onQueryChange = { globalSearch?.updateQuery(it) },
                    onSubmit = { globalSearch?.search() },
                    onRetry = { globalSearch?.retry() },
                    onClearHistory = { globalSearch?.clearHistory() },
                    onOpenHistory = { query ->
                        globalSearch?.updateQuery(query)
                        globalSearch?.search()
                    },
                    // 索引只提供结果列表要显示的列，这里不做「把摘要当卡片」的事：
                    // 交给 ViewModel 按 cardId 回源取完整词卡，取到后再由下面的
                    // LaunchedEffect 打开详情。
                    onSelect = { result -> globalSearch?.open(result) },
                    onBack = { showGlobalVocabularySearch = false },
                )
            } else if (selectedRelearnCard != null) {
                val card = selectedRelearnCard!!
                VocabularyRelearnScreen(
                    state = vocabularyRelearnViewModel?.state?.collectAsState()?.value
                        ?: VocabularyRelearnUiState.Idle,
                    onSubmit = { vocabularyRelearnViewModel?.submit(it) },
                    onRetry = { vocabularyRelearnViewModel?.loadCard(state.profile.id, card) },
                    onBack = {
                        selectedRelearnCard = null
                        vocabularyRelearnViewModel?.load(state.profile.id, todayReady?.activeWordBookId)
                    },
                )
            } else if (selectedLearningRecordCard != null) {
                val card = selectedLearningRecordCard
                if (card != null) {
                    LaunchedEffect(card.cardId) {
                        val request = ++detailPronunciationRequest
                        detailPronunciationMessage = PronunciationStatus.Playing
                        val result = pronunciationStatus(pronunciationProvider?.speak(card.lemma))
                        if (request == detailPronunciationRequest && selectedLearningRecordCard === card) {
                            detailPronunciationMessage = result
                        }
                    }
                    CardDetailScreen(
                        card = card,
                        onRelearn = {
                            selectedRelearnCard = card
                            vocabularyRelearnViewModel?.loadCard(state.profile.id, card)
                        },
                        onNext = recordCards.indexOfFirst { it.cardId == card.cardId }
                            .takeIf { it >= 0 && it + 1 < recordCards.size }
                            ?.let { index -> {
                                selectedLearningRecordCard = recordCards[index + 1]
                                detailPronunciationRequest++
                                detailPronunciationMessage = PronunciationStatus.Idle
                            } },
                        onBack = {
                            selectedLearningRecordCard = null
                            detailPronunciationMessage = PronunciationStatus.Idle
                            detailPronunciationRequest++
                            learningRecordPronunciationRequest++
                            learningRecordsViewModel?.clearDetail()
                        },
                        onSpeak = {
                            val cardSnapshot = card
                            val request = ++learningRecordPronunciationRequest
                            pronunciationScope.launch {
                                val result = pronunciationStatus(pronunciationProvider?.speak(cardSnapshot.lemma))
                                if (request == learningRecordPronunciationRequest && selectedLearningRecordCard === cardSnapshot) {
                                    detailPronunciationMessage = result
                                }
                            }
                        },
                        pronunciationStatus = detailPronunciationMessage ?: PronunciationStatus.Idle,
                        notes = wordQaViewModel?.savedNotes?.collectAsState()?.value.orEmpty(),
                        onSavePersonalNote = { wordQaViewModel?.savePersonalNote(card.lemma, it, card.wordBookId, card.cardId) },
                        personalNoteSaveStatus = personalNoteSaveStatus,
                        vocabularyPresent = vocabularyMembership?.takeIf { it.cardId == card.cardId }?.present,
                        onToggleVocabulary = { vocabularyViewModel?.toggle(state.profile.id, card, Instant.now()) },
                    )
                }
            } else if (showVocabularyRelearn) {
                val relearnState by vocabularyRelearnViewModel?.state?.collectAsState() ?: remember { mutableStateOf<VocabularyRelearnUiState>(VocabularyRelearnUiState.Idle) }
                VocabularyRelearnScreen(
                    state = relearnState,
                    onSubmit = { vocabularyRelearnViewModel?.submit(it) },
                    onRetry = { vocabularyRelearnViewModel?.load(state.profile.id, todayReady?.activeWordBookId) },
                    onBack = {
                        showVocabularyRelearn = false
                        vocabularyViewModel?.load(state.profile.id)
                    },
                )
            } else if (showVocabulary) {
                VocabularyScreen(
                    state = vocabularyState,
                    onBack = { showVocabulary = false },
                    onStartRelearn = { showVocabularyRelearn = true },
                    onRetry = { vocabularyViewModel?.load(state.profile.id) },
                    onOpenCard = {
                        detailSource = DetailSource.VOCABULARY
                        selectedSearchCard = it
                    },
                    onRemove = { vocabularyViewModel?.markMastered(state.profile.id, it) },
                    masteryState = vocabularyMasteryState,
                    onDismissMasteryMessage = { vocabularyViewModel?.clearMasteryMessage() },
                )
            } else if (showLearningRecords) {
                LearningRecordsScreen(
                    state = learningRecordsState,
                    onBack = { showLearningRecords = false },
                    onSelectTab = { learningRecordsViewModel?.selectTab(it) },
                    onRetry = { learningRecordsViewModel?.retry() },
                    onFilter = { learningRecordsViewModel?.selectAllWordsFilter(it) },
                    onOpenRecord = {
                        learningRecordsViewModel?.openRow(it)
                        detailPronunciationMessage = PronunciationStatus.Idle
                        learningRecordPronunciationRequest++
                        detailSource = DetailSource.RECORDS
                        selectedLearningRecordCard = it.wordCard
                    },
                    onOpenWord = {
                        learningRecordsViewModel?.openRow(it)
                        detailPronunciationMessage = PronunciationStatus.Idle
                        learningRecordPronunciationRequest++
                        detailSource = DetailSource.RECORDS
                        selectedLearningRecordCard = it.card
                    },
                )
            } else if (setupRequired || showSetup) {
                LearningSetupScreen(
                    profileId = state.profile.id,
                    viewModel = learningSetupViewModel,
                    onSaved = {
                        showSetup = false
                        todayPlanViewModel.load(state.profile.id)
                    },
                    onCancel = cancelSetup,
                )
            } else if (showLearning) {
                val cardState by wordCardViewModel.uiState.collectAsState()
                val pronunciationScope = rememberCoroutineScope()
                val detailCard by wordCardViewModel.detailCard.collectAsState()
                LaunchedEffect(detailCard) {
                    detailCard?.let { vocabularyViewModel?.loadMembership(state.profile.id, it) }
                }
                // The detail page is an overlay inside the learning branch: it leaves the
                // showLearning state machine untouched, and returning from it (clearDetail) drops
                // the user back onto the same card / normal learning state.
                Box(modifier = Modifier.fillMaxSize()) {
                    LaunchedEffect((cardState as? WordCardUiState.Ready)?.card?.cardId) {
                        val ready = cardState as? WordCardUiState.Ready ?: return@LaunchedEffect
                        val request = ++wordCardPronunciationRequest
                        wordCardPronunciationMessage = PronunciationStatus.Playing.message()
                        val result = pronunciationStatus(pronunciationProvider?.speak(ready.card.lemma)).message()
                        if (request == wordCardPronunciationRequest) {
                            wordCardPronunciationMessage = result
                        }
                    }
                    WordCardScreen(
                        state = cardState,
                        onSubmit = wordCardViewModel::submit,
                        onSpeak = { lemma ->
                            val request = ++wordCardPronunciationRequest
                            wordCardPronunciationMessage = PronunciationStatus.Playing.message()
                            pronunciationScope.launch {
                                val result = pronunciationStatus(pronunciationProvider?.speak(lemma)).message()
                                if (request == wordCardPronunciationRequest) {
                                    wordCardPronunciationMessage = result
                                }
                            }
                        },
                        pronunciationMessage = wordCardPronunciationMessage,
                        onRetry = { wordCardViewModel.load(state.profile.id) },
                        onStartNewPhase = wordCardViewModel::startNewPhase,
                        onBackToPlan = exitLearning,
                    )
                    if (detailCard != null) {
                        val card = detailCard!!
                        LaunchedEffect(card.cardId) {
                            val request = ++detailPronunciationRequest
                            detailPronunciationMessage = PronunciationStatus.Playing
                            val result = pronunciationStatus(pronunciationProvider?.speak(card.lemma))
                            if (request == detailPronunciationRequest && detailCard === card) {
                                detailPronunciationMessage = result
                            }
                        }
                        CardDetailScreen(
                            card = card,
                            onRelearn = {
                                selectedRelearnCard = card
                                vocabularyRelearnViewModel?.loadCard(state.profile.id, card)
                            },
                            onBack = {
                                detailPronunciationRequest++
                                detailPronunciationMessage = PronunciationStatus.Idle
                                wordCardViewModel.clearDetail()
                            },
                            onNext = if (wordCardViewModel.hasNextDetail()) {
                                {
                                    detailPronunciationRequest++
                                    detailPronunciationMessage = PronunciationStatus.Idle
                                    wordCardViewModel.nextDetail()
                                }
                            } else {
                                null
                            },
                            onSpeak = {
                                val request = ++detailPronunciationRequest
                                detailPronunciationMessage = PronunciationStatus.Playing
                                pronunciationScope.launch {
                                    val result = pronunciationStatus(pronunciationProvider?.speak(card.lemma))
                                    if (request == detailPronunciationRequest && detailCard === card) {
                                        detailPronunciationMessage = result
                                    }
                                }
                            },
                            pronunciationStatus = detailPronunciationMessage ?: PronunciationStatus.Idle,
                            onAskAi = {
                                // 换词打开问答层前清掉上一词的状态，防止旧回答串屏。
                                showWordQaCard = card
                                wordQaViewModel?.reset()
                                wordQaViewModel?.loadNotes(state.profile.id, card.lemma, card.wordBookId, card.cardId)
                            },
                        )
                        showWordQaCard?.let { qaCard ->
                            WordAiQaScreen(
                                lemma = qaCard.lemma,
                                state = wordQaUiState,
                                onAsk = { kind -> wordQaViewModel?.ask(kind, qaCard.lemma, qaCard.example, state.profile.id) },
                                onConfirmOutbound = { wordQaViewModel?.confirmOutbound(it) },
                                onSaveNote = { wordQaViewModel?.saveNote() },
                                onSavePersonalNote = { wordQaViewModel?.savePersonalNote(qaCard.lemma, it, qaCard.wordBookId, qaCard.cardId) },
                                savedNotes = wordQaViewModel?.savedNotes?.collectAsState()?.value.orEmpty(),
                                onBack = { showWordQaCard = null },
                            )
                        }
                    }
                }
            } else if (showWorksheetPreview) {
                val worksheetState = worksheetViewModel?.uiState?.collectAsState()?.value
                val context = androidx.compose.ui.platform.LocalContext.current
                // 只有用户显式点「导出」才会拉起系统面板；重进预览页不会重复弹出。
                val shareFile = worksheetViewModel?.shareRequest?.collectAsState()?.value
                LaunchedEffect(shareFile) {
                    shareFile?.let { file ->
                        val launched = runCatching {
                            val intent = WorksheetShareLauncher(context).createChooser(file)
                            if (context !is android.app.Activity) {
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }.isSuccess
                        if (launched) {
                            worksheetViewModel?.consumeShareRequest()
                        } else {
                            worksheetViewModel?.reportShareUnavailable()
                            worksheetViewModel?.consumeShareRequest()
                        }
                    }
                }
                WorksheetPreviewScreen(
                    pages = worksheetState?.pages.orEmpty(),
                    renderedFile = worksheetState?.renderedFile,
                    rendering = worksheetState?.rendering ?: false,
                    message = worksheetState?.message,
                    onBack = {
                        worksheetViewModel?.dismissPreview()
                        showWorksheetPreview = false
                    },
                    onExport = { worksheetViewModel?.export() },
                )
            } else if (showWorksheetSettings) {
                val worksheetState = worksheetViewModel?.uiState?.collectAsState()?.value
                WorksheetSettingsScreen(
                    settings = worksheetState?.settings
                        ?: com.example.englishlearning.learning.worksheet.WorksheetSettings(),
                    selectedCount = worksheetState?.selectedCount ?: 0,
                    onToggleDirection = { worksheetViewModel?.toggleDirection(it) },
                    onSelectRange = { worksheetViewModel?.selectRange(it) },
                    onSelectTemplate = { worksheetViewModel?.selectTemplate(it) },
                    onToggleGrid = { worksheetViewModel?.toggleGrid(it) },
                    onToggleAnswers = { worksheetViewModel?.toggleAnswers(it) },
                    onPreview = {
                        worksheetViewModel?.preview()
                        if (worksheetViewModel?.uiState?.value?.phase == WorksheetPhase.PREVIEW) {
                            showWorksheetPreview = true
                        }
                    },
                    onBack = { showWorksheetSettings = false },
                )
            } else if (showCheckIn) {
                val checkInState by checkInViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf<CheckInUiState>(CheckInUiState.Loading) }
                CheckInScreen(
                    state = checkInState,
                    onBack = { showCheckIn = false },
                    onRetry = { checkInViewModel?.load(state.profile.id) },
                )
            } else if (showAiProfiles) {
                val editor by aiProfileViewModel?.editor?.collectAsState() ?: remember { mutableStateOf(null) }
                AiProfileSettingsScreen(
                    listState = aiProfiles ?: AiProfileListUiState.Loading,
                    editor = editor,
                    onAdd = { aiProfileViewModel?.startCreate() },
                    onEdit = { aiProfileViewModel?.startEdit(it) },
                    onDraftChange = { aiProfileViewModel?.updateDraft(it) },
                    onSave = { aiProfileViewModel?.save() },
                    onCloseEditor = { aiProfileViewModel?.closeEditor() },
                    onDeleteProfile = { aiProfileViewModel?.deleteProfile(it) },
                    onDeleteKey = { aiProfileViewModel?.deleteKey(it) },
                    onSetDefaultTextProfile = { aiProfileViewModel?.setDefaultTextProfile(it) },
                    onSetDefaultImageProfile = { aiProfileViewModel?.setDefaultImageProfile(it) },
                    onBack = {
                        showAiProfiles = false
                        speechSettingsViewModel?.load()
                    },
                )
            } else if (showSpeechSettings) {
                SpeechSettingsScreen(
                    state = speechState,
                    onSelect = { engine, profileId -> speechSettingsViewModel?.select(engine, profileId) },
                    onOpenAiProfiles = {
                        aiProfileViewModel?.load()
                        showAiProfiles = aiProfileViewModel != null
                    },
                    onAddMiMoPreset = { speechSettingsViewModel?.addMiMoPreset() },
                    onPreview = { text -> speechSettingsViewModel?.preview(text) },
                    onBack = { showSpeechSettings = false },
                )
            } else if (selectedFeature == AiFeature.SENTENCE_ANALYSIS) {
                // 已实现的功能走真实屏幕；骨架页只留给尚未接通的功能。
                val sentenceState = sentenceAnalysisViewModel?.uiState?.collectAsState()?.value
                    ?: SentenceAnalysisUiState.Idle
                SentenceAnalysisScreen(
                    state = sentenceState,
                    onAnalyze = { sentence -> sentenceAnalysisViewModel?.analyze(sentence, state.profile.id) },
                    onConfirmOutbound = { sentenceAnalysisViewModel?.confirmOutbound(it) },
                    onBack = { selectedFeatureKey = null },
                )
            } else if (selectedFeature == AiFeature.IMAGE_STUDIO) {
                val studioState = imageStudioViewModel?.uiState?.collectAsState()?.value
                    ?: ImageStudioUiState.Idle
                ImageStudioScreen(
                    state = studioState,
                    onGeneratePrompt = { subject -> imageStudioViewModel?.generatePrompt(subject) },
                    onGenerateImage = { imageStudioViewModel?.generateImage() },
                    onConfirm = { imageStudioViewModel?.confirm(it) },
                    onBack = { selectedFeatureKey = null },
                )
            } else if (selectedFeature != null) {
                AiFeatureScreen(
                    feature = selectedFeature,
                    todayWordCount = todayReady?.newTarget,
                    dueWordCount = todayReady?.dueTarget,
                    onBack = { selectedFeatureKey = null },
                    onOpenSettings = {
                        selectedFeatureKey = null
                        // 配置页真的存在了，就直接送过去；少了这个 ViewModel 时（预览/测试夹具）
                        // 退回「设置」栏，不假装能直达。
                        aiProfileViewModel?.load()
                        if (aiProfileViewModel != null) {
                            showAiProfiles = true
                        } else {
                            selectedTab = AppTab.SETTINGS
                        }
                    },
                    onOpenLearning = {
                        selectedFeatureKey = null
                        selectedTab = AppTab.LEARNING
                    },
                )
            } else if (showReadingHistory && historyArticle == null) {
                val history = (readingState as? ReadingAccessUiState.Ready)?.history.orEmpty()
                ReadingHistoryScreen(
                    history = history,
                    onBack = { showReadingHistory = false },
                    onOpenArticle = { historyArticle = it },
                )
            } else if (showArticleImport) {
                val importState = (readingState as? ReadingAccessUiState.Ready)?.import ?: ImportUiState()
                ArticleImportScreen(
                    state = importState,
                    onTitleChange = { readingAccessViewModel?.importTitleChange(it) },
                    onBodyChange = { readingAccessViewModel?.importBodyChange(it) },
                    onImport = { readingAccessViewModel?.submitImport() },
                    onBack = { showArticleImport = false },
                )
            } else if (readingTarget != null || historyArticle != null) {
                val articleState = articleReadingViewModel?.uiState?.collectAsState()?.value
                val pronunciationScope = rememberCoroutineScope()
                val fromHistory = readingTarget == null
                Box(Modifier.fillMaxSize()) {
                    ArticleReadingScreen(
                        state = articleState,
                        onBack = {
                            if (fromHistory) historyArticle = null else readingAccessViewModel?.closeArticle()
                        },
                        onOpenCard = {
                            detailSource = DetailSource.ARTICLE
                            selectedArticleCard = it
                        },
                        onModeChange = { articleReadingViewModel?.setMode(it) },
                        onToggleTranslation = { articleReadingViewModel?.toggleTranslation() },
                        onOpenDictionaryPlaceholder = {},
                        onOpenDictionary = { tapped ->
                            selectedSearchCard = null
                            searchPronunciationMessage = PronunciationStatus.Idle
                            searchPronunciationRequest++
                            searchPronunciationJob?.cancel()
                            // 阅读页点中的词进索引版全量搜索：旧实现每次都要逐册解析词书包，
                            // 正是用户反馈的「查词慢」。
                            globalVocabularySearchViewModel?.setProfile(state.profile.id)
                            globalVocabularySearchViewModel?.openSearch(tapped)
                            showGlobalVocabularySearch = globalVocabularySearchViewModel != null
                        },
                        onOpenPronunciationPlaceholder = {},
                        onSetLearnedMarks = { articleReadingViewModel?.setLearnedMarks(it) },
                        onCompleteReading = { articleReadingViewModel?.completeReading() },
                        articleScrollPosition = articleScrollPosition,
                        onArticleScrollPositionChange = { articleScrollPosition = it },
                    )
                    // 点词的详情是阅读页之上的覆盖层，不动 readingTarget 的状态机。
                        selectedArticleCard?.let { card ->
                        LaunchedEffect(card.cardId) {
                            val request = ++detailPronunciationRequest
                            detailPronunciationMessage = PronunciationStatus.Playing
                            val result = pronunciationStatus(pronunciationProvider?.speak(card.lemma))
                            if (request == detailPronunciationRequest && selectedArticleCard === card) {
                                detailPronunciationMessage = result
                            }
                        }
                        CardDetailScreen(
                            card = card,
                            onRelearn = {
                                selectedRelearnCard = card
                                vocabularyRelearnViewModel?.loadCard(state.profile.id, card)
                            },
                            onNext = articleState?.cards?.indexOfFirst { it.cardId == card.cardId }
                                ?.takeIf { it >= 0 && it + 1 < (articleState?.cards?.size ?: 0) }
                                ?.let { index -> {
                                    selectedArticleCard = articleState?.cards?.get(index + 1)
                                    detailPronunciationRequest++
                                    detailPronunciationMessage = PronunciationStatus.Idle
                                } },
                            onBack = {
                                selectedArticleCard = null
                                detailPronunciationRequest++
                                detailPronunciationMessage = PronunciationStatus.Idle
                            },
                            onSpeak = {
                                val request = ++detailPronunciationRequest
                                detailPronunciationMessage = PronunciationStatus.Playing
                                pronunciationScope.launch {
                                    val result = pronunciationStatus(pronunciationProvider?.speak(card.lemma))
                                    if (request == detailPronunciationRequest && selectedArticleCard === card) {
                                        detailPronunciationMessage = result
                                    }
                                }
                            },
                            pronunciationStatus = detailPronunciationMessage ?: PronunciationStatus.Idle,
                            onAskAi = {
                                // 换词打开问答层前清掉上一词的状态，防止旧回答串屏。
                                showWordQaCard = card
                                wordQaViewModel?.reset()
                                wordQaViewModel?.loadNotes(state.profile.id, card.lemma, card.wordBookId, card.cardId)
                            },
                        )
                        showWordQaCard?.let { qaCard ->
                            WordAiQaScreen(
                                lemma = qaCard.lemma,
                                state = wordQaUiState,
                                onAsk = { kind -> wordQaViewModel?.ask(kind, qaCard.lemma, qaCard.example, state.profile.id) },
                                onConfirmOutbound = { wordQaViewModel?.confirmOutbound(it) },
                                onSaveNote = { wordQaViewModel?.saveNote() },
                                onSavePersonalNote = { wordQaViewModel?.savePersonalNote(qaCard.lemma, it, qaCard.wordBookId, qaCard.cardId) },
                                savedNotes = wordQaViewModel?.savedNotes?.collectAsState()?.value.orEmpty(),
                                onBack = { showWordQaCard = null },
                            )
                        }
                    }
                }
            } else {
                // 四个一级 tab 的根页面装在这里，底部导航只在这一层出现；上面所有分支都是
                // 覆盖全屏的二级层，因此天然不会显示底导。
                Scaffold(
                    containerColor = MintBackground,
                    bottomBar = { AppBottomBar(selected = selectedTab, onSelect = { selectedTab = it }) },
                ) { contentPadding ->
                    Box(Modifier.fillMaxSize().padding(contentPadding)) {
                        when (selectedTab) {
                            AppTab.LEARNING -> TodayPlanScreen(
                                state = todayState,
                                onRetry = { todayPlanViewModel.load(state.profile.id) },
                                onOpenSetup = { showSetup = true },
                                onStartLearning = {
                                    wordCardViewModel.load(state.profile.id)
                                    showLearning = true
                                },
                                // 阅读与学习工具的完整页面已经各自成为一级 tab，这里不再叠一层
                                // 全屏页，避免出现「底导之上再压一层」的混乱层级。
                                onOpenReading = { selectedTab = AppTab.READING },
                                onOpenLearningTools = { selectedTab = AppTab.SETTINGS },
                                onOpenCheckIn = { showCheckIn = true },
                                onOpenLearningRecords = { showLearningRecords = true },
                                vocabularyCount = (vocabularyState as? VocabularyUiState.Ready)?.items?.size,
                                onOpenVocabulary = {
                                    vocabularyViewModel?.load(state.profile.id)
                                    showVocabulary = vocabularyViewModel != null
                                },
                                onOpenSearch = {
                                    // 主页查词走索引版全量搜索：这里没有旧词书搜索页的
                                    // 「浏览词书」——那属于词书管理语义，不是「查一个词」。
                                    // 传空串即落在「最近搜索」。
                                    globalVocabularySearchViewModel?.setProfile(state.profile.id)
                                    globalVocabularySearchViewModel?.openSearch("")
                                    showGlobalVocabularySearch = globalVocabularySearchViewModel != null
                                },
                            )
                            AppTab.READING -> ReadingAccessScreen(
                                state = readingState,
                                onSelectType = { readingAccessViewModel?.selectType(it) },
                                onOpenHistory = { showReadingHistory = true },
                                onGenerate = { readingAccessViewModel?.generate() },
                                onRegenerate = { readingAccessViewModel?.generate(regenerate = true) },
                                onOpenTodayArticle = { readingAccessViewModel?.openTodayArticle() },
                                onConfirmOutbound = { readingAccessViewModel?.confirmOutbound(it) },
                                onOpenAiSettings = {
                                    // 与 AI 学的跳转同一规则：配置页可用就直达，否则退回设置栏。
                                    aiProfileViewModel?.load()
                                    if (aiProfileViewModel != null) {
                                        showAiProfiles = true
                                    } else {
                                        selectedTab = AppTab.SETTINGS
                                    }
                                },
                                onOpenImport = { showArticleImport = true },
                                onOpenFeed = { readingAccessViewModel?.openFeed() },
                                onFetchFeedItem = { readingAccessViewModel?.fetchFeedItem(it) },
                            )
                            AppTab.AI -> AiLearningScreen(
                                todayWordCount = todayReady?.newTarget,
                                dueWordCount = todayReady?.dueTarget,
                                // 读的是本机真实配置：至少有一套配置设了密钥才算「已配置」。
                                aiConfigured = aiConfigured,
                                onOpenFeature = {
                                    selectedFeatureKey = it.key
                                    // 每次进入功能页都清掉上一句的分析结果与上一张的生图状态，防止旧结果串屏。
                                    if (it == AiFeature.SENTENCE_ANALYSIS) sentenceAnalysisViewModel?.reset()
                                    if (it == AiFeature.IMAGE_STUDIO) imageStudioViewModel?.reset()
                                },
                                onOpenWordList = { selectedTab = AppTab.LEARNING },
                            )
                            AppTab.SETTINGS -> {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val transferState = wordBookTransferViewModel?.uiState?.collectAsState()?.value
                                // SAF 的导入/导出：文件位置由用户选，应用不申请任何存储权限。
                                // `.wbpack` 是私有格式，MIME 用 */*（系统没有它的注册类型）。
                                val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                                    androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                                ) { uri -> uri?.let { wordBookTransferViewModel?.importFrom(it) } }
                                val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                                    androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
                                ) { uri ->
                                    val bookId = exportWordBookId
                                    exportWordBookId = null
                                    uri?.let { selectedUri ->
                                        bookId?.let { wordBookTransferViewModel?.exportTo(selectedUri, it) }
                                    }
                                }
                                LaunchedEffect(Unit) { wordBookTransferViewModel?.refresh() }
                                if (showWordBookExportPicker) {
                                    AlertDialog(
                                        onDismissRequest = { showWordBookExportPicker = false },
                                        title = { Text("选择要导出的词书") },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                transferState?.books.orEmpty().forEach { bookId ->
                                                    Text(
                                                        text = bookId,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                exportWordBookId = bookId
                                                                showWordBookExportPicker = false
                                                                exportLauncher.launch("$bookId.wbpack")
                                                            }
                                                            .padding(vertical = 14.dp)
                                                            .testTag("settings_export_book_$bookId"),
                                                    )
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(onClick = { showWordBookExportPicker = false }) { Text("取消") }
                                        },
                                    )
                                }
                                SettingsScreen(
                                    profileName = state.profile.displayName,
                                    wordBookName = todayReady?.wordBookName,
                                    todayNewTarget = todayReady?.newTarget,
                                    todayDueTarget = todayReady?.dueTarget,
                                    onOpenSetup = { showSetup = true },
                                    onOpenWorksheet = {
                                        worksheetViewModel?.load(state.profile.id)
                                        showWorksheetSettings = worksheetViewModel != null
                                    },
                                    onOpenAiProfiles = {
                                        aiProfileViewModel?.load()
                                        showAiProfiles = aiProfileViewModel != null
                                    },
                                    onOpenSpeechSettings = {
                                        speechSettingsViewModel?.load()
                                        showSpeechSettings = speechSettingsViewModel != null
                                    },
                                    speechEngineStatuses = speechState.engineStatuses(),
                                    aiProfileSubtitle = aiProfiles?.let { ready ->
                                        if (ready.items.isEmpty()) {
                                            "尚未添加，点这里添加第一套 OpenAI 兼容服务"
                                        } else {
                                            "已配置 ${ready.items.size} 套 · ${ready.items.count { it.hasKey }} 套已设置密钥"
                                        }
                                    },
                                    onImportWordBook = {
                                        wordBookTransferViewModel?.consumeMessage()
                                        importLauncher.launch(arrayOf("*/*"))
                                    },
                                    onExportWordBook = {
                                        val books = transferState?.books.orEmpty()
                                        if (transferState?.canExport == true) {
                                            wordBookTransferViewModel?.consumeMessage()
                                            if (books.size == 1) {
                                                exportWordBookId = books.single()
                                                exportLauncher.launch("${books.single()}.wbpack")
                                            } else {
                                                showWordBookExportPicker = true
                                            }
                                        }
                                    },
                                    importedBookCount = transferState?.books?.size ?: 0,
                                    transferBusy = transferState?.busy == true,
                                    transferMessage = transferState?.message,
                                )
                            }
                        }
                    }
                }
            }
        }
        is AppUiState.Error -> ErrorScreen(onRetry = { viewModel.reload() })
    }
}

@Composable
private fun BrandLaunchSurface() {
    Box(
        modifier = Modifier.fillMaxSize().background(AppleMintGradient),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_learning_mark),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun ProfileCreationScreen(name: String, onNameChange: (String) -> Unit, onCreate: () -> Unit) {
    val canCreate = name.isNotBlank()
    Column(
        modifier = Modifier.fillMaxSize().background(MintBackground).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "先认识一下你",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MintPrimaryDark,
        )
        Text(
            text = "填写一个称呼，学习记录只保存在这台设备上。",
            style = MaterialTheme.typography.bodyMedium,
            color = MintTextMuted,
        )
        TextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("姓名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "姓名输入" },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MintSurface,
                unfocusedContainerColor = MintSurface,
                focusedIndicatorColor = MintPrimary,
                unfocusedIndicatorColor = MintOutline,
                cursorColor = MintPrimary,
                focusedLabelColor = MintPrimaryDark,
                unfocusedLabelColor = MintTextMuted,
                focusedTextColor = MintPrimaryDark,
                unfocusedTextColor = MintPrimaryDark,
            ),
        )
        if (!canCreate) {
            Text(
                text = "请输入名字后再创建",
                style = MaterialTheme.typography.bodySmall,
                color = MintTextMuted,
                modifier = Modifier.semantics { contentDescription = "请输入名字提示" },
            )
        }
        Button(
            onClick = onCreate,
            enabled = canCreate,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(AppleMintGradient, RoundedCornerShape(18.dp))
                .semantics { contentDescription = "创建资料" },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = MintTint,
                disabledContentColor = MintTextMuted,
            ),
        ) {
            Text("创建", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun ErrorScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MintBackground).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("无法创建资料，请检查姓名", color = MintPrimaryDark, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.semantics { contentDescription = "返回重新填写" },
            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = Color.White),
        ) {
            Text("返回重新填写")
        }
    }
}

@Composable
private fun LearningSetupScreen(
    profileId: String,
    viewModel: LearningSetupViewModel,
    onSaved: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    LaunchedEffect(profileId) { viewModel.load(profileId) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect == LearningSetupEffect.Saved) onSaved()
        }
    }

    // 删除导入词书：删除前必须二次确认，内置册根本不会进入这里。
    state.pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("删除《${book.displayName}》？") },
            text = { Text("将删除这册导入词书的词卡文件，以及它在本机的学习记录、复习状态与生词记录。其他词书不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDelete(profileId) },
                    modifier = Modifier.testTag("wordbook_delete_confirm"),
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::cancelDelete,
                    modifier = Modifier.testTag("wordbook_delete_cancel"),
                ) { Text("取消") }
            },
        )
    }

    // 切书时发现已学过的同词：由用户决定是否标记为已学习，不静默迁移。
    state.pendingMigration?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.resolveMigration(profileId, apply = false) },
            title = { Text("发现已学过的单词") },
            text = {
                Text(
                    "目标词书里有 ${preview.candidates.size} 个单词你之前已经学过，要把它们标记为已学习吗？" +
                        "原词书的学习记录会原样保留。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.resolveMigration(profileId, apply = true) },
                    modifier = Modifier.testTag("wordbook_migration_confirm"),
                ) { Text("标记为已学习") }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.resolveMigration(profileId, apply = false) },
                    modifier = Modifier.testTag("wordbook_migration_skip"),
                ) { Text("暂不标记") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onCancel != null) {
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = MintPrimaryDark),
                // 这个设置页现在既能从「学习」栏也能从「设置」栏打开，所以文案不能再写死
                // 「返回今日计划」——从设置栏进来时会指错地方。
                modifier = Modifier.semantics { contentDescription = "返回上一层" },
            ) { Text("← 返回上一层", fontWeight = FontWeight.Bold) }
        }
        Text(
            text = "选好词书，开始今天的积累",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MintPrimaryDark,
        )
        Text(
            text = "每天一点点，学习会自然变成习惯。",
            style = MaterialTheme.typography.bodyMedium,
            color = MintTextMuted,
        )
        SetupIntroductionCard()
        Text(
            text = "选择词书",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MintPrimaryDark,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = "这是应用内学习分组，不是官方考试大纲。",
            style = MaterialTheme.typography.bodySmall,
            color = MintTextMuted,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        state.wordBooks.forEach { book ->
            key(book.id) {
                SwipableWordBookCard(
                    book = book,
                    selected = book.id == state.selectedWordBookId,
                    progress = state.progressByBook[book.id],
                    onSelect = { viewModel.selectWordBook(book.id) },
                    onDelete = if (state.canDelete(book.id)) {
                        { viewModel.requestDelete(book) }
                    } else {
                        null
                    },
                )
            }
        }
        DailyTargetCard(
            target = state.dailyNewTarget,
            onTargetChange = viewModel::updateDailyNewTarget,
        )
        DetailToggleRow(
            label = "提交「认识」后查看词义详情",
            description = "默认关闭",
            checked = state.openDetailOnKnown,
            onToggle = viewModel::setOpenDetailOnKnown,
        )
        DetailToggleRow(
            label = "提交「模糊」后查看词义详情",
            description = "默认开启",
            checked = state.openDetailOnFuzzy,
            onToggle = viewModel::setOpenDetailOnFuzzy,
        )
        DetailToggleRow(
            label = "提交「忘记了」后查看词义详情",
            description = "默认开启",
            checked = state.openDetailOnForgotten,
            onToggle = viewModel::setOpenDetailOnForgotten,
        )
        DetailToggleRow(
            label = "显示词汇搜索次数",
            description = "搜索历史中显示累计搜索次数",
            checked = state.showVocabularySearchCount,
            onToggle = viewModel::setShowVocabularySearchCount,
        )
        Button(
            onClick = { viewModel.save(profileId) },
            enabled = !state.saving && !state.deleting,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(AppleMintGradient, RoundedCornerShape(18.dp))
                .semantics { contentDescription = "保存学习设置" },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
            ),
        ) {
            Text(if (state.saving) "保存中…" else "保存并开始学习", fontWeight = FontWeight.Bold)
        }
        state.savedWordBookName?.let {
            Text(
                text = "已保存：$it · 每日新增 ${state.dailyNewTarget} 词",
                style = MaterialTheme.typography.bodySmall,
                color = MintPrimaryDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
        }
        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 10.dp))
        }
    }
}


@Composable
private fun SetupIntroductionCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintTint),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("从一个舒服的目标开始", color = MintPrimary, style = MaterialTheme.typography.labelLarge)
            Text(
                "先选一套适合你的词书，再确定每天愿意投入的量。",
                style = MaterialTheme.typography.bodyMedium,
                color = MintPrimaryDark,
            )
        }
    }
}

/**
 * 导入词书包装层：从左往右滑出删除操作。
 *
 * 内置册的 `onDelete` 为 null，此时**不加任何滑动容器**，从结构上保证内置册没有被删的可能，
 * 而不是靠运行时判断拦一下。
 */
@Composable
internal fun SwipableWordBookCard(
    book: WordBook,
    selected: Boolean,
    progress: com.example.englishlearning.learning.WordBookProgress?,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    if (onDelete == null) {
        WordBookCard(book, selected, progress, onSelect)
        return
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onDelete()
            // 永远不真正滑走：删除必须先经过确认弹窗。
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        // 用户要的是「往左滑」露出删除键：条目向左滑，操作区从右侧露出。
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        modifier = Modifier.testTag("wordbook_swipe_${book.id}"),
        backgroundContent = {
            // 这层红底一直在内容之下，靠上面那张卡**正好铺满整行**把它完全盖住；
            // WordBookCard 里因此刻意不缩放（见那边的注释）。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(18.dp))
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("删除", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
    ) {
        WordBookCard(book, selected, progress, onSelect)
    }
}

@Composable
private fun WordBookCard(
    book: WordBook,
    selected: Boolean,
    progress: com.example.englishlearning.learning.WordBookProgress?,
    onSelect: () -> Unit,
) {
    val cardColor by animateColorAsState(
        targetValue = if (selected) MintTint else MintSurface,
        animationSpec = tween(180),
        label = "wordBookColor",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MintPrimary else Color(0xFFDCEAE1),
        animationSpec = tween(180),
        label = "wordBookBorder",
    )
    Card(
        // 不要给这张卡加缩放动画。
        //
        // 它会被包进 SwipeToDismissBox，而滑动容器会**始终**在内容下方铺一层红色删除底：
        // 静止时本来被卡片完全盖住，可一旦卡片小于行宽，红色就会从四周露出一圈描边，
        // 看起来像报错态（真机像素取证：边缘为 #B3261E，即 M3 的 colorScheme.error）。
        // 想靠「是否正在拖动」来按需显示这层底也不可行——SwipeToDismissBoxState.progress
        // 在锚点相同的静止态返回非 0 值，判断不出拖动与否。
        // 选中态因此只用底色、描边、「已选」标签和单选钮表达，这些已经足够醒目。
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics { contentDescription = "选择词书 ${book.displayName}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = MintPrimary, unselectedColor = MintOutline),
            )
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(book.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                Spacer(Modifier.height(3.dp))
                Text(book.level, style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
                Spacer(Modifier.height(4.dp))
                // 「已学」在左、「总数」在右：一眼能看出这套词书还有多少没碰。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = progress?.let { "已学 ${it.learned}" } ?: "正在加载进度…",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MintPrimaryDark,
                        modifier = Modifier.testTag("wordbook_learned_${book.id}"),
                    )
                    progress?.let {
                        Text(
                            text = "共 ${it.total} 词",
                            style = MaterialTheme.typography.bodySmall,
                            color = MintTextMuted,
                            modifier = Modifier.testTag("wordbook_total_${book.id}"),
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { progress?.fraction ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp)
                        .testTag("wordbook_progress_${book.id}"),
                    color = MintPrimary,
                    trackColor = MintOutline.copy(alpha = 0.35f),
                )
                progress?.let {
                    Text(
                        text = "未学 ${it.unlearned} 词",
                        style = MaterialTheme.typography.bodySmall,
                        color = MintTextMuted,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
            if (selected) {
                Text(
                    text = "已选",
                    color = MintPrimaryDark,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(Color(0xFFCDEDD9), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MintOutline, RoundedCornerShape(24.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
            }
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = MintPrimary, uncheckedTrackColor = Color(0xFFCDEFE1)),
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun DailyTargetCard(target: Int, onTargetChange: (Int) -> Unit) {
    var dragValue by remember { mutableFloatStateOf(target.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(target, isDragging) {
        if (!isDragging) dragValue = target.toFloat()
    }
    val displayedTarget = dragValue.roundToInt()
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MintOutline, RoundedCornerShape(24.dp)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("每日新增", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                    Text("轻轻拖动，实时看到今天的学习量", style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
                }
                Text("$displayedTarget", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MintPrimary)
                Text(" 词", style = MaterialTheme.typography.bodyMedium, color = MintTextMuted, modifier = Modifier.padding(top = 12.dp))
            }
            Slider(
                value = dragValue,
                onValueChange = { value ->
                    isDragging = true
                    dragValue = value
                    onTargetChange(value.roundToInt())
                },
                onValueChangeFinished = {
                    isDragging = false
                    onTargetChange(dragValue.roundToInt())
                },
                valueRange = LearningSetupUiState.MIN_DAILY_TARGET.toFloat()..LearningSetupUiState.MAX_DAILY_TARGET.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = AppleMintLight,
                    activeTrackColor = MintPrimary,
                    inactiveTrackColor = Color(0xFFCDEFE1),
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).semantics { contentDescription = "每日新词目标" },
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1", style = MaterialTheme.typography.labelSmall, color = MintTextMuted)
                Text("25", style = MaterialTheme.typography.labelSmall, color = MintTextMuted)
                Text("50", style = MaterialTheme.typography.labelSmall, color = MintTextMuted)
            }
        }
    }
}
