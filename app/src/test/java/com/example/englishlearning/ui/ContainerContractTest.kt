package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 1 三档容器的源码契约（spec §4.1）。
 *
 * 三档的分工不靠注释自觉：强档一屏至多一枚、软档不描边、裸行靠 1dp 分隔线与 52dp 缩进
 * 表达「同组」，命中区 ≥48dp。全部按**符号**取源，不锚行号。
 */
class ContainerContractTest {
    private val containers: String by lazy { stripComments(uiComponentSource("Containers.kt")) }

    /**
     * 「一屏最多一枚强档卡」的适用范围。批 1 Task 4 立了学习页，Task 5 把词库页并进来；
     * 批 2 把重做过的学习链路五屏（词卡 / 词义详情 / 重新学习 / 默写纸设置 / 默写纸预览）并进来。
     * 用 map 而不是逐屏写死：批 1 余下两屏（我的 / 每日阅读）落地时只在这里加一行。
     *
     * **学习设置不在这张表里**：它住在 `AppScreen.kt`（一个文件装十来个屏），按文件数强档卡
     * 会把别人的数目算到它头上；它的容器口径由 `LearningSetupLayoutContractTest` 在函数窗口内判
     * （`SoftCard(` 恰 2、`StrongCard(` 为 0），比按文件数更准。
     */
    private val screens: Map<String, String> by lazy {
        listOf(
            "TodayPlanScreen.kt",
            "WordBankScreen.kt",
            "WordCardScreen.kt",
            "CardDetailScreen.kt",
            "VocabularyRelearnScreen.kt",
            "WorksheetSettingsScreen.kt",
            "WorksheetPreviewScreen.kt",
            // 批 3：阅读链路五屏逐屏并入（本批按 Task 逐个加行，收口时是 12 屏）。
            "ReadingFeedScreen.kt",
            "ReadingHistoryScreen.kt",
            "ArticleReadingScreen.kt",
            "ArticleImportScreen.kt",
            // 查词（Task 5）：输入区一枚 `SoftCard`、历史行改裸行，零强卡。
            "GlobalVocabularySearchScreen.kt",
            // 批 4：AI 链路七屏逐屏并入（本批按 Task 逐个加行）。AI 主页（Task 1）的 hero 是品牌渐变头卡，
            // 不落 `StrongCard`——四张软卡分组 + 一枚裸行入口，强卡计数为 0。
            "AiLearningScreen.kt",
            // 骨架页（Task 2）：进度分组一枚 `SoftCard`、两枚页级按钮、页末一句说明——零强卡、
            // 零 M3 `Card(`（旧 `SectionCard` 随本轮下线）。
            "AiFeatureScreen.kt",
            // AI 服务（Task 3）：14 个输入分三组＝三枚 `SoftCard`，页末一句说明，吸底保存——零强卡。
            // 旧版私有 `SectionCard`（玻璃底 + 1dp 描边）随本轮整屏重写下线，故本屏也进 `Card(` 归零口径。
            "AiProfileSettingsScreen.kt",
            // 拍照翻译（Task 4）：结果 / 历史 / 模型 / 两类问题卡共五枚 `SoftCard` + 两枚 `EntryRow`
            // 折叠入口——零强卡（旧版 760 行的那根长滚动与 `SavedHistoryList` 的卡一并下线）。
            "PhotoTranslationScreen.kt",
            // 长难句（Task 5）：输入区一枚 `SoftCard`、逐段结果卡改软档、末排两枚文字药片——零强卡。
            "SentenceAnalysisScreen.kt",
            // AI 生图（Task 6）：描述编辑器一枚 `SoftCard`、失败卡改软档，主操作吸底（不透明 `Surface`，
            // 不是容器卡）——零强卡、零 M3 `Card(`（旧提示词卡与失败卡一并下线）。
            "ImageStudioScreen.kt",
            // AI 问词（Task 7）：词义卡 + 我的笔记 + 已存笔记列表共三枚 `SoftCard`、已存笔记逐条 `BareRow`，
            // 失败卡改软档——零强卡、零 M3 `Card(`（旧 `FailureCard` 的 `Card` 一并下线）。
            "WordAiQaScreen.kt",
            // 批 5：设置与系统九屏逐屏并入（Task 1 语音合成）。
            // 语音合成：朗读服务 / 朗读方式两枚 `SoftCard` + 试听裸行折叠体——零强卡、零 M3 `Card(`。
            "SpeechSettingsScreen.kt",
            // 学习提醒（Task 2）：每日提醒 / 提醒时间两枚 `SoftCard`，页末两句说明——零强卡、零 M3 `Card(`。
            "ReminderSettingsScreen.kt",
            // 许可证（Task 3）：隐私说明一枚 `SoftCard`（`LazyColumn` 首条 item）+ 每条台账一枚
            // `SoftCard` 内嵌 `BareRow`——零强卡、零 M3 `Card(`。
            "LicensesScreen.kt",
            // 备份中心（Task 4）：三节各一张 `SoftCard`（走私有 `BackupHubSection` 唯一施工点）+ 四枚
            // `BareRow`；危险区沉底、墨取 `colors.error`——零强卡、零 M3 `Card(`（旧描边节壳下线）。
            "BackupHubScreen.kt",
            // 导出台账（Task 5）：「包含内容」与「不包含」两枚 `SoftCard`（范围四条走 `BareRow`），
            // 次键退文字药片、保存键吸底 —— 零强卡、零 M3 `Card(`。
            "BackupExportScreen.kt",
            // 从备份恢复（Task 6）：四态骨架逐段一张 `SoftCard`（选包 / 扫码 / 口令 / 确认）+ 确认段三枚
            // `BareRow` 摘要 + 导入中卡 + 重启提示卡；页级动作全走药片（M3 按钮只剩取景框里的授权键）
            // —— 零强卡、零 M3 `Card(`。
            "BackupRestoreScreen.kt",
            // 认证导出（Task 7）：三段骨架各一张 `SoftCard`（认证 / 配对与口令 / 保存并配对），
            // 页级动作全走药片——零强卡、零 M3 `Card(`（旧五枚 M3 `Button` 与手搓标题一并下线）。
            "BackupPairingExportScreen.kt",
            // 恢复异常（Task 8）：详情折叠一枚 `SoftCard`（内嵌裸行 + 展开后的原因），
            // 两条出路全走药片——零强卡、零 M3 `Card(`（旧居中列与 M3 `Button` 一并下线）。
            "RecoveryScreen.kt",
        )
            .associateWith { stripComments(uiScreenSource(it)) }
    }

    @Test
    fun `the three tiers are defined exactly once in the shared file`() {
        listOf("fun StrongCard(", "fun SoftCard(", "fun BareRow(").forEach { signature ->
            assertEquals(
                1,
                countMatches(containers, Regex(Regex.escape(signature))),
                "Containers.kt 必须恰一处 $signature",
            )
        }
        // 反空转：同一计数器对「抄两份」的形状必须数出 2。
        assertEquals(2, countMatches("fun BareRow(\nfun BareRow(", Regex(Regex.escape("fun BareRow("))))
    }

    @Test
    fun `the strong card is at most one per screen`() {
        // 本任务先立「至多一枚」的上界；学习页「恰一枚」的落地断言归 Task 4 的
        // TodayPlanBareRowContractTest（那里才真写 StrongCard）。
        screens.forEach { (name, source) ->
            assertTrue(
                countMatches(source, Regex("""\bStrongCard\(""")) <= 1,
                "$name 最多一枚强档卡（spec §4.1：2 = 又回到卡片汤）",
            )
        }
        assertEquals(
            0,
            countMatches(containers, Regex("""\bCard\(""")),
            "Containers.kt 不得退回 M3 Card：三档容器就是替代它的那一层",
        )
    }

    /**
     * 强档卡的分套取色（2026-10-31 用户评审：浅套深墨实底「太重、喧宾夺主」）。
     *
     * 判据仍是「底色与前景都必须取具名 token，且两套各走各的」——只是把浅套的落点从
     * `strongSurface`（`#1F4739`，一块压在白底上的深墨板）改成 `tint2`（浅薄荷一档），
     * 前景随之从 `onStrong` 换成 `text1`。深套**一字不动**：那套 `strongSurface`
     * (`#22332C`) 与 `background` 只差一档明度，深底浅字本来就是对的层次。
     *
     * 为什么不是直接把 `strongSurface` 这个 token 改浅：它同时被主按钮、开关轨道、阅读页
     * 提交键消费（`AppScreen.kt` / `ArticleReadingScreen.kt` / `ArticleImportScreen.kt` 等），
     * 改 token 会连带把那些「实心深色操作面」一起洗淡——用户要降的是**卡片**的层次，不是操作面。
     */
    @Test
    fun `the strong card takes a light mint fill in the light set and stays deep in the dark set`() {
        val body = bodyOf(containers, "fun StrongCard(")
        assertTrue(body.isNotEmpty(), "取不到 StrongCard 本体，判据会空转")
        assertTrue(
            "val fill = if (isDark) colors.strongSurface else colors.tint2" in body,
            "浅套强档卡底必须是 tint2（浅薄荷一档）、深套保持 strongSurface：`strongSurface` 双套都是深底，" +
                "浅套照搬会得到一块压在白背景上的深墨板（用户评审 ②③）",
        )
        assertTrue(
            "val ink = if (isDark) colors.onStrong else colors.text1" in body,
            "前景必须与底色同批分套：浅套 tint2 底上是 text1，深套 strongSurface 底上仍是 onStrong",
        )
        assertTrue(
            "background(fill)" in body,
            "底色链必须真的消费分套后的 fill（写回常量 = 分套形同虚设）",
        )
        assertTrue(
            "CompositionLocalProvider(LocalContentColor provides ink)" in body,
            "卡内容色必须提供分套后的 ink：卡内未显式着色的 Text 会落到容器内容色上",
        )
        // 反空转：把浅套写回深墨实底的形状必须判不到上面两条分套锚。
        val reverted = "val fill = colors.strongSurface\nval ink = colors.onStrong"
        assertTrue(
            "val fill = if (isDark) colors.strongSurface else colors.tint2" !in reverted &&
                "val ink = if (isDark) colors.onStrong else colors.text1" !in reverted,
            "反空转失控：退回不分套的深墨实底竟满足分套锚",
        )
    }

    @Test
    fun `the soft card carries no border`() {
        val body = bodyOf(containers, "fun SoftCard(")
        assertTrue(body.isNotEmpty(), "取不到 SoftCard 本体，判据会空转")
        assertTrue("border" !in body, "软档卡禁止描边：层次由 surface 与 strongSurface 的明度差表达")
        assertTrue("RoundedCornerShape(20.dp)" in body, "软档卡圆角 20dp（§4.1）")
    }

    @Test
    fun `the bare row keeps a 52dp divider indent and a 48dp touch floor`() {
        val body = bodyOf(containers, "fun BareRow(")
        assertTrue(body.isNotEmpty(), "取不到 BareRow 本体，判据会空转")
        assertTrue("heightIn(min = 52.dp)" in body, "裸行最小高度 52dp（§4.3 三档行高）")
        assertTrue("padding(horizontal = 15.dp, vertical = 13.dp)" in body, "裸行内边距 15/13（§4.3）")
        assertTrue("hairlineTop(" in body, "裸行靠 hairlineTop 画同组分隔线")
        assertTrue("BareRowDividerIndent" in body, "分隔线必须用具名缩进常量，不许散写 52.dp")
        assertTrue("val BareRowDividerIndent: Dp = 52.dp" in containers, "缩进常量必须是 52dp：与 40dp 图标 + 12dp 间距对齐")
    }

    @Test
    fun `the entry row no longer owns a private hairline helper`() {
        val entry = stripComments(uiComponentSource("EntryRow.kt"))
        assertTrue("private fun hairlineTop(" !in entry, "EntryRow 的私有 hairlineTop 必须迁到 Containers.kt，否则同形两份")
        assertTrue("hairlineTop(" in entry, "EntryRow 必须改调共享 hairlineTop")
    }

    /** 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。 */
    private fun bodyOf(source: String, signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) return ""
        val open = source.indexOf('{', start)
        if (open < 0) return ""
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open + 1, index)
                }
            }
        }
        return ""
    }
}
