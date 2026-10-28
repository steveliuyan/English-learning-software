package com.example.englishlearning.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.components.hairlineTop
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * F4-03 备份导出确认页。批 5 Task 5 按 spec §5「导出」行重排版式，数据通路一字不改：
 *
 * 两段式：先「生成备份包」得到诚实的范围/数量/大小估算，再经 SAF「另存为」落盘；取消不写文件。
 * 声明固定为「数据留档包 / 不能换机恢复 / 不含 API Key」，并指向「认证导出」这条真实恢复出口。
 *
 * 重排的四处落点：
 *  - **页头**换共享 [AppScreenHeader]（旧的两处自写 `TextButton("返回")` + `22.sp` 手搓标题下线）；
 *  - **包含／不包含分两段**（对账 7）：段一「包含内容」= 诚实声明 + 四条 [BareRow] 范围行；
 *    段二「不包含」= 音频例外一句 + 那枚开关。旧形态把「音频」这件事在范围清单与开关区各讲一遍，
 *    现在**只在段二讲一次**（段一窗口内「音频」为 0，判据见 `BackupExportLayoutContractTest` 第 3 条）；
 *  - **主操作吸底**：「选择保存位置并导出」是不透明 `Surface` 底槽里的唯一实底药片；「生成备份包」
 *    退回屏体文字档次键（改范围后包会失效，重建是修正动作，不是终点）；两枚 M3 `Button` 一并下线；
 *  - **两分支补滚动**：大字号下范围清单 + 开关 + 回执条会越过一屏，旧根列没有任何滚动壳。
 *
 * 可空 ViewModel 是生产组装点，为 null 时页面退化为只读提示、不崩溃
 * 防回归约定（见 AppScreenProductionWiringTest）。
 */
@Composable
fun BackupExportScreen(
    onBack: () -> Unit,
    viewModel: BackupExportViewModel? = null,
) {
    val colors = appColors()
    // 屏锚挂在这枚唯一的根盒上：两分支共用同一个 `backup_export_screen`，宿主与 connected 用例
    // 只需认一次；分支各挂一枚会让「屏」变成两个真值。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("backup_export_screen"),
    ) {
        if (viewModel == null) {
            UnavailableContent(onBack = onBack)
        } else {
            BackupExportContent(viewModel = viewModel, onBack = onBack)
        }
    }
}

/** `viewModel == null` 的降级分支：只读提示，零假入口（没有生成键、没有保存键）。 */
@Composable
private fun UnavailableContent(onBack: () -> Unit) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(title = "导出备份", onBack = onBack)
        SoftCard {
            Text(text = "备份导出功能暂不可用", style = AppType.Body, color = colors.text1)
            Text(
                text = "导出服务还没准备好。返回上一页再进来，通常就恢复了。",
                style = AppType.Footnote,
                color = colors.text2,
            )
        }
    }
}

@Composable
private fun BackupExportContent(
    viewModel: BackupExportViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { if (!state.packageReady && !state.busy) viewModel.prepare() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        viewModel.onDocumentPicked(uri)
    }
    val colors = appColors()
    val settingsInk = appDomain(AppDomain.Settings)

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppScreenHeader(
                title = "导出备份",
                subtitle = "生成 .elpbak 留档文件，写到你自己选的目录；App 自己不留副本。",
                onBack = onBack,
            )

            // ---------- 段一：包含 ----------
            SoftCard {
                Text(text = "包含内容", style = AppType.Body, fontWeight = FontWeight.Bold, color = colors.text1)
                Text(
                    text = state.disclosure,
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.testTag("backup_export_disclosure"),
                )
                Text(
                    text = "包含范围",
                    style = AppType.Label,
                    color = colors.text3,
                    modifier = Modifier.padding(top = 6.dp).testTag("backup_export_scope_header"),
                )
                // 范围逐条成行：行级几何（52dp 下限 + 发丝线 + 52dp 缩进）交给共享裸行，
                // 不再自排「· 标题 —— 说明」的字符串清单。四条都不可点，故 showChevron = false。
                ExportScopeRows.forEachIndexed { index, row ->
                    BareRow(
                        title = row.title,
                        subtitle = row.note,
                        showChevron = false,
                        topDivider = index > 0,
                        modifier = Modifier.testTag("backup_export_scope_${row.tag}"),
                    )
                }
            }

            // ---------- 段二：不包含（音频例外只在这里讲一次） ----------
            SoftCard {
                Text(text = "不包含", style = AppType.Body, fontWeight = FontWeight.Bold, color = colors.text1)
                Text(
                    text = "默认不包含 AI 音频文件；勾选下面的开关才会一起打包，" +
                        "而校验失败的音频会中止导出，不会静默跳过。",
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.testTag("backup_export_audio_note"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "一起导出 AI 音频文件",
                        style = AppType.Body,
                        color = colors.text1,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.includeAudio,
                        onCheckedChange = { viewModel.setIncludeAudio(it) },
                        modifier = Modifier
                            .testTag("backup_export_audio_switch")
                            .semantics { contentDescription = "包含 AI 音频文件" },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = settingsInk.base,
                            uncheckedTrackColor = colors.tint3,
                        ),
                    )
                }
            }

            // 次键：改过范围后旧包失效，重建是修正动作。文字档不抢吸底槽的焦点。
            // 贴右收窄、不 `fillMaxWidth()`：满宽的文字档与吸底主键同宽，真机上会被读成「第二枚快捷键」。
            PillButton(
                text = "生成备份包",
                onClick = { viewModel.prepare() },
                modifier = Modifier.align(Alignment.End),
                style = PillStyle.Text,
                domain = AppDomain.Settings,
                enabled = !state.busy,
                testTag = "backup_export_prepare",
            )

            // Task 25：私有错误/成功提示条换共享 `InlineNotice`（棘轮「错误提示条」族摘行同批）。
            state.error?.let { InlineNotice(text = it, kind = InlineNoticeKind.Error) }
            state.message?.let { InlineNotice(text = it, kind = InlineNoticeKind.Success) }
        }

        // 吸底主操作槽：不透明 `Surface`，内容从它下面穿过（不叠字）。状态行与主键同槽——
        // 「共 N 条 · M 个音频 · 约 X KB」讲的就是这一步要写出去的东西，分开放要用户上下对读。
        // 槽色与屏体软卡同为 `colors.surface`，故槽顶压一条发丝线：2× 字号下长文正好断在槽沿上，
        // 没有边界线时会被读成「卡片内容被截断」。
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                color = colors.surface,
                modifier = hairlineTop(colors.outline)
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExportProgressLine(state = state)
                    PillButton(
                        testTag = "backup_export_save",
                        text = "选择保存位置并导出",
                        onClick = { launcher.launch(state.fileName ?: "backup.elpbak") },
                        modifier = Modifier.fillMaxWidth(),
                        style = PillStyle.Primary,
                        domain = AppDomain.Settings,
                        enabled = state.packageReady && !state.busy,
                    )
                }
            }
        }
    }
}

/**
 * 吸底槽的状态行：忙 → 一句进度；就绪 → 总量估算 + 建议文件名；否则 → 一句引导。
 *
 * 三态共用一个槽位（不叠成三枚条件块）：主键本身随 `enabled` 变灰，状态行只负责解释「现在能不能按」。
 */
@Composable
private fun ExportProgressLine(state: BackupExportUiState) {
    val colors = appColors()
    when {
        state.busy -> Text(
            text = "正在生成备份包…",
            style = AppType.Footnote,
            color = colors.text2,
            modifier = Modifier.testTag("backup_export_busy"),
        )

        state.packageReady -> {
            Text(
                text = state.summary,
                style = AppType.Footnote,
                color = colors.text1,
                modifier = Modifier.testTag("backup_export_summary"),
            )
            state.fileName?.let { name ->
                Text(
                    text = "文件名：$name",
                    style = AppType.Label,
                    color = colors.text2,
                    modifier = Modifier.testTag("backup_export_filename"),
                )
            }
        }

        else -> Text(
            text = "先确认上面的范围，再生成备份包。",
            style = AppType.Footnote,
            color = colors.text2,
        )
    }
}

private data class ExportScopeRow(val title: String, val note: String, val tag: String)

/**
 * 段一的四条范围行。**不含「音频」二字**：音频是段二的例外条件，同一件事讲两遍就分不清哪句是条件
 * （旧清单里那行「已缓存 AI 音频 —— 可通过上方开关排除」正是这个问题）。
 */
private val ExportScopeRows = listOf(
    ExportScopeRow("词书与学习配置", "本机词书选择、每日目标与学习设置", "config"),
    ExportScopeRow("学习进度与复习状态", "三档反馈历史与 FSRS 复习状态", "progress"),
    ExportScopeRow("收藏与笔记", "查词历史与词卡 AI 问答笔记", "notes"),
    ExportScopeRow("AI 配置", "已保存的服务地址、模型与参数（不含密钥）", "ai"),
)
