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
            var showSetup by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showLearning by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showReading by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showReadingHistory by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showLearningTools by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showWorksheetSettings by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            var showWorksheetPreview by rememberSaveable(state.profile.id) { mutableStateOf(false) }
            LaunchedEffect(state.profile.id) { todayPlanViewModel.load(state.profile.id) }
            val todayState by todayPlanViewModel.uiState.collectAsState()
            val setupRequired = todayState == TodayPlanUiState.MissingSetup
            // Only an user-opened setup screen may be dismissed; a mandatory setup (no active
            // word book yet) must stay until a word book is saved, so it gets no escape hatch.
            val cancelSetup: (() -> Unit)? = if (showSetup && !setupRequired) {
                { showSetup = false }
            } else {
                null
            }
            BackHandler(enabled = cancelSetup != null) { cancelSetup?.invoke() }
            // Single exit for the learning flow. It always returns to the today page *and*
            // recomputes its state (F1-04: entering the today page recalculates progress), so
            // work reviewed in the cards shows immediately instead of only after a restart.
            val exitLearning: () -> Unit = {
                showLearning = false
                todayPlanViewModel.load(state.profile.id)
            }
            // Leaving the learning flow is always allowed; unsubmitted cards simply stay open.
            BackHandler(enabled = showLearning) { exitLearning() }
            BackHandler(enabled = showReading) { showReading = false }
            BackHandler(enabled = showReadingHistory) { showReadingHistory = false }
            BackHandler(enabled = showWorksheetPreview) { showWorksheetPreview = false }
            BackHandler(enabled = showWorksheetSettings) { showWorksheetSettings = false }
            BackHandler(enabled = showLearningTools) { showLearningTools = false }
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
                val worksheetState = worksheetViewModel?.uiState?.collectAsState()?.value as? WorksheetUiState.Preview
                val context = androidx.compose.ui.platform.LocalContext.current
                val renderedFile = worksheetState?.rendered?.file
                LaunchedEffect(renderedFile) {
                    renderedFile?.let { context.startActivity(WorksheetShareLauncher(context).createChooser(it)) }
                }
                WorksheetPreviewScreen(
                    pages = worksheetState?.pages.orEmpty(),
                    onBack = { showWorksheetPreview = false },
                    onExport = { worksheetViewModel?.exportPreview() },
                )
            } else if (showWorksheetSettings) {
                val worksheetState = worksheetViewModel?.uiState?.collectAsState()?.value
                val ready = worksheetState as? WorksheetUiState.Ready
                WorksheetSettingsScreen(
                    settings = ready?.settings ?: com.example.englishlearning.learning.worksheet.WorksheetSettings(),
                    selectedCount = ready?.source?.items?.size ?: 0,
                    onToggleDirection = { worksheetViewModel?.toggleDirection(it) },
                    onSelectRange = { worksheetViewModel?.selectRange(it) },
                    onSelectTemplate = { worksheetViewModel?.selectTemplate(it) },
                    onToggleGrid = { worksheetViewModel?.toggleGrid(it) },
                    onToggleAnswers = { worksheetViewModel?.toggleAnswers(it) },
                    onPreview = {
                        worksheetViewModel?.preview()
                        if (worksheetViewModel?.uiState?.value is WorksheetUiState.Preview) showWorksheetPreview = true
                    },
                    onBack = { showWorksheetSettings = false },
                )
            } else if (showLearningTools) {
                LearningToolsScreen(
                    onBack = { showLearningTools = false },
                    onOpenWorksheet = {
                        worksheetViewModel?.load(state.profile.id)
                        showWorksheetSettings = worksheetViewModel != null
                    },
                )
            } else if (showReading && readingAccessViewModel != null) {
                val readingState by readingAccessViewModel.uiState.collectAsState()
                if (showReadingHistory) {
                    val history = (readingState as? ReadingAccessUiState.Ready)?.history.orEmpty()
                    ReadingHistoryScreen(history = history, onBack = { showReadingHistory = false })
                } else {
                    ReadingAccessScreen(
                        state = readingState,
                        onBack = { showReading = false },
                        onSelectType = readingAccessViewModel::selectType,
                        onOpenHistory = { showReadingHistory = true },
                    )
                }
            } else {
                TodayPlanScreen(
                    state = todayState,
                    onRetry = { todayPlanViewModel.load(state.profile.id) },
                    onOpenSetup = { showSetup = true },
                    onStartLearning = {
                        wordCardViewModel.load(state.profile.id)
                        showLearning = true
                    },
                    onOpenReading = {
                        val ready = todayState as? TodayPlanUiState.Ready ?: return@TodayPlanScreen
                        readingAccessViewModel?.load(state.profile.id, ready.isUnlocked, ready.unlockReason)
                        showReading = readingAccessViewModel != null
                    },
                    onOpenLearningTools = { showLearningTools = true },
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
                modifier = Modifier.semantics { contentDescription = "返回今日计划" },
            ) { Text("← 返回今日计划", fontWeight = FontWeight.Bold) }
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
