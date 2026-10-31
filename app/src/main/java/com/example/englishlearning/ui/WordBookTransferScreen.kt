package com.example.englishlearning.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.EntryIconTone
import com.example.englishlearning.ui.components.EntryRow
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.GlassDialog
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.wordbook.WordBookTransferViewModel

/**
 * 「词书内容包」子页（决策 §4）：`.wbpack` 的导入与导出。
 *
 * 2026-11-02 前它是设置页里一枚折叠行 + 宿主持有的一枚导出选择对话框。这一批把**整条 SAF 通路**
 * （两枚 launcher、选册对话框、忙碌禁用与结果提示）搬进本屏：文件选择器的 owner 与消费屏同层，
 * 与 `BackupExportScreen` / `BackupRestoreScreen` 的既有分工一致，宿主因此不再持有选择器状态。
 * 通路本体一字未改：`.wbpack` 是私有 zip 格式，系统没有它的注册类型，所以导入用 `OpenDocument()`
 * 配通配 MIME（星号斜杠星号），导出用 `CreateDocument("application/zip")`（落盘名由用户在这些界面里最终决定）。
 *
 * 可空 ViewModel 是生产组装点：为 null 时两枚子行**真禁用**（`enabled = false`）并给出说明，
 * 不留「点了什么也没发生」的假入口（防回归约定见 `AppScreenProductionWiringTest`）。
 */
@Composable
fun WordBookTransferScreen(
    onBack: () -> Unit,
    viewModel: WordBookTransferViewModel? = null,
) {
    val colors = appColors()
    val state = viewModel?.uiState?.collectAsState()?.value
    val books = state?.books.orEmpty()
    val busy = state?.busy == true || viewModel == null
    var showExportPicker by rememberSaveable { mutableStateOf(false) }
    var exportWordBookId by rememberSaveable { mutableStateOf<String?>(null) }
    // SAF 的导入/导出：文件位置由用户选，应用不申请任何存储权限。
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel?.importFrom(it) }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val bookId = exportWordBookId
        exportWordBookId = null
        uri?.let { selectedUri -> bookId?.let { viewModel?.exportTo(selectedUri, it) } }
    }
    LaunchedEffect(Unit) { viewModel?.refresh() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("word_book_transfer_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = "词书内容包",
            subtitle = "导入 / 导出 .wbpack · 全程离线",
            onBack = onBack,
        )
        SoftCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            EntryRow(
                icon = AppIcon.Upload,
                domain = AppDomain.Library,
                title = "导入词书",
                subtitle = when {
                    viewModel == null -> "这台设备暂时读不到词书目录"
                    busy -> "正在处理，请稍候…"
                    else -> "选择 .wbpack 文件，校验通过后加入本机词书"
                },
                iconTone = EntryIconTone.Tint,
                enabled = viewModel != null && !busy,
                modifier = Modifier.testTag("word_book_transfer_import"),
                onClick = {
                    viewModel?.consumeMessage()
                    importLauncher.launch(arrayOf("*/*"))
                },
            )
            EntryRow(
                icon = AppIcon.Download,
                domain = AppDomain.Library,
                title = "导出词书",
                subtitle = when {
                    viewModel == null -> "这台设备暂时读不到词书目录"
                    busy -> "正在处理，请稍候…"
                    books.isNotEmpty() -> "已导入 ${books.size} 册，导出为 .wbpack 分享给别的设备"
                    else -> "先导入一册词书后才能导出"
                },
                iconTone = EntryIconTone.Tint,
                topDivider = true,
                enabled = viewModel != null && !busy && books.isNotEmpty(),
                modifier = Modifier.testTag("word_book_transfer_export"),
                onClick = {
                    if (state?.canExport == true) {
                        viewModel?.consumeMessage()
                        if (books.size == 1) {
                            exportWordBookId = books.single()
                            exportLauncher.launch("${books.single()}.wbpack")
                        } else {
                            showExportPicker = true
                        }
                    }
                },
            )
        }
        // 结果提示挂在卡外侧：回执是「上一次操作的结果」，不该被卡片内边距吞掉首行（同旧设置页 I4 的口径）。
        state?.message?.let { message ->
            InlineNotice(text = message, kind = InlineNoticeKind.Info, modifier = Modifier.padding(top = 2.dp))
        }
        if (showExportPicker) {
            // 路 B（非破坏性选择）：容器交房内共享玻璃档，取消走共享 PillButton；
            // 列表项是本枚自渲染的（tag 供 connected 逐项定位），口径与搬离宿主前逐字一致。
            GlassDialog(onDismiss = { showExportPicker = false }) {
                Text(text = "选择要导出的词书", style = AppType.Headline, color = appColors().text1)
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    books.forEach { bookId ->
                        Text(
                            text = bookId,
                            style = AppType.Body,
                            color = appColors().text1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    exportWordBookId = bookId
                                    showExportPicker = false
                                    exportLauncher.launch("$bookId.wbpack")
                                }
                                .padding(vertical = 14.dp)
                                .testTag("word_book_export_pick_$bookId"),
                        )
                    }
                }
                PillButton(
                    text = "取消",
                    onClick = { showExportPicker = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    style = PillStyle.Secondary,
                    domain = AppDomain.Settings,
                    testTag = "word_book_export_cancel",
                )
            }
        }
    }
}
