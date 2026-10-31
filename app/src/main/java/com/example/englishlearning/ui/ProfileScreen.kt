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
import com.example.englishlearning.ui.components.EntryIconBadge
import com.example.englishlearning.ui.components.EntryIconTone
import com.example.englishlearning.ui.components.EntryRow
import com.example.englishlearning.ui.components.SectionHeading
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.StrongCard
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 「我的」hub 的一行（静态描述）。
 *
 * [subtitle] 可为 `null`：那三行的副标题是**读得到的真值**（词书名 / AI 配置摘要），必须在装配点
 * 现算而不是写死；其余行是常量说明，直接住在台账里。
 */
data class ProfileRow(
    val title: String,
    val tag: String,
    val icon: AppIcon,
    val domain: AppDomain,
    val subtitle: String?,
    val iconTone: EntryIconTone = EntryIconTone.Tint,
)

/** 「我的」hub 的一个分组（标题 + 行清单）。 */
data class ProfileGroup(
    val title: String,
    val tag: String,
    val rows: List<ProfileRow>,
)

/**
 * 「我的」功能中心的唯一台账（`docs/decisions/2026-11-02-profile-hub-and-four-tabs.md` §2）：
 * **恰 3 组、恰 13 行**（我的词汇 3 / 学习 5 / 应用 5）。
 *
 * 形状逐字取自参考 `sMeHome`（`assets/app.js:1255-1282`）的三个 `section(...)`；本应用比参考多出的
 * 两行（生成默写纸 / 词书内容包）插在「学习」组，因为它们是**已实现**的能力，砍掉就是功能倒退
 * （决策 §4「参考没有这两件，但本应用已实现，不能砍」）。
 *
 * 行标题、图标、域色、副标题与 testTag 只在**这里**定义一处，渲染侧按行取（沿用上一批
 * 「台账不是第二套真值」的纪律）。落点接线在 [ProfileScreen] 的装配点按**行对象**匹配
 * （不是按 tag 字面量再抄一遍，那样 tag 会出现两处、台账与接线谁改都不同步）。
 *
 * JVM 侧 `ProfileHubLedgerTest` 钉本表形状，`ProfilePageContractTest` 钉「每枚字面只写一次 +
 * 宿主逐行接线」；connected 侧 `ProfileScreenTest` 逐条核对这些 tag 真的渲染出来。
 */
object ProfileGroups {
    // U-8（决策 2026-11-03 §5）：三行行名统一到**词库分段的短名**（`WordBankSection.label`：收藏 / 生词 / 错词）。
    // 改前同一批词在三处三个名字——hub 这三行一套、分段一套、合并页的副标题与段卡标题又一套；
    // 用户从 hub 点第三行进到的那一格写的是「错词」，他记住的只有最浅那一层。
    // 分段是落点也是本仓这套词的术语源头，所以以它为准（入口本身按旧 D-6/D-7 裁定保留双入口，收的是名字不是路径）。
    // 旧名按本批口径在 `ui/` 整棵子树（含注释）归零：注释里的旧名就是下一批会照抄的模板。
    // 判据在 `WordListEntryNamesFollowBankSegmentsTest`：期望名从枚举体解析，分段改名时闸跟着走。
    val FavoriteWords = ProfileRow("收藏", "profile_open_favorite_words", AppIcon.Star, AppDomain.Review, "不排进复习 · 只做手动回看")
    val NewWords = ProfileRow("生词", "profile_open_new_words", AppIcon.Bookmark, AppDomain.Learn, "明天的词单里排到前面")
    val WrongWords = ProfileRow("错词", "profile_open_wrong_words", AppIcon.Warning, AppDomain.Review, "按答错次数排序 · 答对两次移出")
    val LearningRecords = ProfileRow("学习记录", "profile_open_learning_records", AppIcon.Chart, AppDomain.Learn, "今日与按日的完成情况")
    val LearningPlan = ProfileRow("学习计划", "profile_open_plan", AppIcon.List, AppDomain.Learn, null, EntryIconTone.Gradient)
    val WordBook = ProfileRow("词书", "profile_open_word_book", AppIcon.Book, AppDomain.Library, null)
    val Worksheet = ProfileRow("生成默写纸", "profile_open_worksheet", AppIcon.Document, AppDomain.Learn, "选中译英或英译中，预览后导出可打印 PDF")
    val WordBookTransfer = ProfileRow("词书内容包", "profile_open_word_book_transfer", AppIcon.Upload, AppDomain.Library, "导入 / 导出 .wbpack · 全离线")
    val AiProfiles = ProfileRow("AI 服务", "profile_open_ai_profiles", AppIcon.Spark, AppDomain.AiSpeech, "添加多套 OpenAI 兼容服务，管理 Endpoint、模型与密钥", EntryIconTone.Gradient)
    val ReminderSpeech = ProfileRow("提醒与发音", "profile_open_reminder_speech", AppIcon.Bell, AppDomain.Settings, "提醒时间 · 系统 TTS 与 AI 发音")
    val Settings = ProfileRow("设置", "profile_open_settings", AppIcon.Gear, AppDomain.Settings, "外观（跟随系统 / 浅色 / 深色）")
    val Backup = ProfileRow("数据与备份", "profile_open_backup", AppIcon.Shield, AppDomain.Settings, "导出本机文件、换机恢复与清空学习数据")
    // U-12（决策 2026-11-03 §5）：行名从旧的那枚「关于＋许可」并列名收窄成「关于」，与它打开的页同名。
    // 旧名把两跳说成一跳——第三方清单不住在关于页里，关于页只留一句进许可页的入口，
    // 用户在这行下面找不到「许可」那层会以为走错页；直跳许可页又会让「关于」这层没入口，
    // 所以保留两跳、只改行名（第二跳那句归关于页，hub 不复述）。判据在 `AboutRowNameIsJustAboutTest`。
    val About = ProfileRow("关于", "profile_open_about", AppIcon.Info, AppDomain.Settings, "版本、致谢与来源说明")

    val all: List<ProfileGroup> = listOf(
        ProfileGroup("我的词汇", "profile_group_vocabulary", listOf(FavoriteWords, NewWords, WrongWords)),
        ProfileGroup("学习", "profile_group_learning", listOf(LearningRecords, LearningPlan, WordBook, Worksheet, WordBookTransfer)),
        ProfileGroup("应用", "profile_group_application", listOf(AiProfiles, ReminderSpeech, Settings, Backup, About)),
    )
}

/**
 * 「我的」= 功能入口中心（决策 §2，2026-11-02 用户拍板按参考重做）。
 *
 * 版面：档案头（姓名 + 连续打卡/累计学过）→ 三格统计（唯一一枚强档卡）→ 我的词汇 / 学习 / 应用 三组行。
 * 上一批曾把本页瘦成「只剩进度」、把设置升成第五个一级 tab；那次改动已被否掉——
 * 「我的」与「设置」并列成两个 tab 是同一件事的两份入口，参考的做法是设置住在「我的」里。
 *
 * 三组行都是**导航行**，不改任何能力本体：词汇三行进词库页对应分段（`onOpenWordBankSection`，
 * 段位只在宿主一处写，见 `MyWordsScreenContractTest`），学习组进既有子页，应用组进既有子页与设置。
 * 因此本页**不**再内联设置内容（同屏两枚页头 + 两个滚动容器是旧形态），也**不**持有 SAF 与密钥读写。
 *
 * 数字与摘要只读宿主传进来的真值：本页不读仓库（口径同 `TodayPlanScreen`）。
 * `ProfileHubLedgerTest` / `ProfilePageContractTest` 钉台账与接线，
 * connected `ProfileScreenTest` + `AppScreenTest` 钉「点了真的去哪」。
 *
 * @param wordBookName 活动词书名；`null` = 还没选词书。学习计划与词书两行的副标题据此表态。
 * @param aiProfileSubtitle 宿主读出的 AI 配置摘要；`null` 时落台账里的引导文案。
 */
@Composable
fun ProfileScreen(
    profileName: String,
    streakDays: Int,
    learnedWordCount: Int,
    articleCount: Int,
    onOpenWordBankSection: (WordBankSection) -> Unit,
    onOpenCheckIn: () -> Unit,
    onOpenReadingHistory: () -> Unit,
    onOpenLearningRecords: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenWorksheet: () -> Unit,
    onOpenWordBookTransfer: () -> Unit,
    onOpenAiProfiles: () -> Unit,
    onOpenReminderSpeech: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackupHub: () -> Unit,
    onOpenAbout: () -> Unit,
    wordBookName: String? = null,
    aiProfileSubtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            // 底部导航会吃掉一截高度，13 行在小屏或大字号下必须整页可滚，否则最后一组被裁掉。
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("profile_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(
            title = profileName,
            // 原型 `.head-sub`（`app.js:1257`）：「连续打卡 N 天 · 累计学过 N 个词」——两枚都是本页收到的真值。
            subtitle = "连续打卡 $streakDays 天 · 累计学过 $learnedWordCount 个词",
            hero = true,
            badge = { EntryIconBadge(icon = AppIcon.Person, domain = AppDomain.Settings) },
        )
        StrongCard(modifier = Modifier.testTag("profile_stats")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileStatCell("已学词", learnedWordCount.toString(), "profile_stat_learned", { onOpenWordBankSection(WordBankSection.Book) }, Modifier.weight(1f))
                ProfileStatCell("连续天数", streakDays.toString(), "profile_stat_streak", onOpenCheckIn, Modifier.weight(1f))
                ProfileStatCell("文章", articleCount.toString(), "profile_stat_articles", onOpenReadingHistory, Modifier.weight(1f))
            }
        }
        ProfileGroups.all.forEach { group ->
            SectionHeading(text = group.title, modifier = Modifier.testTag(group.tag))
            SoftCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                group.rows.forEachIndexed { index, row ->
                    ProfileLedgerEntry(
                        row = row,
                        topDivider = index > 0,
                        wordBookName = wordBookName,
                        aiProfileSubtitle = aiProfileSubtitle,
                        onOpenWordBankSection = onOpenWordBankSection,
                        onOpenLearningRecords = onOpenLearningRecords,
                        onOpenPlan = onOpenPlan,
                        onOpenWorksheet = onOpenWorksheet,
                        onOpenWordBookTransfer = onOpenWordBookTransfer,
                        onOpenAiProfiles = onOpenAiProfiles,
                        onOpenReminderSpeech = onOpenReminderSpeech,
                        onOpenSettings = onOpenSettings,
                        onOpenBackupHub = onOpenBackupHub,
                        onOpenAbout = onOpenAbout,
                    )
                }
            }
        }
    }
}

/**
 * 台账行 → [EntryRow] 的唯一装配点：图标、域色、标题与 testTag 全部取自 [ProfileRow]，
 * 调用处只按**行对象**给出落点与动态副标题。
 *
 * 落点用 `when (row)` 而不是 `when (row.tag)`：tag 字面量在台账里已经写过一次，装配点再写就是第二套
 * 真值（改一处不改另一处会静默失联）。按行对象匹配则台账与接线共用同一个值。
 * 匹配不到即 `error`——宁可当场点名，也不留「画出来了但点不动」的死行。
 */
@Composable
private fun ProfileLedgerEntry(
    row: ProfileRow,
    topDivider: Boolean,
    wordBookName: String?,
    aiProfileSubtitle: String?,
    onOpenWordBankSection: (WordBankSection) -> Unit,
    onOpenLearningRecords: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenWorksheet: () -> Unit,
    onOpenWordBookTransfer: () -> Unit,
    onOpenAiProfiles: () -> Unit,
    onOpenReminderSpeech: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackupHub: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val onClick: () -> Unit = when (row) {
        ProfileGroups.FavoriteWords -> { -> onOpenWordBankSection(WordBankSection.Favorite) }
        ProfileGroups.NewWords -> { -> onOpenWordBankSection(WordBankSection.New) }
        ProfileGroups.WrongWords -> { -> onOpenWordBankSection(WordBankSection.Wrong) }
        ProfileGroups.LearningRecords -> onOpenLearningRecords
        ProfileGroups.LearningPlan -> onOpenPlan
        ProfileGroups.WordBook -> { -> onOpenWordBankSection(WordBankSection.Book) }
        ProfileGroups.Worksheet -> onOpenWorksheet
        ProfileGroups.WordBookTransfer -> onOpenWordBookTransfer
        ProfileGroups.AiProfiles -> onOpenAiProfiles
        ProfileGroups.ReminderSpeech -> onOpenReminderSpeech
        ProfileGroups.Settings -> onOpenSettings
        ProfileGroups.Backup -> onOpenBackupHub
        ProfileGroups.About -> onOpenAbout
        else -> error("「我的」hub 台账里这枚行没有落点：${row.title}（新增行必须同时给出目的地）")
    }
    val subtitle: String? = when (row) {
        ProfileGroups.LearningPlan -> wordBookName?.let { "$it · 换词书与每日目标" } ?: "尚未选择词书 · 去选一本"
        ProfileGroups.WordBook -> wordBookName?.let { "$it · 学习中" } ?: "尚未选择词书"
        ProfileGroups.AiProfiles -> aiProfileSubtitle ?: row.subtitle
        else -> row.subtitle
    }
    EntryRow(
        icon = row.icon,
        domain = row.domain,
        title = row.title,
        subtitle = subtitle,
        iconTone = row.iconTone,
        topDivider = topDivider,
        modifier = Modifier.testTag(row.tag),
        onClick = onClick,
    )
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
