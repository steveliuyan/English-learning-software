package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ai.AiFailureUiText
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.NotConfiguredCard
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.GlassDialog
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.wordqa.WordAiNote
import com.example.englishlearning.wordqa.WordQaKind
import com.example.englishlearning.wordqa.WordQaNotConfiguredReason

/**
 * 问法顺序：spec §5 要「首选『拆词义』实底」，所以 `Breakdown` 必须排在用户最先看到的位置，
 * 其余按枚举序跟上。
 *
 * `Personal`（本地笔记）**仍被 filter 掉**：它不参与 AI 问答，由同屏的「我的笔记」输入区承载。
 * 顺序在这里算一次（顶层常量），屏内只负责遍历——顺序的唯一真值不与渲染代码缠在一起。
 */
private val askKinds: List<WordQaKind> =
    listOf(WordQaKind.Breakdown) +
        WordQaKind.entries.filter { it != WordQaKind.Personal && it != WordQaKind.Breakdown }

/**
 * 词 AI 问答屏（二级页面，从词卡详情进入）。
 *
 * 呈现层约束与文章生成一致：失败文案只来自 [AiFailureUiText] 常量，屏幕不拼接任何
 * 运行时数据；问题只限 [WordQaKind] 三种固定问法，没有自由输入框——「拒绝任意
 * 提示词」的约束在界面上同样是防线。出站确认在 [state] 为
 * [WordAiQaUiState.NeedsOutboundConfirmation] 时弹出，确认前零字节出网。
 *
 * ## 版式（批 4 Task 7，spec §5「AI 问词」行）
 *
 * 旧形态是「`Button` 形态返回 + `headlineSmall` 自绘标题 + 三枚同权实底问法 + 笔记区压在问法
 * 之上 + 回答沉在页底」。现在：
 *  - 页头换共享 [AppScreenHeader]，返回位是固定可及名「返回」；单词由紧随页头的一行说明承载
 *    （tag 仍是 `word_qa_lemma`，共享页头的 `subtitle` 没有标签槽，故不硬塞进去）；
 *  - **回答上移**：状态/回答区排在三枚问法**之上**——点完问法答案就在眼前，不必先滚过一排按钮；
 *  - **4 枚按钮分主次**：三枚问法里 [WordQaKind.Breakdown] 是唯一实底药片，另两枚文字药片；
 *    第 4 枚「保存为笔记」随回答出现，取次要点药片；
 *  - 笔记区下沉：先问、再看答案，最后才写自己的心得；
 *  - 已保存笔记从描边 `Card` 换裸行 [BareRow]（本文件 `Card(` 归零）。
 *
 * 数据通路、tag、校验文案与出站确认三要素一字不动：这一屏只搬外观。
 */
@Composable
fun WordAiQaScreen(
    lemma: String,
    state: WordAiQaUiState,
    onAsk: (WordQaKind) -> Unit,
    onConfirmOutbound: (Boolean) -> Unit,
    onSaveNote: () -> Unit,
    onSavePersonalNote: (String) -> Unit = {},
    savedNotes: List<WordAiNote> = emptyList(),
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    var personalNote by remember(lemma) { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("word_qa_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = "AI 问词",
            onBack = onBack,
        )
        Text(
            text = "单词：$lemma",
            style = AppType.Footnote,
            color = colors.text2,
            modifier = Modifier.testTag("word_qa_lemma"),
        )

        // 状态/回答区在三枚问法**之上**（spec §5「回答上移」）：问完立刻看得见答案。
        when (state) {
            WordAiQaUiState.Idle -> Text(
                text = "选一个问法，AI 会围绕这个单词作答。",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("word_qa_idle_hint"),
            )
            WordAiQaUiState.Asking -> Text(
                text = "正在询问…",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("word_qa_asking"),
            )
            is WordAiQaUiState.Answered -> AnsweredContent(state.kind, state.answer, state.saved, state.saveFailed, onSaveNote)
            is WordAiQaUiState.NeedsOutboundConfirmation -> OutboundConfirmationDialog(state.host, onConfirmOutbound)
            is WordAiQaUiState.NotConfigured -> NotConfiguredCard(
                featureLabel = "AI 问词",
                onConfigure = onOpenAiSettings,
                modifier = Modifier.testTag("word_qa_not_configured"),
                detail = state.reason.guidance(),
            )
            is WordAiQaUiState.Failed -> FailureCard(state.failure.uiText)
        }

        askKinds.forEach { kind ->
            QuestionChip(kind, enabled = state != WordAiQaUiState.Asking, onAsk)
        }

        // 我的笔记：排在问法与回答之后——先问 AI，再记自己的心得。
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "我的笔记",
                style = AppType.Label,
                color = colors.text2,
            )
            OutlinedTextField(
                value = personalNote,
                onValueChange = { personalNote = it },
                label = { Text("记点什么") },
                placeholder = { Text("记录记忆方法、易错点或例句") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("word_qa_personal_note"),
                minLines = 3,
            )
            PillButton(
                text = "保存我的笔记",
                onClick = {
                    onSavePersonalNote(personalNote)
                    personalNote = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                style = PillStyle.Secondary,
                enabled = personalNote.isNotBlank(),
                testTag = "word_qa_save_personal_note",
            )
        }

        if (savedNotes.isNotEmpty()) {
            Text(
                text = "已保存笔记",
                style = AppType.Label,
                color = colors.text2,
            )
            SavedNoteList(savedNotes)
        }
    }
}

/**
 * 一次问答的回答 + 「保存为笔记」。
 *
 * 回答正文的可及名是固定的「AI 回答」（TalkBack 靠它区分「AI 说的」与「我记的」）；
 * 保存键取次要点档：本屏的实底只留给首选问法，回答落定后不该再冒第二枚同权主按钮。
 *
 * 本函数在文件里排在 [QuestionChip] **之前**，与屏内「回答在上、问法在下」的渲染序同向
 * （`WordQaLayoutContractTest` 第 2 条同时钉住文件序与屏体序，两处都不许反转）。
 */
@Composable
private fun AnsweredContent(
    kind: WordQaKind,
    answer: String,
    saved: Boolean,
    saveFailed: Boolean,
    onSaveNote: () -> Unit,
) {
    val colors = appColors()
    Text(
        text = "问法：${kind.label}",
        style = AppType.Footnote,
        color = colors.text2,
        modifier = Modifier.testTag("word_qa_answer_kind"),
    )
    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("word_qa_answer"),
    ) {
        Text(
            text = answer,
            style = AppType.Body,
            color = colors.text1,
            modifier = Modifier.semantics { contentDescription = "AI 回答" },
        )
    }
    PillButton(
        text = if (saved) "已保存到笔记" else "保存为笔记",
        onClick = onSaveNote,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
        style = PillStyle.Secondary,
        enabled = !saved,
        testTag = "word_qa_save_note",
        contentDescription = "保存为笔记",
    )
    if (saveFailed) {
        // Task 26 Step 3：错误行收口到共享 `InlineNotice`；旧锚留在外层（组件的标签后设，会覆盖同一 modifier）。
        Box(Modifier.testTag("word_qa_save_failed")) {
            InlineNotice("笔记没有保存成功，可以再试一次。", InlineNoticeKind.Error)
        }
    }
}

/**
 * 三枚固定问法。
 *
 * 主次由 spec §5 指定：「首选『拆词义』实底，其余文字按钮」。三枚同权实底正是这次要改掉的毛病，
 * 所以档位在这里按 `kind` 分叉，而不是三枚都取默认实底。54dp 最小点按高度保留（大字号下可点性）。
 */
@Composable
private fun QuestionChip(kind: WordQaKind, enabled: Boolean, onAsk: (WordQaKind) -> Unit) {
    PillButton(
        text = kind.label,
        onClick = { onAsk(kind) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
        style = if (kind == WordQaKind.Breakdown) PillStyle.Primary else PillStyle.Text,
        enabled = enabled,
        testTag = "word_qa_chip_${kind.name}",
        contentDescription = "${kind.label}（问 AI）",
    )
}

/**
 * 已保存笔记：裸行逐条（批 4 Task 7）。
 *
 * 旧形态是每条一枚描边 `Card`，八条笔记就是八张卡——卡片汤正是 spec §4.3 点名要收的观感。
 * 裸行只留发丝线分隔，回答本身用两行档（它是内容，不是「去哪」）。
 */
@Composable
private fun SavedNoteList(notes: List<WordAiNote>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        notes.forEachIndexed { index, note ->
            BareRow(
                title = note.answer,
                subtitle = note.kind.label,
                titleMaxLines = 2,
                topDivider = index > 0,
                modifier = Modifier.testTag("word_qa_saved_note_${note.noteId}"),
            )
        }
    }
}

@Composable
private fun OutboundConfirmationDialog(host: String, onConfirmOutbound: (Boolean) -> Unit) {
    GlassDialog(onDismiss = { onConfirmOutbound(false) }) {
        Text("确认发送请求", style = AppType.Headline, color = appColors().text1)
        Text(
            "你的问题与该服务的密钥将发送给 $host，并可能产生费用。",
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag("word_qa_outbound_confirmation_text"),
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
                text = "取消",
                onClick = { onConfirmOutbound(false) },
                modifier = Modifier.weight(1f),
                style = PillStyle.Secondary,
                domain = AppDomain.AiSpeech,
                testTag = "word_qa_decline_outbound",
            )
            PillButton(
                text = "确认发送",
                onClick = { onConfirmOutbound(true) },
                modifier = Modifier.weight(1f),
                style = PillStyle.Primary,
                domain = AppDomain.AiSpeech,
                testTag = "word_qa_confirm_outbound",
            )
        }
    }
}

/**
 * 各拒绝原因对应的**补路指引**（卡的第二行）；第一行「一句现状」由共享 `NotConfiguredCard` 统一措辞。
 *
 * Task 26 Step 3 删掉了本文件同名的私有 `NotConfiguredCard`：旧卡只有一段文字、**没有出口**
 * （spec §3 点名的死屏）；出口现在由共享件提供并接到 `onOpenAiSettings`。
 */
private fun WordQaNotConfiguredReason.guidance(): String = when (this) {
    WordQaNotConfiguredReason.NoDefaultProfile ->
        "去「设置 · AI」添加一套并设为默认。"
    WordQaNotConfiguredReason.DefaultProfileUnavailable ->
        "去「设置 · AI」检查默认服务或补上密钥。"
    WordQaNotConfiguredReason.InvalidEndpoint ->
        "去「设置 · AI」修正服务地址后再试。"
}

@Composable
private fun FailureCard(uiText: AiFailureUiText) {
    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("word_qa_failure"),
    ) {
        Text(
            text = uiText.message,
            style = AppType.Body,
            color = appColors().text1,
            modifier = Modifier.semantics { contentDescription = "问答失败原因" },
        )
    }
}
