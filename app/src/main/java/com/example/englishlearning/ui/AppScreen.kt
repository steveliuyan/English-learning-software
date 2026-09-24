package com.example.englishlearning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
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

private val AppleMintGradient = Brush.linearGradient(listOf(AppleMintStart, AppleMintMiddle, AppleMintEnd))

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
) {
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
            // 阅读来源的二级层：导入页是显式导航；文章页由 readingTarget 驱动；词卡详情是
            // 阅读页之上的本地覆盖层（WordCardViewModel 的详情只服务学习流，不复用）。
            var showArticleImport by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var selectedArticleCard by remember { mutableStateOf<com.example.englishlearning.learning.domain.WordCard?>(null) }
            val selectedFeature = AiFeature.entries.firstOrNull { it.key == selectedFeatureKey }
            LaunchedEffect(state.profile.id) { todayPlanViewModel.load(state.profile.id) }
            // AI 配置是设备级的（与学习者无关），启动时读一次，好让「设置」栏的摘要和「AI 学」页
            // 的徽章说的是本机真实状态，而不是写死的假设。
            LaunchedEffect(state.profile.id) { aiProfileViewModel?.load() }
            val aiProfiles = aiProfileViewModel?.listState?.collectAsState()?.value as? AiProfileListUiState.Ready
            val aiConfigured = aiProfiles?.items?.any { it.hasKey } == true
            val todayState by todayPlanViewModel.uiState.collectAsState()
            val todayReady = todayState as? TodayPlanUiState.Ready
            val setupRequired = todayState == TodayPlanUiState.MissingSetup
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
                showLearning = false
                todayPlanViewModel.load(state.profile.id)
            }
            val overlayOpen = setupRequired || showSetup || showLearning || showReadingHistory ||
                showWorksheetSettings || showWorksheetPreview || selectedFeature != null || showAiProfiles ||
                showArticleImport || readingTarget != null || selectedArticleCard != null
            // BackHandler 按「后声明者优先」分派，所以下面严格按优先级从低到高排列：层级越靠内
            // 越晚声明，越先拿到返回键。调整顺序会直接改变返回键行为，别随手重排。
            BackHandler(enabled = !overlayOpen && selectedTab != AppTab.LEARNING) {
                selectedTab = AppTab.LEARNING
            }
            BackHandler(enabled = cancelSetup != null) { cancelSetup?.invoke() }
            // Leaving the learning flow is always allowed; unsubmitted cards simply stay open.
            BackHandler(enabled = showLearning) { exitLearning() }
            BackHandler(enabled = showReadingHistory) { showReadingHistory = false }
            // 阅读来源的层级从浅到深：导入页 → 文章页 → 点词的词卡详情。
            // 声明顺序即优先级（后声明者先拿返回键），别随手重排。
            BackHandler(enabled = showArticleImport) { showArticleImport = false }
            BackHandler(enabled = readingTarget != null) { readingAccessViewModel?.closeArticle() }
            BackHandler(enabled = selectedArticleCard != null) { selectedArticleCard = null }
            // 设置页先声明、预览页后声明：两者同时为真时（从设置页点进预览）返回键要先关预览。
            BackHandler(enabled = showWorksheetSettings) { showWorksheetSettings = false }
            BackHandler(enabled = showWorksheetPreview) {
                worksheetViewModel?.dismissPreview()
                showWorksheetPreview = false
            }
            // 这一层内部还有「列表 / 编辑」两态，编辑态会自己再声明一条更靠后的 BackHandler，
            // 因此从编辑页按返回键先关编辑页，而不是直接退掉整层。
            BackHandler(enabled = showAiProfiles) { showAiProfiles = false }
            BackHandler(enabled = selectedFeature != null) { selectedFeatureKey = null }
            if (setupRequired || showSetup) {
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
                val detailCard by wordCardViewModel.detailCard.collectAsState()
                // The detail page is an overlay inside the learning branch: it leaves the
                // showLearning state machine untouched, and returning from it (clearDetail) drops
                // the user back onto the same card / normal learning state.
                Box(modifier = Modifier.fillMaxSize()) {
                    WordCardScreen(
                        state = cardState,
                        onSubmit = wordCardViewModel::submit,
                        onRetry = { wordCardViewModel.load(state.profile.id) },
                        onBackToPlan = exitLearning,
                    )
                    if (detailCard != null) {
                        CardDetailScreen(
                            card = detailCard!!,
                            onBack = wordCardViewModel::clearDetail,
                        )
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
                    onBack = { showAiProfiles = false },
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
            } else if (showReadingHistory) {
                val history = (readingState as? ReadingAccessUiState.Ready)?.history.orEmpty()
                ReadingHistoryScreen(history = history, onBack = { showReadingHistory = false })
            } else if (showArticleImport) {
                val importState = (readingState as? ReadingAccessUiState.Ready)?.import ?: ImportUiState()
                ArticleImportScreen(
                    state = importState,
                    onTitleChange = { readingAccessViewModel?.importTitleChange(it) },
                    onBodyChange = { readingAccessViewModel?.importBodyChange(it) },
                    onImport = { readingAccessViewModel?.submitImport() },
                    onBack = { showArticleImport = false },
                )
            } else if (readingTarget != null) {
                val articleState = articleReadingViewModel?.uiState?.collectAsState()?.value
                Box(Modifier.fillMaxSize()) {
                    ArticleReadingScreen(
                        state = articleState,
                        onBack = { readingAccessViewModel?.closeArticle() },
                        onOpenCard = { selectedArticleCard = it },
                        onModeChange = { articleReadingViewModel?.setMode(it) },
                        onToggleTranslation = { articleReadingViewModel?.toggleTranslation() },
                        onOpenDictionaryPlaceholder = {},
                        onOpenPronunciationPlaceholder = {},
                    )
                    // 点词的详情是阅读页之上的覆盖层，不动 readingTarget 的状态机。
                    selectedArticleCard?.let { card ->
                        CardDetailScreen(card = card, onBack = { selectedArticleCard = null })
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
                                onOpenFeature = { selectedFeatureKey = it.key },
                                onOpenWordList = { selectedTab = AppTab.LEARNING },
                            )
                            AppTab.SETTINGS -> SettingsScreen(
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
                                aiProfileSubtitle = aiProfiles?.let { ready ->
                                    if (ready.items.isEmpty()) {
                                        "尚未添加，点这里添加第一套 OpenAI 兼容服务"
                                    } else {
                                        "已配置 ${ready.items.size} 套 · ${ready.items.count { it.hasKey }} 套已设置密钥"
                                    }
                                },
                            )
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
            WordBookCard(
                book = book,
                selected = book.id == state.selectedWordBookId,
                onSelect = { viewModel.selectWordBook(book.id) },
            )
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
        Button(
            onClick = { viewModel.save(profileId) },
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
            Text("保存并开始学习", fontWeight = FontWeight.Bold)
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

@Composable
private fun WordBookCard(book: WordBook, selected: Boolean, onSelect: () -> Unit) {
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
    val cardScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.985f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "wordBookScale",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
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
