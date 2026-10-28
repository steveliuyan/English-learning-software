package com.example.englishlearning.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.englishlearning.export.WorksheetPreviewRenderer
import com.example.englishlearning.learning.worksheet.WorksheetDirection
import com.example.englishlearning.learning.worksheet.WorksheetPage
import com.example.englishlearning.learning.worksheet.WorksheetTemplate
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.hairlineTop
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 导出前的强制预览：直接显示应用自己渲染出来的 PDF 页面图像，所见即所得。
 *
 * 预览的图片来源就是 `renderedFile`，与「导出」交给系统的是同一个文件，
 * 因此三份模板在预览里天然长得不一样，也不存在「预览和 PDF 不一致」。
 *
 * ## 版式（批 2 Task 5 / spec §3、§5）
 *
 * 三层：共享页头（返回位 + 「预览默写纸」）→ `weight(1f)` 的唯一滚动正文（页面列表）→
 * `PreviewActionBar` 吸底。旧版没有页头、标题是自己写的一行 `headlineSmall`，「返回修改」
 * 与「导出 PDF」并排躺在底部同一行里——想回上一页得先滚到页面尽头，且返回被摆成与导出同级的动作。
 *
 * 页纸底色取 `appColors().surface` 而不是 `Color.White`：预览里那层平台的意义是「一张纸」，
 * 深色主题下纯白纸会自己发光，与阅读区、卡片用的是两套配色语言。
 */
@Composable
fun WorksheetPreviewScreen(
    pages: List<WorksheetPage>,
    renderedFile: File?,
    rendering: Boolean,
    message: String?,
    onBack: () -> Unit,
    onExport: () -> Unit,
) {
    val colors = appColors()
    val context = LocalContext.current
    val pageImages by produceState(initialValue = emptyList<ImageBitmap>(), renderedFile) {
        value = renderedFile?.let { file ->
            withContext(Dispatchers.IO) {
                WorksheetPreviewRenderer(context).render(file, maxWidth = PREVIEW_MAX_WIDTH)
                    .map { it.asImageBitmap() }
            }
        }.orEmpty()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("worksheet_preview_screen"),
    ) {
        AppScreenHeader(
            title = "预览默写纸",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (rendering) "正在生成 PDF 预览…" else "共 ${pageImages.size} 页",
                    style = AppType.Footnote,
                    color = colors.text2,
                )

                message?.let { text ->
                    InlineNotice(text = text, kind = InlineNoticeKind.Error)
                }

                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    when {
                        rendering || pageImages.isEmpty() && message == null ->
                            CircularProgressIndicator(color = colors.strongSurface)
                        pageImages.isEmpty() -> Text(
                            text = "没有可预览的页面。",
                            style = AppType.Body,
                            color = colors.text2,
                        )
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("worksheet_preview_pages"),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            itemsIndexed(pageImages) { index, image ->
                                Column(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = pageLabel(index, pages.getOrNull(index)),
                                        style = AppType.Label,
                                        color = colors.text2,
                                    )
                                    Image(
                                        bitmap = image,
                                        contentDescription = "第 ${index + 1} 页预览",
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                            .clip(AppShape.Card)
                                            .background(colors.surface)
                                            .aspectRatio(image.width.toFloat() / image.height.toFloat()),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        PreviewActionBar(
            enabled = !rendering && renderedFile != null,
            onExport = onExport,
        )
    }
}

/**
 * 吸底动作条：只有一枚主操作。
 *
 * 旧版这一行是两枚实底按钮（「返回修改」浅底 + 「导出 PDF」深底），返回已上移到页头，
 * 这里就只剩导出；条底铺 `colors.surface`，与滚动正文的 `background` 分层，故条顶压一条发丝线
 * （批 5 遗留 7 收口，与 `BackupExportScreen.kt:221` 同形）：预览图滚到条沿时没有线会被读成「被截断」。
 */
@Composable
private fun PreviewActionBar(
    enabled: Boolean,
    onExport: () -> Unit,
) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .then(hairlineTop(colors.outline))
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("worksheet_preview_action_bar"),
    ) {
        Button(
            onClick = onExport,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("worksheet_export_action"),
            shape = AppShape.Button,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.strongSurface,
                contentColor = colors.onStrong,
                disabledContainerColor = colors.tint3,
                disabledContentColor = colors.text3,
            ),
        ) {
            Text(
                text = "导出 PDF",
                style = AppType.Label,
                color = if (enabled) colors.onStrong else colors.text3,
            )
        }
    }
}

private fun pageLabel(index: Int, page: WorksheetPage?): String {
    val label = when {
        page == null -> "纸张"
        page.answers.isNotEmpty() -> "答案页"
        page.template == WorksheetTemplate.FULL_LIST -> "我的词表"
        page.template == WorksheetTemplate.EBBINGHAUS_REVIEW -> "艾宾浩斯抗遗忘"
        page.direction == WorksheetDirection.EN_TO_ZH -> "拼写测试 · 英译中"
        else -> "拼写测试 · 中译英"
    }
    return "第 ${index + 1} 页 · $label"
}

private const val PREVIEW_MAX_WIDTH = 1000
