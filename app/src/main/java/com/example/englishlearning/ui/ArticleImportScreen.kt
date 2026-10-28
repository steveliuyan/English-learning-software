package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.englishlearning.reading.ArticleQualityPolicy
import com.example.englishlearning.reading.ImportRejection
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.hairlineTop
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 粘贴导入页。全程不出网（`ImportArticleUseCase` 无任何网络依赖），
 * 所以这里没有出站确认，只有责任声明与逐项拒绝原因。
 *
 * 版式（批 3 Task 4）：页头（共享 `AppScreenHeader`）+ 可滚表单 + 屏底吸底操作条三条带。
 * 三处信息架构改动：
 *  1. **规则前置**——「40~1200 词」从「事后拒绝才说」搬到正文框上方并常驻。门槛先说明，
 *     用户不用白写一遍才知道要求；阈值直接读 `ArticleQualityPolicy.forImported`，
 *     与 VM 的校验同一个真值（写死数字必然会与判据分叉）。
 *  2. **主操作吸底**——提交原在整条表单流末尾（下面还排着拒绝提示与责任声明），大字号下要滚
 *     过整屏才点得到，而它是这一屏唯一的主操作。
 *  3. **常显但空表单禁用**——`enabled` 是「标题非空 && 正文非空 && 非提交中」。校验本身仍在
 *     VM（这里不重复校验，只表达「现在还不能提交」）：重复校验会与 VM 判据分叉。
 */
@Composable
fun ArticleImportScreen(
    state: ImportUiState,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("article_import_screen"),
    ) {
        AppScreenHeader(
            title = "粘贴导入",
            onBack = onBack,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
        // 表单区可滚：正文框 8 行 + 三类提示 + 大字号 2f 之后必然超屏，不能滚就等于丢内容。
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 词数规则卡：正文框**上方**、常驻、不随输入变化。
            //
            // 阈值读 `ArticleQualityPolicy.forImported` 的 `minWords` / `maxWords`——这是 VM 校验用的同一份
            // 策略（`ImportArticleUseCase` 走 `firstRejection(..., forImported)`），界面上的数字因此不可能
            // 与判据分叉。用弱卡 `SoftCard`：这是说明，不是要用户点的地方；强卡额度留给主操作那一侧。
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "正文至少 ${ArticleQualityPolicy.forImported.minWords} 个单词，" +
                        "最多 ${ArticleQualityPolicy.forImported.maxWords} 个单词；中英混排按单词切分计数。",
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.testTag("import_rule"),
                )
            }
            TextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_title"),
                colors = importFieldColors(),
            )
            TextField(
                value = state.body,
                onValueChange = onBodyChange,
                label = { Text("英文正文") },
                // 8 行：粘贴的是整篇文章，6 行时一屏只看得见开头，用户没法核对自己粘了什么。
                minLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_body"),
                colors = importFieldColors(),
            )
            Text(
                text = "导入不会发起任何网络请求；请确保你有权使用粘贴的内容。",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag("import_disclaimer"),
            )
            if (state.rejection != null) {
                // Task 26 Step 3：错误行收口到共享 `InlineNotice`。旧锚 `import_rejection_reason` 由外层 Box 承载：
                // 组件在调用方的 modifier 之后设自己的 `inline_notice_error` 标签，同一个 modifier 上的标签会被覆盖。
                Box(Modifier.testTag("import_rejection_reason")) {
                    InlineNotice(state.rejection.message(), InlineNoticeKind.Error)
                }
            }
            if (state.storageFailed) {
                Box(Modifier.testTag("import_storage_failed")) {
                    InlineNotice("本地存储暂时不可用，文章没有保存。", InlineNoticeKind.Error)
                }
            }
            if (state.submitting) {
                Text(
                    text = "正在保存…",
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.testTag("import_submitting"),
                )
            }
        }
        // 吸底操作条：本屏唯一的主 CTA。
        //
        // 条底铺页底色 `background`（不是 `surface`），靠顶部 1dp 发丝线与可滚表单分层；表单在它上面滚，
        // 它自己不动。高度 `heightIn(min = 50.dp)` 对齐 spec §4.5 的主 CTA 50–52dp。
        // 实底取 `strongSurface` + `onStrong`（主操作档，同 `ArticleReadingScreen` 的「完成阅读」与
        // `WorksheetPreviewScreen` 的确认条）；禁用态落 `tint3` + `text3`——常显但明确「现在还不能点」。
        //
        // 文案固定「导入文章」：保存进行中也不改成「正在保存…」（那是 `import_submitting` 那行的话），
        // 同一枚按钮不因状态改身份。
        //
        // 三条禁用条件写在按钮自己的 `enabled` 上、不抽成外面算好的布尔：抽出去会让「为什么不能提交」
        // 与按钮分离在源码两处，读代码的人得跳两次才知道门槛是「标题 + 正文 + 非提交中」。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .testTag("import_action_bar")
                .then(hairlineTop(colors.outlineSoft))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = onImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("import_submit"),
                enabled = state.title.isNotBlank() && state.body.isNotBlank() && !state.submitting,
                shape = AppShape.Button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.strongSurface,
                    contentColor = colors.onStrong,
                    disabledContainerColor = colors.tint3,
                    disabledContentColor = colors.text3,
                ),
            ) {
                Text(
                    text = "导入文章",
                    style = AppType.Label,
                    color = if (state.title.isNotBlank() && state.body.isNotBlank() && !state.submitting) {
                        colors.onStrong
                    } else {
                        colors.text3
                    },
                )
            }
        }
    }
}

/** 拒绝原因的固定文案：逐项对应校验规则，不含任何运行时拼接的用户输入。 */
private fun ImportRejection.message(): String = when (this) {
    ImportRejection.BlankTitle -> "标题不能为空。"
    ImportRejection.BlankBody -> "正文不能为空。"
    ImportRejection.BodyTooShort -> "正文太短，至少需要 40 个单词。"
    ImportRejection.BodyTooLong -> "正文太长，请缩短内容后重试。"
    ImportRejection.NotEnglish -> "正文需要是英文。"
    ImportRejection.DangerousMarkup -> "正文包含不允许的字符或标记，请检查后重试。"
}

/**
 * 输入框取色：只读组合期 `appColors()`（原先读的是静态薄荷桥，深套下浅墨压深底即失效）。
 * 函数体保留——两个输入框共用一套色，抽出来才只有一个真值。
 */
@Composable
private fun importFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = appColors().surface,
    unfocusedContainerColor = appColors().surface,
    focusedIndicatorColor = appColors().text2,
    unfocusedIndicatorColor = appColors().outlineSoft,
    cursorColor = appColors().text2,
    focusedLabelColor = appColors().text1,
    unfocusedLabelColor = appColors().text2,
    focusedTextColor = appColors().text1,
    unfocusedTextColor = appColors().text1,
)
