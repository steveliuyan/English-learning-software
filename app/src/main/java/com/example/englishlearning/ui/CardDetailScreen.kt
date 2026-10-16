package com.example.englishlearning.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.wordqa.WordAiNote
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.DomainColors
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import com.example.englishlearning.ui.theme.MintTint

/**
 * Static, read-only detail view for a single word card (spec F1-06).
 *
 * Every content module renders only when its data is present. A blank field hides
 * the whole module: no empty heading, no "暂无" placeholder (AC1-12).
 *
 * 阅读顺序：单词卡（音标、发音、全部词性释义、操作）→ 配图 → 例句 → 派生词 / 短语 / 近义词 / 词形变化。
 *
 * The screen stays UI-only: it receives a [WordCard] and explicit callbacks for navigation,
 * pronunciation and relearning. Storage, provider selection and learning state remain outside
 * the composable.
 */
@Composable
fun CardDetailScreen(
    card: WordCard,
    onBack: () -> Unit,
    onSpeak: () -> Unit = {},
    onSpeakBritish: (() -> Unit)? = null,
    onRelearn: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    vocabularyPresent: Boolean? = null,
    onToggleVocabulary: (() -> Unit)? = null,
    // F4-01 决策⑦收藏开关：与生词本开关同款——宿主给足「状态+回调」才出现，UI 不触存储。
    favoritePresent: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    // 安全边界：详情页只收无字段的发音状态枚举，固定文案由本屏映射——
    // 任意字符串（含潜在敏感内容）无法从调用方塞进详情页。
    pronunciationStatus: PronunciationStatus = PronunciationStatus.Idle,
    // F3-02 四控：详情页是「一份 UI、五种宿主」，播控组只给学习流详情 / 记录详情 / 阅读详情三处
    // （决策 §2：收藏浮层与查词/搜索卡是一次性浏览发音，不加控件组）。宿主显式点头才渲染，
    // 否则默认空 lambda 的「停止」会变成点了没反应的死按钮。
    pronunciationControlsEnabled: Boolean = false,
    // 裁决 B2：暂停/继续的呈现条件只看宿主的引擎选择，不看 pausePlayback() 返回值。
    pauseResumeSupported: Boolean = false,
    onPauseResume: () -> Unit = {},
    onStopPronunciation: () -> Unit = {},
    onAskAi: (() -> Unit)? = null,
    notes: List<WordAiNote> = emptyList(),
    onSavePersonalNote: ((String) -> Unit)? = null,
    onDeleteNote: ((String) -> Unit)? = null,
    personalNoteSaveStatus: PersonalNoteSaveStatus = PersonalNoteSaveStatus.Idle,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("card_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .testTag("card_detail_back")
                    .semantics { contentDescription = "返回" },
                colors = ButtonDefaults.textButtonColors(contentColor = MintPrimaryDark),
            ) { Text("←", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            Text(
                text = "词义详情",
                style = AppType.Headline,
                modifier = Modifier.semantics { heading() },
            )
        }

        WordHeroCard(
            card = card,
            pronunciationStatus = pronunciationStatus,
            pronunciationControlsEnabled = pronunciationControlsEnabled,
            pauseResumeSupported = pauseResumeSupported,
            onPauseResume = onPauseResume,
            onStopPronunciation = onStopPronunciation,
            onSpeak = onSpeak,
            onSpeakBritish = onSpeakBritish,
            onRelearn = onRelearn,
            onNext = onNext,
            vocabularyPresent = vocabularyPresent,
            onToggleVocabulary = onToggleVocabulary,
            favoritePresent = favoritePresent,
            onToggleFavorite = onToggleFavorite,
            onAskAi = onAskAi,
        )

        // 配图优先用词书包导入的真实文件（`imagePath`）；没有才回退到内置 drawable 映射。
        // 两条路都渲染在同一种卡片里，界面看不出差别。
        val importedImage = card.imagePath
        if (!importedImage.isNullOrBlank()) {
            ImportedIllustration(lemma = card.lemma, path = importedImage)
        } else {
            lemmaToDrawableRes(card.lemma)?.let { res ->
                DetailCard {
                    Image(
                        painter = painterResource(res),
                        contentDescription = "说明图 ${card.lemma}",
                        contentScale = ContentScale.Crop,
                        modifier = illustrationModifier.testTag("card_detail_illustration"),
                    )
                }
            }
        }

        val example = card.example?.takeIf { it.isNotBlank() }
        val exampleZh = card.exampleZh?.takeIf { it.isNotBlank() }
        if (example != null || exampleZh != null) {
            DetailSection(title = "例句") {
                example?.let { sentence ->
                    Text(
                        text = highlightLemma(sentence, card.lemma),
                        style = AppType.Body,
                        modifier = Modifier
                            .testTag("card_detail_example")
                            .semantics { contentDescription = "例句 $sentence" },
                    )
                }
                exampleZh?.let { translation ->
                    Text(
                        text = translation,
                        style = AppType.Footnote,
                        modifier = Modifier
                            .testTag("card_detail_example_zh")
                            .semantics { contentDescription = "例句译文 $translation" },
                    )
                }
            }
        }

        // 我的笔记展示栏就在例句框下方（2026-10-13 真机反馈第二轮）：
        // 打开词就能看到笔记内容；添加/删除仍收在弹窗里，页面不噪。
        if (notes.isNotEmpty() || onSavePersonalNote != null) {
            var showNotesDialog by remember { mutableStateOf(false) }
            DetailSection(title = "我的笔记", tag = "card_detail_notes_section") {
                if (notes.isEmpty()) {
                    Text(
                        text = "还没有笔记。点「添加 / 管理」记第一条。",
                        style = AppType.Footnote,
                    )
                } else {
                    notes.forEach { note ->
                        Text(
                            text = note.answer,
                            style = AppType.Body,
                            modifier = Modifier
                                .testTag("card_detail_note_display_${note.noteId}")
                                .semantics { contentDescription = "笔记 ${note.answer}" },
                        )
                    }
                }
                PillButton(
                    label = "添加 / 管理",
                    description = "管理我的笔记",
                    onClick = { showNotesDialog = true },
                    modifier = Modifier.testTag("card_detail_notes_manage"),
                )
            }
            if (showNotesDialog) {
                PersonalNotesDialog(
                    notes = notes,
                    onSave = onSavePersonalNote,
                    onDelete = onDeleteNote,
                    saveStatus = personalNoteSaveStatus,
                    onDismiss = { showNotesDialog = false },
                )
            }
        }

        if (card.derived.isNotEmpty()) {
            DetailSection(title = "派生词", tag = "card_detail_derived") {
                card.derived.forEach { RelationLine(it.lemma, it.partOfSpeech, it.meaningZh) }
            }
        }

        if (card.phrases.isNotEmpty()) {
            DetailSection(title = "关联短语", tag = "card_detail_phrases") {
                card.phrases.forEach { RelationLine(it.text, null, it.meaningZh) }
            }
        }

        if (card.synonyms.isNotEmpty()) {
            DetailSection(title = "近义词", tag = "card_detail_synonyms") {
                card.synonyms.forEach { RelationLine(it.lemma, it.partOfSpeech, it.meaningZh) }
            }
        }

        if (card.inflections.isNotEmpty()) {
            DetailSection(title = "词形变化", tag = "card_detail_inflections") {
                Text(text = card.inflections.joinToString("、"), style = AppType.Body)
            }
        }
    }
}

/** 单词卡：单词、音标 + 发音、全部词性释义、重新学习 / 问 AI。 */
@Composable
private fun WordHeroCard(
    card: WordCard,
    pronunciationStatus: PronunciationStatus,
    pronunciationControlsEnabled: Boolean,
    pauseResumeSupported: Boolean,
    onPauseResume: () -> Unit,
    onStopPronunciation: () -> Unit,
    onSpeak: () -> Unit,
    onSpeakBritish: (() -> Unit)?,
    onRelearn: (() -> Unit)?,
    onNext: (() -> Unit)?,
    vocabularyPresent: Boolean?,
    onToggleVocabulary: (() -> Unit)?,
    favoritePresent: Boolean?,
    onToggleFavorite: (() -> Unit)?,
    onAskAi: (() -> Unit)?,
) {
    DetailCard {
        Text(
            text = card.lemma,
            style = AppType.Display.copy(fontSize = 36.sp),
            modifier = Modifier
                .testTag("card_detail_lemma")
                .semantics { contentDescription = card.lemma },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (card.ipa.isNotBlank()) {
                Text(
                    text = card.ipa,
                    style = AppType.Body.copy(color = MintTextMuted),
                    modifier = Modifier
                        .testTag("card_detail_ipa")
                        .semantics { contentDescription = "音标 ${card.ipa}" },
                )
            }
            Spacer(Modifier.width(4.dp))
            // 发音入口：喇叭 + 口音标注。默认美式；英式仅在调用方提供时出现。
            AccentSpeakerButton(
                accentLabel = "美",
                accentDescription = "播放 ${card.lemma} 发音",
                tag = "card_detail_speak",
                onClick = onSpeak,
            )
            onSpeakBritish?.let { speakBritish ->
                AccentSpeakerButton(
                    accentLabel = "英",
                    accentDescription = "播放 ${card.lemma} 英式发音",
                    tag = "card_detail_speak_british",
                    onClick = speakBritish,
                )
            }
        }
        // F5-03 决策 §4（Task 10 携带的 T9 复评 N-4，口径同阅读页 `article_status_live_region`）：
        // 发音状态文字必须由**常驻 live region 容器**承载，靠子节点进出触发播报
        // （Task 6 判例 WordCardScreen.word_card_status_live_region）。
        // ①tag `card_detail_pronunciation_message` 留在瞬时 Text 上（既有引用不破）；
        // ②liveRegion 只在容器上，瞬时子节点不得再带；③播控 Row 保持在容器之外（B1 口径）。
        // 已知代价：容器空态仍占外层 spacedBy 一份幽灵间距——与阅读页同形同代价，归 UI 重设计轮。
        Column(
            modifier = Modifier
                .testTag("card_detail_status_live_region")
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            pronunciationStatus.message()?.let { message ->
                Text(
                    text = message,
                    style = AppType.Footnote,
                    modifier = Modifier
                        .testTag("card_detail_pronunciation_message")
                        .semantics { contentDescription = message },
                )
            }
        }
        // F3-02 播控组（与词卡同款 OutlinedButton 形态、同一呈现窗口 Playing/Paused）：
        // 只出现在宿主点头的三处详情（学习流 / 记录 / 阅读），收藏浮层与搜索卡详情保持纯浏览。
        val speakingOrPaused =
            pronunciationStatus == PronunciationStatus.Playing ||
            pronunciationStatus == PronunciationStatus.Paused
        if (pronunciationControlsEnabled && speakingOrPaused) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pauseResumeSupported) {
                    OutlinedButton(
                        onClick = onPauseResume,
                        modifier = Modifier
                            .testTag("card_detail_pronunciation_toggle")
                            .semantics { contentDescription = if (pronunciationStatus == PronunciationStatus.Paused) "继续播放发音" else "暂停发音" },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MintPrimaryDark),
                    ) { Text(if (pronunciationStatus == PronunciationStatus.Paused) "继续" else "暂停") }
                }
                OutlinedButton(
                    onClick = onStopPronunciation,
                    modifier = Modifier
                        .testTag("card_detail_pronunciation_stop")
                        .semantics { contentDescription = "停止播放发音" },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MintPrimaryDark),
                ) { Text("停止") }
            }
        }

        // 有多词性释义时逐条列出（替代原先顶部那一行重复的首条释义）；占位词卡只有一条。
        if (card.senses.isNotEmpty()) {
            Column(Modifier.testTag("card_detail_senses"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                card.senses.forEach { SenseRow(it.partOfSpeech, it.meaningZh) }
            }
        } else if (card.meaningZh.isNotBlank() || card.partOfSpeech.isNotBlank()) {
            SenseRow(
                partOfSpeech = card.partOfSpeech,
                meaningZh = card.meaningZh,
                posModifier = Modifier
                    .testTag("card_detail_part_of_speech")
                    .semantics { contentDescription = "词性 ${card.partOfSpeech}" },
                meaningModifier = Modifier
                    .testTag("card_detail_meaning")
                    .semantics { contentDescription = "释义 ${card.meaningZh}" },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (favoritePresent != null && onToggleFavorite != null) {
                PillButton(
                    label = if (favoritePresent) "★ 已收藏" else "☆ 收藏",
                    description = if (favoritePresent) "取消收藏 ${card.lemma}" else "收藏 ${card.lemma}",
                    onClick = onToggleFavorite,
                    modifier = Modifier.testTag("card_detail_favorite_toggle"),
                )
            }
            if (vocabularyPresent != null && onToggleVocabulary != null) {
                PillButton(
                    label = if (vocabularyPresent) "移出生词本" else "加入生词本",
                    description = if (vocabularyPresent) "移出 ${card.lemma} 生词本" else "加入 ${card.lemma} 生词本",
                    onClick = onToggleVocabulary,
                    modifier = Modifier.testTag("card_detail_vocabulary_toggle"),
                )
            }
            onRelearn?.let { callback ->
                PillButton(label = "重新学习", description = "重新学习 ${card.lemma}", onClick = callback)
            }
            onNext?.let { callback ->
                PillButton(
                    label = "下一个单词",
                    description = "查看下一个单词",
                    onClick = callback,
                    modifier = Modifier.testTag("card_detail_next"),
                )
            }
            onAskAi?.let { callback ->
                PillButton(
                    label = "问 AI",
                    description = "问 AI（${card.lemma}）",
                    onClick = callback,
                    modifier = Modifier.testTag("card_detail_ask_ai"),
                )
            }
        }
    }
}

/** 一条词性释义：词性做成浅绿小标签（深色字，保证对比度），释义用正文深色。 */
@Composable
private fun PersonalNoteEditor(
    onSave: (String) -> Unit,
    saveStatus: PersonalNoteSaveStatus,
) {
    var draft by remember { androidx.compose.runtime.mutableStateOf("") }
    LaunchedEffect(saveStatus) {
        if (saveStatus == PersonalNoteSaveStatus.Saved) draft = ""
    }
    androidx.compose.material3.OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text("添加个人笔记") },
        placeholder = { Text("记录记忆方法、易错点或例句") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth().testTag("card_detail_personal_note_input"),
    )
    TextButton(
        onClick = { onSave(draft.trim()) },
        enabled = draft.isNotBlank() && saveStatus != PersonalNoteSaveStatus.Saving,
        modifier = Modifier.testTag("card_detail_personal_note_save"),
    ) {
        Text(
            when (saveStatus) {
                PersonalNoteSaveStatus.Saving -> "保存中…"
                PersonalNoteSaveStatus.Saved -> "已保存笔记"
                else -> "保存笔记"
            },
        )
    }
    when (saveStatus) {
        PersonalNoteSaveStatus.Failed -> Text(
            "笔记保存失败，请重试。",
            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("card_detail_personal_note_save_failed"),
        )
        PersonalNoteSaveStatus.Saved -> Text(
            "笔记已保存。",
            color = MintPrimaryDark,
            modifier = Modifier.testTag("card_detail_personal_note_saved"),
        )
        else -> Unit
    }
}

/**
 * 我的笔记弹窗：列表 + 逐条删除 + 添加，全部收在这一层；
 * 由例句下方的展示栏「添加 / 管理」按钮打开（真机反馈两轮：笔记内容要在页面直接可见，
 * 但增删集中在弹窗，页面不噪）。
 */
@Composable
private fun PersonalNotesDialog(
    notes: List<WordAiNote>,
    onSave: ((String) -> Unit)?,
    onDelete: ((String) -> Unit)?,
    saveStatus: PersonalNoteSaveStatus,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("我的笔记", style = AppType.Title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (notes.isEmpty()) {
                    Text(
                        text = "还没有笔记。可以记下记忆方法、易错点或例句。",
                        style = AppType.Body,
                        color = MintTextMuted,
                        modifier = Modifier.testTag("card_detail_notes_empty"),
                    )
                }
                notes.forEach { note ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_detail_note_${note.noteId}"),
                    ) {
                        Text(note.answer, style = AppType.Body)
                        onDelete?.let { remove ->
                            TextButton(
                                onClick = { remove(note.noteId) },
                                modifier = Modifier
                                    .testTag("card_detail_note_delete_${note.noteId}")
                                    .semantics { contentDescription = "删除这条笔记" },
                            ) {
                                Text("删除", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                onSave?.let { save ->
                    PersonalNoteEditor(onSave = save, saveStatus = saveStatus)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("card_detail_notes_dialog_close")) {
                Text("关闭", fontWeight = FontWeight.Bold)
            }
        },
    )
}

/**
 * 发音按钮：Material 风格喇叭（自绘，24dp）+ 口音标注字（美/英）。
 *
 * 不引 material-icons 库（依赖锁定决策见 AppBottomBar 注释），喇叭按 Material Icons
 * `volume_up` 的几何比例自绘：喇叭主体 + 两道声波弧线。
 */
@Composable
private fun AccentSpeakerButton(
    accentLabel: String,
    accentDescription: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(AppShape.Pill)
            .background(MintTint)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            // F5-03 / Task 10：美/英喇叭原本只有 ~36dp 高（connected RED 实测 36.19dp），
            // 补到 48dp 触控下限；只扩节点边界，字形与标注字尺寸不变。
            .minimumInteractiveComponentSize()
            .testTag(tag)
            .semantics { contentDescription = accentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SpeakerGlyph()
        Text(
            text = accentLabel,
            style = AppType.Footnote.copy(color = MintPrimaryDark, fontWeight = FontWeight.SemiBold),
        )
    }
}

/** Material `volume_up` 比例的自绘喇叭，24dp 视觉尺寸。 */
@Composable
private fun SpeakerGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        // 喇叭箱体：小矩形 + 向左张开的梯形号角
        val boxLeft = w * 0.14f
        val boxRight = w * 0.34f
        val boxTop = h * 0.40f
        val boxBottom = h * 0.60f
        drawRect(
            color = MintPrimaryDark,
            topLeft = androidx.compose.ui.geometry.Offset(boxLeft, boxTop),
            size = androidx.compose.ui.geometry.Size(boxRight - boxLeft, boxBottom - boxTop),
        )
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(boxRight, boxTop)
                lineTo(w * 0.52f, h * 0.24f)
                lineTo(w * 0.52f, h * 0.76f)
                lineTo(boxRight, boxBottom)
                close()
            },
            color = MintPrimaryDark,
        )
        // 两道声波弧线
        drawArc(
            color = MintPrimaryDark,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.32f),
            size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.36f),
        )
        drawArc(
            color = MintPrimaryDark,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.56f, h * 0.20f),
            size = androidx.compose.ui.geometry.Size(w * 0.36f, h * 0.60f),
        )
    }
}

@Composable
private fun SenseRow(
    partOfSpeech: String,
    meaningZh: String,
    posModifier: Modifier = Modifier,
    meaningModifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        if (partOfSpeech.isNotBlank()) {
            Text(
                text = partOfSpeech,
                style = AppType.Label.copy(color = MintPrimaryDark, fontWeight = FontWeight.SemiBold),
                modifier = posModifier
                    .clip(AppShape.Pill)
                    .background(MintTint)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        if (meaningZh.isNotBlank()) {
            Text(text = meaningZh, style = AppType.Body, modifier = meaningModifier.weight(1f))
        }
    }
}

/** 浅绿底、深色字的胶囊按钮：与薄荷绿主题一致，替代默认的紫色文字按钮。 */
@Composable
private fun PillButton(label: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        shape = AppShape.Pill,
        colors = ButtonDefaults.textButtonColors(containerColor = MintTint, contentColor = MintPrimaryDark),
        modifier = modifier.semantics { contentDescription = description },
    ) { Text(label, style = AppType.Footnote.copy(color = MintPrimaryDark, fontWeight = FontWeight.SemiBold)) }
}

@Composable
private fun DetailCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = AppShape.Card,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, AppShape.Card),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** 例句、派生词、短语、近义词、词形变化共用的小节：一个标题 + 内容。 */
@Composable
private fun DetailSection(title: String, tag: String? = null, content: @Composable ColumnScope.() -> Unit) {
    DetailCard(modifier = if (tag != null) Modifier.testTag(tag) else Modifier) {
        Text(text = title, style = AppType.Title, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** 一行关联词：英文加粗在前，词性浅色小字，中文释义正文色。 */
@Composable
private fun RelationLine(word: String, partOfSpeech: String?, meaningZh: String) {
    val line = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MintPrimaryDark)) { append(word) }
        if (!partOfSpeech.isNullOrBlank()) {
            withStyle(SpanStyle(color = MintTextMuted, fontSize = 13.sp)) { append("  $partOfSpeech") }
        }
        append("  $meaningZh")
    }
    Text(text = line, style = AppType.Body)
}

/** 例句里把单词加粗、用品牌深绿标出，其余保持正文样式。 */
private fun highlightLemma(sentence: String, lemma: String): AnnotatedString {
    val range = lemmaRangeIn(sentence, lemma) ?: return AnnotatedString(sentence)
    return buildAnnotatedString {
        append(sentence.substring(0, range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = DomainColors.AiSpeech.deep)) {
            append(sentence.substring(range.first, range.last + 1))
        }
        append(sentence.substring(range.last + 1))
    }
}

/**
 * 例句中要加粗的单词位置；找不到返回 null。
 *
 * 忽略大小写、只认词首（前一个字符不是字母），所以 `Parents` 能标出 `parent`，
 * 而 `separate` 里的 `rate` 不会被误标。
 */
internal fun lemmaRangeIn(text: String, lemma: String): IntRange? {
    val word = lemma.trim()
    if (word.isEmpty()) return null
    var from = 0
    while (true) {
        val start = text.indexOf(word, from, ignoreCase = true)
        if (start < 0) return null
        if (start == 0 || !text[start - 1].isLetter()) return start until start + word.length
        from = start + 1
    }
}

/** 配图固定 4:3、圆角裁切：一屏内能同时看到单词卡和配图，不再占满整屏。 */
private val illustrationModifier = Modifier
    .fillMaxWidth()
    .aspectRatio(4f / 3f)
    .clip(AppShape.Button)

/**
 * 导入配图的渲染：文件被外部删除或解码失败时**不留空图框**，改为一行可读提示。
 * 与内置图一样，缺图时不该让整个详情页崩掉——词卡的其他内容仍然可用。
 */
@Composable
private fun ImportedIllustration(lemma: String, path: String) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
    if (bitmap == null) {
        Text(
            text = "配图暂时读不出来，可重新导入这册词书。",
            style = AppType.Footnote,
            modifier = Modifier
                .testTag("card_detail_image_unreadable")
                .semantics { contentDescription = "配图读不出来" },
        )
        return
    }
    DetailCard {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "配图 $lemma",
            contentScale = ContentScale.Crop,
            modifier = illustrationModifier.testTag("card_detail_image_file"),
        )
    }
}
