package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.InlineIconBadge
import com.example.englishlearning.ui.components.QuietCard
import com.example.englishlearning.ui.components.SectionHeading
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.Sticker
import com.example.englishlearning.ui.components.StickerBadge
import com.example.englishlearning.ui.components.StickerSize
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 关于（批 2 Task 22-B，原型 `sMeAbout` `assets/app.js:1366-1384`）。
 *
 * 新建屏（用户 2026-10-29 拍板的五枚之一）：原型画了、本仓此前没有独立关于页
 * （`关于` 只在设置页里出现过一次）。骨架逐段对原型：`barTop('关于')` → 应用块
 * （56dp `books` 贴纸 + 应用名 + 「版本 x · 单机优先」）→ 一句 `.meta` → `section('说明')`
 * → `section('致谢', .card-quiet)`。
 *
 * 三处按「读不到就整枚不显示 / 不写不成立的话」处理：
 *  1. 「版本 2026.11」取 `versionName` **形参**（宿主从构建信息给），屏内不编版本号；
 *  2. 「6 项第三方组件与内容来源」只在 [licenseCount] 非空时拼进去（数目由许可清单解析结果给），
 *     读不到就只写「第三方组件与内容来源」；
 *  3. 「致谢」那段只留**本仓成立**的两句（图标自制、字体 Newsreader / OFL）；原型里
 *     「示例文章由本地模型提供」与本仓不符（文章由用户配置的 AI 服务按需生成），不搬。
 *
 * 2026-10-31 去重（`docs/decisions/2026-10-31-settings-as-fifth-tab.md`）：说明区从三行收成一行。
 *  - 「隐私说明」死行（`onClick = null`，只有副标题没有落点）删除——隐私说明本身是许可页的
 *    第一段，而本页已有 `about_privacy_line` 一句话概述，再画一枚不可点的行只是诱饵；
 *  - 「备份与恢复」行删除——备份的唯一入口在 设置 → 数据与安全，关于页不是备份的入口；
 *  - 「开源许可与来源」改名「开源许可与隐私说明」，与许可页标题逐字一致：入口名与落地页名
 *    指向同一件事，用户不必在两个近义名之间猜是不是两页。
 */
@Composable
fun AboutScreen(
    versionName: String,
    licenseCount: Int?,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("about_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(title = "关于", onBack = onBack)
        Row(
            modifier = Modifier.fillMaxWidth().testTag("about_app_block"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StickerBadge(sticker = Sticker.Books, size = StickerSize.Large, dimension = 56.dp, corner = 16.dp)
            Column {
                Text("英语学习", style = AppType.Title, color = colors.text1)
                Text("版本 $versionName · 单机优先", style = AppType.Caption, color = colors.text3)
            }
        }
        Text(
            text = "背词、复习、阅读与 AI 助学合成一个应用：所有学习记录存在本机，" +
                "只有你主动使用 AI 功能时才会把必要的文字发出去。",
            style = AppType.Meta,
            color = colors.text2,
            modifier = Modifier.testTag("about_privacy_line"),
        )
        SectionHeading(text = "说明")
        SoftCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            BareRow(
                title = "开源许可与隐私说明",
                subtitle = if (licenseCount != null) "$licenseCount 项第三方组件与内容来源" else "第三方组件与内容来源",
                leading = { InlineIconBadge(icon = AppIcon.Document, domain = AppDomain.Settings) },
                modifier = Modifier.testTag("about_licenses_row"),
                onClick = onOpenLicenses,
            )
        }
        SectionHeading(text = "致谢")
        QuietCard {
            Text(
                text = "图标为本项目自制，字体使用 Newsreader（SIL OFL 1.1）。",
                style = AppType.Caption,
                color = colors.text3,
            )
        }
    }
}
