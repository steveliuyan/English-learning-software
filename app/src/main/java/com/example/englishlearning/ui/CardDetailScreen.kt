package com.example.englishlearning.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.SpeakerButton
import com.example.englishlearning.ui.components.SpeakerState
import com.example.englishlearning.ui.components.WordRow
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

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
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("card_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 页头交给统一组件承载（spec §3 页头族）：标题 + 返回位的几何与墨色一处定义，
        // 本屏只传标题和导航回调。
        AppScreenHeader(
            title = "词义详情",
            onBack = onBack,
        )

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

        // 配图不再是通栏大图：72dp 缩略图挂在词头行尾（spec §5），
        // 「有没有配图」这条信息留着，纵向空间还给释义。
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

        // 关联词三节的行归一到共享词行（spec §3「一行 lemma + 释义」族）：私有那一枚行件用
        // 加粗 + 拼字符串把三栏塞进同一枚 Text，词头、词性、释义在读屏与深色模式下都分不开；
        // 词行的四枚槽位墨由组件的墨色纯表给，层级靠 text1 / text2 的落差而不是字重。
        if (card.derived.isNotEmpty()) {
            DetailSection(title = "派生词", tag = "card_detail_derived") {
                card.derived.forEach { WordRow(lemma = it.lemma, gloss = it.meaningZh, meta = it.partOfSpeech) }
            }
        }

        if (card.phrases.isNotEmpty()) {
            DetailSection(title = "关联短语", tag = "card_detail_phrases") {
                card.phrases.forEach { WordRow(lemma = it.text, gloss = it.meaningZh) }
            }
        }

        if (card.synonyms.isNotEmpty()) {
            DetailSection(title = "近义词", tag = "card_detail_synonyms") {
                card.synonyms.forEach { WordRow(lemma = it.lemma, gloss = it.meaningZh, meta = it.partOfSpeech) }
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
    val colors = appColors()
    val illustration = rememberIllustrationSource(card.lemma, card.imagePath)
    DetailCard {
        // 词头行：词 + 72dp 配图缩略图。缩略图只是「有图」的凭证，读屏仍给一句说明。
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = card.lemma,
                style = AppType.Display.copy(fontSize = 36.sp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("card_detail_lemma")
                    .semantics { contentDescription = card.lemma },
            )
            illustration.bitmap?.let { decoded ->
                Image(
                    bitmap = decoded.asImageBitmap(),
                    contentDescription = "配图 ${card.lemma}",
                    contentScale = ContentScale.Crop,
                    modifier = illustrationThumbnailModifier.testTag("card_detail_image_file"),
                )
            }
            illustration.fallbackRes?.let { res ->
                Image(
                    painter = painterResource(res),
                    contentDescription = "说明图 ${card.lemma}",
                    contentScale = ContentScale.Crop,
                    modifier = illustrationThumbnailModifier.testTag("card_detail_illustration"),
                )
            }
        }
        // 文件被外部删掉或解码失败时不留空图框，改一行可读提示（图位在这里干脆不占面积）。
        if (illustration.unreadable) {
            Text(
                text = "配图暂时读不出来，可重新导入这册词书。",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier
                    .testTag("card_detail_image_unreadable")
                    .semantics { contentDescription = "配图读不出来" },
            )
        }
        // 音标 + 美/英发音药片：整行**可换行**（`FlowRow`），不是挤在一行里。
        //
        // 真机取证（批 2 Task 7）：1080×2340 上「音标 + 两枚 `播放发音` 药片」的自然宽度超出卡内
        // 可用宽度约 27dp，`Row` 的度量方式是按顺序扣减剩余空间，最后那枚英式药片被压到只剩字宽，
        // 「播放发音」竖排成一列乱码。换成 `FlowRow` 后每枚药片量自然尺寸，放不下就整枚挪到下一行——
        // 2.0 字号下同理（一片一行），只换行、不压扁、不裁字。
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (card.ipa.isNotBlank()) {
                Text(
                    text = card.ipa,
                    style = AppType.Body.copy(color = colors.text2),
                    modifier = Modifier
                        .testTag("card_detail_ipa")
                        .semantics { contentDescription = "音标 ${card.ipa}" },
                )
            }
            // 发音入口归一到共享药片（spec §3「两套同构几何」的详情页那一半）：
            // 喇叭字形、48dp 触控下限、三态可见文案都由组件一处交付，本屏只给态、动作、tag 与口音尾缀。
            // 默认美式；英式仅在调用方提供时出现，两口音靠尾缀分档，屏侧不写任何朗读定语。
            SpeakerButton(
                state = SpeakerState.Idle,
                onClick = { onSpeak() },
                modifier = Modifier.testTag("card_detail_speak"),
                accentLabel = "美",
            )
            onSpeakBritish?.let { speakBritish ->
                SpeakerButton(
                    state = SpeakerState.Idle,
                    onClick = { speakBritish() },
                    modifier = Modifier.testTag("card_detail_speak_british"),
                    accentLabel = "英",
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
                    // 暂停-继续同样走共享药片（口径同词卡屏 Task 15）：三态文案由组件的可见文字承载
                    // （clickable 会把子孙合并进同一枚语义节点），屏侧不再另写一份朗读文案。
                    SpeakerButton(
                        state = cardDetailSpeakerState(pronunciationStatus),
                        onClick = { onPauseResume() },
                        modifier = Modifier.testTag("card_detail_pronunciation_toggle"),
                    )
                }
                TextButton(
                    onClick = onStopPronunciation,
                    modifier = Modifier
                        .testTag("card_detail_pronunciation_stop")
                        .semantics { contentDescription = "停止播放发音" },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.text1),
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

        // 五枚动作药片拆两行（3 + 2）：一行放五枚时窄屏 + 大字号会把最后一枚压成竖排单字。
        WordActionPillGrid(
            lemma = card.lemma,
            favoritePresent = favoritePresent,
            onToggleFavorite = onToggleFavorite,
            vocabularyPresent = vocabularyPresent,
            onToggleVocabulary = onToggleVocabulary,
            onRelearn = onRelearn,
            onNext = onNext,
            onAskAi = onAskAi,
        )
        // F5-03 决策 §4（Task 10 携带的 T9 复评 N-4，口径同阅读页 `article_status_live_region`）：
        // 发音状态文字由**常驻 live region 容器**承载，靠子节点进出触发播报
        // （Task 6 判例 WordCardScreen.word_card_status_live_region）。
        // ①tag `card_detail_pronunciation_message` 留在瞬时 Text 上（既有引用不破）；
        // ②liveRegion 只在容器上，瞬时子节点不得再带；③容器零子节点时不留任何可见/占位面积。
        // 排版上排到卡内最后一块：外层 `spacedBy(10.dp)` 的幽灵间距只会落在卡片底部，
        // 不再把「词头 / 音标 / 释义」之间的距离撑开（UI 重设计轮要解决的正是这条）。
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
    }
}

/**
 * 动作药片网格（spec §5：5 枚改两行 3 + 2）。
 *
 * 分工分两行：收藏 / 生词本 / 重新学习是**对词本身**的动作，下一个 / 问 AI 是**继续读下去**的动作。
 * 每枚拿 `weight(1f)`，窄屏和大字号下不会再把「词 + 图标」压成竖排单字。
 */
@Composable
private fun WordActionPillGrid(
    lemma: String,
    favoritePresent: Boolean?,
    onToggleFavorite: (() -> Unit)?,
    vocabularyPresent: Boolean?,
    onToggleVocabulary: (() -> Unit)?,
    onRelearn: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onAskAi: (() -> Unit)?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (favoritePresent != null && onToggleFavorite != null) {
            PillButton(
                label = if (favoritePresent) "★ 已收藏" else "☆ 收藏",
                description = if (favoritePresent) "取消收藏 $lemma" else "收藏 $lemma",
                onClick = onToggleFavorite,
                modifier = Modifier
                    .weight(1f)
                    .testTag("card_detail_favorite_toggle"),
            )
        }
        if (vocabularyPresent != null && onToggleVocabulary != null) {
            PillButton(
                label = if (vocabularyPresent) "移出生词本" else "加入生词本",
                description = if (vocabularyPresent) "移出 $lemma 生词本" else "加入 $lemma 生词本",
                onClick = onToggleVocabulary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("card_detail_vocabulary_toggle"),
            )
        }
        onRelearn?.let { callback ->
            PillButton(
                label = "重新学习",
                description = "重新学习 $lemma",
                onClick = callback,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        onNext?.let { callback ->
            PillButton(
                label = "下一个单词",
                description = "查看下一个单词",
                onClick = callback,
                modifier = Modifier
                    .weight(1f)
                    .testTag("card_detail_next"),
            )
        }
        onAskAi?.let { callback ->
            PillButton(
                label = "问 AI",
                description = "问 AI（$lemma）",
                onClick = callback,
                modifier = Modifier
                    .weight(1f)
                    .testTag("card_detail_ask_ai"),
            )
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
        PersonalNoteSaveStatus.Failed -> Box(Modifier.testTag("card_detail_personal_note_save_failed")) {
            // Task 26 Step 3：错误行收口到共享 `InlineNotice`；旧锚留在外层（组件的标签后设，会覆盖同一 modifier）。
            InlineNotice("笔记保存失败，请重试。", InlineNoticeKind.Error)
        }
        PersonalNoteSaveStatus.Saved -> Text(
            "笔记已保存。",
            color = appColors().success,
            modifier = Modifier.testTag("card_detail_personal_note_saved"),
        )
        else -> Unit
    }
}

/**
 * 我的笔记弹窗：列表 + 逐条删除 + 添加，全部收在这一层；
 * 由例句下方的展示栏「添加 / 管理」按钮打开（真机反馈两轮：笔记内容要在页面直接可见，
 * 但增删集中在弹窗，页面不噪）。
 *
 * 标题写「管理笔记」而不是「我的笔记」：页面上的小节已经叫「我的笔记」，
 * 弹窗再叫一遍等于同一句话读两遍，也说不清弹窗为什么存在（spec §5）。
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
        title = { Text("管理笔记", style = AppType.Title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (notes.isEmpty()) {
                    Text(
                        text = "还没有笔记。可以记下记忆方法、易错点或例句。",
                        style = AppType.Body,
                        color = appColors().text2,
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
                                // Task 26 Step 3：这是**破坏性动作标签**（不是错误提示行），取值与 `colorScheme.error`
                                // 逐字同值（`MintGlassTheme.kt: error = set.error`），改读主题访问器只为止住族 2 读点。
                                Text("删除", color = appColors().error)
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
 * 宿主呈现态 → 药片三态（详情页的映射，口径同词卡屏 Task 15 的 `wordCardSpeakerState`）。
 *
 * 播控组只在 Playing / Paused 这一档窗口里长出来（F3-02 决策 §2），所以这里只有两态有意义：
 * Paused 才是 Paused（点下去是「继续」那一档），其余一律按 Playing 处理（点下去是「暂停」那一档）。
 * 文案与字形都由组件那侧分派，本函数不分派第三套——它只回答「宿主这一档对应组件哪一态」。
 * 抽成 `internal` 纯函数是为了让这档映射能在 JVM 侧逐态点名（connected 读不到 `SpeakerState`）。
 */
internal fun cardDetailSpeakerState(status: PronunciationStatus): SpeakerState =
    if (status == PronunciationStatus.Paused) SpeakerState.Paused else SpeakerState.Playing

@Composable
private fun SenseRow(
    partOfSpeech: String,
    meaningZh: String,
    posModifier: Modifier = Modifier,
    meaningModifier: Modifier = Modifier,
) {
    val colors = appColors()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        if (partOfSpeech.isNotBlank()) {
            Text(
                text = partOfSpeech,
                style = AppType.Label.copy(color = colors.text1, fontWeight = FontWeight.SemiBold),
                modifier = posModifier
                    .clip(AppShape.Pill)
                    .background(colors.tint)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        if (meaningZh.isNotBlank()) {
            Text(text = meaningZh, style = AppType.Body, modifier = meaningModifier.weight(1f))
        }
    }
}

/** 浅底、深字的胶囊按钮：容器取中性 tint，墨取 text1，与卡片同一套色阶。 */
@Composable
private fun PillButton(label: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = appColors()
    TextButton(
        onClick = onClick,
        shape = AppShape.Pill,
        colors = ButtonDefaults.textButtonColors(containerColor = colors.tint, contentColor = colors.text1),
        modifier = modifier.semantics { contentDescription = description },
    ) { Text(label, style = AppType.Footnote, color = colors.text1, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun DetailCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    SoftCard(modifier = modifier, contentPadding = PaddingValues(18.dp), content = content)
}

/** 例句、派生词、短语、近义词、词形变化共用的小节：一个标题 + 内容。 */
@Composable
private fun DetailSection(title: String, tag: String? = null, content: @Composable ColumnScope.() -> Unit) {
    DetailCard(modifier = if (tag != null) Modifier.testTag(tag) else Modifier) {
        Text(text = title, style = AppType.Title, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** 例句里把单词加粗、用当前主题的品牌深绿标出，其余保持正文样式。 */
@Composable
private fun highlightLemma(sentence: String, lemma: String): AnnotatedString {
    val range = lemmaRangeIn(sentence, lemma) ?: return AnnotatedString(sentence)
    val lemmaInk = appDomain(AppDomain.AiSpeech).deep
    return buildAnnotatedString {
        append(sentence.substring(0, range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = lemmaInk)) {
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

/**
 * 配图缩略图：72dp 见方、圆角裁切，挂在词头行尾。
 *
 * 原来是通栏 4:3 大图，一屏里单词卡被挤到看不见；缩略图把「有没有配图」这条信息留住，
 * 又把纵向空间还给释义（spec §5）。
 */
private val illustrationThumbnailSize = 72.dp

private val illustrationThumbnailModifier = Modifier
    .size(illustrationThumbnailSize)
    .clip(AppShape.Button)

/**
 * 配图来源的三种互斥结果：导入文件解码出的位图 / 内置 drawable 回退 / 读不出来。
 *
 * 抽成组合期就能定论的取值，是为了让「图放不放进词头行」有确定答案——
 * 空图框和占位格子都不该占那 72dp（spec §5「有图才显示」）。
 */
private class IllustrationSource(
    val bitmap: Bitmap?,
    val fallbackRes: Int?,
    val unreadable: Boolean,
)

@Composable
private fun rememberIllustrationSource(lemma: String, path: String?): IllustrationSource {
    // 导入配图优先：词书包里的真实文件（`imagePath`）；没有才回退到内置 drawable 映射。
    val bitmap = remember(path) {
        path?.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it) }
    }
    val fallbackRes = remember(lemma, path) {
        if (path.isNullOrBlank()) lemmaToDrawableRes(lemma) else null
    }
    return IllustrationSource(
        bitmap = bitmap,
        fallbackRes = fallbackRes,
        unreadable = !path.isNullOrBlank() && bitmap == null,
    )
}
