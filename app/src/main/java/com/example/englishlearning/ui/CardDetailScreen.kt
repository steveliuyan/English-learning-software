package com.example.englishlearning.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.wordqa.WordAiNote
import com.example.englishlearning.ui.components.AppGlyph
import com.example.englishlearning.ui.components.AppGlyphLayer
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.DialogButton
import com.example.englishlearning.ui.components.DialogButtonRole
import com.example.englishlearning.ui.components.DialogPanel
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.SpeakerButton
import com.example.englishlearning.ui.components.SpeakerState
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppFont
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
 * 版式按原型 `sWordDetail`（`assets/app.js:519-559`，对齐批 1 Task 11）：
 * 页头 → 主词 / 音标 / 词性 → 口音与次键行（学习流宿主里那一行是「继续学习」，其余宿主是「重新学习」）
 * → 释义 → 例句 → 我的笔记 → 常用搭配 → 近义词 →
 * 词形与派生 → 页末提示 → 「用 AI 追问这个词」块按钮。
 * 主词站在页面上，卡片从「释义」小节才开始——原型没有英雄卡。
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
    // m03373（用户走查）：学习流里「重新学习」是错的承诺——它是错题本/生词本的重学入口，
    // 而学习流详情是从「刚评完这一张」打开的，用户要的是接着学下一张。宿主给这个回调，
    // 详情页就把次键位画成一枚「继续学习」主按钮（Primary 档），并且**不再画「重新学习」**。
    // 其余四种宿主（查词 / 记录 / 收藏 / 阅读）不传它，「重新学习」原样保留。
    onContinue: (() -> Unit)? = null,
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
    ) {
        // 页头交给统一组件承载（spec §3 页头族）：原型 `barTop` 的标题 + 返回位 + 右上收藏键。
        // 收藏是「对词本身」的一枚图标动作，按原型收进页头尾槽；收藏浮层这类只读宿主不传状态时不渲染。
        AppScreenHeader(
            title = "词义详情",
            onBack = onBack,
            action = favoriteHeaderAction(card.lemma, favoritePresent, onToggleFavorite),
        )
        Spacer(Modifier.height(10.dp))

        WordHead(
            card = card,
            pronunciationStatus = pronunciationStatus,
            pronunciationControlsEnabled = pronunciationControlsEnabled,
            pauseResumeSupported = pauseResumeSupported,
            onPauseResume = onPauseResume,
            onStopPronunciation = onStopPronunciation,
            onSpeak = onSpeak,
            onSpeakBritish = onSpeakBritish,
            onRelearn = onRelearn,
            onContinue = onContinue,
            onNext = onNext,
            vocabularyPresent = vocabularyPresent,
            onToggleVocabulary = onToggleVocabulary,
        )

        val hasSenses = card.senses.isNotEmpty() ||
            card.meaningZh.isNotBlank() ||
            card.partOfSpeech.isNotBlank()
        if (hasSenses) {
            SectionGap()
            DetailSection(title = "释义") {
                if (card.senses.isNotEmpty()) {
                    SenseList(card.senses)
                } else {
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
            }
        }

        val example = card.example?.takeIf { it.isNotBlank() }
        val exampleZh = card.exampleZh?.takeIf { it.isNotBlank() }
        if (example != null || exampleZh != null) {
            SectionGap()
            DetailSection(title = "例句") {
                example?.let { sentence ->
                    Text(
                        text = highlightLemma(sentence, card.lemma),
                        style = AppType.BodyLg.copy(fontFamily = AppFont.Serif, color = colors.text1),
                        modifier = Modifier
                            .testTag("card_detail_example")
                            .semantics { contentDescription = "例句 $sentence" },
                    )
                }
                exampleZh?.let { translation ->
                    Text(
                        text = translation,
                        style = AppType.Meta.copy(color = colors.text2),
                        modifier = Modifier
                            .padding(top = 6.dp)
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
            SectionGap()
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
                    text = "添加 / 管理",
                    onClick = { showNotesDialog = true },
                    contentDescription = "管理我的笔记",
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

        if (card.phrases.isNotEmpty()) {
            SectionGap()
            DetailSection(title = "常用搭配", tag = "card_detail_phrases") {
                card.phrases.forEachIndexed { index, phrase ->
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = colors.outlineSoft,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    Text(
                        text = phrase.text,
                        style = AppType.Body.copy(
                            fontFamily = AppFont.Serif,
                            fontSize = 16.sp,
                            color = colors.text1,
                        ),
                    )
                    // 自镜像搭配（英文串与中文串逐字相同）在包解析处已归一为 null：
                    // 只讲得出一个字段就只讲一个字段，不把同一串字念两遍。
                    phrase.meaningZh?.let { meaning ->
                        Text(
                            text = meaning,
                            style = AppType.Caption.copy(color = colors.text3),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }

        if (card.synonyms.isNotEmpty()) {
            SectionGap()
            DetailSection(title = "近义词", tag = "card_detail_synonyms") {
                SynonymChips(card.synonyms.map { it.lemma })
            }
        }

        if (card.derived.isNotEmpty() || card.inflections.isNotEmpty()) {
            SectionGap()
            DetailSection(title = "词形与派生", tag = "card_detail_wordforms") {
                if (card.derived.isNotEmpty()) {
                    Text(
                        text = card.derived.joinToString("；") { it.lemma },
                        style = AppType.Meta.copy(color = colors.text2),
                        modifier = Modifier
                            .testTag("card_detail_derived")
                            .semantics { contentDescription = "派生词 ${card.derived.joinToString("、") { it.lemma }}" },
                    )
                }
                if (card.inflections.isNotEmpty()) {
                    Text(
                        text = card.inflections.joinToString(" · "),
                        style = AppType.Caption.copy(color = colors.text3),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag("card_detail_inflections")
                            .semantics { contentDescription = "词形变化 ${card.inflections.joinToString("、")}" },
                    )
                }
            }
        }

        // N-2：本屏是「一份 UI、五种宿主」。只有宿主真的给了 AI 问词入口（学习流 / 记录详情）时，
        // 页末才提示「AI 问词都从这里进入」；搜索 / 查词那一路传 onAskAi = null、压根不渲染 AskAiButton，
        // 若无条件渲染这句提示，就是对着一枚点不到的按钮撒谎。提示与入口同生同灭。

        // 2026-11-09 真人走查：原型那句「长难句分析、AI 问词都从这里进入」在本仓只兑现了一半——
        // 本页只有一枚「用 AI 追问这个词」，落到 AI 问词屏也只有近义辨析 / 造个句子 / 谐音记忆三种问法；
        // 长难句分析是 AI 助学里的独立一屏（吃的是整句，不是词卡），从这里进不去。留着「长难句分析」
        // 就是把用户指向一条走不通的路，故按实情收成只剩 AI 问词（其余措辞照旧）。
        onAskAi?.let { ask ->
            SectionGap()
            InlineNotice(
                text = "AI 问词从这里进入，围绕这一个词继续追问。",
                kind = InlineNoticeKind.Info,
            )
            Spacer(Modifier.height(12.dp))
            AskAiButton(lemma = card.lemma, onAskAi = ask)
        }
    }
}

/**
 * 页头尾槽的收藏键（原型 `barTop` 的 `icon-btn`）。
 *
 * m03373 起两态都是**同一枚书签**，只换「层 + 色」：未收藏＝仅描边 + 次级墨（text2），
 * 已收藏＝三层实心 + 学习域实色（`appDomain(Learn).deep`）。旧做法是 Star ↔ Bookmark 换字形，
 * 用户走查判「不明显」——形状变了人还得先记住哪个是「开」。
 */
@Composable
private fun favoriteHeaderAction(
    lemma: String,
    favoritePresent: Boolean?,
    onToggleFavorite: (() -> Unit)?,
): (@Composable () -> Unit)? {
    if (favoritePresent == null || onToggleFavorite == null) return null
    val colors = appColors()
    val learn = appDomain(AppDomain.Learn)
    return {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(AppShape.Icon)
                .testTag("card_detail_favorite_toggle")
                .clickable(role = Role.Button, onClick = onToggleFavorite)
                .semantics {
                    contentDescription = if (favoritePresent) "取消收藏 $lemma" else "收藏 $lemma"
                },
            contentAlignment = Alignment.Center,
        ) {
            // m03373（用户走查「右上角的收藏不明显，只是改了个形状而已」）：两态都用**同一枚书签**，
            // 靠「层 + 色」区分——未收藏只描边、取次级墨；已收藏三层实心、取学习域实色（浅深两套都亮）。
            // 换字形（旧做法 Star ↔ Bookmark）恰恰是最认不出的一招：形状变了，人还得先记住哪个是「开」。
            AppGlyph(
                icon = AppIcon.Bookmark,
                tint = if (favoritePresent) learn.deep else colors.text2,
                size = 22.dp,
                layer = if (favoritePresent) AppGlyphLayer.Duotone else AppGlyphLayer.StrokeOnly,
            )
        }
    }
}

/**
 * 词头：主词（46sp 衬线）+ 音标 / 词性 + 口音药片 + 次键行 + 播控组。
 *
 * 不装卡（原型 sWordDetail 的主词直接落在页面上）；配图缩略图挂在主词行尾，
 * 「有没有配图」这条信息留着，纵向空间还给释义。
 */
@Composable
private fun WordHead(
    card: WordCard,
    pronunciationStatus: PronunciationStatus,
    pronunciationControlsEnabled: Boolean,
    pauseResumeSupported: Boolean,
    onPauseResume: () -> Unit,
    onStopPronunciation: () -> Unit,
    onSpeak: () -> Unit,
    onSpeakBritish: (() -> Unit)?,
    onRelearn: (() -> Unit)?,
    onContinue: (() -> Unit)?,
    onNext: (() -> Unit)?,
    vocabularyPresent: Boolean?,
    onToggleVocabulary: (() -> Unit)?,
) {
    val colors = appColors()
    val illustration = rememberIllustrationSource(card.lemma, card.imagePath)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = card.lemma,
                style = AppType.Lemma,
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
        // 音标 + 词性同一行（原型 .word-ipa + .word-pos）：词性是衬线斜体品牌深色。
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (card.ipa.isNotBlank()) {
                Text(
                    text = card.ipa,
                    style = AppType.Meta.copy(color = colors.text3),
                    modifier = Modifier
                        .testTag("card_detail_ipa")
                        .semantics { contentDescription = "音标 ${card.ipa}" },
                )
            }
            if (card.partOfSpeech.isNotBlank()) {
                Text(
                    text = card.partOfSpeech,
                    style = AppType.Meta.copy(
                        fontFamily = AppFont.Serif,
                        fontStyle = FontStyle.Italic,
                        color = colors.brandDeep,
                    ),
                    modifier = Modifier
                        .testTag("card_detail_pos")
                        .semantics { contentDescription = "词性 ${card.partOfSpeech}" },
                )
            }
        }
        // 口音与次键行：原型「英式 / 美式 / 生词本」三枚次键并排；本仓发音入口归一到共享药片
        // （三态文案一处定义，spec §3），触控下限与配色由组件交付，屏侧只给 tag、动作与口音尾缀。
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
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
            if (vocabularyPresent != null && onToggleVocabulary != null) {
                PillButton(
                    text = if (vocabularyPresent) "移出生词本" else "加入生词本",
                    onClick = onToggleVocabulary,
                    style = PillStyle.Secondary,
                    domain = AppDomain.Learn,
                    contentDescription = if (vocabularyPresent) "移出 ${card.lemma} 生词本" else "加入 ${card.lemma} 生词本",
                    modifier = Modifier.testTag("card_detail_vocabulary_toggle"),
                )
            }
        }
        if (onContinue != null || onRelearn != null || onNext != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // m03373：学习流详情只给「继续学习」——它和词卡回执的「下一个词」是**同一条推进边**
                // （`WordCardViewModel.advance()`），所以不会写出第二个学习事件。
                onContinue?.let { callback ->
                    PillButton(
                        text = "继续学习",
                        onClick = callback,
                        style = PillStyle.Primary,
                        domain = AppDomain.Learn,
                        contentDescription = "继续学习下一个单词",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card_detail_continue"),
                    )
                }
                onRelearn?.let { callback ->
                    PillButton(
                        text = "重新学习",
                        onClick = callback,
                        style = PillStyle.Secondary,
                        domain = AppDomain.Learn,
                        contentDescription = "重新学习 ${card.lemma}",
                        modifier = Modifier.weight(1f),
                    )
                }
                onNext?.let { callback ->
                    PillButton(
                        text = "下一个单词",
                        onClick = callback,
                        style = PillStyle.Secondary,
                        domain = AppDomain.Learn,
                        contentDescription = "查看下一个单词",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card_detail_next"),
                    )
                }
            }
        }
        // F3-02 播控组（与词卡同款共享药片、同一呈现窗口 Playing/Paused）：
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
        // F5-03 决策 §4：发音状态文字由**常驻 live region 容器**承载，靠子节点进出触发播报
        // （Task 6 判例 WordCardScreen.word_card_status_live_region）。
        // ①tag `card_detail_pronunciation_message` 留在瞬时 Text 上（既有引用不破）；
        // ②liveRegion 只在容器上，瞬时子节点不得再带；③容器零子节点时不留任何可见/占位面积。
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

/** 小节之间的 24dp 节奏（原型 `.sec` margin-top: var(--s6)）。 */
@Composable
private fun SectionGap() {
    Spacer(Modifier.height(24.dp))
}

/**
 * 小节：标题一行（原型 `.sec-title`，17sp/650）+ 卡片本体（`SoftCard`，白面 / 无描边 / 20 圆角）。
 *
 * 标题在卡外、卡内不再有第二层壳——旧 `DetailCard` 私壳退役。
 */
@Composable
private fun DetailSection(
    title: String,
    tag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = if (tag != null) Modifier.testTag(tag) else Modifier) {
        Text(
            text = title,
            style = AppType.Title,
            modifier = Modifier
                .padding(start = 2.dp, bottom = 12.dp)
                .semantics { heading() },
        )
        SoftCard(contentPadding = PaddingValues(16.dp), content = content)
    }
}

/**
 * 释义行：词性一列（38dp 定宽、衬线斜体、brand-deep）+ 释义（15sp/500）。
 *
 * 多词性时逐条列出；占位词卡只有一条，走 [posModifier] / [meaningModifier] 的既有锚。
 */
@Composable
private fun SenseRow(
    partOfSpeech: String,
    meaningZh: String,
    posModifier: Modifier = Modifier,
    meaningModifier: Modifier = Modifier,
) {
    val colors = appColors()
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (partOfSpeech.isNotBlank()) {
            Text(
                text = partOfSpeech,
                style = AppType.Meta.copy(
                    fontFamily = AppFont.Serif,
                    fontStyle = FontStyle.Italic,
                    color = colors.brandDeep,
                ),
                modifier = posModifier.width(38.dp),
            )
        }
        if (meaningZh.isNotBlank()) {
            Text(
                text = meaningZh,
                style = AppType.Body.copy(fontWeight = FontWeight(500), color = colors.text1),
                modifier = meaningModifier.weight(1f),
            )
        }
    }
}

/**
 * 近义词静态芯片（原型 `.chip chip-static`）：30dp 本体、药片形、surface 底 + line 描边 + ink-2 墨。
 *
 * 静态：近义词是给读的信息，不是筛选操作，所以没有 clickable / toggleable 语义。
 */
@Composable
private fun SynonymChips(synonyms: List<String>) {
    val colors = appColors()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        synonyms.forEach { lemma ->
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .clip(AppShape.Pill)
                    .background(colors.surface)
                    .border(1.dp, colors.outline, AppShape.Pill)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = lemma,
                    style = AppType.Caption.copy(color = colors.text2, fontWeight = FontWeight(600)),
                )
            }
        }
    }
}

/** 页末的 AI 追问块按钮（原型 `btn btn-secondary btn-block` + spark 字形）。 */
@Composable
private fun AskAiButton(lemma: String, onAskAi: () -> Unit) {
    PillButton(
        text = "用 AI 追问这个词",
        onClick = onAskAi,
        style = PillStyle.Secondary,
        domain = AppDomain.AiSpeech,
        contentDescription = "问 AI（$lemma）",
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_detail_ask_ai"),
    )
}

/** 一条词性释义的承载行（保留给多词性列表）。 */
@Composable
private fun SenseList(senses: List<com.example.englishlearning.learning.domain.WordSense>) {
    val colors = appColors()
    Column(Modifier.testTag("card_detail_senses")) {
        senses.forEachIndexed { index, sense ->
            if (index > 0) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = colors.outlineSoft,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            SenseRow(partOfSpeech = sense.partOfSpeech, meaningZh = sense.meaningZh)
        }
    }
}

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
    DialogPanel(onDismissRequest = onDismiss) {
        Text("管理笔记", style = AppType.Title, color = appColors().text1)
        // 面板本体已带「窗口可用高」上限 + 竖向滚动（见 `DialogPanel`），这里不再套第二层 `verticalScroll`：
        // 嵌套滚动只有外层生效，笔记条数一多内层就是个哑修饰，还会让「滚到底才看得见关闭」重新变成运气。
        Column(
            modifier = Modifier.padding(top = 12.dp),
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
        // 出口键走共享 `DialogButton`（原先是手搓 `TextButton` + `FontWeight.Bold`：字号不吃六档、
        // 触控面靠 padding 凑，与另外两枚对话框的出口读起来不是同一档）。tag 逐字不变，connected 照旧定位。
        DialogButton(
            role = DialogButtonRole.Dismiss,
            label = "关闭",
            onClick = onDismiss,
            tag = "card_detail_notes_dialog_close",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        )
    }
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
