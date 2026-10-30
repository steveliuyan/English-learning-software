package com.example.englishlearning.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.WordBookProgress
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.InlineIconBadge
import com.example.englishlearning.ui.components.SectionHeading
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.StrongCard
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import kotlin.math.roundToInt

/**
 * 词书详情（批 1 Task 15，原型 `sBookDetail` `assets/app.js:608-640`）。
 *
 * 位置：词库页「词书」段的**默认面**（详情 ↔ 词单浏览两态，由宿主该段自己切）。
 * 页头归 [WordBankScreen] 壳（「词库」+ 当前词书名 + 返回位），本屏只出正文：
 * 强卡（进度环 + 正在使用 + 书名 + 已学 x / y 词）→「这本书的情况」→「操作」。
 *
 * 与原型的三处结构性差别（理由与判据见 `BookDetailScreenContractTest` 文件头）：
 *  1. `.strong-foot`「按每天 4 个新词，还剩 N 天」整句不搬——「这本书每天学几个」本仓没有读数；
 *  2. 「这本书的情况」不留 `已掌握 / 词书版本` 两行：本仓只有 `未学 / 学习中 / 待复习` 三态，
 *     词书版本也没传到界面；
 *  3. 「操作」只留「查看完整词单 / 更换词书」：按书导出进度、按书删进度两项能力本仓不存在，
 *     页头 `more` 菜单随之不画（它唯一的内容就是那枚删进度）。
 *
 * [progress] / [learningCount] / [dueCount] 为 `null` 时**整行不画**：词单还没读出来（或还没选择词书）
 * 时摆一个「0」就是把「不知道」说成「没有」，与「读不到的那枚整枚不显示」同口径。
 */
@Composable
fun BookDetailScreen(
    bookName: String,
    progress: WordBookProgress?,
    learningCount: Int?,
    dueCount: Int?,
    onOpenWordList: () -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("book_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 原型 `.strong-card.on-strong`（`app.css:383-421`）：强卡分套墨由容器给，
        // 卡内正文一律显式取 `LocalContentColor.current`（`AppType` 各档自带浅套静态墨，压深底不可读）。
        StrongCard(modifier = Modifier.testTag("book_detail_summary")) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BookProgressRing(
                    learned = progress?.learned ?: 0,
                    total = progress?.total ?: 0,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "正在使用",
                        style = AppType.Eyebrow,
                        color = LocalContentColor.current.copy(alpha = 0.74f),
                    )
                    Text(
                        bookName,
                        style = AppType.StrongTitle,
                        color = LocalContentColor.current,
                        maxLines = 2,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (progress != null) {
                        Text(
                            "已学 ${progress.learned} / ${progress.total} 词",
                            style = AppType.Meta,
                            color = LocalContentColor.current.copy(alpha = 0.74f),
                            modifier = Modifier.padding(top = 4.dp).testTag("book_detail_learned"),
                        )
                    }
                }
            }
        }
        SectionHeading(text = "这本书的情况")
        SoftCard(contentPadding = PaddingValues(0.dp)) {
            Column(Modifier.fillMaxWidth()) {
                if (progress != null) {
                    BookFactRow(title = "已学", value = "${progress.learned}")
                    BookFactRow(title = "未学", value = "${progress.unlearned}", topDivider = true)
                }
                if (learningCount != null) {
                    BookFactRow(title = "学习中", value = "$learningCount", topDivider = progress != null)
                }
                if (dueCount != null) {
                    BookFactRow(title = "待复习", value = "$dueCount", topDivider = progress != null || learningCount != null)
                }
            }
        }
        SectionHeading(text = "操作")
        SoftCard(contentPadding = PaddingValues(0.dp)) {
            Column(Modifier.fillMaxWidth()) {
                BareRow(
                    title = "查看完整词单",
                    subtitle = "按字母与学习状态排序",
                    onClick = onOpenWordList,
                    leading = { InlineIconBadge(icon = AppIcon.List, domain = AppDomain.Learn) },
                    modifier = Modifier.testTag("book_detail_open_word_list"),
                )
                BareRow(
                    title = "更换词书",
                    subtitle = "换一本词书继续学",
                    topDivider = true,
                    onClick = onOpenPicker,
                    leading = { InlineIconBadge(icon = AppIcon.BookOpen, domain = AppDomain.Library) },
                    modifier = Modifier.testTag("book_detail_open_picker"),
                )
            }
        }
    }
}

/** 「这本书的情况」的一行（原型 `.row.row-flat`：左标题、右 `.row-value`，非可点）。 */
@Composable
private fun BookFactRow(title: String, value: String, topDivider: Boolean = false) {
    val colors = appColors()
    Column(Modifier.fillMaxWidth()) {
        if (topDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 1.dp)
                    .background(colors.outlineSoft),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = AppType.Body,
                color = colors.text1,
                modifier = Modifier.weight(1f),
            )
            Text(text = value, style = AppType.Meta, color = colors.text3)
        }
    }
}

/**
 * 进度环（原型 `ring(done, total, 86)`，`app.js:186-195` / `app.css:653-668`）：
 * 86dp、5dp 描边、底环 16% 透明度、进度弧圆头、环心 serif 百分比。
 *
 * 墨一律取 `LocalContentColor.current`：本件只长在 `StrongCard` 里，分套墨由容器给。
 * 百分比字号取 `AppType.StatNum`（26sp serif）——原型 `serif 21px` 在六档锁里没有对应档，
 * 登记为 token 近似（`BookDetailScreenContractTest` 第 5 条偏离）。
 */
@Composable
private fun BookProgressRing(learned: Int, total: Int, modifier: Modifier = Modifier) {
    val ink = LocalContentColor.current
    val fraction = if (total > 0) (learned.toFloat() / total).coerceIn(0f, 1f) else 0f
    Box(
        modifier = modifier
            .size(86.dp)
            .testTag("book_detail_ring")
            .semantics { contentDescription = "已学 ${(fraction * 100).roundToInt()}%" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = 5.dp.toPx()
            val diameter = size.minDimension - strokePx
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = ink.copy(alpha = 0.16f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            if (fraction > 0f) {
                drawArc(
                    color = ink,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${(fraction * 100).roundToInt()}",
                style = AppType.StatNum,
                color = ink,
            )
            Text(
                text = "%",
                style = AppType.Footnote,
                color = ink.copy(alpha = 0.74f),
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}
