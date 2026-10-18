package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 深色令牌消费棘轮（批 1 Task 8 Step 3）。
 *
 * 管什么：`ui` 包里对 `AppPalette.*` / `DomainColors.*` 的**静态**读点——它们绕开
 * `LocalAppColors` / `LocalDomainColors`，在深色模式下必然拿到浅套取值。每个文件当前命中数登记在
 * [DarkTokenSeedAllowlist]，断言是「逐文件精确相等」，两侧都不许偏：
 *  ① 实际 > seed（含全新文件冒出来）→ 新增即挡；
 *  ② 实际 < seed，或 seed 里的文件已改名/消失 → 也挡。
 * 所以迁完一屏的深色读点，必须同步把该文件的 seed 减到 0（最后删行）；seed 是债务台账，不是永久豁免。
 * 批 2/批 3 每个迁移任务 Step「迁完本屏深色读点」的依据就是这张表的递减，批 3 收口时整表清空。
 *
 * 不管什么：本棘轮不宣称「全站零静态深色读点」。它只保证「新增即挡 + 存量只准同步递减」。
 *
 * 主题不变量（两类静态读点不是一回事，批 2/批 3 别把力气花错地方，也别拿第二类当第一类的借口）：
 *  - 半透明叠层：`AppPalette.GlassFill` / `GlassHighlight` / `Scrim`。它们是「白或黑 + alpha」的蒙版，
 *    压在图片或玻璃面板上，取值本就不跟主题走，深浅两套都合法。日后 seed 里出现这类站点，按当前口径
 *    原样保留即可，不必为了「清零」强行 token 化（强行主题化蒙版会让深色下的面板糊成一团）。
 *  - 浅套硬编码：`AppPalette.TextPrimary` / `TextSecondary` / `Surface` / `Separator` /
 *    `OnMintPrimary` 与 `DomainColors` 各域的 `deep`。这些是浅底取值，深底压深字直接不可读，
 *    必须改成 `appColors().x` / `appDomain(AppDomain.X).deep`。`theme/AppType.kt` 的顶层 `TextStyle`
 *    写死 `TextPrimary`/`TextSecondary` 正是这一族的真实债务，所以它进 seed、不享有任何豁免。
 *
 * 口径：
 *  - 规则里的 `\b` 是必要的：`LocalDomainColors.current.getValue(...)` 含子串 `DomainColors.`，
 *    没有左边界就会把「新式正确写法」误判成静态读点——`composition local reads of the same name
 *    are not counted as static reads` 用例拿合成片段当场证此事。
 *  - 先剥注释再计数：定义文件的 KDoc 会合法复述 `AppPalette.X`，计入契约就成了空转。
 *  - 一行可含两处命中（`if (selected) DomainColors.AiSpeech.deep else AppPalette.TextSecondary`），
 *    所以本棘轮按**命中数**而不是按行计数，seed 形状是 `Map<文件, 次数>`；这也是它与裸 hex 棘轮的
 *    行集 allowlist 形状不同的原因。
 */

/**
 * 债务台账：`ui` 包内静态 `AppPalette.` / `DomainColors.` 读点的按文件命中数，key 为相对 `ui` 根的路径。
 * 顶层 `Brush` / 形参默认值这类「非可组合上下文」站点若在新迁移里出现，也必须登记到这张表，并在 offender
 * 指引下于代码里写明它为何读不了 `appColors()`——这张表就是登记处，不设第三张永久豁免表。
 */
internal val DarkTokenSeedAllowlist: Map<String, Int> = mapOf(
    "AiProfileSettingsScreen.kt" to 39,
    "TodayPlanScreen.kt" to 18,
    "SettingsScreen.kt" to 10,
    "CheckInScreen.kt" to 9,
    "SpeechSettingsScreen.kt" to 8,
    "WordCardScreen.kt" to 7,
    "components/glass/GlassButton.kt" to 7,
    "PhotoTranslationScreen.kt" to 6,
    "ReadingAccessScreen.kt" to 6,
    "theme/AppType.kt" to 6,
    "WordAiQaScreen.kt" to 5,
    "ImageStudioScreen.kt" to 4,
    "AiFeatureScreen.kt" to 3,
    "AppScreen.kt" to 3,
    "SentenceAnalysisScreen.kt" to 3,
    "components/glass/GlassOverlay.kt" to 3,
    "AiLearningScreen.kt" to 2,
    "AppBottomBar.kt" to 2,
    "BackupExportScreen.kt" to 2,
    "components/glass/GlassDialog.kt" to 2,
    "ArticleImportScreen.kt" to 1,
    "ArticleReadingScreen.kt" to 1,
    "CardDetailScreen.kt" to 1,
    "GlobalVocabularySearchScreen.kt" to 1,
    "LearningRecordsScreen.kt" to 1,
    "ReadingHistoryScreen.kt" to 1,
    "RecoveryScreen.kt" to 1,
    "ReminderSettingsScreen.kt" to 1,
    "WordBookWordsScreen.kt" to 1,
    "WorksheetPreviewScreen.kt" to 1,
    "WorksheetSettingsScreen.kt" to 1,
    "components/IconBadge.kt" to 1,
)

/**
 * 政策豁免表（只做「文档化 + 防空转」，不做永久豁免）：四个令牌定义文件允许引用静态源，
 * 因为它们本身就是静态源的书写地。本测试当场断言两件事——
 *  ① 条目文件真实存在：改名或删文件都会让豁免表指向空，棘轮不许无声换扫法；
 *  ② 条目当前命中数为 0，且不在 seed 表里：一旦有人往定义文件里塞**消费**代码（读 `AppPalette.X`
 *    来渲染），第②条当场红，届时只能二选一——改回 `appColors()`，或显式修订本表并写明理由。
 */
internal val StaticReadPolicyExemptions: Map<String, String> = mapOf(
    "AppPalette.kt" to "浅套静态源与 DomainColors 定义本体：值在这里写死，不该出现对自身 object 的读取",
    "AppColors.kt" to "深浅两套 AppColors / DomainPalette 装配源：只产 DataClass 实例，消费一律走 appColors()/appDomain()",
    "MintGlassTheme.kt" to "主题装配点：只提供 CompositionLocal，不读静态源",
    "MintPalette.kt" to "薄荷令牌静态 getter 桥：内部读 LocalAppColors / LocalDomainColors，不读 AppPalette",
)

/**
 * 结构形态注记（不是豁免）：`DarkTokenSeedAllowlist` 里哪些命中位于「非可组合上下文」——
 * 形参默认值与顶层 `val/var`。Kotlin 不允许在非组合上下文里调用 `@Composable`，这类站点不能就地换成
 * `appDomain(...)`；正确迁移做法是把默认值改成可空哨兵、在函数体内解析（`accent: DomainAccent? = null`
 * 然后 `accent ?: appDomain(AppDomain.AiSpeech)`）。计数仍以 [DarkTokenSeedAllowlist] 为准，本表只是注记，
 * 修掉一处时两张表一起减 1；两张表都由「精确相等」守着，新增未登记即红。
 *
 * 登记内容：`components/glass/GlassButton.kt` 的 `PillButton` 形参默认值
 * `accent: DomainAccent = DomainColors.AiSpeech`（计划 Task 3 现场撞出、brief Step 3 点名要「一并列入并注明」
 * 的那一类）。顶层 `Brush` 形态当前实测 0 处：现状三处顶层 `Brush.linearGradient(...)` 读的是保持静态的
 * `AppleMint*`（MintPalette 那条线），不含 `AppPalette.` / `DomainColors.`，本规则看不见它们。
 */
internal val NonComposableStaticReadLedger: Map<String, Int> = mapOf(
    "components/glass/GlassButton.kt" to 1,
)

class DarkTokenConsumptionRatchetTest {
    private val uiDir = File(UI_MAIN_SOURCE_PATH)

    /**
     * 静态读点规则。`\b` 排除 `LocalDomainColors.`（CompositionLocal，新式正确写法）；
     * `[A-Za-z0-9_]+` 让每个成员读取各计一次，同一行的两处命中不会被合并成一处。
     */
    private val rule = Regex("""\b(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")

    /** 顶层 `val/var` 形态：只在剥注释后仍位于列 0 的行上生效（块注释剥离保留换行，缩进信息不丢）。 */
    private val topLevelDeclarationRead =
        Regex("""^(private |internal |public )?(val|var)\s+\w+.*\b(?:AppPalette|DomainColors)\.""")

    /** 形参 / 带类型标注的 `名字: 类型 = AppPalette.X` 形态（默认参数不能调 @Composable，故结构上不可就地迁移）。 */
    private val typedDeclarationRead =
        Regex("""\w+\s*:\s*[A-Z]\w*\s*=\s*\b(?:AppPalette|DomainColors)\.""")

    private fun File.relativeUiPath(): String = relativeTo(uiDir).invariantSeparatorsPath

    private fun actualCounts(): Map<String, Int> = uiFiles(uiDir)
        .associate { it.relativeUiPath() to countMatches(it.readText(), rule) }
        .filterValues { it > 0 }

    /** 命中「非可组合上下文」两种形态的行，返回「相对路径: 折叠行」。 */
    private fun nonComposableShapedSites(): List<String> = uiFiles(uiDir).flatMap { file ->
        stripComments(file.readText()).lines()
            .filter { line ->
                val atColumnZero = line.isNotEmpty() && !line.first().isWhitespace()
                (atColumnZero && topLevelDeclarationRead.containsMatchIn(line)) ||
                    typedDeclarationRead.containsMatchIn(line)
            }
            .map { line -> "${file.relativeUiPath()}: ${line.trim().replace(Regex("""\s+"""), " ")}" }
    }

    @Test
    fun `seed and exemption keys point at real scanned files`() {
        val files = uiFiles(uiDir)
        val byPath = files.map { it.relativeUiPath() }.toSet()
        val unknown = DarkTokenSeedAllowlist.keys.filterNot { it in byPath }
        assertTrue(
            unknown.isEmpty(),
            "seed 里登记的静态读点文件不在扫描集里（改名/移动/已迁完都要同步 seed）：$unknown；扫描根 $UI_MAIN_SOURCE_PATH"
        )
        val unknownExemptions = StaticReadPolicyExemptions.keys.filterNot { name -> byPath.any { it.endsWith("/$name") } }
        assertTrue(
            unknownExemptions.isEmpty(),
            "政策豁免表指向定义文件已不存在：$unknownExemptions —— 豁免空转即为棘轮失效，必须先修表或修路径"
        )
    }

    @Test
    fun `no static dark read appears in a file the seed does not list`() {
        val unseeded = actualCounts().filterKeys { it !in DarkTokenSeedAllowlist }
        assertTrue(
            unseeded.isEmpty(),
            "新增静态深色读点（应改为 appColors().x / appDomain(AppDomain.X).deep）：\n" +
                unseeded.entries.joinToString("\n") { (path, count) -> "$path 出现 $count 处" } +
                "\n批 2/批 3 的迁移不接受在这里临时开豁免"
        )
    }

    @Test
    fun `a file whose reads dropped below the seed must decrement the seed too`() {
        val actual = actualCounts()
        val stale = DarkTokenSeedAllowlist.filterKeys { it !in actual }
        assertTrue(
            stale.isEmpty(),
            "seed 登记的文件已无静态深色读点（债务已迁完）：$stale —— 应把该行的 seed 减到 0 并删行，" +
                "残留豁免会让本棘轮退化成永久白名单"
        )
    }

    @Test
    fun `per-file static read count equals the seed count exactly`() {
        val actual = actualCounts()
        val drifted = DarkTokenSeedAllowlist
            .filterKeys { it in actual }
            .filter { (path, seedCount) -> actual.getValue(path) != seedCount }
        assertTrue(
            drifted.isEmpty(),
            "静态深色读点与 seed 不符（增即挡，减了不同步改 seed 也挡）：\n" +
                drifted.entries.joinToString("\n") { (path, seedCount) ->
                    "$path 实际 ${actual.getValue(path)} 处，seed $seedCount 处——" +
                        "应改为 appColors().x / appDomain(AppDomain.X).deep，并同步把本文件 seed 减 1"
                }
        )
    }

    @Test
    fun `policy exempt definition files stay free of static consumption reads`() {
        val offendersInThemeDir = violations("深色令牌静态读点", rule, emptySet(), File(UI_THEME_SOURCE_PATH))
        val leaked = offendersInThemeDir.filter { offender ->
            StaticReadPolicyExemptions.keys.any { name -> offender.startsWith("$name:") }
        }
        assertTrue(
            leaked.isEmpty(),
            "政策豁免的定义文件里出现了静态读点（豁免只覆盖「这里是值的来源」，不覆盖消费）：\n" +
                leaked.joinToString("\n") +
                "\n改回 appColors() / appDomain()；若确实必须留在定义文件里，先修订本表理由并同步 seed"
        )
        val seededExemptFiles = StaticReadPolicyExemptions.keys.filter { name ->
            DarkTokenSeedAllowlist.keys.any { it.endsWith("/$name") }
        }
        assertTrue(
            seededExemptFiles.isEmpty(),
            "定义文件既被政策豁免又进了债务 seed，两套口径自相矛盾：$seededExemptFiles"
        )
    }

    @Test
    fun `non-composable shaped reads are exactly the registered ledger`() {
        val sites = nonComposableShapedSites()
        val detected = sites.groupingBy { it.substringBefore(": ") }.eachCount()
        val unregistered = detected.filterKeys { it !in NonComposableStaticReadLedger }
        assertTrue(
            unregistered.isEmpty(),
            "出现未登记的「非可组合上下文」静态读点（顶层 val/var 或形参默认值）：\n" +
                sites.filter { it.substringBefore(": ") in unregistered.keys }.joinToString("\n") +
                "\n首选改结构迁移（默认值换成可空哨兵、在函数体内 appDomain(...) 解析），" +
                "确实无法迁移才登记进 NonComposableStaticReadLedger 并写明理由；本用例只做形态点名，判红由人来裁决。"
        )
        assertTrue(
            NonComposableStaticReadLedger.keys.all { it in detected },
            "注记表里登记的站点已不存在（结构已改对或文件已改名）：" +
                NonComposableStaticReadLedger.keys.filterNot { it in detected } +
                " —— 注记必须随之删除，残留注记会让下一个同类站点蒙过去。"
        )
        assertEquals(NonComposableStaticReadLedger, detected, "非可组合形态站点与注记表逐文件计数不符")
    }

    @Test
    fun `composition local reads of the same name are not counted as static reads`() {
        val snippet = "val c = LocalDomainColors.current.getValue(AppDomain.Learn).base\n" +
            "val d = AppPalette.Separator\n"
        val hits = rule.findAll(stripComments(snippet)).count()
        assertTrue(
            hits == 1,
            "规则必须只认静态源读取、不把 LocalDomainColors.current 算成债务：命中 $hits 次（应为 1）"
        )
    }
}
