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
 * 所以迁完一屏的深色读点，必须同步把该文件的 seed 减 1、减到 0 才删行；seed 是债务台账，不是永久豁免。
 * 批 2/批 3 每个迁移任务 Step「迁完本屏深色读点」的依据就是这张表的递减。批 3 收口（计划 line 119 / Task 26 Step 3）
 * 的判据是「seed 清零，或只剩确实与主题无关、且已逐条登记并写明理由的站点」——不是无条件「整表清空」。
 *
 * 不管什么：本棘轮不宣称「全站零静态深色读点」。它只保证「新增即挡 + 存量只准同步递减」。
 *
 * 主题不变量（两类静态读点不是一回事，批 2/批 3 别把力气花错地方，也别拿第二类当第一类的借口）：
 *  - 半透明叠层：`AppPalette.GlassFill` / `GlassHighlight` / `Scrim`。它们是「白或黑 + alpha」的蒙版，
 *    压在图片或玻璃面板上，取值本就不跟主题走，深浅两套都合法。这一族的站点既留在 [DarkTokenSeedAllowlist]
 *    （计数照常承担），又逐条登记进 [ThemeInvariantMaskReadLedger] 写明「为什么永远不该迁成 appColors()」；
 *    收口时它们是计划口径允许的「与主题无关、已登记理由」残留，别为了「清零」强行主题化（强行主题化蒙版会让
 *    深色下的面板糊成一团，正是 AC-R-11 要防的）。
 *  - 浅套硬编码：`AppPalette.TextPrimary` / `TextSecondary` / `Surface` / `Separator` /
 *    `OnMintPrimary` 与 `DomainColors` 各域的 `deep`。这些是浅底取值，深底压深字直接不可读，
 *    必须改成 `appColors().x` / `appDomain(AppDomain.X).deep`。`theme/AppType.kt` 的顶层 `TextStyle`
 *    写死 `TextPrimary`/`TextSecondary` 正是这一族的真实债务，所以它进 seed、不享有任何豁免。
 *
 * 四张表的分工（修复轮 1 的 C1，替代旧版「不设第三张永久豁免表」那句已被 a90f4fe 自身推翻的措辞）：
 *  - [DarkTokenSeedAllowlist]：债务台账，`Map<相对路径, 命中数>`，双向精确相等，是「还欠多少」的权威。
 *  - [StaticReadPolicyExemptions]：四个令牌定义/装配文件，`Map<相对路径, 理由>`，当前静态读点为 0，只做文档化 + 空转守卫。
 *  - [ThemeInvariantMaskReadLedger]：主题不变蒙版色站点，`Map<相对路径, 命中数>`，双向精确相等；计数不从 seed 扣除，
 *    只回答「这一处为什么永远不该迁成 appColors()」。
 *  - [ParameterDefaultStaticReadLedger]：非可组合上下文里能按行形态安全识别的站点（形参默认值 + 文件顶层 val/var），
 *    `Map<相对路径, 命中数>`，双向精确相等；计数同样不从 seed 扣除，只标注「这一处不能就地换 appColors()」。
 *    注记表不是豁免表：它不改变 seed 计数，只是把同一处命中在 seed 之外再记一面，修一处要两张表同减，谁单独漂移都会红。
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
 * 指引下于代码里写明它为何读不了 `appColors()`；这类「与主题无关 / 结构上不可就地迁移」的站点另有专门注记表
 * （[ThemeInvariantMaskReadLedger]、[ParameterDefaultStaticReadLedger]）承载其理由，本表仍是「还欠多少」的唯一权威。
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
 * 政策豁免表（只做「文档化 + 防空转」，不做永久豁免）：四个令牌定义/装配文件允许引用静态源，
 * 因为它们本身就是静态源的书写地。key 为相对 `ui` 根的路径（修复轮 1 的 M-N2：与其余台账同口径，按精确相等匹配，
 * 不再用裸文件名 + 后缀匹配，免得同名文件互相冒名顶替）。本测试当场断言两件事——
 *  ① 条目文件真实存在：改名或删文件都会让豁免表指向空，棘轮不许无声换扫法；
 *  ② 条目当前命中数为 0，且不在 seed 表里：一旦有人往定义文件里塞**消费**代码（读 `AppPalette.X`
 *    来渲染），第②条当场红，届时只能二选一——改回 `appColors()`，或显式修订本表并写明理由。
 */
internal val StaticReadPolicyExemptions: Map<String, String> = mapOf(
    "theme/AppPalette.kt" to "浅套静态源与 DomainColors 定义本体：值在这里写死，不该出现对自身 object 的读取",
    "theme/AppColors.kt" to "深浅两套 AppColors / DomainPalette 装配源：只产 DataClass 实例，消费一律走 appColors()/appDomain()",
    "theme/MintGlassTheme.kt" to "主题装配点：只提供 CompositionLocal，不读静态源",
    "theme/MintPalette.kt" to "薄荷令牌静态 getter 桥：内部读 LocalAppColors / LocalDomainColors，不读 AppPalette",
)

/**
 * 主题不变蒙版色的成员名（修复轮 1 的 C1）：半透明叠层，深底浅底都用同一 alpha 蒙版，token 化只会让两套主题
 * 各抄一遍同一个值，所以这几枚成员**永远**不该迁成 `appColors()`。本表把「成员名 ∈ {GlassFill, GlassHighlight,
 * Scrim}」抽成常量，使蒙版色判定可测：`mask ledger splits by member name with synthetic probes` 用合成正/反片段
 * 证明这张表真在按成员名分流（命中蒙版色成员即计数、命中其它成员即不计数），而不是恒真。
 */
internal val ThemeInvariantMaskMembers: List<String> = listOf("GlassFill", "GlassHighlight", "Scrim")

/**
 * 主题不变蒙版色台账（不是豁免）：`DarkTokenSeedAllowlist` 里哪些命中是上面那族蒙版色，逐文件计数。
 * 计数仍以 [DarkTokenSeedAllowlist] 为准（蒙版色命中照常计入 debt seed），本表只是「为什么这一处永远不该迁」的逐条理由登记，
 * 兑现计划 line 119「收口时 seed 清零或仅剩确实与主题无关、已逐条写明理由的站点」里的「登记理由」落点。
 * 与 seed 同口径双向精确相等：注记里有的站点若实际已不存在 → 红；实际是蒙版色却未登记 → 红；同文件计数漂移 → 红。
 * 值即现场实测的蒙版色读点（GlassOverlay / GlassDialog / IconBadge 整文件都是蒙版色，AiProfileSettingsScreen /
 * SpeechSettingsScreen 各只有 2 处是蒙版色，其余仍是必须迁移的浅套硬编码）。
 */
internal val ThemeInvariantMaskReadLedger: Map<String, Int> = mapOf(
    "components/glass/GlassOverlay.kt" to 3,
    "AiProfileSettingsScreen.kt" to 2,
    "components/glass/GlassDialog.kt" to 2,
    "SpeechSettingsScreen.kt" to 2,
    "components/IconBadge.kt" to 1,
)

/**
 * 形参默认值 / 文件顶层声明注记（不是豁免；修复轮 1 的 I2，旧名 `NonComposableStaticReadLedger` 过度声称「覆盖所有
 * 非可组合上下文」）：本表只覆盖「能按行形态安全识别、且结构上不能就地换 appColors()」的两类站点——
 *  ① 形参默认值 `名字: 类型 = AppPalette.X`（Kotlin 不允许在默认参数里调 `@Composable`）；
 *  ② 文件顶层 `val/var`（列 0；object 初始化期同样调不了 `@Composable`）。
 * 正确迁移做法是把默认值改成可空哨兵、在函数体内解析（`accent: DomainAccent? = null` 然后
 * `accent ?: appDomain(AppDomain.AiSpeech)`）；顶层 val 则改造成由装配点现造的工厂函数。
 *
 * 有意排除的同族另一形态：**`object` 成员属性初始化器**（如 `theme/AppType.kt` 的 `val Display = TextStyle(...
 * color = AppPalette.TextPrimary)`）。它与「写在 `@Composable` 函数体里的可就地迁移局部 `val myStyle = TextStyle(...
 * AppPalette.X)`」文本形状完全相同，按行取样无法区分二者——若把它补进本表，就会同时误伤可迁移的体内局部（反向假阳），
 * 或漏掉日后新冒出的成员初始化器（重蹈本表被判的过度声称）。故本表**故意不采样**该形态，明确交由 [DarkTokenSeedAllowlist]
 * 里 `theme/AppType.kt` 的 seed 承载（深色下正文不可读的迁移由批 3 的排印改造 / `appTypography()` 摘色版负责）；
 * `parameter default ledger excludes object member initializer by design` 用合成片段钉住这一排除不是靠运气。
 *
 * 计数仍以 [DarkTokenSeedAllowlist] 为准，本表只是注记，修掉一处时两张表一起减 1；两张表都由「精确相等」守着，
 * 新增未登记即红。登记内容：`components/glass/GlassButton.kt` 的 `PillButton` 形参默认值
 * `accent: DomainAccent = DomainColors.AiSpeech`（计划 Task 3 现场撞出、brief Step 3 点名要「一并列入并注明」的那一类）。
 * 顶层 `Brush.linearGradient(...)`（数量可用 `grep -rn` 于 `ui/` 现算）读的是保持静态的 `AppleMint*`，不含
 * `AppPalette.` / `DomainColors.`，本规则看不见它们，也不归本棘轮管——见类 KDoc 的「品牌渐变模式不变」口径。
 */
internal val ParameterDefaultStaticReadLedger: Map<String, Int> = mapOf(
    "components/glass/GlassButton.kt" to 1,
)

class DarkTokenConsumptionRatchetTest {
    private val uiDir = File(UI_MAIN_SOURCE_PATH)

    /**
     * 静态读点规则。`\b` 排除 `LocalDomainColors.`（CompositionLocal，新式正确写法）；
     * `[A-Za-z0-9_]+` 让每个成员读取各计一次，同一行的两处命中不会被合并成一处。
     */
    private val rule = Regex("""\b(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")

    /**
     * 形态取样正则一律以 `rule.pattern` 收尾拼装（修复轮 1 的 M-N4：旧版各写一份 `\b(?:AppPalette|DomainColors)\.`
     * 字面副本，`rule` 放宽时注记表会静默少报）。这样形态取样永远与主规则同温，`rule` 一改两侧同步漂移，
     * `ledger sites are a subset of the static read rule` 用例再交叉断言注记站点必为 `rule` 命中，兜住耦合。
     */
    private val topLevelDeclarationRead =
        Regex("""^(private |internal |public )?(val|var)\s+\w+.*${rule.pattern}""")

    private val typedDeclarationRead =
        Regex("""\w+\s*:\s*[A-Z]\w*\s*=\s*${rule.pattern}""")

    /** 蒙版色判定（C1）：主规则前缀 + 成员名白名单，命中三枚蒙版色成员之一才计数。 */
    private val maskRule = Regex("""\bAppPalette\.(?:${ThemeInvariantMaskMembers.joinToString("|")})\b""")

    private fun File.relativeUiPath(): String = relativePathFrom(uiDir)

    private fun actualCounts(): Map<String, Int> = uiFiles(uiDir)
        .associate { it.relativeUiPath() to countMatches(it.readText(), rule) }
        .filterValues { it > 0 }

    private fun maskCounts(): Map<String, Int> = uiFiles(uiDir)
        .associate { it.relativeUiPath() to countMatches(it.readText(), maskRule) }
        .filterValues { it > 0 }

    /** 命中「形参默认值 / 文件顶层 val」两种行形态的站点，返回「相对 `dir` 路径: 折叠行」；dir 缺省为 ui 根。 */
    private fun parameterDefaultShapedSites(dir: File = uiDir): List<String> = uiFiles(dir).flatMap { file ->
        stripComments(file.readText()).lines()
            .filter { line ->
                val atColumnZero = line.isNotEmpty() && !line.first().isWhitespace()
                (atColumnZero && topLevelDeclarationRead.containsMatchIn(line)) ||
                    typedDeclarationRead.containsMatchIn(line)
            }
            .map { line -> "${file.relativeTo(dir).invariantSeparatorsPath}: ${line.trim().replace(Regex("""\s+"""), " ")}" }
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
        val unknownExemptions = StaticReadPolicyExemptions.keys.filterNot { it in byPath }
        assertTrue(
            unknownExemptions.isEmpty(),
            "政策豁免表指向定义文件已不存在：$unknownExemptions —— 豁免空转即为棘轮失效，必须先修表或修路径"
        )
        val unknownMaskFiles = ThemeInvariantMaskReadLedger.keys.filterNot { it in byPath }
        assertTrue(
            unknownMaskFiles.isEmpty(),
            "蒙版色台账指向的文件不在扫描集里（改名/已迁完都要同步台账）：$unknownMaskFiles"
        )
        val unknownLedgerFiles = ParameterDefaultStaticReadLedger.keys.filterNot { it in byPath }
        assertTrue(
            unknownLedgerFiles.isEmpty(),
            "形参默认值注记表指向的文件不在扫描集里（改名/已迁完都要同步注记）：$unknownLedgerFiles"
        )
    }

    @Test
    fun `no static dark read appears in a file the seed does not list`() {
        val unseeded = actualCounts().filterKeys { it !in DarkTokenSeedAllowlist }
        assertTrue(
            unseeded.isEmpty(),
            "新增静态深色读点（应改为 appColors().x / appDomain(AppDomain.X).deep，并把本屏迁移交回批 2/批 3 的收口任务）：\n" +
                unseeded.entries.joinToString("\n") { (path, count) -> "$path 出现 $count 处" } +
                "\n若该文件的命中全部是主题不变蒙版色（AppPalette.GlassFill/GlassHighlight/Scrim），它本就该留在 seed" +
                "并登记进 ThemeInvariantMaskReadLedger——别删 seed、别强行主题化蒙版；除此之外不接受临时豁免。"
        )
    }

    @Test
    fun `a file whose reads dropped below the seed must decrement the seed too`() {
        val actual = actualCounts()
        val stale = DarkTokenSeedAllowlist.filterKeys { it !in actual }
        assertTrue(
            stale.isEmpty(),
            "seed 登记的文件已无静态深色读点（该文件债务已全部迁走）：$stale —— 应把该行的 seed 一并删掉；" +
                "但若它仍有命中、只是 seed 写多了，那是计数漂移、由逐文件相等用例处理，不是删行"
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
        // M-N2：政策豁免表 key 是相对路径，与 actualCounts()/seed 同一口径，直接精确集合判定，不再 endsWith 匹配裸名。
        val actual = actualCounts()
        val leaked = StaticReadPolicyExemptions.keys.filter { it in actual }
        assertTrue(
            leaked.isEmpty(),
            "政策豁免的定义文件里出现了静态读点（豁免只覆盖「这里是值的来源」，不覆盖消费）：" +
                leaked.joinToString("\n") { "$it 命中 ${actual.getValue(it)} 处" } +
                "\n改回 appColors() / appDomain()；若确实必须留在定义文件里，先修订本表理由并同步 seed"
        )
        val seededExemptFiles = StaticReadPolicyExemptions.keys.filter { it in DarkTokenSeedAllowlist.keys }
        assertTrue(
            seededExemptFiles.isEmpty(),
            "定义文件既被政策豁免又进了债务 seed，两套口径自相矛盾：$seededExemptFiles"
        )
    }

    @Test
    fun `parameter default and top level shaped reads are exactly the registered ledger`() {
        val sites = parameterDefaultShapedSites()
        val detected = sites.groupingBy { it.substringBefore(": ") }.eachCount()
        val unregistered = detected.filterKeys { it !in ParameterDefaultStaticReadLedger }
        assertTrue(
            unregistered.isEmpty(),
            "出现未登记的「形参默认值 / 文件顶层 val」静态读点：\n" +
                sites.filter { it.substringBefore(": ") in unregistered.keys }.joinToString("\n") +
                "\n首选改结构迁移（形参默认值换成可空哨兵、在函数体内 appDomain(...) 解析；顶层 val 改造成装配点现造的工厂函数）；" +
                "object 成员属性初始化器是同族另一形态，不登记进本表，交由 DarkTokenSeedAllowlist 承载其 seed。" +
                "确实结构上无法迁移才登记进 ParameterDefaultStaticReadLedger 并写明理由；本用例只做形态点名，判红由人来裁决。"
        )
        assertTrue(
            ParameterDefaultStaticReadLedger.keys.all { it in detected },
            "注记表里登记的站点已不存在（结构已改对或文件已改名）：" +
                ParameterDefaultStaticReadLedger.keys.filterNot { it in detected } +
                " —— 注记必须随之删除，残留注记会让下一个同类站点蒙过去。"
        )
        assertEquals(
            ParameterDefaultStaticReadLedger,
            detected,
            "形参默认值 / 文件顶层形态站点与注记表逐文件计数不符",
        )
    }

    @Test
    fun `ledger sites are a subset of the static read rule and of the seed`() {
        // M-N4：形态取样与主规则可能各写一份而漂移；本用例交叉断言每个注记站点既是一个 rule 命中，又落在 seed 里。
        val ruleHits = parameterDefaultShapedSites().filter { site ->
            rule.containsMatchIn(site.substringAfter(": "))
        }
        val sites = parameterDefaultShapedSites()
        assertTrue(
            ruleHits.size == sites.size,
            "注记表点名的站点里出现了不是主规则命中的行（形态正则与 rule 漂移）：\n" +
                sites.filterNot { it in ruleHits }.joinToString("\n")
        )
        val outsideSeed = sites.map { it.substringBefore(": ") }.distinct().filterNot { it in DarkTokenSeedAllowlist.keys }
        assertTrue(
            outsideSeed.isEmpty(),
            "注记表点名的文件不在债务 seed 里——注记必须 ⊆ 债务，谁单独漂移都会红：$outsideSeed"
        )
        ParameterDefaultStaticReadLedger.forEach { (path, count) ->
            assertTrue(
                (DarkTokenSeedAllowlist[path] ?: 0) >= count,
                "注记表在 $path 记了 $count 处，超过 seed ${DarkTokenSeedAllowlist[path]} 处——注记 ⊆ 债务"
            )
        }
    }

    @Test
    fun `theme invariant mask reads are exactly the registered ledger`() {
        val actual = maskCounts()
        val unregistered = actual.filterKeys { it !in ThemeInvariantMaskReadLedger }
        assertTrue(
            unregistered.isEmpty(),
            "出现未登记的主题不变蒙版色（AppPalette.GlassFill/GlassHighlight/Scrim）读点：$unregistered —— " +
                "蒙版色是合法残留，必须逐条登记进 ThemeInvariantMaskReadLedger 并写明理由，" +
                "别让「与主题无关」的站点游离在任何台账之外。"
        )
        val stale = ThemeInvariantMaskReadLedger.keys.filterNot { it in actual }
        assertTrue(
            stale.isEmpty(),
            "蒙版色台账里登记的文件已无蒙版色读点（已迁走/改名）：$stale —— 台账随之删除，残留会让下一处蒙版色蒙混过去。"
        )
        val drifted = ThemeInvariantMaskReadLedger.filterKeys { it in actual }
            .filter { (path, ledgerCount) -> actual.getValue(path) != ledgerCount }
        assertTrue(
            drifted.isEmpty(),
            "蒙版色逐文件计数与台账不符（增即挡，减了不同步改也挡）：\n" +
                drifted.entries.joinToString("\n") { (path, ledgerCount) ->
                    "$path 实际 ${actual.getValue(path)} 处，台账 $ledgerCount 处"
                }
        )
    }

    @Test
    fun `theme invariant mask ledger is a subset of the seed`() {
        // 蒙版色不豁免计数：每处蒙版色读点必须同时计入 debt seed（修一处两张表同减，谁单独漂移都会红）。
        ThemeInvariantMaskReadLedger.forEach { (path, maskCount) ->
            val seedCount = DarkTokenSeedAllowlist[path]
            assertTrue(
                seedCount != null && seedCount >= maskCount,
                "蒙版色台账在 $path 记了 $maskCount 处，但 debt seed 为 ${seedCount ?: "无"}——" +
                    "蒙版色计入 debt 台账，不允许只进注记表不进 seed"
            )
        }
        val actual = maskCounts()
        actual.forEach { (path, maskCount) ->
            val readCount = actualCounts()[path] ?: 0
            assertTrue(
                readCount >= maskCount,
                "$path 蒙版色 $maskCount 处却只有静态读点总计 $readCount 处——蒙版色取样与主规则口径矛盾"
            )
        }
    }

    @Test
    fun `mask ledger splits by member name with synthetic probes`() {
        // C1：证明蒙版色判定真在按成员名分流（不是恒真）。正片段被计数、反片段不被计数，缺一不可。
        val positive = "val scrim = AppPalette.Scrim\nval fill = AppPalette.GlassFill\nval edge = AppPalette.GlassHighlight\n"
        val negative = "val ink = AppPalette.TextPrimary\nval line = AppPalette.Separator\n" +
            "val ink2 = AppPalette.TextSecondary\nval deep = DomainColors.AiSpeech.deep\n" +
            "val local = LocalAppPalette.GlassFill\n"
        assertEquals(3, rule.findAll(stripComments(positive)).count(), "主规则应认出三枚蒙版色读取（共 3 处静态读点）")
        assertEquals(3, maskRule.findAll(stripComments(positive)).count(), "蒙版色规则必须认出全部三枚蒙版色成员命中")
        assertTrue(
            maskRule.findAll(stripComments(negative)).none(),
            "蒙版色规则不得把浅套硬编码 / 各域 deep / LocalAppPalette 误判成蒙版色（否则台账恒真）：" +
                maskRule.findAll(stripComments(negative)).map { it.value }
        )
    }

    @Test
    fun `parameter default ledger excludes object member initializer by design`() {
        // I2：合成正/反片段，钉住「形参默认值/顶层 val 被认出」与「object 成员属性初始化器被明确排除」都不是靠运气。
        val dir = java.nio.file.Files.createTempDirectory("param-default-ratchet").toFile()
        try {
            File(dir, "Probe.kt").writeText(
                """
                object AppType {
                    val Display = TextStyle(color = AppPalette.TextPrimary)
                }
                @Composable fun Row(accent: DomainAccent = DomainColors.AiSpeech) {}
                private val TopInk = AppPalette.Separator
                @Composable fun Body() {
                    val myStyle = TextStyle(color = AppPalette.TextSecondary)
                }
                """.trimIndent() + "\n"
            )
            val detected = parameterDefaultShapedSites(dir).map { it.substringAfter(": ") }
            assertTrue(
                detected.any { it.contains("accent: DomainAccent = DomainColors.AiSpeech") },
                "形参默认值形态必须被认出：$detected"
            )
            assertTrue(
                detected.any { it.contains("private val TopInk = AppPalette.Separator") },
                "文件顶层 val 形态必须被认出：$detected"
            )
            assertTrue(
                detected.none { it.contains("val Display = TextStyle") },
                "object 成员属性初始化器（缩进 val，无列 0、无 名字: 类型 =）必须被本表排除，不能误纳：$detected"
            )
            assertTrue(
                detected.none { it.contains("val myStyle = TextStyle") },
                "可就地迁移的 @Composable 体内局部 val 必须被排除（否则反向假阳把债务误标成注记）：$detected"
            )
        } finally {
            dir.deleteRecursively()
        }
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
