package com.example.englishlearning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import kotlinx.coroutines.Job
import androidx.compose.runtime.rememberCoroutineScope
import com.example.englishlearning.language.domain.PronunciationAccent
import com.example.englishlearning.language.domain.PronunciationEngine
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.englishlearning.export.WorksheetShareLauncher
import androidx.compose.ui.unit.dp
import com.example.englishlearning.BuildConfig
import com.example.englishlearning.R
import com.example.englishlearning.learning.WordBook
import com.example.englishlearning.learning.WordBookRecordStatus
import com.example.englishlearning.ui.components.AppCard
import com.example.englishlearning.ui.components.AppChip
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.AppSwitch
import com.example.englishlearning.ui.components.ConfirmDestructiveDialog
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.PickRow
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SectionHeading
import com.example.englishlearning.ui.components.hairlineTop
import com.example.englishlearning.ui.components.glass.GlassDialog
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.AppleMintEnd
import com.example.englishlearning.ui.theme.AppleMintMiddle
import com.example.englishlearning.ui.theme.AppleMintStart
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
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
private enum class DetailSource { VOCABULARY, RECORDS, ARTICLE, GLOBAL_SEARCH, TODAY_QUEUE }

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
    reminderSettingsViewModel: ReminderSettingsViewModel? = null,
    checkInViewModel: CheckInViewModel? = null,
    learningRecordsViewModel: LearningRecordsViewModel? = null,
    wordBookWordsViewModel: WordBookWordsViewModel? = null,
    vocabularyViewModel: VocabularyViewModel? = null,
    vocabularyRelearnViewModel: VocabularyRelearnViewModel? = null,
    pronunciationProvider: PronunciationProvider? = null,
    wordQaViewModel: WordAiQaViewModel? = null,
    globalVocabularySearchViewModel: GlobalVocabularySearchViewModel? = null,
    refreshVocabularySearchIndex: com.example.englishlearning.learning.RefreshVocabularySearchIndexUseCase? = null,
    sentenceAnalysisViewModel: SentenceAnalysisViewModel? = null,
    photoTranslationViewModel: PhotoTranslationViewModel? = null,
    imageStudioViewModel: ImageStudioViewModel? = null,
    wordBookTransferViewModel: com.example.englishlearning.wordbook.WordBookTransferViewModel? = null,
    backupExportViewModel: BackupExportViewModel? = null,
    backupRestoreViewModel: BackupRestoreViewModel? = null,
    backupPairingExportViewModel: BackupProtectedExportViewModel? = null,
    favoritesViewModel: FavoritesViewModel? = null,
    wrongWordsViewModel: WrongWordsViewModel? = null,
    articleVersionViewModel: ArticleVersionViewModel? = null,
    learningDataDeletionViewModel: LearningDataDeletionViewModel? = null,
) {
    val pronunciationScope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    // CompositionLocal 只能在组合期读取，recreate 用 Activity 引用需在 when 之前取出。
    val activityForRecreate = LocalContext.current as? android.app.Activity
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
            // 裁决⑤ 第二件：外刊 Feed 从阅读主页的内联区改独立二级层，宿主路由照抄上面 showReadingHistory 那一套
            // （状态位 + overlayOpen 追加一项 + BackHandler + 挂载分支）；主页只留一枚通往它的入口。
            var showReadingFeed by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showWorksheetSettings by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showWorksheetPreview by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            // 选中的 AI 功能存 key 而不是枚举实例：String 进 Bundle 最省心，将来加功能也不用改存法。
            var selectedFeatureKey by rememberSaveable(state.profile.id) { mutableStateOf<String?>(null) }
            var showAiProfiles by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showSpeechSettings by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showReminderSettings by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showBackupExport by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showBackupRestore by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showBackupPairingExport by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            // 裁决⑥：设置页「备份与恢复」不再就地折叠，改成这枚真子页路由；
            // 打开子屏时它保持 true（`else if` 先真先赢，子屏压在上面），子屏返回自然回到本页。
            var showBackupHub by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showLicenses by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            // 批 2 Task 22-B：独立「关于」页（原型 `sMeAbout`）。
            var showAbout by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showCheckIn by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            // 批 1 Task 16：新建的「今日词单」二级层（原型 `learn-queue`）。路由照本文件的既有四件套——
            // 状态位 + overlayOpen 追加一项 + BackHandler + `else if` 挂载分支；主页只留那一条裸行入口。
            var showTodayWordList by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            // 批 1 Task 17：新建的「复习队列」二级层（原型 `learn-review`）。同一套四件套接线。
            var showReviewQueue by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            // Task 26 Step 3：四枚 AI 屏的「未配置卡」出口统一送到 AI 配置页（共享 `NotConfiguredCard` 的
            // 必填 `onConfigure`）。少了这个 ViewModel（预览/测试夹具）时退回「设置」栏，不假装能直达——
            // 与 AiFeatureScreen 的 `onOpenSettings` 同一口径。
            val openAiProfilesOrSettings: () -> Unit = {
                aiProfileViewModel?.load()
                if (aiProfileViewModel != null) {
                    showAiProfiles = true
                } else {
                    selectedTab = AppTab.SETTINGS
                }
            }
            // Task 22-B（R-22.1）：原 `showFavorites` 独立叠层路由已摘除，收藏改由合并页 MyWordsScreen 承载；
            // 下面三枚收藏详情宿主变量（selectedFavoriteCard / favoritesPronunciation*）保留——合并页的
            // 收藏段点开单词时复用的正是这套详情覆盖层。
            var selectedFavoriteCard by remember { mutableStateOf<com.example.englishlearning.learning.domain.WordCard?>(null) }
            var favoritesPronunciationMessage by remember { mutableStateOf(PronunciationStatus.Idle) }
            var favoritesPronunciationRequest by remember { mutableIntStateOf(0) }
            // 裁决①（Task 20-A）：「我的词」= 收藏 · 生词本 · 错词 一页三段。三条更老的路由按 R-22.1 拆成两席
            // 摘除（`showVocabulary` 归 **Task 22-A**；`showFavorites` / `showWrongWords` 归 **Task 22-B**）。
            // 批 1 Task 4 + Task 5 再往前并一层：学习页的「词库」行与「复习生词本」CTA 都进**词库页**
            // （四分段），合并页从此是词库页的一段 pane，不再自己持有全屏路由。
            // 批 1 Task 5（spec §3.3）：词库页是词汇域**唯一**的路由叠层。旧的两条独立路由
            // （`showWordBookWords` = 词书单词页、`showMyWords` = 我的词合并页）并进这一枚开关：
            // 四分段（词书 / 生词 / 错词 / 收藏）由 `wordBankSection` 持有，两条打开通路各自点名落哪一段。
            // 段位随开关一起 rememberSaveable：关掉再开回到「词书」段（默认落点也是唯一带筛选与字母栏的一段）。
            var showWordBank by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var wordBankSection by rememberSaveable(state.profile.id) { mutableStateOf(WordBankSection.Book) }
            // 批 1 Task 15：词库页「词书」段的两态（词书详情 ↔ 词单浏览）。开关**必须住在这一层**：
            // 词卡详情是 `else if (selectedLearningRecordCard != null)` 那一支，词库那一支在详情打开时
            // 会离开组合——开关若写在词库分支内部，从详情退回时会被忘掉、词单凭空跳回详情。
            // 键带 `showWordBank`：关掉词库再开，回到默认的详情面。
            var bookPaneShowsWordList by rememberSaveable(state.profile.id, showWordBank) { mutableStateOf(false) }
            var showLearningRecords by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            // 词书段的列表滚动位置：键带开关，关掉再开回到 A 组顶部（Task 21 的会话口径逐字保留）。
            val wordBookWordsListState = key(state.profile.id, showWordBank) {
                rememberLazyListState()
            }
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
            val wordBookWordsState by wordBookWordsViewModel?.state?.collectAsState() ?: remember { mutableStateOf(WordBookWordsUiState()) }
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
            // F4-01 决策⑦：收藏集合在档案就绪时加载一次，星标状态由它派生；
            // 收藏页点词 → ViewModel 回源 → 一次性事件交给详情（与全量查词同一模式）。
            val favoritesState = favoritesViewModel?.uiState?.collectAsState()?.value
            val favoriteIds = (favoritesState as? FavoritesUiState.Ready)?.words?.mapTo(HashSet()) { it.cardId }
                ?: hashSetOf<String>()
            LaunchedEffect(state.profile.id, favoritesViewModel) {
                favoritesViewModel?.load(state.profile.id)
            }
            // 错词状态在词库页的错词段与详情层两处是同一份，所以在这里收一次（原先只在错词页本地收）。
            val wrongWordsState = wrongWordsViewModel?.uiState?.collectAsState()?.value
                ?: WrongWordsUiState.Loading
            val openedFavoriteCard = favoritesViewModel?.openedCard?.collectAsState()?.value
            LaunchedEffect(openedFavoriteCard, showWordBank, wordBankSection) {
                val opened = openedFavoriteCard ?: return@LaunchedEffect
                favoritesViewModel?.consumeOpenedCard()
                // Task 22-B：收藏详情原有两个宿主页（收藏页与「我的词」的收藏段），收藏页下线后
                // 只剩词库页的收藏段这一宿主段；仍开着那一段时才把回源结果交给详情层
                // （批 1 Task 5：关掉整页或切到别段，迟到的回源结果一律丢弃）。
                if (!showWordBank || wordBankSection != WordBankSection.Favorite) return@LaunchedEffect
                favoritesPronunciationRequest++
                favoritesPronunciationMessage = PronunciationStatus.Idle
                selectedFavoriteCard = opened
            }
            val vocabularyCards = (vocabularyState as? VocabularyUiState.Ready)?.items?.mapNotNull { it.card }.orEmpty()
            val recordCards = when (learningRecordsState.selectedTab) {
                LearningRecordsTab.TODAY -> learningRecordsState.today.mapNotNull { it.wordCard }
                LearningRecordsTab.HISTORY -> learningRecordsState.history.flatMap { it.records }.mapNotNull { it.wordCard }
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
            // F3-02 四控：词卡/阅读页各持一份「状态 + 文案 + 代次」。状态与文案同源
            // （文案 = status.message()），播控组按状态呈现，代次用来把迟到的 Played 挡在 Idle 呈现之外（裁决 B3）。
            var wordCardPronunciationStatus by remember { mutableStateOf(PronunciationStatus.Idle) }
            var articlePronunciationMessage by remember { mutableStateOf<String?>(null) }
            var articlePronunciationStatus by remember { mutableStateOf(PronunciationStatus.Idle) }
            var articlePronunciationRequest by remember { mutableIntStateOf(0) }
            // Task 9 修复轮 I-2：阅读页三元组的唯一清场收口。三条退出路径（屏内 onBack、
            // BackHandler(readingTarget→closeArticle)、BackHandler(historyArticle)）都必须调用它；
            // 代次自增在清场时发生，挡住迟到的 Played 覆盖下一次进文的 Idle 呈现。
            val clearArticlePronunciation: () -> Unit = {
                articlePronunciationMessage = null
                articlePronunciationStatus = PronunciationStatus.Idle
                articlePronunciationRequest++
            }
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
            // AI 配置是设备级的（与学习者无关），启动时读一次，好让「设置」栏的摘要和「AI 助学」页
            // 的徽章说的是本机真实状态，而不是写死的假设。
            LaunchedEffect(state.profile.id) { aiProfileViewModel?.load() }
            LaunchedEffect(state.profile.id) { speechSettingsViewModel?.load() }
            val aiProfiles = aiProfileViewModel?.listState?.collectAsState()?.value as? AiProfileListUiState.Ready
            val speechState = speechSettingsViewModel?.state?.collectAsState()?.value ?: SpeechSettingsUiState()
            // 裁决 B2（决策 §2，Task 7 取证）：`android.speech.tts.TextToSpeech` 没有公开 pause()/resume()，
            // 引擎侧恒返回 false。因此「暂停/继续」按钮的呈现条件只能是**选中引擎 ≠ SystemTts**，
            // 在 UI 侧一次算好传给三处屏。
            // N-3（Task 10 携带的 T9 复评 Minor，纯注释订正）：本段旧措辞写「若改成看 pausePlayback()
            // 返回值，系统 TTS 下点暂停会落 `else -> Idle` 让整组消失」——`else -> Idle` 已被决策 §2
            // 「播控失败的状态机口径」废除，五处宿主（记录详情 / 词卡 / 收藏详情 / 阅读整篇 / 阅读点词详情）
            // 现均为 `else -> 原状态`，即失败时**保持原状态、整组与「停止」照常可达**。
            // 准确口径是：正因 `else` 保持原状态，「呈现条件」与「pausePlayback() 返回值」解耦才成立；
            // 若把呈现条件改按返回值，系统 TTS 下（恒 false）播控组会整组不呈现，连始终可用的「停止」也一起消失；
            // 若把失败分支写成落 Idle，则朗读仍在继续时「停止」同样会被收掉（N-1 的两处新例即钉此性质）。
            // 词路径的 activeProvider 恒为 Kokoro（PronunciationRouter.kt:54），故系统 Tts 下这里偏保守，
            // 属决策 §2 已登记的收窄口径，本任务不放宽。
            val pauseResumeSupported = speechState.selectedEngine != PronunciationEngine.SystemTts
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
            // 批 1 Task 5：词库页按**段**取数——只有当前那一段的数据源会被点名加载，切段才拉该段
            // （旧形状是两枚独立 effect 各盯一枚开关：`LaunchedEffect(showWordBookWords, …)` 与
            // 错词页那枚，现在并成这一条按段分派的通路）。位置在 `todayReady` 之后：词书段按活动词书取数。
            LaunchedEffect(showWordBank, wordBankSection, state.profile.id, todayReady?.activeWordBookId) {
                if (!showWordBank) return@LaunchedEffect
                when (wordBankSection) {
                    WordBankSection.Book -> wordBookWordsViewModel?.load(todayReady?.activeWordBookId)
                    WordBankSection.New -> vocabularyViewModel?.load(state.profile.id)
                    WordBankSection.Wrong -> wrongWordsViewModel?.load(state.profile.id)
                    WordBankSection.Favorite -> favoritesViewModel?.load(state.profile.id)
                }
            }
            // 进入阅读 tab 才算准入；今日计划未就绪时按「未解锁 + 说明原因」处理，不假装已解锁。
            LaunchedEffect(selectedTab, state.profile.id, todayReady?.isUnlocked) {
                // 批 1 Task 6：我的页第三格统计是「文章数」，它读的就是这份阅读历史；
                // 只在进阅读 tab 时取数会让那一格在没进过阅读 tab 前恒为 0（假数字）。
                if (selectedTab == AppTab.READING || selectedTab == AppTab.SETTINGS) {
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
            // 批 1 Task 6：打卡快照在宿主层**只收一次**（原先只在打卡页分支里收）——学习页的
            // 「连续天数 / 今日文章已读」与我的页三格统计都读它，两处各收一份就是两份真源。
            val checkInState by checkInViewModel?.uiState?.collectAsState()
                ?: remember { mutableStateOf<CheckInUiState>(CheckInUiState.Loading) }
            // 两个页面读的是同一份快照里的同一批数字，所以在宿主层派生一次、按名传两处：
            // 学习页（连续天数 + 今日文章已读）与我的页（连续天数 + 已学词）。派生只此一处，
            // 免得某一页自己再 `as? Ready` 一遍、兜底值各写各的。
            val checkInReady = checkInState as? CheckInUiState.Ready
            val streakDays = checkInReady?.streakDays ?: 0
            val learnedWordCount = checkInReady?.learnedWordCount ?: 0
            val todayArticleRead = (checkInReady?.today?.completedReadingCount ?: 0) > 0
            // 预加载：学习页与我的页一露面就要有这两个数字。keepReady 保证这次重载
            // 不把页面闪回 Loading、失败也保留上一份好快照（CheckInViewModelTest 钉着口径）。
            LaunchedEffect(selectedTab, state.profile.id) {
                if (selectedTab == AppTab.LEARNING || selectedTab == AppTab.SETTINGS) {
                    checkInViewModel?.load(state.profile.id, keepReady = true)
                }
            }
            // F4-01 决策 §8：删除某个文章版本成功后，按现参重放历史加载刷新列表；
            // 计数只在「真删成功」时递增，拒删与失败都不该触发无谓重读。
            val versionDeleteCount = articleVersionViewModel?.versionDeletions?.collectAsState()?.value ?: 0
            LaunchedEffect(versionDeleteCount) {
                if (versionDeleteCount > 0) {
                    readingAccessViewModel?.load(
                        profileId = state.profile.id,
                        isUnlocked = todayReady?.isUnlocked == true,
                        unlockReason = todayReady?.unlockReason ?: "今日计划尚未就绪",
                    )
                }
            }
            // F4-01 决策 §9：删除全部学习数据成功后重放各可见宿主的加载（今日页必刷，
            // 打卡/学习记录/错词只刷已打开的）。文章与收藏保留面不动，不重放。
            val learningDataDeletionUi = learningDataDeletionViewModel?.uiState?.collectAsState()?.value
                ?: LearningDataDeletionUiState()
            // F4-01 Spec 04 L18：删除全部学习数据先报级联条数、再二次确认；
            // 「删除全部学习数据」入口只做 request，仓储删除只从「确认删除」进入。
            // 裁决⑥：入口已搬进 BackupHubScreen 末节，本框必须住在 `Ready` 顶层——三级层是
            // if/else if 串，设置分支与 hub 分支互斥，留在设置分支里的对话框在子页打开时根本不会组合。
            val pending = learningDataDeletionUi.pending
            if (pending != null) {
                ConfirmDestructiveDialog(
                    state = true,
                    title = "删除全部学习数据？",
                    // 条数与保留面逐字沿用站点原文（F4-01 Spec 04 L18 的口径）；
                    // 自写的「删除不可撤销。」交组件固定句承载。
                    cascadeLines = listOf(
                        "将删除学习记录 ${pending.eventCount} 条、复习调度 " +
                            "${pending.reviewStateCount} 条、每日计划 ${pending.planCount} 天、" +
                            "阅读完成 ${pending.completionCount} 条、迁移审计 ${pending.migrationAuditCount} 条。",
                    ),
                    keepLines = listOf(
                        "文章 ${pending.keptArticleCount} 篇与收藏 ${pending.keptFavoriteCount} 条保留；" +
                            "词书、搜索历史与设置不受影响。",
                    ),
                    irreversible = true,
                    confirmLabel = "确认删除",
                    onConfirm = { learningDataDeletionViewModel?.confirmDelete(state.profile.id) },
                    onDismiss = { learningDataDeletionViewModel?.cancelDelete() },
                )
            }
            val learningDataDeleteCount = learningDataDeletionViewModel?.dataDeletions?.collectAsState()?.value ?: 0
            LaunchedEffect(learningDataDeleteCount) {
                if (learningDataDeleteCount > 0) {
                    todayPlanViewModel.load(state.profile.id)
                    if (showCheckIn) checkInViewModel?.load(state.profile.id)
                    if (showLearningRecords) {
                        learningRecordsViewModel?.load(state.profile.id, todayReady?.planId, todayReady?.activeWordBookId)
                    }
                    // Task 22-B：删除数据后回刷可见屏。批 1 Task 5：错词那一刷只在词库页正停在
                    // 「错词」段时执行（停在别段时那一段不可见，刷了也没人看）。
                    if (showWordBank && wordBankSection == WordBankSection.Wrong) {
                        wrongWordsViewModel?.load(state.profile.id)
                    }
                }
            }
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
                // I-1（Task 9 评审）：状态必须与文案成对清零——其余写入点全是 status+message 成对写。
                // 漏清会让重进学习时新词卡顶着上一张卡的 Playing 长出「暂停/停止」行而文案节点不出现，
                // 读屏用户听到的是别张卡的状态语义。
                wordCardPronunciationStatus = PronunciationStatus.Idle
                showLearning = false
                todayPlanViewModel.load(state.profile.id)
            }
            LaunchedEffect(showCheckIn, state.profile.id) {
                if (showCheckIn) checkInViewModel?.load(state.profile.id)
            }
            val overlayOpen = setupRequired || showSetup || showLearning || showReadingHistory || showCheckIn || showTodayWordList || showReviewQueue || selectedRelearnCard != null ||
                showWorksheetSettings || showWorksheetPreview || selectedFeature != null || showAiProfiles || showSpeechSettings || showReminderSettings || showBackupExport || showBackupRestore || showBackupPairingExport || showBackupHub || showLearningRecords || showAbout || selectedLearningRecordCard != null || showGlobalVocabularySearch || showVocabularyRelearn || selectedSearchCard != null ||
                showWordBank ||
                showArticleImport || readingTarget != null || historyArticle != null || selectedArticleCard != null || showReadingFeed ||
                showWordQaCard != null || selectedFavoriteCard != null
            // BackHandler 按「后声明者优先」分派，所以下面严格按优先级从低到高排列：层级越靠内
            // 越晚声明，越先拿到返回键。调整顺序会直接改变返回键行为，别随手重排。
            BackHandler(enabled = !overlayOpen && selectedTab != AppTab.LEARNING) {
                selectedTab = AppTab.LEARNING
            }
            BackHandler(enabled = cancelSetup != null) { cancelSetup?.invoke() }
            // Leaving the learning flow is always allowed; unsubmitted cards simply stay open.
            BackHandler(enabled = showLearning) { exitLearning() }
            BackHandler(enabled = showReadingHistory) { showReadingHistory = false }
            // Feed 二级层与阅读历史同优先级档：文章层（下方后声明）永远先拿走返回键。
            BackHandler(enabled = showReadingFeed) { showReadingFeed = false }
            BackHandler(enabled = showCheckIn) { showCheckIn = false }
            // 批 1 Task 16：「今日词单」二级层。挂载位置在打卡之前（于是它压得住打卡页），
            // 所以返回处理器也声明在打卡之后；但它压不住学习流程 / 设置 / 词义详情那三层（挂载更靠前、
            // z 序更高），而本文件的声明顺序学不到这一点——`showLearning` 的处理器声明在本条**之前**，
            // 「后声明者优先」会让词单抢走返回键。故按 `showArticleImport` / `historyArticle` 那两枚的同款
            // 写法（「本层 && !更高层 && 详情未开」）把让位条件写进 `enabled`，不靠声明顺序赌。
            BackHandler(
                enabled = showTodayWordList && !showLearning && !setupRequired && !showSetup &&
                    selectedSearchCard == null && selectedRelearnCard == null,
            ) { showTodayWordList = false }
            // 批 1 Task 17：「复习队列」二级层，与词单同一优先级档（两条路由互斥，不会同开）。
            // 让位条件照抄上面那枚，不靠声明顺序赌（同一条理由见 Task 16 的注释）。
            BackHandler(
                enabled = showReviewQueue && !showLearning && !setupRequired && !showSetup &&
                    selectedSearchCard == null && selectedRelearnCard == null,
            ) { showReviewQueue = false }
            BackHandler(enabled = selectedLearningRecordCard != null) {
                selectedLearningRecordCard = null
                detailPronunciationMessage = PronunciationStatus.Idle
                learningRecordPronunciationRequest++
                learningRecordsViewModel?.clearDetail()
            }
            BackHandler(enabled = selectedSearchCard != null) { closeSearchDetail() }
            // Task 22-B：原「收藏列表」的退页 BackHandler（`showFavorites && …`）随收藏页路由一并摘除；
            // 收藏详情覆盖层的关闭（下面这枚 `selectedFavoriteCard != null`）保留——合并页收藏段点开单词时
            // 用的就是这套详情层，返回键先关详情、再退合并页（合并页退页处理器在下方，后声明者优先）。
            BackHandler(enabled = selectedFavoriteCard != null) {
                selectedFavoriteCard = null
                favoritesPronunciationRequest++
                favoritesPronunciationMessage = PronunciationStatus.Idle
            }
            // 批 1 Task 5：词库页三段各自复用现成的详情宿主（收藏→selectedFavoriteCard、
            // 生词本→selectedSearchCard、错词 / 词书段→selectedLearningRecordCard），
            // 任一详情开着时本处理器让位，返回键先关详情再退整页。
            // 旧的两枚退页处理器（合并页 `showMyWords` / 词书单词页 `showWordBookWords`）在这里合成一枚：
            // 如今只剩这一条路由，两枚并存会让返回键按声明序落到其中一枚、行为随任务顺序漂。
            BackHandler(
                enabled = showWordBank && selectedFavoriteCard == null &&
                    selectedSearchCard == null && selectedLearningRecordCard == null,
            ) { showWordBank = false }
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
            LaunchedEffect(showVocabularyRelearn, state.profile.id) {
                if (showVocabularyRelearn) vocabularyRelearnViewModel?.load(state.profile.id, todayReady?.activeWordBookId)
            }
            LaunchedEffect(selectedTab, state.profile.id) {
                if (selectedTab == AppTab.LEARNING) vocabularyViewModel?.load(state.profile.id)
            }
            LaunchedEffect(selectedSearchCard, selectedLearningRecordCard, selectedArticleCard, selectedFavoriteCard) {
                wordQaViewModel?.reset()
                selectedSearchCard?.let {
                    vocabularyViewModel?.loadMembership(state.profile.id, it)
                    wordQaViewModel?.loadNotes(state.profile.id, it.lemma, it.wordBookId, it.cardId)
                }
                selectedLearningRecordCard?.let {
                    vocabularyViewModel?.loadMembership(state.profile.id, it)
                    wordQaViewModel?.loadNotes(state.profile.id, it.lemma, it.wordBookId, it.cardId)
                }
                selectedFavoriteCard?.let {
                    vocabularyViewModel?.loadMembership(state.profile.id, it)
                    wordQaViewModel?.loadNotes(state.profile.id, it.lemma, it.wordBookId, it.cardId)
                }
                selectedArticleCard?.let {
                    wordQaViewModel?.loadNotes(state.profile.id, it.lemma, it.wordBookId, it.cardId)
                }
            }
            // 历史旧版叠在历史列表之上：返回先关文章回列表，再按一次才退历史。
            // 退出必须清阅读发音三元组（I-2 收口③），否则重进任何文章顶着旧状态。
            BackHandler(enabled = historyArticle != null && !showGlobalVocabularySearch && selectedSearchCard == null) {
                clearArticlePronunciation()
                historyArticle = null
            }
            // 阅读来源的层级从浅到深：导入页 → 文章页 → 点词的词卡详情。
            // 声明顺序即优先级（后声明者先拿返回键），别随手重排。
            BackHandler(enabled = showArticleImport && !showGlobalVocabularySearch && selectedSearchCard == null) { showArticleImport = false }
            BackHandler(enabled = readingTarget != null && !showGlobalVocabularySearch && selectedSearchCard == null) {
                clearArticlePronunciation()
                readingAccessViewModel?.closeArticle()
            }
            // I-3（Task 9 评审）：系统返回键关阅读生词详情时必须与屏内 onBack（及 :429-434 记录宿主）
            // 同形地清共享详情状态——该详情宿主用的就是 detailPronunciation* 两个字段，
            // 漏清会让下一处「纯消费」宿主（学习流详情）顶着旧 Playing 长出假播控行。
            BackHandler(enabled = selectedArticleCard != null && !showGlobalVocabularySearch && selectedSearchCard == null) {
                selectedArticleCard = null
                detailPronunciationMessage = PronunciationStatus.Idle
                detailPronunciationRequest++
            }
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
            BackHandler(enabled = showReminderSettings) { showReminderSettings = false }
            // 裁决⑥：hub 声明在三枚子屏**之前**（后声明者优先）——子屏开着时返回键先关子屏，
            // 再从 hub 回到设置页。
            BackHandler(enabled = showBackupHub) { showBackupHub = false }
            BackHandler(enabled = showAbout) { showAbout = false }
            BackHandler(enabled = showBackupExport) { showBackupExport = false }
            BackHandler(enabled = showBackupRestore) { showBackupRestore = false }
            BackHandler(enabled = showBackupPairingExport) { showBackupPairingExport = false }
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
                            val result = pronunciationStatus(pronunciationProvider?.speakWord(cardSnapshot.lemma))
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
                        // 批 1 Task 16：「今日词单」点开的那一枚，「下一个」沿**该屏的呈现序**推进
                        // （新学段在前、复习段在后，与词单屏两节的上下次序逐字一致），
                        // 不借用生词本 / 词书那两份列表。
                        DetailSource.TODAY_QUEUE ->
                            nextAfter(todayReady?.newQueue.orEmpty() + todayReady?.dueQueue.orEmpty()) { it.cardId == card.cardId }
                                ?.let { next -> { selectedSearchCard = next } }
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
                            searchPronunciationMessage = PronunciationStatus.Playing
                            searchPronunciationJob = pronunciationScope.launch {
                                val result = pronunciationStatus(pronunciationProvider?.speakWord(cardSnapshot.lemma, PronunciationAccent.American))
                                if (request == searchPronunciationRequest && selectedSearchCard === cardSnapshot) {
                                    searchPronunciationMessage = result
                                }
                            }
                        },
                        onSpeakBritish = {
                            val cardSnapshot = card
                            val request = ++searchPronunciationRequest
                            searchPronunciationJob?.cancel()
                            searchPronunciationMessage = PronunciationStatus.Playing
                            searchPronunciationJob = pronunciationScope.launch {
                                val result = pronunciationStatus(pronunciationProvider?.speakWord(cardSnapshot.lemma, PronunciationAccent.British))
                                if (request == searchPronunciationRequest && selectedSearchCard === cardSnapshot) {
                                    searchPronunciationMessage = result
                                }
                            }
                        },
                        pronunciationStatus = searchPronunciationMessage,
                        notes = wordQaViewModel?.savedNotes?.collectAsState()?.value.orEmpty(),
                        onSavePersonalNote = { wordQaViewModel?.savePersonalNote(card.lemma, it, card.wordBookId, card.cardId) },
                        onDeleteNote = { wordQaViewModel?.deleteNote(it) },
                        personalNoteSaveStatus = personalNoteSaveStatus,
                        vocabularyPresent = vocabularyMembership?.takeIf { it.cardId == card.cardId }?.present,
                        onToggleVocabulary = { vocabularyViewModel?.toggle(state.profile.id, card, Instant.now()) },
                        favoritePresent = if (favoritesViewModel != null) card.cardId in favoriteIds else null,
                        onToggleFavorite = favoritesViewModel?.let { vm -> { vm.toggle(card.cardId, card.wordBookId, card.lemma) } },
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
                    onClearHistory = { globalSearch?.requestClearHistory() },
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
                // F4-01 Spec L18：清空搜索历史=不可逆操作，先报级联条数再二次确认；
                // 「清空搜索历史」按钮只做 request，仓储 clear 只从「确认清空」进入。
                val pendingClear = globalSearch?.pendingClear?.collectAsState()?.value
                pendingClear?.let { pending ->
                    ConfirmDestructiveDialog(
                        state = true,
                        title = "清空搜索历史？",
                        cascadeLines = listOf(
                            if (pending.recordCount != null) {
                                "将删除本机全部 ${pending.recordCount} 条搜索记录。"
                            } else {
                                "将删除本机全部搜索记录（暂时读不到具体条数）。"
                            },
                        ),
                        // 保留面逐字沿用站点原文；「不可撤销」声明交组件固定句承载（AGENTS.md 业务规则）。
                        keepLines = listOf("收藏、文章与学习记录不受影响。"),
                        irreversible = true,
                        confirmLabel = "确认清空",
                        onConfirm = { globalSearch?.clearHistory() },
                        onDismiss = { globalSearch?.cancelClearHistory() },
                    )
                }
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
                    // 发音只由用户点击触发：打开学习记录详情不自动发音。
                    // （2bff744 曾在此自动 speak(lemma)，与 onSpeak 叠加成两次调用，
                    //   learningRecordDetailPronunciationRoutesTheHeldLemmaToTheProvider 断言
                    //   spokenTexts 恰为一次点击的结果，据此还原为用户触发的唯一路径。）
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
                            detailPronunciationMessage = PronunciationStatus.Playing
                            pronunciationScope.launch {
                                val result = pronunciationStatus(pronunciationProvider?.speakWord(cardSnapshot.lemma, PronunciationAccent.American))
                                if (request == learningRecordPronunciationRequest && selectedLearningRecordCard === cardSnapshot) {
                                    detailPronunciationMessage = result
                                }
                            }
                        },
                        onSpeakBritish = {
                            val cardSnapshot = card
                            val request = ++learningRecordPronunciationRequest
                            detailPronunciationMessage = PronunciationStatus.Playing
                            pronunciationScope.launch {
                                val result = pronunciationStatus(pronunciationProvider?.speakWord(cardSnapshot.lemma, PronunciationAccent.British))
                                if (request == learningRecordPronunciationRequest && selectedLearningRecordCard === cardSnapshot) {
                                    detailPronunciationMessage = result
                                }
                            }
                        },
                        pronunciationStatus = detailPronunciationMessage ?: PronunciationStatus.Idle,
                        pronunciationControlsEnabled = true,
                        pauseResumeSupported = pauseResumeSupported,
                        // 暂停/继续**不**自增代次：暂停不改变这一次发声的归属（裁决 B3）。
                        onPauseResume = {
                            val held = detailPronunciationMessage
                            val next = when {
                                held == PronunciationStatus.Playing && pronunciationProvider?.pausePlayback() == true -> PronunciationStatus.Paused
                                held == PronunciationStatus.Paused && pronunciationProvider?.resumePlayback() == true -> PronunciationStatus.Playing
                                // 决策 §2（Task 9 评审后裁决）：未被引擎接受时**保持原状态**——
                                // 落 Idle 会让整组控件（含唯一可用的「停止」）在朗读仍在继续时消失。
                                else -> held
                            }
                            detailPronunciationMessage = next
                        },
                        // 停止：先自增**本宿主发声结果所用的**代次（这里是 learningRecordPronunciationRequest，
                        // 详情三宿主各用自己的守卫计数器），再 stopPlayback()，然后直接置空闲（裁决 B3）。
                        onStopPronunciation = {
                            learningRecordPronunciationRequest++
                            pronunciationProvider?.stopPlayback()
                            detailPronunciationMessage = PronunciationStatus.Idle
                        },
                        notes = wordQaViewModel?.savedNotes?.collectAsState()?.value.orEmpty(),
                        onSavePersonalNote = { wordQaViewModel?.savePersonalNote(card.lemma, it, card.wordBookId, card.cardId) },
                        onDeleteNote = { wordQaViewModel?.deleteNote(it) },
                        personalNoteSaveStatus = personalNoteSaveStatus,
                        vocabularyPresent = vocabularyMembership?.takeIf { it.cardId == card.cardId }?.present,
                        onToggleVocabulary = { vocabularyViewModel?.toggle(state.profile.id, card, Instant.now()) },
                        favoritePresent = if (favoritesViewModel != null) card.cardId in favoriteIds else null,
                        onToggleFavorite = favoritesViewModel?.let { vm -> { vm.toggle(card.cardId, card.wordBookId, card.lemma) } },
                    )
                }
            } else if (selectedFavoriteCard != null) {
                val card = selectedFavoriteCard
                if (card != null) {
                    // 收藏详情与学习记录详情同一 UI-only 模式：发音只由用户点击触发；
                    // 请求序号 + 卡引用双重守卫，防止跨页竞态把旧结果写进当前状态。
                    CardDetailScreen(
                        card = card,
                        onRelearn = {
                            selectedRelearnCard = card
                            vocabularyRelearnViewModel?.loadCard(state.profile.id, card)
                        },
                        onBack = {
                            selectedFavoriteCard = null
                            favoritesPronunciationMessage = PronunciationStatus.Idle
                            favoritesPronunciationRequest++
                        },
                        onSpeak = {
                            val cardSnapshot = card
                            val request = ++favoritesPronunciationRequest
                            favoritesPronunciationMessage = PronunciationStatus.Playing
                            pronunciationScope.launch {
                                val result = pronunciationStatus(pronunciationProvider?.speakWord(cardSnapshot.lemma, PronunciationAccent.American))
                                if (request == favoritesPronunciationRequest && selectedFavoriteCard === cardSnapshot) {
                                    favoritesPronunciationMessage = result
                                }
                            }
                        },
                        onSpeakBritish = {
                            val cardSnapshot = card
                            val request = ++favoritesPronunciationRequest
                            favoritesPronunciationMessage = PronunciationStatus.Playing
                            pronunciationScope.launch {
                                val result = pronunciationStatus(pronunciationProvider?.speakWord(cardSnapshot.lemma, PronunciationAccent.British))
                                if (request == favoritesPronunciationRequest && selectedFavoriteCard === cardSnapshot) {
                                    favoritesPronunciationMessage = result
                                }
                            }
                        },
                        pronunciationStatus = favoritesPronunciationMessage,
                        notes = wordQaViewModel?.savedNotes?.collectAsState()?.value.orEmpty(),
                        onSavePersonalNote = { wordQaViewModel?.savePersonalNote(card.lemma, it, card.wordBookId, card.cardId) },
                        onDeleteNote = { wordQaViewModel?.deleteNote(it) },
                        personalNoteSaveStatus = personalNoteSaveStatus,
                        vocabularyPresent = vocabularyMembership?.takeIf { it.cardId == card.cardId }?.present,
                        onToggleVocabulary = { vocabularyViewModel?.toggle(state.profile.id, card, Instant.now()) },
                        favoritePresent = true,
                        onToggleFavorite = { favoritesViewModel?.toggle(card.cardId, card.wordBookId, card.lemma) },
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
            } else if (showLearningRecords) {
                LearningRecordsScreen(
                    state = learningRecordsState,
                    onBack = { showLearningRecords = false },
                    onSelectTab = { learningRecordsViewModel?.selectTab(it) },
                    onRetry = { learningRecordsViewModel?.retry() },
                    onOpenRecord = {
                        learningRecordsViewModel?.openRow(it)
                        detailPronunciationMessage = PronunciationStatus.Idle
                        learningRecordPronunciationRequest++
                        detailSource = DetailSource.RECORDS
                        selectedLearningRecordCard = it.wordCard
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
                    // 学习时自动发音（D1，docs/decisions/2026-10-06-auto-pronunciation-and-wordbook-words.md）：
                    // 词卡就绪自动朗读一次，受语音设置开关控制（默认开）；与用户点击 onSpeak
                    // 共存互不取消——就绪自动 1 次 + 点击 = 2 次是合法序列。浏览场景（详情/词书/
                    // 记录/阅读）一律不自动发音，维持批次 5/6 的用户触发唯一契约。
                    val autoSpeakOnCardReady = speechSettingsViewModel?.state?.collectAsState()?.value?.autoSpeakOnCardReady ?: true
                    LaunchedEffect((cardState as? WordCardUiState.Ready)?.card?.cardId) {
                        val card = (cardState as? WordCardUiState.Ready)?.card ?: return@LaunchedEffect
                        if (!autoSpeakOnCardReady) return@LaunchedEffect
                        val request = ++wordCardPronunciationRequest
                        wordCardPronunciationStatus = PronunciationStatus.Playing
                        wordCardPronunciationMessage = PronunciationStatus.Playing.message()
                        // 状态与文案同源于一个局部 val：链式 .message() 会让「呈现的文案」与
                        // 「播控组的呈现状态」两次求值，两者一旦分叉按钮组就和文字说不上话。
                        val resultStatus = pronunciationStatus(pronunciationProvider?.speakWord(card.lemma))
                        if (request == wordCardPronunciationRequest) {
                            wordCardPronunciationStatus = resultStatus
                            wordCardPronunciationMessage = resultStatus.message()
                        }
                    }
                    if (cardState is WordCardUiState.AllDone) {
                        // 批 1 Task 18（新建屏 sSummary；原型 `app.js:1688` / `:1701` 末卡推进 → `go('learn-summary')`）：
                        // 本轮最后一张卡提交完、按「完成本轮」之后，画面交给「今日完成」而不是词卡屏——
                        // 原型在这条路径上画的是收尾屏，不是「词卡都提交完了」那一行字。
                        // 三个要件：
                        //  - **不新增导航状态位**：收尾屏挂在既有的 `showLearning` 分支里、按 `AllDone` 分派，
                        //    所以系统返回键仍由 `BackHandler(enabled = showLearning)` 那一条接管（原型三个出口
                        //    全指 learn-home，与之一致），`overlayOpen` 也不必再改；
                        //  - **三个出口同一条 `exitLearning`**：页头 ← / 页头「完成」/ 吸底「回到今日」在原型里
                        //    都是 `data-to="learn-home"`，这里复用同一枚退出通路（回今日学习并重算计划），
                        //    不另写一份「只关浮层不重算」的半边退出；
                        //  - **收尾屏不碰播放器**：换成这一屏时 `WordCardScreen` 自己离开组合，F5-03 的退出停播
                        //    守卫照常把停播恰好委托给宿主一次（`AppScreenTest` / `WordCardScreenTest` 钉着计数）。
                        // `dateLabel` 只在计划快照就绪时给；没就绪就传 null，屏侧 `.eyebrow` 整行不进组合。
                        val done = cardState as WordCardUiState.AllDone
                        TodaySummaryScreen(
                            state = done,
                            dateLabel = todayReady?.localDateLabel,
                            articleRead = todayArticleRead,
                            onBack = exitLearning,
                            onDone = exitLearning,
                            onOpenReading = {
                                exitLearning()
                                selectedTab = AppTab.READING
                            },
                        )
                    } else {
                        WordCardScreen(
                            state = cardState,
                            onSubmit = wordCardViewModel::submit,
                            onReveal = wordCardViewModel::revealMeaning,
                            onSpeak = { lemma ->
                                val request = ++wordCardPronunciationRequest
                                wordCardPronunciationStatus = PronunciationStatus.Playing
                                wordCardPronunciationMessage = PronunciationStatus.Playing.message()
                                pronunciationScope.launch {
                                    val resultStatus = pronunciationStatus(pronunciationProvider?.speakWord(lemma))
                                    if (request == wordCardPronunciationRequest) {
                                        wordCardPronunciationStatus = resultStatus
                                        wordCardPronunciationMessage = resultStatus.message()
                                    }
                                }
                            },
                            pronunciationMessage = wordCardPronunciationMessage,
                            pronunciationStatus = wordCardPronunciationStatus,
                            pauseResumeSupported = pauseResumeSupported,
                            onPauseResume = {
                                val next = when {
                                    wordCardPronunciationStatus == PronunciationStatus.Playing && pronunciationProvider?.pausePlayback() == true -> PronunciationStatus.Paused
                                    wordCardPronunciationStatus == PronunciationStatus.Paused && pronunciationProvider?.resumePlayback() == true -> PronunciationStatus.Playing
                                    // 决策 §2（Task 9 评审后裁决）：未被引擎接受时**保持原状态**——
                                    // 落 Idle 会让整组控件（含唯一可用的「停止」）在朗读仍在继续时消失。
                                    else -> wordCardPronunciationStatus
                                }
                                wordCardPronunciationStatus = next
                                wordCardPronunciationMessage = next.message()
                            },
                            // 裁决 B3：先自增代次再 stopPlayback()——播放器层把用户主动停止以 Played 收口，
                            // 迟到的 Played 会被代次挡掉，不会覆盖这里的 Idle 呈现；也禁止另立「已停止」文案。
                            onStopPronunciation = {
                                wordCardPronunciationRequest++
                                pronunciationProvider?.stopPlayback()
                                wordCardPronunciationStatus = PronunciationStatus.Idle
                                wordCardPronunciationMessage = null
                            },
                            onRetry = { wordCardViewModel.load(state.profile.id) },
                            onStartNewPhase = wordCardViewModel::startNewPhase,
                            // 回执态的唯一推进口（原型「下一个词 / 完成本轮」）：只有它换卡，
                            // 评分成功后的三档按钮已经不在组合里。
                            onNext = wordCardViewModel::advance,
                            onBackToPlan = exitLearning,
                        )
                    }
                    if (detailCard != null) {
                        val card = detailCard!!
                        // 打开详情不自动发音：只有 onSpeak（用户点击「播放 X 发音」）才调用 provider，
                        // 否则点击一次会累计两次调用（见 2bff744 回归）。
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
                                    val result = pronunciationStatus(pronunciationProvider?.speakWord(card.lemma, PronunciationAccent.American))
                                    if (request == detailPronunciationRequest && detailCard === card) {
                                        detailPronunciationMessage = result
                                    }
                                }
                            },
                            onSpeakBritish = {
                                val request = ++detailPronunciationRequest
                                detailPronunciationMessage = PronunciationStatus.Playing
                                pronunciationScope.launch {
                                    val result = pronunciationStatus(pronunciationProvider?.speakWord(card.lemma, PronunciationAccent.British))
                                    if (request == detailPronunciationRequest && detailCard === card) {
                                        detailPronunciationMessage = result
                                    }
                                }
                            },
                            pronunciationStatus = detailPronunciationMessage ?: PronunciationStatus.Idle,
                            pronunciationControlsEnabled = true,
                            pauseResumeSupported = pauseResumeSupported,
                            // 暂停/继续不自增代次（裁决 B3）：暂停不改变这一次发声的归属。
                            onPauseResume = {
                                val held = detailPronunciationMessage
                                val next = when {
                                    held == PronunciationStatus.Playing && pronunciationProvider?.pausePlayback() == true -> PronunciationStatus.Paused
                                    held == PronunciationStatus.Paused && pronunciationProvider?.resumePlayback() == true -> PronunciationStatus.Playing
                                    // 决策 §2（Task 9 评审后裁决）：未被引擎接受时**保持原状态**——
                                    // 落 Idle 会让整组控件（含唯一可用的「停止」）在朗读仍在继续时消失。
                                    else -> held
                                }
                                detailPronunciationMessage = next
                            },
                            // 停止：先自增本宿主的发声代次再 stopPlayback()，最后直接置空闲——
                            // 播放器层把用户主动停止以 Played 收口（决策 §2），代次在前才挡得住迟到的 Played。
                            onStopPronunciation = {
                                detailPronunciationRequest++
                                pronunciationProvider?.stopPlayback()
                                detailPronunciationMessage = PronunciationStatus.Idle
                            },
                            favoritePresent = if (favoritesViewModel != null) card.cardId in favoriteIds else null,
                            onToggleFavorite = favoritesViewModel?.let { vm -> { vm.toggle(card.cardId, card.wordBookId, card.lemma) } },
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
                                onOpenAiSettings = {
                                    showWordQaCard = null
                                    openAiProfilesOrSettings()
                                },
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
            } else if (showReviewQueue && todayReady != null) {
                // 批 1 Task 17：复习队列（原型 `learn-review`）。数据不另开通路——到期队列就是今日计划快照的
                // `dueCardIds` 取到内容后的那份列表，与「今日词单」的复习节同一份快照、同一个来源。
                // 挂载位置排在词单分支之前：两条路由互斥（没有一处界面能同时打开它们），但「挂载更早 = z 序更高」
                // 与本文件「后声明的 BackHandler 优先」必须配成一对，否则以后有人照着返回键去猜层级。
                ReviewQueueScreen(
                    state = todayReady,
                    onBack = { showReviewQueue = false },
                    onStartReview = {
                        showReviewQueue = false
                        wordCardViewModel.load(state.profile.id)
                        showLearning = true
                    },
                    onOpenWord = { card ->
                        selectedSearchCard = card
                        detailSource = DetailSource.TODAY_QUEUE
                    },
                )
            } else if (showTodayWordList && todayReady != null) {
                // 批 1 Task 16：今日词单（原型 `learn-queue`）。数据不另开一条通路——词单就是今日计划快照
                // 的 `newCardIds` / `dueCardIds` 取到内容后的那份列表，与同屏强卡的计数同一份快照。
                TodayWordListScreen(
                    state = todayReady,
                    onBack = { showTodayWordList = false },
                    onOpenSetup = {
                        // 原型 `.bar-action「调整」→ plan-edit`：设置层压在本层之上（挂载更早 = z 序更高），
                        // 这里把本层关掉，返回时回到学习主页，而不是留下两层叠着等返回键逐层拆。
                        showTodayWordList = false
                        showSetup = true
                    },
                    onStartLearning = {
                        showTodayWordList = false
                        wordCardViewModel.load(state.profile.id)
                        showLearning = true
                    },
                    onOpenWord = { card ->
                        selectedSearchCard = card
                        detailSource = DetailSource.TODAY_QUEUE
                    },
                )
            } else if (showCheckIn) {
                // 批 1 Task 6：快照在宿主层统一收（见上方 checkInState），这里不再收第二份。
                CheckInScreen(
                    state = checkInState,
                    onBack = { showCheckIn = false },
                    onRetry = { checkInViewModel?.load(state.profile.id) },
                )
            } else if (showWordBank) {
                // 词库页（spec §3.3）：一枚壳 + 四分段，一段一面。两枚旧页降级为 pane——
                // 词书段复用 WordBookWordsScreen（自带筛选与字母栏），其余三段复用合并页 MyWordsScreen
                // 并靠 visibleSections 收窄到点名的那一段（三段共用同一处形参表，见 myWordsPane）。
                // 页头只留壳上那一枚：两枚 pane 都以 showHeader = false 挂进来。
                val myWordsPane: @Composable (MyWordsSection) -> Unit = { paneSection ->
                    MyWordsScreen(
                        favoritesState = favoritesState ?: FavoritesUiState.Loading,
                        vocabularyState = vocabularyState,
                        wrongWordsState = wrongWordsState,
                        section = paneSection,
                        // 收窄到点名段：合并页默认三段同屏（老行为与 connected 夹具不破），
                        // 词库页里每格只该看见自己那一段。
                        visibleSections = listOf(paneSection),
                        showHeader = false,
                        onBack = { showWordBank = false },
                        onOpenFavoriteWord = { favoritesViewModel?.open(it) },
                        onUnfavorite = { favoritesViewModel?.toggle(it.cardId, it.wordBookId, it.lemma) },
                        onOpenVocabularyCard = {
                            detailSource = DetailSource.VOCABULARY
                            selectedSearchCard = it
                        },
                        onOpenWrongWord = {
                            detailPronunciationMessage = PronunciationStatus.Idle
                            learningRecordPronunciationRequest++
                            detailSource = DetailSource.RECORDS
                            selectedLearningRecordCard = it
                        },
                        // I-4（修复轮 1）：生词本段两枚能力接宿主**现成**通路，不新建任何线路——
                        // 重学走 showVocabularyRelearn 分支（其渲染早于本页、自带 BackHandler），
                        // 行内标记走老页挂载处同一回调 vocabularyViewModel?.markMastered。
                        onStartRelearn = { showVocabularyRelearn = true },
                        onMarkMastered = { vocabularyViewModel?.markMastered(state.profile.id, it) },
                        // N-1（修复轮 3）：结果态必须由本页消费并复位——与老页挂载处 :852/:853 **同一来源**
                        // （vocabularyMasteryState 在宿主层恒收集，复位回调就是 clearMasteryMessage），
                        // 不新建通路。少了这两行时：失败静默（StorageUnavailable 不 reload、合并页零反馈），
                        // 而残留的 Success 会让随后打开的老生词页弹出一枚属于另一页动作的结果框。
                        masteryState = vocabularyMasteryState,
                        onDismissMasteryMessage = { vocabularyViewModel?.clearMasteryMessage() },
                        // m-2（修复轮 1）：重试只刷用户点的那一册，不再一次拉三册
                        // （旧写法会把另外两段打回 Loading，连带闪 loading 与历史 I-1 的重复落位）。
                        onRetry = { retrySection ->
                            when (retrySection) {
                                MyWordsSection.Favorites -> favoritesViewModel?.load(state.profile.id)
                                MyWordsSection.Wordbook -> vocabularyViewModel?.load(state.profile.id)
                                MyWordsSection.Wrong -> wrongWordsViewModel?.load(state.profile.id)
                            }
                        },
                    )
                }
                WordBankScreen(
                    wordBookName = todayReady?.wordBookName ?: "未选择词书",
                    section = wordBankSection,
                    onSelectSection = { wordBankSection = it },
                    onBack = {
                        if (bookPaneShowsWordList) bookPaneShowsWordList = false else showWordBank = false
                    },
                    bookPane = {
                        if (bookPaneShowsWordList) {
                            // 词书单词是纯浏览场景：点词只打开详情，不自动发音（D1/D2）。
                            // 页头关掉（`showHeader = false`）：返回位只留壳那一枚，否则同屏两个「返回」。
                            WordBookWordsScreen(
                                state = wordBookWordsState,
                                listState = wordBookWordsListState,
                                onBack = { bookPaneShowsWordList = false },
                                onFilter = { wordBookWordsViewModel?.selectFilter(it) },
                                onRetry = { wordBookWordsViewModel?.retry() },
                                onOpenWord = {
                                    detailPronunciationMessage = PronunciationStatus.Idle
                                    learningRecordPronunciationRequest++
                                    detailSource = DetailSource.RECORDS
                                    selectedLearningRecordCard = it.card
                                },
                                showHeader = false,
                            )
                        } else {
                            // 两枚状态计数只在词单真读出来时给：`null` 让详情页把那两行整行不画，
                            // 而不是在「还没读出来」时摆一个「0」（读不到的那枚整枚不显示）。
                            val bookWords = wordBookWordsState.words
                            val wordsReadable = !wordBookWordsState.loading &&
                                wordBookWordsState.error == null &&
                                bookWords.isNotEmpty()
                            BookDetailScreen(
                                bookName = todayReady?.wordBookName ?: "未选择词书",
                                progress = todayReady?.bookProgress,
                                learningCount = if (wordsReadable) {
                                    bookWords.count { it.status == WordBookRecordStatus.Learning }
                                } else {
                                    null
                                },
                                dueCount = if (wordsReadable) {
                                    bookWords.count { it.status == WordBookRecordStatus.Due }
                                } else {
                                    null
                                },
                                onOpenWordList = { bookPaneShowsWordList = true },
                                // 「更换词书」走既有学习设置（选词书在那里）：先关词库再把设置层压上来，
                                // 返回时回到学习主页，不留两层叠着等返回键逐层拆（与今日词单同一条口径）。
                                onOpenPicker = {
                                    showWordBank = false
                                    showSetup = true
                                },
                            )
                        }
                    },
                    newPane = { myWordsPane(MyWordsSection.Wordbook) },
                    wrongPane = { myWordsPane(MyWordsSection.Wrong) },
                    favoritePane = { myWordsPane(MyWordsSection.Favorites) },
                    // 分段标签的条数（原型 `sMeWords` 的三格计数）：只从三段**已有的** UiState 现算，
                    // 不新开数据通路、不读仓储。读不到就是 null —— 壳会把那一格画成裸段名，
                    // 而不是用 0 把「还没读出来」说成「确实是空的」。
                    newCount = (vocabularyState as? VocabularyUiState.Ready)?.items?.size,
                    wrongCount = (wrongWordsState as? WrongWordsUiState.Ready)?.cards?.size,
                    favoriteCount = (favoritesState as? FavoritesUiState.Ready)?.words?.size,
                )
            } else if (showAiProfiles) {
                val editor by aiProfileViewModel?.editor?.collectAsState() ?: remember { mutableStateOf(null) }
                // 批 4 Task 3：AI 服务屏里的两枚危险动作（清除密钥 / 删除配置）不再点了就执行——
                // 屏侧只发请求，确认框住在这里。请求态是屏内局部状态，所以两枚确认框必须与
                // `AiProfileSettingsScreen(` 同住这一分支：合成样片之外的第三级浮层不归本层管。
                var pendingAiProfileDelete by remember(state.profile.id) { mutableStateOf<String?>(null) }
                var pendingAiProfileKeyClear by remember(state.profile.id) { mutableStateOf<String?>(null) }
                AiProfileSettingsScreen(
                    listState = aiProfiles ?: AiProfileListUiState.Loading,
                    editor = editor,
                    onAdd = { aiProfileViewModel?.startCreate() },
                    onEdit = { aiProfileViewModel?.startEdit(it) },
                    onDraftChange = { aiProfileViewModel?.updateDraft(it) },
                    onSave = { aiProfileViewModel?.save() },
                    onCloseEditor = { aiProfileViewModel?.closeEditor() },
                    onRequestDeleteProfile = { pendingAiProfileDelete = it },
                    onRequestDeleteKey = { pendingAiProfileKeyClear = it },
                    onSetDefaultTextProfile = { aiProfileViewModel?.setDefaultTextProfile(it) },
                    onSetDefaultImageProfile = { aiProfileViewModel?.setDefaultImageProfile(it) },
                    onBack = {
                        showAiProfiles = false
                        speechSettingsViewModel?.load()
                    },
                )
                // 删除配置：配置元数据与已保存的密钥一起走（仓储侧先删密钥再删元数据），
                // 所以正文必须点名密钥也会清除；文章/收藏/学习记录不在这一侧，如实保留面。
                pendingAiProfileDelete?.let { profileId ->
                    ConfirmDestructiveDialog(
                        state = true,
                        title = "删除这套 AI 配置？",
                        cascadeLines = listOf(
                            "将删除这台设备上这套配置的名称、网站地址、Endpoint、模型与高级参数，并清除已保存的密钥。",
                        ),
                        keepLines = listOf(
                            "文章、收藏、学习记录与词库不受影响；其他 AI 配置也不受影响。",
                        ),
                        irreversible = true,
                        confirmLabel = "确认删除",
                        onConfirm = {
                            aiProfileViewModel?.deleteProfile(profileId)
                            pendingAiProfileDelete = null
                        },
                        onDismiss = { pendingAiProfileDelete = null },
                    )
                }
                // 清除密钥：元数据全留，只丢密钥；这套配置若正被当作默认服务，默认位会一并解绑。
                pendingAiProfileKeyClear?.let { profileId ->
                    ConfirmDestructiveDialog(
                        state = true,
                        title = "清除已保存的密钥？",
                        cascadeLines = listOf(
                            "清除后这套配置将无法调用服务，需要重新粘贴密钥才能再用。",
                        ),
                        keepLines = listOf(
                            "名称、网站地址、Endpoint、模型与高级参数保留；文章、收藏与学习记录不受影响。",
                        ),
                        irreversible = true,
                        confirmLabel = "确认清除",
                        onConfirm = {
                            aiProfileViewModel?.deleteKey(profileId)
                            pendingAiProfileKeyClear = null
                        },
                        onDismiss = { pendingAiProfileKeyClear = null },
                    )
                }
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
                    onToggleAutoSpeakOnCardReady = { enabled ->
                        speechSettingsViewModel?.setAutoSpeakOnCardReady(enabled)
                    },
                    // 批 5 Task 1：语速由点按循环改为滑杆，宿主只把滑杆位置转成倍率下发；
                    // 吸附到四档由 ViewModel 做（`SpeechRateSteps` 是唯一档位表）。
                    onSetSpeechRate = { rate -> speechSettingsViewModel?.setSpeechRate(rate) },
                    onBack = { showSpeechSettings = false },
                )
            } else if (showReminderSettings) {
                // F4-02：权限判定在 composable 上下文完成；onEnableRequested 只收"已授权"结果。
                // 拒绝后不重复请求（Android 记住拒绝态），设置说明提供系统设置入口。
                //
                // 批 5 收口（真机目视）：原来把 `notificationGranted` 当**组合期快照**捕获进 lambda，
                // 授权完成后没有任何回调去 `setEnabled(true)`，于是开关永远停在关、用户得先退出本页再进来。
                // 改成「权限结果走 launcher 回调」：授权成功当场打开开关；已授权时点一下直接落库。
                // 只换权限通路，状态机与落库口径一律不动（仍只经 `setEnabled(true)`）。
                val reminderActivity = LocalContext.current as? android.app.Activity
                val needsNotificationPermission = android.os.Build.VERSION.SDK_INT >= 33 &&
                    reminderActivity != null &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        reminderActivity,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) reminderSettingsViewModel?.setEnabled(true)
                }
                ReminderSettingsScreen(
                    onBack = { showReminderSettings = false },
                    onEnableRequested = {
                        if (needsNotificationPermission) {
                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            reminderSettingsViewModel?.setEnabled(true)
                        }
                    },
                    viewModel = reminderSettingsViewModel,
                )
            } else if (showBackupExport) {
                // F4-03：无认证 Salvage 包导出确认页；SAF「另存为」在页面内部处理，取消不写文件。
                BackupExportScreen(
                    onBack = { showBackupExport = false },
                    viewModel = backupExportViewModel,
                )
            } else if (showBackupRestore) {
                // F4-04：新设备认证恢复页；扫码/密码/二次确认在页面内部处理，无旁路入口。
                BackupRestoreScreen(
                    onBack = { showBackupRestore = false },
                    viewModel = backupRestoreViewModel,
                )
            } else if (showBackupPairingExport) {
                // F4-04：旧设备认证导出页；系统认证门控 + 配对二维码 + 可选密码 + SAF 落盘，页面内无跳过认证入口。
                BackupPairingExportScreen(
                    onBack = { showBackupPairingExport = false },
                    viewModel = backupPairingExportViewModel,
                )
            } else if (showBackupHub) {
                // 裁决⑥：三节子页。它挂在三枚子屏**之后**（先真先赢）——子屏开着时压在上面，
                // 子屏 onBack 只置子屏 false，自然回到本页；本页返回键再回设置页。
                BackupHubScreen(
                    onBack = { showBackupHub = false },
                    onOpenBackupExport = { showBackupExport = backupExportViewModel != null },
                    onOpenBackupPairingExport = { showBackupPairingExport = backupPairingExportViewModel != null },
                    onOpenBackupRestore = { showBackupRestore = backupRestoreViewModel != null },
                    onDeleteAllLearningData = {
                        learningDataDeletionViewModel?.requestDelete(state.profile.id)
                    },
                    deleteAllLearningDataNotice = learningDataDeletionUi.notice,
                )
            } else if (showAbout) {
                // 「关于」页：版本号从构建信息取（`buildConfig = true`），许可数暂留 `null`
                // （行文案退成不带数字的那一句；许可条数以 `LicensesScreen` 的解析结果为准，后续注入）。
                AboutScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    licenseCount = null,
                    onBack = { showAbout = false },
                    onOpenLicenses = { showAbout = false; showLicenses = true },
                    onOpenBackup = { showAbout = false; showBackupHub = true },
                )
            } else if (showLicenses) {
                // F5-04：许可台账页。数据源=构建期从 docs/third-party-notices.md 同步的 assets；
                // 读不到传空串，页面如实显示「许可台账暂不可读」，零假台账零假入口。
                // 批 5 Task 3（对账 2 的宿主侧一半）：空态的「重新读取」必须能真的重读——旧写法是
                // 无键 `remember`，一次失败就永远缓存了空串，页面那枚重试键会是假按钮。
                // 故把重读次数当键：点一次重读，`remember` 失效重跑一遍 assets 读取。
                val licensesContext = LocalContext.current
                var licensesReload by remember { mutableStateOf(0) }
                val noticesMarkdown = remember(licensesReload) {
                    runCatching {
                        licensesContext.assets.open("third-party-notices.md").bufferedReader().use { it.readText() }
                    }.getOrDefault("")
                }
                LicensesScreen(
                    markdown = noticesMarkdown,
                    onBack = { showLicenses = false },
                    onRetry = { licensesReload += 1 },
                )
            } else if (selectedFeature == AiFeature.SENTENCE_ANALYSIS) {
                // 已实现的功能走真实屏幕；骨架页只留给尚未接通的功能。
                val sentenceState = sentenceAnalysisViewModel?.uiState?.collectAsState()?.value
                    ?: SentenceAnalysisUiState.Idle
                // 批 4 Task 5：屏侧只上报朗读请求，语音能力与代次校验留在本层（与拍照翻译同形）：
                // 连点两枚段落后，先发的那次朗读结果不许覆盖后一次的反馈。
                var sentenceSpeakMessage by remember { mutableStateOf(PronunciationStatus.Idle) }
                var sentenceSpeakRequest by remember { mutableStateOf(0L) }
                var sentenceSpeakJob by remember { mutableStateOf<Job?>(null) }
                SentenceAnalysisScreen(
                    state = sentenceState,
                    onAnalyze = { sentence -> sentenceAnalysisViewModel?.analyze(sentence, state.profile.id) },
                    onConfirmOutbound = { sentenceAnalysisViewModel?.confirmOutbound(it) },
                    speakMessage = sentenceSpeakMessage.message(),
                    onSpeak = { text ->
                        val request = ++sentenceSpeakRequest
                        sentenceSpeakJob?.cancel()
                        sentenceSpeakMessage = PronunciationStatus.Playing
                        sentenceSpeakJob = pronunciationScope.launch {
                            val result = pronunciationStatus(pronunciationProvider?.speakSentence(text))
                            if (request == sentenceSpeakRequest) {
                                sentenceSpeakMessage = result
                            }
                        }
                    },
                    onBack = {
                        sentenceSpeakJob?.cancel()
                        selectedFeatureKey = null
                    },
                    onOpenAiSettings = {
                        selectedFeatureKey = null
                        openAiProfilesOrSettings()
                    },
                )
            } else if (selectedFeature == AiFeature.PHOTO_TRANSLATION) {
                // F3-03：拍照翻译。图片只进临时工作区（零残留由状态机保证）；翻译每次出站都确认。
                val photoState = photoTranslationViewModel?.uiState?.collectAsState()?.value
                    ?: PhotoTranslationUiState()
                var photoSpeakMessage by remember { mutableStateOf(PronunciationStatus.Idle) }
                var photoSpeakRequest by remember { mutableStateOf(0L) }
                var photoSpeakJob by remember { mutableStateOf<Job?>(null) }
                // 批 4 Task 4：历史删除不再点了就执行——屏侧只发请求，确认框归本层（与 AI 服务页同形）。
                var pendingPhotoTranslationDelete by remember(state.profile.id) { mutableStateOf<String?>(null) }
                LaunchedEffect(state.profile.id) { photoTranslationViewModel?.refreshHistory(state.profile.id) }
                PhotoTranslationScreen(
                    state = photoState,
                    onImageBytes = { bytes -> photoTranslationViewModel?.onImageBytes(bytes) },
                    onOcrTextEdited = { text -> photoTranslationViewModel?.onOcrTextEdited(text) },
                    onTranslate = { photoTranslationViewModel?.onTranslateTapped() },
                    onConfirmOutbound = { confirmed -> photoTranslationViewModel?.onConfirmationResult(confirmed) },
                    onRefreshModelStatus = { photoTranslationViewModel?.refreshModelStatus() },
                    onInstallModel = { photoTranslationViewModel?.installModel() },
                    onUninstallModel = { photoTranslationViewModel?.uninstallModel() },
                    onSave = { photoTranslationViewModel?.saveCurrentTranslation(state.profile.id) },
                    onRequestDeleteSaved = { recordId -> pendingPhotoTranslationDelete = recordId },
                    onOpenDictionary = { tapped ->
                        selectedSearchCard = null
                        searchPronunciationMessage = PronunciationStatus.Idle
                        searchPronunciationRequest++
                        searchPronunciationJob?.cancel()
                        globalVocabularySearchViewModel?.setProfile(state.profile.id)
                        globalVocabularySearchViewModel?.openSearch(tapped)
                        showGlobalVocabularySearch = globalVocabularySearchViewModel != null
                    },
                    speakMessage = photoSpeakMessage.message(),
                    onSpeak = { sentence ->
                        val request = ++photoSpeakRequest
                        photoSpeakJob?.cancel()
                        photoSpeakMessage = PronunciationStatus.Playing
                        photoSpeakJob = pronunciationScope.launch {
                            val result = pronunciationStatus(pronunciationProvider?.speakSentence(sentence))
                            if (request == photoSpeakRequest) {
                                photoSpeakMessage = result
                            }
                        }
                    },
                    onBack = {
                        photoSpeakJob?.cancel()
                        photoTranslationViewModel?.reset()
                        selectedFeatureKey = null
                    },
                    onOpenAiSettings = {
                        selectedFeatureKey = null
                        openAiProfilesOrSettings()
                    },
                )
                // 批 4 Task 4：历史删除的确认门。级联句说清「原图 + 译文一起走」，保留面说清「别的都没动」，
                // 不点确认零写入（与「删除这一版」「删除这套 AI 配置」同一档）。
                pendingPhotoTranslationDelete?.let { recordId ->
                    ConfirmDestructiveDialog(
                        state = true,
                        title = "删除这条翻译记录？",
                        cascadeLines = listOf(
                            "将删除这条已保存的原文、译文，以及随它保存的原图（图片文件同时从本机清除）。",
                        ),
                        keepLines = listOf(
                            "其他历史记录、词库、文章与学习记录不受影响。",
                        ),
                        irreversible = true,
                        confirmLabel = "确认删除",
                        onConfirm = {
                            photoTranslationViewModel?.deleteSaved(state.profile.id, recordId)
                            pendingPhotoTranslationDelete = null
                        },
                        onDismiss = { pendingPhotoTranslationDelete = null },
                    )
                }
            } else if (selectedFeature == AiFeature.IMAGE_STUDIO) {
                val studioState = imageStudioViewModel?.uiState?.collectAsState()?.value
                    ?: ImageStudioUiState.Idle
                ImageStudioScreen(
                    state = studioState,
                    onGeneratePrompt = { subject -> imageStudioViewModel?.generatePrompt(subject) },
                    // 描述可编辑（批 4 Task 6）：屏内改过的那一句从这里进 VM，出站用它而不是模型原稿。
                    onGenerateImage = { prompt -> imageStudioViewModel?.generateImage(prompt) },
                    onConfirm = { imageStudioViewModel?.confirm(it) },
                    onBack = { selectedFeatureKey = null },
                    onOpenAiSettings = {
                        selectedFeatureKey = null
                        openAiProfilesOrSettings()
                    },
                )
            } else if (selectedFeature != null) {
                AiFeatureScreen(
                    feature = selectedFeature,
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
                // 锁定态同样要给出历史：历史与解锁正交，未解锁只挡住「今天生成新文章」。
                val history = readingState.historyOrEmpty
                val versionState = articleVersionViewModel?.uiState?.collectAsState()?.value ?: ArticleVersionUiState()
                ReadingHistoryScreen(
                    history = history,
                    onBack = { showReadingHistory = false },
                    onOpenArticle = { historyArticle = it },
                    onRequestDeleteVersion = { articleVersionViewModel?.requestDelete(it) },
                    notice = versionState.notice,
                )
                // F4-01 决策 §8：删除前二次确认，并明列级联影响条数；不点「确认删除」零写入。
                val pendingDelete = versionState.pending
                pendingDelete?.let { pending ->
                    ConfirmDestructiveDialog(
                        state = true,
                        title = "删除这一版？",
                        // 这一枚的保留面（「同组剩余 N 个版本可读」）本来就写在级联句里，
                        // 拆成两行会改动措辞，所以按逐字保留红线整句归 cascadeLines、保留面档留空。
                        cascadeLines = listOf(
                            "将删除《${pending.article.title}》第 ${pending.article.version} 版（${pending.article.localDate}）。",
                            "级联影响：该版阅读完成记录 ${pending.impact.removedCompletionCount} 条将一并删除；同组剩余 ${pending.impact.remainingVersionCount} 个版本可读。",
                        ),
                        keepLines = emptyList(),
                        irreversible = true,
                        confirmLabel = "确认删除",
                        onConfirm = { articleVersionViewModel?.confirmDelete() },
                        onDismiss = { articleVersionViewModel?.cancelDelete() },
                    )
                }
            } else if (showReadingFeed && readingTarget == null && historyArticle == null) {
                // 裁决⑤ 第二件：外刊列表与抓取归本层，阅读主页只留 `reading_source_feed` 一枚入口。
                // 挂载守卫照抄 `showReadingHistory && historyArticle == null` 那一形：文章层一旦在顶上，
                // 列表就让位，返回键先把文章关掉、再退本层（对应上面的 BackHandler 声明顺序）。
                val feedState = (readingState as? ReadingAccessUiState.Ready)?.feed ?: FeedUiState.Idle
                ReadingFeedScreen(
                    feed = feedState,
                    onBack = { showReadingFeed = false },
                    onFetchFeedItem = { readingAccessViewModel?.fetchFeedItem(it) },
                    // 批 3 Task 1：两种取源失败态各带一枚「重试」，接的是现成入口 `openFeed()`
                    // （`ReadingAccessViewModel.kt:190-207`，内部会先把状态置 Loading 再取源）。
                    // 屏不持 ViewModel、也不新增数据通路：这里只转一次回调。
                    onRetry = { readingAccessViewModel?.openFeed() },
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
                            // I-2 收口①：屏内「返回」与两条 BackHandler 走同一清场闭包。
                            clearArticlePronunciation()
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
                        // F3-02：整篇朗读从 fire-and-forget 升级为「有状态、可播控」——
                        // 请求前置 Playing、回来后按代次校验写回结果（旧的即发即忘连文案都不给）。
                        onSpeakSentence = { sentence ->
                            val request = ++articlePronunciationRequest
                            articlePronunciationStatus = PronunciationStatus.Playing
                            articlePronunciationMessage = PronunciationStatus.Playing.message()
                            pronunciationScope.launch {
                                val result = pronunciationStatus(pronunciationProvider?.speakSentence(sentence))
                                if (request == articlePronunciationRequest) {
                                    articlePronunciationStatus = result
                                    articlePronunciationMessage = result.message()
                                }
                            }
                        },
                        pronunciationMessage = articlePronunciationMessage,
                        pronunciationStatus = articlePronunciationStatus,
                        pauseResumeSupported = pauseResumeSupported,
                        onPauseResume = {
                            val next = when {
                                articlePronunciationStatus == PronunciationStatus.Playing && pronunciationProvider?.pausePlayback() == true -> PronunciationStatus.Paused
                                articlePronunciationStatus == PronunciationStatus.Paused && pronunciationProvider?.resumePlayback() == true -> PronunciationStatus.Playing
                                // 决策 §2（Task 9 评审后裁决）：未被引擎接受时**保持原状态**——
                                // 落 Idle 会让整组控件（含唯一可用的「停止」）在朗读仍在继续时消失。
                                else -> articlePronunciationStatus
                            }
                            articlePronunciationStatus = next
                            articlePronunciationMessage = next.message()
                        },
                        onStopPronunciation = {
                            articlePronunciationRequest++
                            pronunciationProvider?.stopPlayback()
                            articlePronunciationStatus = PronunciationStatus.Idle
                            articlePronunciationMessage = null
                        },
                        onSetLearnedMarks = { articleReadingViewModel?.setLearnedMarks(it) },
                        onCompleteReading = { articleReadingViewModel?.completeReading() },
                        articleScrollPosition = articleScrollPosition,
                        onArticleScrollPositionChange = { articleScrollPosition = it },
                    )
                    // 点词的详情是阅读页之上的覆盖层，不动 readingTarget 的状态机。
                        selectedArticleCard?.let { card ->
                        // 打开详情不自动发音：只有 onSpeak（用户点击「播放 X 发音」）才调用 provider，
                        // 否则点击一次会累计两次调用（见 2bff744 回归）。
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
                                    val result = pronunciationStatus(pronunciationProvider?.speakWord(card.lemma, PronunciationAccent.American))
                                    if (request == detailPronunciationRequest && selectedArticleCard === card) {
                                        detailPronunciationMessage = result
                                    }
                                }
                            },
                            onSpeakBritish = {
                                val request = ++detailPronunciationRequest
                                detailPronunciationMessage = PronunciationStatus.Playing
                                pronunciationScope.launch {
                                    val result = pronunciationStatus(pronunciationProvider?.speakWord(card.lemma, PronunciationAccent.British))
                                    if (request == detailPronunciationRequest && selectedArticleCard === card) {
                                        detailPronunciationMessage = result
                                    }
                                }
                            },
                            pronunciationStatus = detailPronunciationMessage ?: PronunciationStatus.Idle,
                            pronunciationControlsEnabled = true,
                            pauseResumeSupported = pauseResumeSupported,
                            // 暂停/继续不自增代次（裁决 B3）：暂停不改变这一次发声的归属。
                            onPauseResume = {
                                val held = detailPronunciationMessage
                                val next = when {
                                    held == PronunciationStatus.Playing && pronunciationProvider?.pausePlayback() == true -> PronunciationStatus.Paused
                                    held == PronunciationStatus.Paused && pronunciationProvider?.resumePlayback() == true -> PronunciationStatus.Playing
                                    // 决策 §2（Task 9 评审后裁决）：未被引擎接受时**保持原状态**——
                                    // 落 Idle 会让整组控件（含唯一可用的「停止」）在朗读仍在继续时消失。
                                    else -> held
                                }
                                detailPronunciationMessage = next
                            },
                            // 停止：先自增本宿主的发声代次再 stopPlayback()，最后直接置空闲——
                            // 播放器层把用户主动停止以 Played 收口（决策 §2），代次在前才挡得住迟到的 Played。
                            onStopPronunciation = {
                                detailPronunciationRequest++
                                pronunciationProvider?.stopPlayback()
                                detailPronunciationMessage = PronunciationStatus.Idle
                            },
                            favoritePresent = if (favoritesViewModel != null) card.cardId in favoriteIds else null,
                            onToggleFavorite = favoritesViewModel?.let { vm -> { vm.toggle(card.cardId, card.wordBookId, card.lemma) } },
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
                                onOpenAiSettings = {
                                    showWordQaCard = null
                                    openAiProfilesOrSettings()
                                },
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
                                // 批 1 Task 4/5 + Task 16/17：学习页的四条裸行，目的地分别是今日词单 / 复习队列 /
                                // 今日文章 / 本机词库。词库那条打开通路必须**同时**点名段位：只赋 showWordBank 不赋段位 =
                                // 从复习行落到词书段（用户点的是复习，看到的是列表第一页）。
                                onOpenWordList = { showTodayWordList = true },
                                onOpenReviewQueue = { showReviewQueue = true },
                                onOpenWordBank = { wordBankSection = WordBankSection.Book; showWordBank = true },
                                // 批 1 Task 19：「复习生词本」那枚终点（`onReviewWords` → 词库生词段）随原型
                                // `sLearnEmpty`（`assets/app.js:501-518`）下线——那一屏只画两枚出口，而它的两枚
                                // 走下面的既有通路（onOpenSetup / onOpenReading）。生词段的出口是词库页自己的
                                // 分段控件（`WordBankScreen` 的四格 tab），不留第二条绕过 tab 的暗线。
                                onOpenSearch = {
                                    // 主页查词走索引版全量搜索：这里没有旧词书搜索页的
                                    // 「浏览词书」——那属于词书管理语义，不是「查一个词」。
                                    // 传空串即落在「最近搜索」。
                                    globalVocabularySearchViewModel?.setProfile(state.profile.id)
                                    globalVocabularySearchViewModel?.openSearch("")
                                    showGlobalVocabularySearch = globalVocabularySearchViewModel != null
                                },
                                // 原型对齐批 1：页头行尾的齿轮是「设置」入口（原型 `data-to="me-settings"`）。
                                // 应用里没有独立的设置页，设置九行住在「我的」一级页，故落到该 tab——
                                // 不新建路由、不叠浮层。
                                onOpenSettings = { selectedTab = AppTab.SETTINGS },
                                // 批 1 Task 6：两个数字与我的页同源（宿主层那一份 checkInState），
                                // 学习页自己不再读仓库。「今日文章已读」= 今天有过完成阅读的记录。
                                streakDays = streakDays,
                                todayArticleRead = todayArticleRead,
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
                                    // 与 AI 助学 tab 的跳转同一规则：配置页可用就直达，否则退回设置栏。
                                    aiProfileViewModel?.load()
                                    if (aiProfileViewModel != null) {
                                        showAiProfiles = true
                                    } else {
                                        selectedTab = AppTab.SETTINGS
                                    }
                                },
                                onOpenImport = { showArticleImport = true },
                                // 主页这一枚只负责导航：进二级层的同时触发一次取列表（取源本体仍归 ViewModel）。
                                onOpenFeed = {
                                    showReadingFeed = true
                                    readingAccessViewModel?.openFeed()
                                },
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
                                    if (it == AiFeature.PHOTO_TRANSLATION) photoTranslationViewModel?.reset()
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
                                    // 路 B（非破坏性选择）：容器交房内共享玻璃档，取消走共享 PillButton；
                                    // 列表项是本枚自渲染的（tag 供 connected 逐项定位），原地保留不改口径。
                                    GlassDialog(onDismiss = { showWordBookExportPicker = false }) {
                                        Text(text = "选择要导出的词书", style = AppType.Headline, color = appColors().text1)
                                        Column(
                                            modifier = Modifier.padding(top = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            transferState?.books.orEmpty().forEach { bookId ->
                                                Text(
                                                    text = bookId,
                                                    style = AppType.Body,
                                                    color = appColors().text1,
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
                                        PillButton(
                                            text = "取消",
                                            onClick = { showWordBookExportPicker = false },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 16.dp),
                                            style = PillStyle.Secondary,
                                            domain = AppDomain.Settings,
                                            testTag = "wordbook_export_cancel",
                                        )
                                    }
                                }
                                // 批 1 Task 6：设置 tab 的根页换成「我的」（spec §3.6）——档案头 + 三格统计
                                // + 学习记录行，设置的四组九行原样作为内容槽挂在下面。SettingsScreen 本身
                                // 保留为装载缝（connected 设置页测试直接挂它），但宿主只挂这一页：
                                // 同一 tab 里再挂一枚 SettingsScreen 就是两个滚动容器、两枚页头。
                                ProfileScreen(
                                    profileName = state.profile.displayName,
                                    streakDays = streakDays,
                                    learnedWordCount = learnedWordCount,
                                    // 阅读历史与解锁状态正交（`historyOrEmpty` 四种状态都表态），
                                    // 所以锁定态下这一格也是真数。
                                    articleCount = readingState.historyOrEmpty.size,
                                    onOpenWordBank = { wordBankSection = WordBankSection.Book; showWordBank = true },
                                    onOpenCheckIn = { showCheckIn = true },
                                    onOpenReadingHistory = { showReadingHistory = true },
                                    onOpenLearningRecords = { showLearningRecords = true },
                                    settingsContent = {
                                        SettingsGroupsContent(
                                            wordBookName = todayReady?.wordBookName,
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
                                            onOpenReminderSettings = {
                                                reminderSettingsViewModel?.load()
                                                showReminderSettings = reminderSettingsViewModel != null
                                            },
                                            // 裁决⑥：备份与恢复收成一条导航入口；四枚入口与删除行在子页三节里。
                                            onOpenBackupHub = { showBackupHub = true },
                                            onOpenLicenses = { showLicenses = true },
                                            onOpenAbout = { showAbout = true },
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
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        is AppUiState.NeedsRecovery -> {
            // 批 5 Task 8（对账 1）：恢复态自己也要能出去。旧形态此分支只给 `onRetry`，
            // 而本屏文案自己写着「先确认导出留档」——承诺了却不给入口。
            // 覆盖层判断因此提到本分支内层：留档一开就整屏让位给导出屏，
            // **状态机与数据通路一字不改**（不碰 NeedsRecovery → Ready 的转换）。
            var showRecoveryExport by rememberSaveable { mutableStateOf(false) }
            if (showRecoveryExport) {
                // VM 缺席时导出屏自己走只读降级分支（零假入口），所以这里不再额外拦一道——
                // 拦下来只会让主键变成「按了什么也没发生」。
                BackupExportScreen(
                    onBack = { showRecoveryExport = false },
                    viewModel = backupExportViewModel,
                )
            } else {
                RecoveryScreen(
                    reason = state.reason,
                    // SP-B 决策 §3 v1：重试 = 探针清错误态并重开一次 + activity.recreate() 重建视图树；
                    // ViewModel 与 @Singleton 探针跨 recreate 保留，失败会再次落回 NeedsRecovery，不吞原因。
                    onRetry = {
                        viewModel.retryDatabaseStartup()
                        activityForRecreate?.recreate()
                    },
                    onOpenBackupExport = { showRecoveryExport = true },
                )
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
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // 封面：本屏是首启流程唯一没有品牌视觉的一屏（`BrandLaunchSurface` 只活了一帧就被换掉），
        // spec §5「启动/建档三屏内完成 + 品牌渐变封面」把这段品牌感留在这里。
        // `AppleMintGradient` 是品牌渐变（spec §2.1 不动项、`AppleMint*` 令牌本就静态），全屏只此一处——
        // 旧版按钮是「渐变底 + 透明容器色」绕过去的老皮肤，本批改回实心块后不再重复用渐变。
        // 渐变是浅薄荷，白字只有 ≈1.29–2.40:1，故深字 token `onBrand` 压在上面（禁令 2 同口径）。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .background(AppleMintGradient),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_learning_mark),
                contentDescription = null,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "从今天开始",
                style = AppType.Title,
                color = colors.onBrand,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "一天一点积累",
                style = AppType.Footnote,
                color = colors.onBrand,
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // R-13.1 / C-1：这一屏的页头同样交统一组件承载，把本文件最后一处 M3 `typography` 的 headline 档
            // 裸读数清零——页头族 seed 摘行的判据是「该屏 forbidden 读点清零」，不是「补一行统一页头调用就准删」
            // （族判定面带「本文件调了共享页头即整体合规」出口，残余读数会跟着永久隐形，见 SharedComponentUniquenessRatchetTest）。
            // 色值安全：组件标题读 `colors.text1`、副标题读 `colors.text2`。
            // 首启流程没有「上一层」可返回，故 `onBack = null`（R-9.2：不渲染返回位、不留占位）。
            AppScreenHeader(
                title = "先认识一下你",
                subtitle = "填写一个称呼，学习记录只保存在这台设备上。",
                onBack = null,
            )
            TextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("姓名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "姓名输入" },
                // 输入框是「一屏一个输入面」，底色取 surface、焦点态用深实底描边（与主操作同色），
                // 不再走静态 Mint 桥：深套下 Mint* 是浅套常量，会把浅底留在深色主题里。
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedIndicatorColor = colors.strongSurface,
                    unfocusedIndicatorColor = colors.outline,
                    cursorColor = colors.strongSurface,
                    focusedLabelColor = colors.text1,
                    unfocusedLabelColor = colors.text2,
                    focusedTextColor = colors.text1,
                    unfocusedTextColor = colors.text1,
                ),
            )
            if (!canCreate) {
                Text(
                    text = "请输入名字后再创建",
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.semantics { contentDescription = "请输入名字提示" },
                )
            }
            Button(
                onClick = onCreate,
                enabled = canCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .semantics { contentDescription = "创建资料" },
                // 主操作与学习设置屏同口径：深色实底 + 反白字，圆角交 `AppShape.Button`，不再手搓渐变皮。
                shape = AppShape.Button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.strongSurface,
                    contentColor = colors.onStrong,
                    disabledContainerColor = colors.tint3,
                    disabledContentColor = colors.text3,
                ),
            ) {
                // 前景色跟着按钮下发的 ink 走（`contentColor` / `disabledContentColor`）：
                // `AppType.Label` 自带 `text2`，自钉 `onStrong` 又会让禁用态变成近白字压浅底。
                Text(
                    text = "创建",
                    style = AppType.Label,
                    color = LocalContentColor.current,
                    fontWeight = FontWeight.Bold,
                )
            }
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
            colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = appColors().onBrand),
        ) {
            Text("返回重新填写")
        }
    }
}

@Composable
internal fun LearningSetupScreen(
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
    // 文案口径按 F4-01 决策 §6 反转：删的是词书内容与词条，学习历史与文章**保留**。
    state.pendingDelete?.let { book ->
        val preview = state.deletePreview
        ConfirmDestructiveDialog(
            state = true,
            title = "删除《${book.displayName}》？",
            // 级联（删什么）与保留面（留什么）各按原站点的两档逐字给；「其他词书不受影响」属保留面档，
            // 原样跟着第二行下来。自写「不可撤销」声明由组件固定句承载。
            cascadeLines = listOf(
                if (preview != null) {
                    "将删除这册导入词书的词卡文件与词条 ${preview.entryCount} 条。"
                } else {
                    "将删除这册导入词书的词卡文件与全部词条内容。"
                },
            ),
            keepLines = listOf(
                if (preview != null) {
                    "学习历史与文章保留：该册学习记录 ${preview.keptEventCount} 条、文章 ${preview.keptArticleCount} 篇仍可翻阅（复习状态与生词笔记同保留）。"
                } else {
                    "本机学习历史与文章保留，仍可翻阅；暂时读不到具体条数。"
                },
                "其他词书不受影响。",
            ),
            irreversible = true,
            confirmLabel = "删除",
            onConfirm = { viewModel.confirmDelete(profileId) },
            onDismiss = viewModel::cancelDelete,
        )
    }

    // 切书时发现已学过的同词：由用户决定是否标记为已学习，不静默迁移。
    state.pendingMigration?.let { preview ->
        // 路 B（非破坏、二选一）：容器交共享玻璃档，两枚动作走共享 PillButton（不手搓裸按钮）。
        GlassDialog(onDismiss = { viewModel.resolveMigration(profileId, apply = false) }) {
            Text(text = "发现已学过的单词", style = AppType.Headline, color = appColors().text1)
            Text(
                text = "目标词书里有 ${preview.candidates.size} 个单词你之前已经学过，要把它们标记为已学习吗？原词书的学习记录会原样保留。",
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("wordbook_migration_message"),
                style = AppType.Body,
                color = appColors().text1,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PillButton(
                    text = "暂不标记",
                    onClick = { viewModel.resolveMigration(profileId, apply = false) },
                    modifier = Modifier.weight(1f),
                    style = PillStyle.Secondary,
                    domain = AppDomain.Library,
                    testTag = "wordbook_migration_skip",
                )
                PillButton(
                    text = "标记为已学习",
                    onClick = { viewModel.resolveMigration(profileId, apply = true) },
                    modifier = Modifier.weight(1f),
                    style = PillStyle.Primary,
                    domain = AppDomain.Library,
                    testTag = "wordbook_migration_confirm",
                )
            }
        }
    }

    val colors = appColors()
    // 原型 `sPlanEdit` / `sCardSettings` 的版面是「可滚表单 + `.foot` 吸底条」，所以这里拆两段：
    // 内容段 `weight(1f).verticalScroll()`，条在滚动区**之外**——用 `align(Alignment.BottomCenter)`
    // 要给外层铺 `fillMaxSize` 再叠内容，与 `verticalScroll` 的高度约束打架（`StickyFooterHairlineGuardTest` 同口径）。
    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 页头承担「我在哪 + 怎么提交」（原型 `barTop({title:'学习计划', action: 保存})`）。
            // 返回位仍由共享页头承载，旧版那枚 `← 返回上一层` 的 `TextButton` 已退役；
            // 强制首设路径 `onCancel == null` 时页头返回位整段不渲染（R-9.2）。
            AppScreenHeader(
                title = "学习计划",
                onBack = onCancel,
                action = { SetupHeaderSave(
                    enabled = !state.saving && !state.deleting,
                    onSave = { viewModel.save(profileId) },
                ) },
            )
            // 原型 `.meta`：这一句讲的是「这一屏改了什么、不改什么」——词书切换最怕被理解成清进度。
            // 旧 subtitle 那句「每天一点点，学习会自然变成习惯。」不携带口径，随合并屏退役。
            Text(
                text = "计划只影响每天排几个词，不会清掉你已经学过的进度。",
                style = AppType.Meta,
                color = colors.text3,
            )

            // ---------- 节 1：选择词书 ----------
            SectionHeading(text = "选择词书")
            Text(
                text = "这是应用内学习分组，不是官方考试大纲。",
                style = AppType.Caption,
                color = colors.text3,
            )
            // 选词书本体是原型 `.pick.pick-radio` 的通栏行列表（`app.js:587-606`；批 5 spec §5 的
            // 「2 列色块卡」于批 1 Task 13-A 作废）：读取还在进行时先立 3 行同形状骨架，让这一屏的
            // 第一帧就不是空的；真实列表到位后逐册渲染。只在列表为空时占位——删除词书后的
            // 重载不得把已有列表顶掉闪一次。用户投诉的是「打开选取词书先卡一下，然后整列突然蹦出来」
            // （spec F1-01，决策 2026-10-17）。删除入口是行上的长按（滑删与色块都已退役）。
            // 「使用中」给的是**已生效**的活动册（`savedWordBookId`），不是屏上的草稿选中位：
            // 用户点了另一本还没保存时，那一本还不「在使用」。
            WordBookPicker(
                wordBooks = state.wordBooks,
                loading = state.wordBooksLoading,
                selectedId = state.selectedWordBookId,
                activeBookId = state.savedWordBookId,
                progressByBook = state.progressByBook,
                progressLoading = state.progressLoading,
                canDelete = state::canDelete,
                onSelect = viewModel::selectWordBook,
                onRequestDelete = viewModel::requestDelete,
            )

            // ---------- 节 2：每日新增（原型 `chip(n + ' 个')` 那一行） ----------
            val selectedBook = state.wordBooks.firstOrNull { it.id == state.selectedWordBookId }
            SectionHeading(text = "每日新增", trailing = "当前 ${state.dailyNewTarget} 个")
            DailyTargetChips(
                target = state.dailyNewTarget,
                onTargetChange = viewModel::updateDailyNewTarget,
            )
            // 预估句绑真数据：数字来自当前所选册的真实剩余词量，读不到就整句不显示
            // （决策 2026-10-29「样例指标一律绑真数据」；原型的 4308 / 756 是样例且自身不自洽）。
            val estimate = planEstimateText(
                bookName = selectedBook?.displayName,
                remainingWords = selectedBook?.id?.let { state.progressByBook[it]?.unlearned },
            )
            if (estimate != null) {
                Text(text = estimate, style = AppType.Caption, color = colors.text3)
            }

            // ---------- 节 3：复习上限（原型 `.card` + `.stat-num` + `.range`） ----------
            SectionHeading(text = "复习上限")
            ReviewLimitSection(
                limit = state.dailyReviewLimit,
                onLimitChange = viewModel::updateReviewLimit,
            )

            // ---------- 节 4：评分之后怎么走（原型 `sCardSettings` 唯一可迁移的那一节） ----------
            SectionHeading(
                text = "评分之后怎么走",
                trailing = if (state.openDetailOnKnown) "认识后停留" else "认识后直接下一个",
            )
            AppCard {
                DetailToggleRow(
                    label = "评「认识」也展开释义",
                    description = "关：认识直接进入下一个词。开：多看一眼释义与例句再继续。",
                    checked = state.openDetailOnKnown,
                    onToggle = viewModel::setOpenDetailOnKnown,
                )
                DetailToggleRow(
                    label = "评「模糊」也展开释义",
                    description = "默认开启：没想起来的词需要先看一眼",
                    checked = state.openDetailOnFuzzy,
                    onToggle = viewModel::setOpenDetailOnFuzzy,
                    topDivider = true,
                )
                DetailToggleRow(
                    // 第三档术语跟词卡屏对齐：词卡评级是「认识 / 模糊 / 不认识」，这里不再是「忘记了」。
                    label = "评「不认识」也展开释义",
                    description = "默认开启：没想起来的词需要先看一眼",
                    checked = state.openDetailOnForgotten,
                    onToggle = viewModel::setOpenDetailOnForgotten,
                    topDivider = true,
                )
            }
            Text(
                text = revealCaptionText(openDetailOnKnown = state.openDetailOnKnown),
                style = AppType.Caption,
                color = colors.text3,
            )

            // ---------- 节 5：搜索历史 ----------
            SectionHeading(text = "搜索历史")
            AppCard {
                DetailToggleRow(
                    label = "显示词汇搜索次数",
                    description = "搜索历史中显示累计搜索次数",
                    checked = state.showVocabularySearchCount,
                    onToggle = viewModel::setShowVocabularySearchCount,
                )
            }

            // 原型 `notice(..., 'info')`。后半句按本仓真实规则改写（`GetOrCreateTodayPlanUseCase`
            // 只在「同一本地日 + 同一活动词书」时复用旧快照）：照抄那句「今日词单会立刻按新计划重排」
            // 是一个能被证伪的谎，AGENTS.md 明写「设置默认从次日生效」。
            InlineNotice(
                text = "改完点右上角「保存」。换词书会立刻重排今日词单，调整每日量从次日生效。",
                kind = InlineNoticeKind.Info,
            )
            state.message?.let {
                // Task 26 Step 3：错误行收口到共享 `InlineNotice`（族 2 的清零动作），墨色/底线/字形由组件承担。
                InlineNotice(text = it, kind = InlineNoticeKind.Error)
            }
        }
        SetupSaveBar(
            saving = state.saving,
            deleting = state.deleting,
            onSave = { viewModel.save(profileId) },
        )
    }
}

/** 原型 `var opts = [3, 5, 10, 20]`：每日新增只给四枚离散档位（滑杆上的 7 词、13 词用户自己也不信）。 */
internal val DAILY_TARGET_OPTIONS = listOf(3, 5, 10, 20)

/** 复习上限的刻度，逐字取原型 `<input class="range" min="10" max="80" step="5">`。 */
internal const val MIN_REVIEW_LIMIT = 10
internal const val MAX_REVIEW_LIMIT = 80
internal const val REVIEW_LIMIT_STEP = 5

/**
 * `Slider.steps` 要的是**中间**分段数，不是步长：10..80 每 5 一格 = 15 段 − 1 = 14 枚中间档。
 * 由上面三常量推导而不是写死 14，改 min/max/step 时滑杆不会停在错的格子上。
 */
internal const val REVIEW_LIMIT_STEPS = (MAX_REVIEW_LIMIT - MIN_REVIEW_LIMIT) / REVIEW_LIMIT_STEP - 1

/**
 * 页头右侧的保存位（原型 `.bar-action`）：`brand-deep` 的**纯文字动作**，不是第二枚带底的按钮——
 * 页头出现一枚实底就把「一屏一个主操作」的重量抢走（spec §4.1）。
 *
 * 可及名「页头保存学习设置」与吸底条那枚「保存学习设置」**必须不同**：两处同名会让 connected 侧
 * `onNodeWithContentDescription` 撞成多节点、取样当场失败。
 */
@Composable
private fun SetupHeaderSave(enabled: Boolean, onSave: () -> Unit) {
    val colors = appColors()
    Text(
        text = "保存",
        style = AppType.Label,
        color = if (enabled) colors.brandDeep else colors.text3,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(AppShape.Pill)
            .clickable(enabled = enabled, onClick = onSave)
            .semantics { contentDescription = "页头保存学习设置" }
            .padding(horizontal = 10.dp, vertical = 10.dp),
    )
}

/**
 * 「每日新增」的四枚 chip（原型 `section('每日新增', chips[3,5,10,20], '当前 N 个')`）。
 *
 * `FlowRow` 而不是 `Row`：原型是 `flex-wrap`，2× 字号下四枚 chip 必然超一行，`Row` 会挤破这节。
 */
@Composable
private fun DailyTargetChips(target: Int, onTargetChange: (Int) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DAILY_TARGET_OPTIONS.forEach { option ->
            AppChip(
                label = "$option 个",
                selected = option == target,
                onClick = { onTargetChange(option) },
            )
        }
    }
}

/**
 * 「复习上限」那张卡（原型 `.card` 里的 `.stat-num` + `.range` + `.caption`）。
 *
 * 末行 caption **不照抄原型那句**：原型写的是「超过上限时先复习最久没见的」，是一条排序承诺，
 * 而本轮 `dueTarget` 生成逻辑零改动（spec 01:28 V1 不设复习硬上限），照抄就是空头承诺。
 * 用户 2026-10-29 裁决：先把文案改成不承诺的口径，等真接进排期再回到原型那句
 * （判据 `LearningSetupLayoutContractTest.the review limit keeps the prototype number and range`，
 * 台账 `docs/decisions/2026-10-29-plan-review-limit-preference.md`）。
 *
 * 拖动只改屏上的草稿，提交点在「保存计划」（`LearningSetupViewModel.updateReviewLimit` 的草稿口径）：
 * 这条滑杆不像开关那样即时落库。可及名内嵌当前值（F5-03 规则 5，锚点从旧「每日新词目标」滑杆换过来，
 * 判据不降级）。
 *
 * 轨道色不走批 5 的「深套换域色」三元式：原型那根 `.range` 的 CSS 只有一句
 * `accent-color: var(--brand-deep)`（`assets/app.css:761`），和 `.switch[aria-checked='true']` 同一条品牌深绿
 * ——它不是一屏一域色的成员。`brandDeep` 两套主题各自有值（浅 #1A7A63 / 深 #7FE3C1），
 * 深套天然压在 `surface` 上可见，批 5 那条「轨道消失」缺陷在这里由 token 自己解决；
 * 未填充段是浏览器默认的中性浅灰，取 `outline`（不是 tint 系，绿底会把「未填」读成淡化的「已填」）。
 */
@Composable
private fun ReviewLimitSection(limit: Int, onLimitChange: (Int) -> Unit) {
    val colors = appColors()
    var dragValue by remember { mutableFloatStateOf(limit.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(limit, isDragging) {
        if (!isDragging) dragValue = limit.toFloat()
    }
    val displayed = dragValue.roundToInt()
    AppCard {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "$displayed", style = AppType.StatNum, color = colors.text1)
            Text(text = "个 / 天", style = AppType.Caption, color = colors.text3)
        }
        Slider(
            value = dragValue,
            onValueChange = { value ->
                isDragging = true
                dragValue = value
                onLimitChange(value.roundToInt())
            },
            onValueChangeFinished = {
                isDragging = false
                onLimitChange(dragValue.roundToInt())
            },
            valueRange = MIN_REVIEW_LIMIT.toFloat()..MAX_REVIEW_LIMIT.toFloat(),
            steps = REVIEW_LIMIT_STEPS,
            colors = SliderDefaults.colors(
                thumbColor = colors.brandDeep,
                activeTrackColor = colors.brandDeep,
                inactiveTrackColor = colors.outline,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .semantics { contentDescription = "每日复习上限，当前 ${displayed} 个" },
        )
        Text(
            text = "当前版本先记下这个上限，今日复习仍会排完全部到期词。",
            style = AppType.Caption,
            color = colors.text3,
        )
    }
}

/**
 * 原型 `.foot` 的吸底保存条：主操作永远在屏底，与页头那枚文字动作是同一条动作的两个入口。
 *
 * 条自铺页底色（`colors.background`），所以槽顶必须一条 `hairlineTop`——表单卡从它上面滚过时，
 * 没有那条线就会被读成「内容被截断」（批 5 遗留 7，站点登记在 `StickyFooterHairlineGuardTest`）。
 */
@Composable
private fun SetupSaveBar(saving: Boolean, deleting: Boolean, onSave: () -> Unit) {
    val colors = appColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_save_bar")
            .background(colors.background)
            .then(hairlineTop(colors.outline))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Button(
            onClick = onSave,
            enabled = !saving && !deleting,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .semantics { contentDescription = "保存学习设置" },
            // 主操作是单实底（撤掉品牌渐变与透明底）：深色实底 + 反白字才是「一屏一个主操作」
            // 该有的重量（spec §4.1）。圆角交 `AppShape.Button`，不手搓。
            shape = AppShape.Button,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.strongSurface,
                contentColor = colors.onStrong,
                disabledContainerColor = colors.tint3,
                disabledContentColor = colors.text3,
            ),
        ) {
            // 前景色跟着按钮下发的 ink 走（`contentColor` / `disabledContentColor`）：`AppType.Label`
            // 自带 `text2`，自钉 `onStrong` 又会让禁用态变成近白字压浅底——两态都得由按钮说。
            Text(
                text = if (saving) "保存中…" else "保存计划",
                style = AppType.Label,
                color = LocalContentColor.current,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}


/** 首帧骨架的行数：够填满「选择词书」下方一屏，又不至于让真实列表到位后滚动位置大幅跳动。 */
internal const val WORD_BOOK_PLACEHOLDER_ROWS = 3

/**
 * 一行的最小高：与共享 [PickRow] 的触控下限同一档（原型 `.pick` 自然高 22 + 上下 `var(--s3)`×2 = 46dp，
 * F5-03 抬到 48dp）。
 *
 * 声明为 internal 是给 connected 侧的：骨架行高与真实行的判据必须读生产真值，
 * 不该在测试里另抄一份 `48f` 靠巧合对齐（R-13.1 / m-4）。
 */
internal val WordBookRowMinHeight = 48.dp

/**
 * 选词书的呈现层：原型 `.pick.pick-radio` 的**通栏行列表**（`app.js:587-606` `sBookPicker` 与
 * `app.js:1467-1487` `sOnboarding2` 用的是同一个形状）。
 *
 * **批 1 Task 13-A 改判留因**：批 5 spec §5 那句「选词书 2 列色块卡（域色 tint + `Book` + 书名 + 词数）」
 * 作废（用户 2026-10-29 裁决：原型为准）。原型全篇没有任何词书网格画法——`app.css` 里
 * `grid-template-columns` 0 命中。于是两列的机械形状（`chunked` / `weight(1f)` / 格缝 / 色块锚）整体退役，
 * 逐册一行、行与行之间一条 `line-soft` 细线、首行不画线（`.pick:first-child { border-top: 0 }`）。
 *
 * 不引 `LazyVerticalGrid` 这条**不随版面翻**：本屏整页已是 `Column(verticalScroll)`，竖向滚动容器里嵌
 * 懒网格会被测成无限高约束并抛 `Vertically scrollable component was measured with an infinity maximum
 * height constraints`；词书是十位数以内的固定集合，本来也不需要懒加载。
 *
 * 从 `LearningSetupScreen` 里抽出为可独立渲染的层，是为了让 connected 侧能脱离 ViewModel 的 `load`
 * 派发（在测试宿主里永不执行）直接喂真实行来验通栏几何与长按删除——整屏路径只能停在骨架那一态。
 *
 * 首帧读盘时铺 [WORD_BOOK_PLACEHOLDER_ROWS] 行同形状骨架，真实列表到位后逐册渲染。只在列表为空时用
 * 骨架占位——删除词书后的重载不得把已有列表顶掉闪一次。容器挂 `selectableGroup()`：这一组是单选，
 * TalkBack 靠它把「在册与册之间滑动」讲成一组互斥选项，而不是一串按钮。
 */
@Composable
internal fun WordBookPicker(
    wordBooks: List<WordBook>,
    loading: Boolean,
    selectedId: String?,
    /**
     * 当前**已生效**的活动词书 id（宿主角取 `state.savedWordBookId`，不是屏上的草稿选中位）。
     * 原型 `.row-value`「使用中」按 `b.learnt > 0` 判，而 mock 里「有进度」与「被选中」恰好是同一本
     * （`data.js:10-17`）；真数据里两者会分家，绑进度会把学过但没在用的册念成使用中，绑草稿会在用户
     * 还没保存时就把「使用中」搬到别的册上。三种绑法里只有活动册这一种说的是实话。
     *
     * 刻意不给默认值：漏传等于整屏永远念不出「使用中」，那是一条会静默失效的原型文案。
     */
    activeBookId: String?,
    progressByBook: Map<String, com.example.englishlearning.learning.WordBookProgress>,
    progressLoading: Boolean,
    canDelete: (String) -> Boolean,
    onSelect: (String) -> Unit,
    onRequestDelete: (WordBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
    ) {
        if (loading && wordBooks.isEmpty()) {
            repeat(WORD_BOOK_PLACEHOLDER_ROWS) { index ->
                WordBookRowSkeleton(
                    // 骨架与真实行同一套形状与线序：首行不画线，回填时线不许从别处冒出来。
                    tag = "wordbook_row_skeleton_$index",
                    topDivider = index > 0,
                )
            }
        } else {
            wordBooks.forEachIndexed { index, book ->
                key(book.id) {
                    WordBookPickRow(
                        book = book,
                        selected = book.id == selectedId,
                        active = book.id == activeBookId,
                        progress = progressByBook[book.id],
                        progressLoading = progressLoading,
                        // 内置册 deletable 为 false：行上既没有长按，也没有「删除词书」自定义动作——
                        // 从结构上保证内置册没有被删的可能，不靠运行时拦一下。
                        deletable = canDelete(book.id),
                        topDivider = index > 0,
                        onSelect = { onSelect(book.id) },
                        onRequestDelete = { onRequestDelete(book) },
                    )
                }
            }
        }
    }
}

/**
 * 一行词书（原型 `.pick.pick-radio`）：22dp 圆标记 + `.row-title` 书名 + `.row-sub` 词量与进度 +
 * 可选 `.row-value`「使用中」。
 *
 * 形状全部交共享 [PickRow]，本层只负责「这一行说什么」三件事：
 *  1. **副行是唯一的数据行**：`4308 词 · 已学 3` / `4308 词 · 未开始`，逐字取 `app.js:595`。
 *     词量取册元数据（`book.totalWords`），首发布列表时就在手里，绝不挂在 progress 上——否则
 *     「进度计算中」的册连一共有多少词都念不出来（`WordBookCardProgressContractTest` 规则 1）。
 *     进度三种状态分开说，降级态替换的是「· 已学 M」那一段，绝不写成「已学 0」（本地优先：降级要
 *     如实呈现）。批 5 色块那句「共 N 词」的「共」是原型没有的修饰，随色块一起退役。
 *  2. **等级行撤下**（CET-4 / 博士）：原型 `.pick` 只有 title + sub + value 三档，没有第四行。
 *     等级还在词书详情屏与导入流程里念得到，不在这儿补。
 *  3. **进度条撤下**：通栏行里塞一条 `LinearProgressIndicator` 会把行高顶起来，原型没有这一档；
 *     「已学 N」的文字已经承担同一件事。
 *
 * 删除入口是**长按**（原型这一档没画删除，本仓的删除也只有这里，撤掉就砍掉唯一路径；用户 2026-10-29
 * 裁定沿用长按、不加原型没有的控件）。长按与读屏自定义动作都由 [PickRow] 挂在同一个语义节点上
 * （终审 A 的 I-A2：手势、`contentDescription`、`stateDescription`、`customActions`、testTag 不得分家），
 * 本层因此不许自己再挂 `combinedClickable` / `selectable`——第二枚可点面会让手势与语义各走各的。
 *
 * 顶部分隔线归宿主：[topDivider] 由调用方按行序给（原型 `:first-child` 无线），画在行**外侧**、全宽。
 */
@Composable
private fun WordBookPickRow(
    book: WordBook,
    selected: Boolean,
    /** 是不是当前活动词书 → 原型 `.row-value` 那句「使用中」（绑法理由见 [WordBookPicker]）。 */
    active: Boolean,
    progress: com.example.englishlearning.learning.WordBookProgress?,
    /**
     * 整页进度回填是否还在进行：用来区分「还在算」与「这一本算不出来」两种 `progress == null`。
     * 刻意不给默认值——漏传会把「还在算」念成「暂时读不到进度」，那是个会说谎的降级标签。
     */
    progressLoading: Boolean,
    deletable: Boolean,
    topDivider: Boolean,
    onSelect: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    // 副行后半段：四档形状各写各的，`已学 0` 那种合并写法在这儿会当场说谎。
    val learned = when {
        progress != null && progress.learned > 0 -> "已学 ${progress.learned}"
        progress != null -> "未开始"
        progressLoading -> "进度计算中"
        else -> "暂时读不到进度"
    }
    PickRow(
        label = book.displayName,
        subtitle = "${book.totalWords} 词 · $learned",
        checked = selected,
        radio = true,
        trailing = if (active) "使用中" else null,
        trailingTag = if (active) "wordbook_active_${book.id}" else null,
        contentDescription = "选择词书 ${book.displayName}",
        onClickLabel = "选择词书",
        secondaryActionLabel = if (deletable) "删除词书" else null,
        onSecondaryAction = if (deletable) onRequestDelete else null,
        modifier = modifier
            .fillMaxWidth()
            // testTag 挂在 `padding` **之前**（本轮是行内侧的 12dp 上下内距）：语义节点要覆盖整行，
            // 否则 connected 侧量到的 bounds 只有内容区，通栏宽度与行高断言会整体缩水一圈。
            .testTag("wordbook_pick_${book.id}")
            .then(hairlineTop(if (topDivider) colors.outlineSoft else null, startIndent = 0.dp)),
        // 单选行的语义是「这一点把这本勾上」，`onCheckedChange` 给的是新布尔值；
        // 宿主只关心「哪一本被点」，故忽略布尔值直接转给 onSelect（取消选中不是一条路：活动册不许为空）。
        onCheckedChange = { onSelect() },
    )
}

/**
 * 一行的首帧骨架：与 [WordBookPickRow] 同形状、同线序，但不写任何数字、也不画任何文案。
 *
 * 骨架里出现「0 词」是最容易被写进去的假信号（F1-01：首帧不许放假数字），所以这里只有形状。
 * 与真实行同样的是：22dp 标记盒、12dp 间隙、上下 12dp、`heightIn(min = WordBookRowMinHeight)`、
 * 首行不画线；不一样的是它没有可点面（骨架不是入口，读屏也不该聚焦它）。
 */
@Composable
private fun WordBookRowSkeleton(
    tag: String,
    topDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 线与真实行同序、同宽度，但它挂在 testTag **外侧**：`hairlineTop` 的 1dp 顶内距会裁掉
            // 内侧语义节点的 bounds（`PaddingModifier.semanticsBounds`），真实行因为 `PickRow` 里那条
            // 更靠内的 `semantics{}` 也被裁掉 1dp，骨架这一支没有内层语义节点——不把它放在外侧，
            // 骨架量到的行框就比真实行**多出** 1dp，「骨架与真实行同形状」这条判据会自己骗自己。
            .then(hairlineTop(if (topDivider) colors.outlineSoft else null, startIndent = 0.dp))
            .testTag(tag)
            .heightIn(min = WordBookRowMinHeight)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(colors.outlineSoft.copy(alpha = 0.30f), CircleShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(0.42f)
                    .height(12.dp)
                    .background(colors.outlineSoft.copy(alpha = 0.22f), AppShape.Icon),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.24f)
                    .height(10.dp)
                    .background(colors.outlineSoft.copy(alpha = 0.22f), AppShape.Icon),
            )
        }
    }
}

/**
 * 一行开关（原型 `.row`：`.row-title` 15px + `.row-sub` 12px / ink-3 + `.switch` 46×28 药片）。
 *
 * 开关交共享 [AppSwitch]：轨道色由组件统一承担（开 `brandDeep` / 关 `surface2`，正是原型
 * `.switch.on` 与 `.switch` 的两档），批 5 那套「屏侧深套三元式自调 `checkedTrackColor`」在这一行退役——
 * 换的是控件本身，不是把自调色再调一次；台账里本屏的轨道站点因此只剩「复习上限」那枚滑杆。
 *
 * 行不自带卡壳（外层已是 [AppCard]，卡里套卡就是「卡片汤」），组内多行靠一条 [hairlineTop] 分开；
 * 行高仍留 56dp 下限，保可点区与 2× 字号下的换行余量。
 * `contentDescription = label` 是 F5-03 的既有要求：裸开关在 TalkBack 里只会念「开 / 关」。
 */
@Composable
private fun DetailToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    topDivider: Boolean = false,
) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(hairlineTop(if (topDivider) colors.outlineSoft else null))
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = AppType.Body, color = colors.text1)
            Text(description, style = AppType.Caption, color = colors.text3)
        }
        AppSwitch(
            checked = checked,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}
