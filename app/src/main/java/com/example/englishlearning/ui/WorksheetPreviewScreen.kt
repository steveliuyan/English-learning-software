package com.example.englishlearning.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.export.WorksheetPreviewRenderer
import com.example.englishlearning.learning.worksheet.WorksheetDirection
import com.example.englishlearning.learning.worksheet.WorksheetPage
import com.example.englishlearning.learning.worksheet.WorksheetTemplate
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 导出前的强制预览：直接显示应用自己渲染出来的 PDF 页面图像，所见即所得。
 *
 * 预览的图片来源就是 `renderedFile`，与「导出」交给系统的是同一个文件，
 * 因此三份模板在预览里天然长得不一样，也不存在「预览和 PDF 不一致」。
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
            .background(MintBackground)
            .padding(20.dp)
            .testTag("worksheet_preview_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = MintPrimaryDark)) {
            Text("← 返回修改", fontWeight = FontWeight.Bold)
        }
        Text("预览默写纸", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text(
            text = if (rendering) "正在生成 PDF 预览…" else "共 ${pageImages.size} 页 · 以下就是将要导出的 PDF 页面",
            color = MintTextMuted,
        )
        message?.let { Text(it, color = MintPrimaryDark, style = MaterialTheme.typography.bodySmall) }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when {
                rendering || pageImages.isEmpty() && message == null -> CircularProgressIndicator(color = MintPrimary)
                pageImages.isEmpty() -> Text("没有可预览的页面。", color = MintTextMuted)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("worksheet_preview_pages"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(pageImages) { index, image ->
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                pageLabel(index, pages.getOrNull(index)),
                                color = MintPrimaryDark,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Image(
                                bitmap = image,
                                contentDescription = "第 ${index + 1} 页预览",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(image.width.toFloat() / image.height.toFloat())
                                    .padding(top = 6.dp)
                                    .background(Color.White, RoundedCornerShape(6.dp)),
                            )
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
            ) { Text("返回修改") }
            Button(
                onClick = onExport,
                enabled = !rendering && renderedFile != null,
                modifier = Modifier.weight(1f).height(52.dp).testTag("worksheet_export_action"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MintPrimary,
                    contentColor = Color.White,
                    disabledContainerColor = MintSurface,
                    disabledContentColor = MintTextMuted,
                ),
            ) { Text("导出 PDF", fontWeight = FontWeight.Bold) }
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
