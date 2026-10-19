package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.WordBookProgress
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.EntryIconTone
import com.example.englishlearning.ui.components.EntryRow
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * 批 2 Task 14B（AC-R-20）：就绪态收成「1 枚主 CTA + 一组入口行」，今日数字只留一处权威尺寸。
 *
 * 三条版面裁决（稿 `mockups.html:893-940`、内容审计 `docs/design/2026-10-17-content-placement-audit.md:116`）：
 *  1. **数字的权威只有一处**：`AppType.Display` 全屏恰好一枚，落在进度卡的「今日完成数」那一格；
 *     词书百分比、卡上的两个计数、明细行、工作量图例全部降到 `AppType.Footnote`，且一律取中性墨
 *     （`text1`/`text2`）不带强调色——「今天到底做了多少」只有一个视觉重量，其余数字是同一条信息的注脚。
 *  2. **八枚整宽按钮收成 1 CTA + 入口行组**：只有「继续学习/复习」回答「今天做什么」，它留在主操作位；
 *     阅读文章是**受解锁态约束**的支撑动作（enabled 态与解锁文案都要留在行为面上，批 3 才并成状态点），
 *     故保持本屏唯一一枚 M3 `Button`；其余 6 个目录型动作与页首常驻搜索条一起收进 [TodayPlanEntryGroup]。
 *  3. **取色只读动态 token**：中性色 `appColors()`、域色 `appDomain(...)`，本屏不再有裸 hex、静态令牌读点、
 *     `Mint*` 快捷名与库内排印档。三形裸 hex 的域色对位与各自行由写在 [WorkloadKind.color] 与
 *     [TodayPlanEntryGroup] 上。
 *
 * 业务规则一行未动：解锁条件、每日快照不可变性、计数口径与 ViewModel 都在本文件之外。
 */
@Composable
fun TodayPlanScreen(
    state: TodayPlanUiState,
    onRetry: () -> Unit = {},
    onOpenSetup: () -> Unit = {},
    onStartLearning: () -> Unit = {},
    onOpenReading: () -> Unit = {},
    onOpenLearningTools: () -> Unit = {},
    onOpenCheckIn: () -> Unit = {},
    onOpenLearningRecords: () -> Unit = {},
    onOpenWordBookWords: () -> Unit = {},
    onOpenVocabulary: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    vocabularyCount: Int? = null,
) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            // 底部导航会吃掉 64dp，这里必须可滚动，否则小屏上最后一排入口会被裁掉。
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("today_plan_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppScreenHeader(title = "今日学习计划")
        when (state) {
            TodayPlanUiState.Loading -> {
                CircularProgressIndicator(
                    color = appDomain(AppDomain.AiSpeech).deep,
                    modifier = Modifier.size(48.dp).testTag("today_plan_loading"),
                )
                Text("正在准备今日计划…", style = AppType.Footnote, color = colors.text2)
            }
            TodayPlanUiState.MissingSetup -> {
                Text(
                    "先选择词书并设置每日目标",
                    style = AppType.Body,
                    color = colors.text2,
                    modifier = Modifier.testTag("today_plan_missing_setup"),
                )
                PillButton(
                    text = "去设置词书",
                    onClick = onOpenSetup,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "today_plan_open_setup",
                    contentDescription = "去设置词书",
                )
            }
            TodayPlanUiState.Unavailable -> {
                Text(
                    "今日计划暂时无法读取，请稍后重试",
                    style = AppType.Body,
                    color = colors.text2,
                    modifier = Modifier.testTag("today_plan_unavailable"),
                )
                PillButton(
                    text = "重试",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "today_plan_retry",
                    contentDescription = "重试",
                )
            }
            is TodayPlanUiState.Ready -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, colors.outlineSoft, RoundedCornerShape(24.dp)).testTag("today_plan_summary"),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(state.wordBookName, style = AppType.Title, color = colors.text1)
                        Text("应用内学习分组，不是官方考试大纲", style = AppType.Footnote, color = colors.text2)
                        Text("计划日期：${state.localDateLabel}", style = AppType.Footnote, color = colors.text2)
                        // 参考竞品词书卡：左边给百分比，右边给「已学/总数 词」，条子在下，
                        // 再补一行「未学」。AC-R-20 之后这一组全部是注脚尺寸——整册进度不是
                        // 「今天做了多少」，它不许跟今日完成数抢同一个视觉重量。
                        state.bookProgress?.let { progress ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Text(
                                    text = learnedPercentLabel(progress),
                                    style = AppType.Footnote,
                                    color = colors.text2,
                                    modifier = Modifier.testTag("today_plan_learned_percent"),
                                )
                                Text(
                                    text = "${progress.learned}/${progress.total} 词",
                                    style = AppType.Footnote,
                                    color = colors.text2,
                                    modifier = Modifier.testTag("today_plan_book_words"),
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress.fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                                    .testTag("today_plan_book_progress"),
                                // 整册进度属于「学习材料」这一域，与下方「今日新增」色点同源。
                                // 取 deep 不取 base：这是压 `surface` 的图形，spec §2.1 的 base 端
                                // （学习域 #0A84FF）只有 3.6:1，deep 端 6.4:1 才稳过非文本 3:1 线。
                                color = appDomain(AppDomain.Learn).deep,
                                trackColor = colors.outlineSoft,
                            )
                            Text(
                                text = "未学 ${progress.unlearned} 词",
                                style = AppType.Footnote,
                                color = colors.text2,
                                modifier = Modifier.testTag("today_plan_book_unlearned"),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CountCard("今日新增 ${state.newTarget} 词", "今日新增 ${state.newTarget} 词", "today_plan_new_count", appDomain(AppDomain.Learn).deep)
                    CountCard("今日复习 ${state.dueTarget} 词", "今日复习 ${state.dueTarget} 词", "today_plan_due_count", appDomain(AppDomain.Review).deep)
                }
                val totalDone = state.newDone + state.dueDone
                val totalTarget = state.newTarget + state.dueTarget
                // 这张卡的容器/描边/前景是同批迁移的（Task 12.5 那轮），且**不得**给它显式
                // `tonalElevation`：主题把 `surfaceTint` 设成 `set.onMint`，叠加 alpha 一旦到 ≥3dp
                // 就会把 ThemeModeSwitchTest 那 ±2 逐通道容差撑破（判据与实测见该用例 KDoc）。
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.outlineSoft, RoundedCornerShape(18.dp))
                        .testTag("today_plan_progress_summary"),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.totalTasks == 0) {
                            Text("今天暂无学习任务", style = AppType.Footnote, color = colors.text2, modifier = Modifier.testTag("today_plan_empty"))
                        } else {
                            Text("今日学习进度", style = AppType.Label, color = colors.text2, modifier = Modifier.testTag("today_plan_task_total"))
                            // 全屏唯一的权威数字：今日完成数。后缀「项已完成」留在注脚档，
                            // 否则这枚尺寸的重量会被复制到一行说明上。
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "$totalDone/$totalTarget",
                                    style = AppType.Display,
                                    color = colors.text1,
                                    modifier = Modifier.testTag("today_plan_total_progress"),
                                )
                                Text("项已完成", style = AppType.Footnote, color = colors.text2, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Text("新增 ${state.newDone}/${state.newTarget} · 复习 ${state.dueDone}/${state.dueTarget}", style = AppType.Footnote, color = colors.text2, modifier = Modifier.testTag("today_plan_progress"))
                        }
                    }
                }
                Text(
                    if (state.isUnlocked) "已解锁：文章已解锁" else "未解锁",
                    style = AppType.Footnote,
                    color = if (state.isUnlocked) colors.success else colors.text2,
                    modifier = Modifier.testTag("today_plan_unlock_status"),
                )
                // 这里原本是「优先复习 N 个到期词，再学习新增词」的文字提示。
                // 按用户要求改成图解：一条分段条把「已学 / 今日复习 / 今日新学」各画成一节，
                // 精确数字下沉到条下的图例里。所以这一段不再保留对应文案——同一件事
                // 既画条又写句子只会让主操作上方变得啰嗦。图例数字同样按注脚档走（AC-R-20）。
                val workload = TodayWorkload.of(
                    bookProgress = state.bookProgress,
                    newTarget = state.newTarget,
                    newDone = state.newDone,
                    dueTarget = state.dueTarget,
                    dueDone = state.dueDone,
                )
                // 主按钮的措辞与条子同源：条子说今天还有复习量，按钮就说「开始复习」。
                val learningAction = if (workload.reviewPending > 0) "开始复习" else "开始学习"
                if (!workload.isEmpty) TodayWorkloadBar(workload)
                PillButton(
                    text = learningAction,
                    onClick = onStartLearning,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    testTag = "today_plan_start_learning",
                    contentDescription = learningAction,
                )
                // 本屏唯一保留的 M3 Button：阅读文章是支撑动作，但它的 `enabled` 态、禁用墨色与
                // 「完成新词与复习后解锁文章」那条说明共同承载解锁语义，入口行组件没有「禁用态」这一档
                // （EntryRow 契约只有可点语义），所以这一站点不符 EntryRow 契约，保持按钮形状不动。
                Button(
                    onClick = onOpenReading,
                    enabled = state.isUnlocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("today_plan_open_reading")
                        .semantics { contentDescription = "阅读文章" },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.outlineSoft),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surface,
                        // 容器随主题换套，前景同批改读动态域色：静态域色是浅套深红，压深底只有 2.96:1
                        // （Task 12.5 修复轮 I-2）。浅套侧 `appDomain(Reading).deep` 与静态值逐字相同
                        // （#C81E46，由域色双钉用例保证），浅色像素零变化。
                        contentColor = appDomain(AppDomain.Reading).deep,
                        disabledContainerColor = colors.surface,
                        disabledContentColor = colors.text2,
                    ),
                ) { Text("阅读文章") }
                if (!state.isUnlocked) {
                    Text(
                        "完成新词与复习后解锁文章",
                        style = AppType.Footnote,
                        color = colors.text2,
                        modifier = Modifier.testTag("today_plan_reading_status"),
                    )
                }
                TodayPlanEntryGroup(
                    vocabularyCount = vocabularyCount,
                    onOpenSearch = onOpenSearch,
                    onOpenWordBookWords = onOpenWordBookWords,
                    onOpenVocabulary = onOpenVocabulary,
                    onOpenLearningRecords = onOpenLearningRecords,
                    onOpenCheckIn = onOpenCheckIn,
                    onOpenLearningTools = onOpenLearningTools,
                    onOpenSetup = onOpenSetup,
                )
            }
        }
    }
}

/**
 * 入口行组（AC-R-20 的「一组入口行」）：原先 6 枚整宽按钮 + 页首常驻搜索条收成一张卡里的 7 条共享
 * [EntryRow]，除首行外每条一条顶线（R-10.5）。
 *
 * 域色对位（每枚都有来历，不是凑色）：
 *  - 查词 → `Library`、词书单词 → `Learn`、生词本 → `Reading`：逐字对应稿 `mockups.html:911-916` 的
 *    `--d-library` / `--d-learn` / `--d-reading` 三行；
 *  - 学习记录 → `Learn`（它记的是学习行为本身，与「词书单词」同一域，本屏不给它第二种颜色）；
 *  - 打卡与成就 → `Review`：稿「连续与记录」那一组用的就是 `--d-review`（`mockups.html:930`），
 *    打卡页自身的强调色也是复习域（`CheckInScreen.kt` 通体 `DomainColors.Review.deep`）。**这一枚同时
 *    了结了那处无对应令牌的棕色裸 hex `#795B36`**：它的语义是「打卡/成就」，最近且成体系的对应值就是
 *    复习域 `deep`（浅套 #AB5700，压白 5.13:1），不是随便挑一枚像棕色的色（登记优于蒙混）；
 *  - 学习工具与设置 / 调整词书与目标 → `Settings`：`onOpenLearningTools` 落的是设置 tab
 *    （`AppScreen.kt:1536`），`调整词书与目标` 打开的是学习设置，稿也把它并进设置页同名条目。
 *
 * 图标容器一律 [EntryIconTone.Tint]（R-10.2）：本屏的饱和底归主 CTA 一处，入口行用弱档底色，
 * 免得七行渐变把「今天做什么」压下去。
 *
 * `contentDescription` 留在调用方传进的 `modifier` 上（与行本身同一枚可点节点），既保住既有用例
 * （`AppScreenTest` 按「学习记录」「调整词书与目标」这些描述点导航）也不为了迁就组件签名丢无障碍语义。
 */
@Composable
private fun TodayPlanEntryGroup(
    vocabularyCount: Int?,
    onOpenSearch: () -> Unit,
    onOpenWordBookWords: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onOpenLearningRecords: () -> Unit,
    onOpenCheckIn: () -> Unit,
    onOpenLearningTools: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val colors = appColors()
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.outlineSoft, RoundedCornerShape(18.dp))
            .testTag("today_plan_entry_group"),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            EntryRow(
                icon = AppIcon.Search,
                domain = AppDomain.Library,
                title = "查词",
                iconTone = EntryIconTone.Tint,
                modifier = Modifier.testTag("today_plan_search_entry").semantics { contentDescription = "搜索单词或短语" },
                onClick = onOpenSearch,
            )
            EntryRow(
                icon = AppIcon.BookOpen,
                domain = AppDomain.Learn,
                title = "词书单词",
                subtitle = "整册浏览",
                iconTone = EntryIconTone.Tint,
                topDivider = true,
                modifier = Modifier.testTag("today_plan_open_wordbook_words").semantics { contentDescription = "词书单词" },
                onClick = onOpenWordBookWords,
            )
            EntryRow(
                icon = AppIcon.Bookmark,
                domain = AppDomain.Reading,
                title = "生词本",
                trailing = vocabularyCount?.let { "$it 个" },
                iconTone = EntryIconTone.Tint,
                topDivider = true,
                modifier = Modifier
                    .testTag("today_plan_open_vocabulary")
                    .semantics {
                        contentDescription = if (vocabularyCount == null) {
                            "生词本"
                        } else {
                            "生词本，共 ${vocabularyCount} 个词"
                        }
                    },
                onClick = onOpenVocabulary,
            )
            EntryRow(
                icon = AppIcon.Clock,
                domain = AppDomain.Learn,
                title = "学习记录",
                iconTone = EntryIconTone.Tint,
                topDivider = true,
                modifier = Modifier.testTag("today_plan_open_learning_records").semantics { contentDescription = "学习记录" },
                onClick = onOpenLearningRecords,
            )
            EntryRow(
                icon = AppIcon.Star,
                domain = AppDomain.Review,
                title = "打卡与成就",
                iconTone = EntryIconTone.Tint,
                topDivider = true,
                modifier = Modifier.testTag("today_plan_open_check_in").semantics { contentDescription = "查看打卡与成就" },
                onClick = onOpenCheckIn,
            )
            EntryRow(
                icon = AppIcon.Gear,
                domain = AppDomain.Settings,
                title = "学习工具与设置",
                iconTone = EntryIconTone.Tint,
                topDivider = true,
                modifier = Modifier.testTag("today_plan_learning_tools").semantics { contentDescription = "学习工具与设置" },
                onClick = onOpenLearningTools,
            )
            EntryRow(
                icon = AppIcon.List,
                domain = AppDomain.Settings,
                title = "调整词书与目标",
                iconTone = EntryIconTone.Tint,
                topDivider = true,
                modifier = Modifier.testTag("today_plan_open_setup_entry").semantics { contentDescription = "调整词书与目标" },
                onClick = onOpenSetup,
            )
        }
    }
}

/**
 * 两张计数卡（今日新增 / 今日复习）。
 *
 * 数字降到 `AppType.Footnote` + `text1` 中性墨：这两个数说的是「今天排了多少」，
 * 与进度卡里的「今天做了多少」是同一件事的两端，尺寸上不许压过后者；
 * 卡自己的强调色载体是 [marker] 那枚圆点（[WorkloadKind.color] 同一套域色，同一概念在本屏只一套颜色）。
 */
@Composable
private fun RowScope.CountCard(text: String, description: String, tag: String, marker: Color) {
    val shape = RoundedCornerShape(18.dp)
    Card(
        colors = CardDefaults.cardColors(containerColor = appColors().surface),
        shape = shape,
        modifier = Modifier.weight(1f).border(1.dp, appColors().outlineSoft, shape).testTag(tag)
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(7.dp).background(marker, CircleShape))
            Text(text, style = AppType.Footnote, color = appColors().text1)
        }
    }
}

/**
 * 「已学 88.1%」——一位小数，**截断**而非四舍五入。
 *
 * 截断是刻意的：2680/3039 四舍五入会得到 88.2%，看起来像「多学了一个千分点」；
 * 进度条这种只增不减的指标按「已达成的下界」表述更不容易引起争议。
 *
 * 不用 `String.format` 也是刻意的：它按默认 Locale 输出，在逗号小数点的地区会给出
 * 「88,1%」。这里用整数运算凑出小数位，结果与地区无关。
 */
private fun learnedPercentLabel(progress: WordBookProgress): String {
    val tenths = (progress.fraction * 1000f).toInt()
    return "已学 ${tenths / 10}.${tenths % 10}%"
}

/**
 * 工作量三档的域色表（brief Step 2「复习 `Review` 系」）。
 *
 * Task 3 之后这些色值都是组合期读点，所以这张表本身必须是组合期函数：两个调用点
 * （`TodayWorkloadBar` 的分段条与图例圆点）都在组合期内。原先那两枚顶层 `private val ... Color`
 * 是「非可组合上下文」的静态读点（深色档浮浅套值），已随本轮清掉。
 *
 * 取值口径：
 *  - `LEARNED` → `AiSpeech`（已学 = 品牌薄荷，与「继续学习」的品牌主色同族）；
 *  - `REVIEW` → `Review`（复习域；与「今日复习」色点、打卡入口同一颜色）；
 *  - `NEW` → `Learn`（学习域；与「今日新增」色点、词书单词入口同一颜色）。
 * 三档一律取 `deep` 不取 `base`：它们是压在 `surface`/`outlineSoft` 底上的小面积图形，
 * spec §2.1 的 `base` 端（复习 #FF9F0A、AI #2EC99C）只有 2.06–2.11:1，进不了非文本 3:1 线，
 * `deep` 端 4.02–6.42:1 才成立；深色档 `deep == base`，观感仍是亮色分段。
 */
@Composable
private fun WorkloadKind.color(): Color = when (this) {
    WorkloadKind.LEARNED -> appDomain(AppDomain.AiSpeech).deep
    WorkloadKind.REVIEW -> appDomain(AppDomain.Review).deep
    WorkloadKind.NEW -> appDomain(AppDomain.Learn).deep
}

private fun WorkloadKind.label(): String = when (this) {
    WorkloadKind.LEARNED -> "已学"
    WorkloadKind.REVIEW -> "今日复习"
    WorkloadKind.NEW -> "今日新学"
}

private fun WorkloadKind.tag(): String = "today_plan_workload_${name.lowercase()}"

/**
 * 今日工作量分段条：`[已学][今日复习][今日新学]`，剩下的部分留成轨道色。
 *
 * 宽度全在 [TodayWorkload] 里算好了——那里是纯 Kotlin，语义由 JVM 单元测试钉住；
 * 这里只负责把 [WorkloadSegment] 画出来，以及把三行图例写全（含计数为 0 的那些）。
 * 图例里的数字是注脚档：分段条负责形状，精确数下沉成注脚，不再长出第二种尺寸。
 */
@Composable
private fun TodayWorkloadBar(workload: TodayWorkload) {
    val colors = appColors()
    val shape = RoundedCornerShape(5.dp)
    Column(modifier = Modifier.fillMaxWidth().testTag("today_plan_workload")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(shape)
                .background(colors.outlineSoft.copy(alpha = 0.35f)),
        ) {
            workload.segments.forEach { segment ->
                Box(
                    Modifier
                        .weight(segment.share)
                        .fillMaxHeight()
                        .background(segment.kind.color())
                        .testTag(segment.kind.tag() + "_bar")
                        .semantics {
                            contentDescription = "${segment.kind.label()} ${segment.count} 词"
                        },
                )
            }
            if (workload.remainder > 0f) {
                Box(Modifier.weight(workload.remainder).fillMaxHeight())
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkloadKind.entries.forEach { kind ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(kind.color(), CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "${kind.label()} ${workload.countOf(kind)}",
                        style = AppType.Footnote,
                        color = colors.text2,
                        modifier = Modifier.testTag(kind.tag() + "_label"),
                    )
                }
            }
        }
    }
}
