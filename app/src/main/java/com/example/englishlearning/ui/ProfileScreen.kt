package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.EntryIconBadge
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.StrongCard
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 「我的」一级页（spec §3.6，2026-10-31 修订）：档案头 → 三格统计 → 学习记录行。
 *
 * 为什么这一页只放「进度」：2026-10-31 用户拍板把设置从本页里拆出去、升成第五个一级 tab
 * （`docs/decisions/2026-10-31-settings-as-fifth-tab.md`）。此前本页同时承担「我的」与「设置」，
 * 于是「关于 / 许可 / 备份」既像设置又像信息，在关于页里长出了第二份入口。现在两条语义各归各页。
 *
 * 版面口径（与三档容器同一套语义）：
 *  - 三格统计住**唯一一枚**强档卡（§4.1 一屏至多一枚强卡）——它是这一页的主信息；
 *  - 学习记录是软档卡里的一条 [BareRow]（不是又一张描边卡）；
 *  - 本页**不**自带设置分组、不持有设置内容的滚动容器与页头：设置归 `SettingsScreen`。
 *
 * 三格各自是深链，不是死数字：
 *  - 已学词 → 词库（词书段）：用户看到 42 的第一个动作通常是「看看是哪 42 个」；
 *  - 连续天数 → 打卡页：连续天数在那页有逐日明细；
 *  - 文章 → 阅读历史：历史与解锁状态正交，锁定态也读得到。
 *
 * 数字只读宿主传进来的真值：本页不读仓库、不拼统计句子（口径同 `TodayPlanScreen` 的
 * `streakDays` / `todayArticleRead`）。`ProfilePageContractTest` 钉三格锚与三枚回调，
 * connected `ProfileScreenTest` 钉「点了真的去哪」。
 */
@Composable
fun ProfileScreen(
    profileName: String,
    streakDays: Int,
    learnedWordCount: Int,
    articleCount: Int,
    onOpenWordBank: () -> Unit,
    onOpenCheckIn: () -> Unit,
    onOpenReadingHistory: () -> Unit,
    onOpenLearningRecords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            // 底部导航会吃掉一截高度，小屏或大字号下整页必须可滚动，否则最后一组被裁掉。
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("profile_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = profileName,
            // 原型 `.head-sub`（`app.js:1257`）：「连续打卡 N 天 · 累计学过 N 个词」——两枚都是本页收到的真值。
            // 旧副标题那句话（「API Key 只保存在本机加密区」）是隐私口径，不属于页头身份行：
            // 它已在设置页与备份说明里出现，挂在名字下面既不回答问题也不构成信息。
            subtitle = "连续打卡 $streakDays 天 · 累计学过 $learnedWordCount 个词",
            hero = true,
            badge = { EntryIconBadge(icon = AppIcon.Person, domain = AppDomain.Settings) },
        )
        StrongCard(modifier = Modifier.testTag("profile_stats")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileStatCell("已学词", learnedWordCount.toString(), "profile_stat_learned", onOpenWordBank, Modifier.weight(1f))
                ProfileStatCell("连续天数", streakDays.toString(), "profile_stat_streak", onOpenCheckIn, Modifier.weight(1f))
                ProfileStatCell("文章", articleCount.toString(), "profile_stat_articles", onOpenReadingHistory, Modifier.weight(1f))
            }
        }
        SoftCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            BareRow(
                title = "学习记录",
                subtitle = "今日已学与历史",
                onClick = onOpenLearningRecords,
                modifier = Modifier.testTag("profile_open_learning_records"),
            )
        }
    }
}

/**
 * 一格格统计：数值在上、标签在下，整格可点。
 *
 * 命中区 `heightIn(min = 72.dp)`：远高于 48dp 水线（F5-03），因为这一格同时要装 Headline 数字与
 * Footnote 标签；把它做成 48dp 会把两行字挤成一团。
 * TalkBack 读「标签，当前 数值」一整句（[contentDescription]），而不是先念一个孤零零的数字。
 * 「当前」这两个字不是文案口味，是 `AccessibilityLabelContractTest` 禁令1a 的硬要求：字面量里必须含
 * **汉字**（U+4E00–U+9FFF），纯插值模板与全角标点都不算——所以不能写成 `"$label，$value"`。
 * 墨色取容器内容色 `LocalContentColor.current`（2026-10-31 用户评审 ②）：这一格只住在 [StrongCard] 里，
 * 而强档卡的底与墨是**分套**的（浅套 `tint2` 底 + `text1` 墨、深套 `strongSurface` 底 + `onStrong` 墨）。
 * 写死 `onStrong`（`#E9F2EC`）在浅套就是浅薄荷底上的近白字，实测不可读；取容器内容色则两套自动到位。
 */
@Composable
private fun ProfileStatCell(
    label: String,
    value: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(vertical = 10.dp, horizontal = 6.dp)
            .testTag(tag)
            .semantics { contentDescription = "$label，当前 $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = AppType.Headline, color = LocalContentColor.current)
        Text(label, style = AppType.Footnote, color = LocalContentColor.current.copy(alpha = 0.72f))
    }
}
