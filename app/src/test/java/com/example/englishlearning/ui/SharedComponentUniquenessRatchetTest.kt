package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 共享组件唯一性棘轮（批 1 Task 12 Step 3，承接 Task 8 Step 4「顺延到 Task 12」那一棒）。
 *
 * 管什么：**同一形态不得有第二份组件**。批 1 把 12 枚共享组件建齐之后，屏侧那些「各自手写一遍」的私有份
 * 就成了债务；本棘轮按 spec §3 的四族形态逐族登记债务台账（[ComponentFamilySeeds]），
 * 断言是「逐族**精确集合相等**」，两侧都不许偏：
 *  ① 实际违规文件多出 seed 之外的一枚（含全新屏冒出来）→ 新增即挡；
 *  ② 实际少了（某屏接线完毕）而 seed 未同步递减 → 也挡。
 * 所以批 2/批 3 每屏接线完，必须同步把该文件从对应族的 seed 里删掉；seed 是债务台账，不是永久豁免。
 *
 * 不管什么（**R-12.3 ⑤ 的预登记豁免，Task 9 评审裁决 4.3 前置②**）：本棘轮**不管「就地改既有组件的取值面」**。
 * Task 14 修 `PillButton` 的静态 ink / 档位差时必须改 `PillButton` 本体，而不是新建第二枚按钮——
 * 那条路径不得被本棘轮挡。这不是口头承诺：[the ratchet blocks a second component but never an in place value edit]
 * 拿 `GlassButton.kt` 的真源码做**就地取值改写**，证明四族判定面对改写前后逐字不变；
 * 同一条用例再拿合成片段证明反方向（多写一份同形态组件当场判违规）。
 * 写进注释与 seed 说明是为了避免批 2 为绕门再造一枚按钮：绕不过去，而且**不该**绕。
 *
 * 也不管什么（口径边界，别把力气花错地方）：本棘轮不宣称「四族债务清零」。批 1 时点业务屏尚未接线
 * （precheck §3：`grep -rl 'AppScreenHeader('` 只命中组件自身），所以四族的 seed 都是**实测存量**，
 * 清零归批 2/批 3 逐屏接线，最终收口判据见计划批 3。
 *
 * 三条实现口径（前几棒踩过的坑）：
 *  - **先剥注释再匹配**：`components/InlineNotice.kt` 的 KDoc 里逐字复述了本棘轮那条 grep 命令
 *    （含 `AppPalette.ErrorText\|colorScheme.error`），`AppScreen.kt` 也有一行注释提到 `colorScheme.error`；
 *    不剥注释就会把注释当债务、把契约写成空转。读法一律走共享夹具 [uiComponentSource]/[uiFiles] + [stripComments]。
 *    **夹具本身也有债**（修复轮 1 的 Fix 1）：正则版 `stripComments` 不认字符串字面量，SAF 的 MIME 字面量
 *    「星斜星斜」那一枚会让块注释正则吞掉其后几十行真代码，使 `BackupRestoreScreen.kt` 的错误色裸用点
 *    从棘轮视野里消失。现夹具是字符串感知的扫描式实现，由
 *    [stripComments keeps code after a string literal that contains a comment marker] 与
 *    [notice family ledger pins the backup restore site that comment stripping used to swallow] 两条钉住，勿改回正则版。
 *  - **同名遮蔽**：四屏的私有 `NotConfiguredCard` 与共享份**同名**（precheck §2），所以「文件里出现
 *    `NotConfiguredCard(`」不能当合规证据；规则形状必须是「出现 `private fun NotConfiguredCard(` 即违规」
 *    +「`components/NotConfiguredCard.kt` 恰 1 处 `fun NotConfiguredCard(`」两条并立。
 *  - **词行不能只搜函数名**（precheck §6 / R-12.3 ④）：私有份多是 `Row { Text(lemma); Text(gloss) }` 这类
 *    **无名裸组合**，函数名各屏自取（`WordBookWordsRow`/`RecordRow`/`FavoriteRow`/`SenseRow`…）。
 *    故规则按**私有 Row 函数体窗口**取样：体内并排出现「lemma 那枚 Text」与「另一枚 Text」才算一族。
 *    只搜 `.lemma` 会误伤（`AppScreen.kt` 的 `DetailToggleRow` 与 `CardDetailScreen.kt` 的 `SenseRow`
 *    文件级都提到 lemma，但函数体里没有 lemma 词头行），窗口化之后两枚都不判违规。
 *  - **`Icons.` 禁令复述**：Global Constraint 13 的口径是 `\bIcons\.`（词界锚点必要：`AppIcons.` 里 `Icons`
 *    前一个字符 `p` 是词字符，`\b` 不成立，所以 33 枚字形的宿主对象名不会被误判——precheck §5）。
 *    禁令的真值由 `AppIconsStructureContractTest` 以 import 字符串形状钉住，本棘轮**复述**同一口径，
 *    让「新组件偷偷引库内图标」在份数棘轮上也当场红，而不是只在结构测试里红。
 */
class SharedComponentUniquenessRatchetTest {

    private val uiDir = File(UI_MAIN_SOURCE_PATH)

    /** 扫描面：`ui/` 全子树（含 `components/` 与 `components/glass/`）的「相对路径 → 剥注释源码」。 */
    private fun scannedSources(): List<Pair<String, String>> =
        uiFiles(uiDir).map { it.relativePathFrom(uiDir) to stripComments(it.readText()) }

    // ---------- 四族规则（纯函数：只吃源码文本，便于合成用例双向自证） ----------

    /** 族 1 页头：用了库内 `headline*` 版式却不调共享页头。 */
    private val headlineRead = Regex("""MaterialTheme\.typography\.headline""")
    private fun isHeaderOffender(source: String): Boolean =
        headlineRead.containsMatchIn(source) && !source.contains("AppScreenHeader(")

    /** 族 2 提示条：直接拿错误色渲染 `Text` 却不调 `InlineNotice(`。 */
    private val errorInkRead = Regex("""AppPalette\.ErrorText|colorScheme\.error""")
    private fun isNoticeOffender(source: String): Boolean =
        errorInkRead.containsMatchIn(source) && !source.contains("InlineNotice(")

    /** 族 3 未配置卡：出现**私有**同形态定义即违规（同名遮蔽，见类注释第三条）。 */
    private val privateNotConfiguredCard = Regex("""private fun NotConfiguredCard\(""")
    private fun isCardOffender(source: String): Boolean = privateNotConfiguredCard.containsMatchIn(source)

    /**
     * 族 4 词行：私有 `*Row*` 组合体内并排「lemma 词头 Text」+「另一枚 Text」，且本文件不调共享 `WordRow(`。
     *
     * 取样窗口是**顶层私有 Row 函数的函数体**：本仓顶层函数的右花括号写在第 0 列，故窗口取
     * 「`^private fun …Row…(` 起，到其后第一个 `\n}` 止」。窗口化是本族不误伤的关键（见类注释第三条）。
     */
    private val privateRowFunction = Regex("""(?m)^private fun [A-Za-z0-9_]*Row[A-Za-z0-9_]*\(""")
    private val lemmaText = Regex("""Text\([^)]*[Ll]emma""")
    private val anyText = Regex("""Text\(""")
    private val sharedWordRowCall = Regex("""(^|[^A-Za-z0-9_])WordRow\(""")

    private fun rowFunctionBodies(source: String): List<String> =
        privateRowFunction.findAll(source).map { match ->
            val end = source.indexOf("\n}", match.range.first).let { if (it == -1) source.length else it }
            source.substring(match.range.first, end)
        }.toList()

    private fun isWordRowOffender(source: String): Boolean {
        if (sharedWordRowCall.containsMatchIn(source)) return false
        return rowFunctionBodies(source).any { body ->
            lemmaText.containsMatchIn(body) && anyText.findAll(body).count() >= 2
        }
    }

    /** 四族判定面（族名 → 判定函数）：seed 表与合成自证用例都按这张表遍历，避免漏一族。 */
    private val families: Map<String, (String) -> Boolean> = linkedMapOf(
        "页头（应调 AppScreenHeader）" to ::isHeaderOffender,
        "错误提示条（应调 InlineNotice）" to ::isNoticeOffender,
        "未配置卡（应调 NotConfiguredCard）" to ::isCardOffender,
        "词行（应调 WordRow）" to ::isWordRowOffender,
    )

    private fun offendersOf(rule: (String) -> Boolean): Set<String> =
        scannedSources().filter { (_, source) -> rule(source) }.map { (path, _) -> path }.toSet()

    // ---------- 债务台账（seed）----------

    /**
     * 四族的债务台账：族名 → 违规文件的相对路径集（相对 `ui/` 根，正斜杠）。
     *
     * seed 取值为控制者本席 grep 实测 + 本文件同口径复算（`task-12-precheck.md` §2/§3/§4/§6、计划 R-12.3）：
     *  - 页头 **22** 枚：R-12.3 ② 明写「批 1 断不出 14 → 0」，本族只钉实测存量、只减不增，清零归批 3；
     *  - 提示条 **12** 枚：R-12.3 ③ 订正了 Task 8 Step 4 点名的清单（`LearningRecordsScreen.kt` 与
     *    `SettingsScreen.kt` **不在**其中），以实测 12 为准，清零归批 3；
     *  - 未配置卡 **4** 枚：R-12.3 ① 已核实（`ImageStudioScreen`/`PhotoTranslationScreen`/
     *    `SentenceAnalysisScreen`/`WordAiQaScreen` 各一份私有定义），批 2/批 3 接线时逐枚删；
     *  - 词行 **6** 枚：spec §3 点名的 6 个文件（`WordBookWordsScreen`/`LearningRecordsScreen`/
     *    `GlobalVocabularySearchScreen`/`VocabularyScreen`/`WrongWordsScreen`/`FavoritesScreen`）。
     *    spec 说「7 份」是「6 文件 + `GlobalVocabularySearchScreen` 里的第二变体」，
     *    本台账按**文件**计（同文件两变体接线时一并消失，按变体计会让 seed 半路删不掉）。
     *
     * 台账**不含**任何组件文件：`components/` 下的 12 枚是各族的唯一真值，它们出现在 seed 里就等于
     * 把「唯一份」登记成「债务」；[canonical component files exist with exactly one top level definition each]
     * 从反方向钉这件事。
     */
    private val componentFamilySeeds: Map<String, Set<String>> = mapOf(
        "页头（应调 AppScreenHeader）" to setOf(
            "AiFeatureScreen.kt",
            "AiLearningScreen.kt",
            "AppScreen.kt",
            "ArticleImportScreen.kt",
            "ArticleReadingScreen.kt",
            "CheckInScreen.kt",
            "FavoritesScreen.kt",
            "GlobalVocabularySearchScreen.kt",
            "ImageStudioScreen.kt",
            "LicensesScreen.kt",
            "PhotoTranslationScreen.kt",
            "ReadingAccessScreen.kt",
            "ReadingHistoryScreen.kt",
            "SentenceAnalysisScreen.kt",
            "SettingsScreen.kt",
            "TodayPlanScreen.kt",
            "VocabularyScreen.kt",
            "WordAiQaScreen.kt",
            "WordCardScreen.kt",
            "WorksheetPreviewScreen.kt",
            "WorksheetSettingsScreen.kt",
            "WrongWordsScreen.kt",
        ),
        "错误提示条（应调 InlineNotice）" to setOf(
            "AppScreen.kt",
            "ArticleImportScreen.kt",
            "BackupExportScreen.kt",
            "BackupPairingExportScreen.kt",
            "BackupRestoreScreen.kt",
            "CardDetailScreen.kt",
            "ReadingAccessScreen.kt",
            "ReminderSettingsScreen.kt",
            "SpeechSettingsScreen.kt",
            "WordAiQaScreen.kt",
            "WordCardScreen.kt",
            "WorksheetSettingsScreen.kt",
        ),
        "未配置卡（应调 NotConfiguredCard）" to setOf(
            "ImageStudioScreen.kt",
            "PhotoTranslationScreen.kt",
            "SentenceAnalysisScreen.kt",
            "WordAiQaScreen.kt",
        ),
        "词行（应调 WordRow）" to setOf(
            "FavoritesScreen.kt",
            "GlobalVocabularySearchScreen.kt",
            "LearningRecordsScreen.kt",
            "VocabularyScreen.kt",
            "WordBookWordsScreen.kt",
            "WrongWordsScreen.kt",
        ),
    )

    // ---------- 组件面（存在 + 恰一处定义） ----------

    /**
     * 批 1 结束时 `components` 目录下的 12 枚 `.kt`（precheck §1 / R-12.3：Task 11 后 10 枚 + 本任务 2 枚）。
     * **按「点名文件必须存在」写，不按总数写**——总数会随后续批次漂移（R-12.3 明令）。
     */
    private val canonicalComponentFiles = listOf(
        "AppIcon.kt",
        "AppIcons.kt",
        "AppScreenHeader.kt",
        "ConfirmDestructiveDialog.kt",
        "EntryRow.kt",
        "IconBadge.kt",
        "InlineNotice.kt",
        "NotConfiguredCard.kt",
        "OutboundConfirmDialog.kt",
        "ScreenUnavailable.kt",
        "SpeakerButton.kt",
        "WordRow.kt",
    )

    /** 组件名 → 承载它的文件（`IconBadge.kt` 与 `InlineNotice.kt` 各承载两枚公开组件）。 */
    private val canonicalDefinitions: Map<String, String> = mapOf(
        "AppGlyph" to "AppIcon.kt",
        "AppScreenHeader" to "AppScreenHeader.kt",
        "ConfirmDestructiveDialog" to "ConfirmDestructiveDialog.kt",
        "EntryRow" to "EntryRow.kt",
        "EntryIconBadge" to "IconBadge.kt",
        "InlineIconBadge" to "IconBadge.kt",
        "InlineNotice" to "InlineNotice.kt",
        "InlineNoticeAction" to "InlineNotice.kt",
        "NotConfiguredCard" to "NotConfiguredCard.kt",
        "OutboundConfirmDialog" to "OutboundConfirmDialog.kt",
        "ScreenUnavailable" to "ScreenUnavailable.kt",
        "SpeakerButton" to "SpeakerButton.kt",
        "WordRow" to "WordRow.kt",
    )

    /**
     * 12 枚组件文件都存在，且每枚公开组件在**自己的文件里恰一处顶层定义**。
     *
     * 两条断言合起来才是「唯一性」：
     *  - 存在性走共享夹具 [uiComponentSource]（内置「文件恰一个 + 目录守卫」，缺失/改名/挪目录当场点名）；
     *  - 定义份数用 `(?m)^fun <Name>(` 钉**顶层**定义：缩进过的同形态私有份（`    private fun WordRow(`）
     *    不被这条数到，它归四族规则管——两条各管一半，谁都别替对方兜底。
     * 第二份顶层定义（哪怕在别的文件里）会让「组件在哪」失唯一真值，所以按**全扫描面**计数而不是按文件计数。
     */
    @Test
    fun canonical_component_files_exist_with_exactly_one_top_level_definition_each() {
        val sources = scannedSources()
        assertTrue(sources.isNotEmpty(), "扫描面为空：$UI_MAIN_SOURCE_PATH 下没读到 .kt（棘轮会假绿）")
        val present = sources.map { (path, _) -> path.substringAfterLast('/') }.toSet()
        val missing = canonicalComponentFiles.filterNot { it in present }
        assertTrue(
            missing.isEmpty(),
            "批 1 组件文件缺失（改名/挪目录都要同步本表与各族规则）：$missing；扫描根 ${uiDir.path}，" +
                "JVM 工作目录 ${File(".").absolutePath}"
        )
        canonicalComponentFiles.forEach { name ->
            // 目录守卫：这 12 枚必须真在 components/ 下，不许散落到屏文件里。
            assertTrue(
                uiComponentSource(name).isNotBlank(),
                "$name 剥注释后为空：组件文件是空壳（唯一份被搬空 == 没有唯一份）"
            )
        }
        canonicalDefinitions.forEach { (component, file) ->
            val total = sources.sumOf { (_, source) ->
                countMatches(source, Regex("""(?m)^fun $component\("""))
            }
            assertEquals(
                1,
                total,
                "共享组件 $component 在整个 ui/ 扫描面上应恰一处顶层定义（承载文件 $file），实际 $total 处——" +
                    "0 处 = 组件被删/改名，2 处及以上 = 长出了第二份同形态组件"
            )
            val inOwner = sources.first { (path, _) -> path.endsWith("/$file") }.second
            assertEquals(
                1,
                countMatches(inOwner, Regex("""(?m)^fun $component\(""")),
                "$component 的唯一顶层定义应在 $file 里（跑到别的文件即组件归属失唯一真值）"
            )
        }
        // AppIcons.kt 承载的是 33 枚字形的枚举，不是 @Composable：按 enum 声明钉唯一性。
        val enums = sources.sumOf { (_, source) -> countMatches(source, Regex("""(?m)^enum class AppIcon\b""")) }
        assertEquals(1, enums, "enum class AppIcon 应恰一处声明（第二处即第二套字形清单）")
    }

    // ---------- 四族棘轮 ----------

    /** seed 表的族名与 key 必须与 [families] 逐字对得上，且 seed 里不许混进组件文件。 */
    @Test
    fun seed_table_covers_every_family_and_never_lists_a_canonical_component() {
        assertEquals(
            families.keys,
            componentFamilySeeds.keys,
            "族判定面与 seed 台账的族名不一致（漏一族 == 那一族棘轮空转）"
        )
        val componentPaths = canonicalComponentFiles.map { "components/$it" }.toSet() +
            "components/glass/GlassButton.kt" +
            "components/glass/GlassDialog.kt" +
            "components/glass/GlassOverlay.kt"
        componentFamilySeeds.forEach { (family, seed) ->
            assertTrue(seed.isNotEmpty(), "$family 的 seed 为空：空台账让本族棘轮退化成 no-op（批 1 时点四族都有存量）")
            val leaked = seed.filter { it in componentPaths }
            assertTrue(
                leaked.isEmpty(),
                "$family 的 seed 把组件文件登记成债务：$leaked —— 组件是各族的唯一真值，不是待清的私有份"
            )
        }
    }

    /** seed 里的文件必须真在扫描面上（改名/移动/接线完毕都要同步删行）。 */
    @Test
    fun seed_files_point_at_real_scanned_files() {
        val scanned = scannedSources().map { (path, _) -> path }.toSet()
        val unknown = componentFamilySeeds.values.flatten().filterNot { it in scanned }.distinct()
        assertTrue(
            unknown.isEmpty(),
            "seed 登记的文件不在扫描集里（改名/移动/已接线都要同步 seed）：$unknown；扫描根 ${uiDir.path}"
        )
    }

    /** 逐族精确集合相等：多出 seed 的一枚即新增债务（挡），少了而 seed 未递减即台账漂移（也挡）。 */
    @Test
    fun each_family_offender_set_equals_its_seed_exactly() {
        families.forEach { (family, rule) ->
            val actual = offendersOf(rule)
            val seed = componentFamilySeeds.getValue(family)
            val added = actual - seed
            assertTrue(
                added.isEmpty(),
                "$family 出现 seed 之外的私有份：$added —— 应改调共享组件（页头 AppScreenHeader / " +
                    "提示条 InlineNotice / 未配置卡 NotConfiguredCard / 词行 WordRow），" +
                    "而不是再造一份同形态组件；若确属本族形态之外的合法写法，先修订规则形状再改 seed。"
            )
            val cleared = seed - actual
            assertTrue(
                cleared.isEmpty(),
                "$family 的这些文件已不再违规，seed 必须同步删行（seed 是债务台账不是永久豁免）：$cleared"
            )
        }
    }

    /**
     * `\bIcons\.` 全域 0 处（Global Constraint 13 的复述，precheck §5）。
     *
     * 词界锚点是必要的：`AppIcons.` 里 `Icons` 前一个字符 `p` 是词字符，`\b` 不成立，
     * 所以这条不会把 33 枚字形的宿主对象名判成违规。**不要**退化成无锚点的 `Icons\.`
     * （那会把 `AppIcons.` 的 7 处命中全判成违规，棘轮当场假红）。
     * 禁令的另一半（import 形状）由 `AppIconsStructureContractTest` 钉；本条钉的是**用法形状**，
     * 让「新组件直接写 `Icons.Filled.X`」在份数棘轮上也红。
     */
    @Test
    fun no_library_icon_usage_anywhere_under_ui() {
        val offenders = scannedSources()
            .filter { (_, source) -> countMatches(source, Regex("""\bIcons\.""")) > 0 }
            .map { (path, _) -> path }
        assertTrue(
            offenders.isEmpty(),
            "出现库内图标用法（禁 androidx.compose.material.icons.Icons，一律走 AppIcon + AppGlyph）：$offenders"
        )
        // 反空转：这条必须判得出真违规，否则锚点写错成 `AppIcons\.` 一类也会「0 处」假绿。
        assertTrue(
            countMatches("val v = Icons.Filled.Search", Regex("""\bIcons\.""" )) == 1,
            "合成正例未被判违规：\\bIcons\\. 的形状或 stripComments 失控（棘轮空转）"
        )
        assertEquals(
            0,
            countMatches("val v = AppIcons.all", Regex("""\bIcons\.""")),
            "合成负例被误判：词界锚点失效会把 AppIcons. 判成违规（precheck §5）"
        )
    }

    /**
     * **预登记豁免的可执行证明**（R-12.3 ⑤ / Task 9 评审裁决 4.3 前置②）：
     * 本棘轮管「同一形态不得有第二份组件」，**不管「就地改既有组件的取值面」**。
     *
     * 三段承重：
     *  ① 拿 `components/glass/GlassButton.kt`（`PillButton` 的承载文件）的**真源码**做就地取值改写
     *     （dp 数值、令牌读取、圆角档、字重都换一遍——正是 Task 14 要动的那一面），
     *     四族判定面对改写前后必须**逐字不变**：改取值不产生新组件，所以不得被本棘轮挡；
     *  ② `PillButton` 的承载文件本身不在任何一族的 seed 里，且 `fun PillButton(` 全扫描面恰一处
     *     （Task 14 只有「改本体」这一条路，没有「另起一枚」这条路）；
     *  ③ 反方向：给同一段合成屏源码**追加**一份同形态私有组件，四族各自当场判违规——
     *     证明 ① 的「不挡」是规则形状使然，不是规则空转。
     */
    @Test
    fun the_ratchet_blocks_a_second_component_but_never_an_in_place_value_edit() {
        val pillPath = "components/glass/GlassButton.kt"
        val pill = uiComponentSource("GlassButton.kt", "/components/glass/")
        // ① 就地改取值面：数值、令牌、圆角、字重各换一遍（Task 14 的动作面），四族判定必须不变。
        val valueEdited = pill
            .replace("16.dp", "20.dp")
            .replace("12.dp", "14.dp")
            .replace("AppPalette.", "AppPaletteRenamed.")
            .replace("DomainColors.", "DomainColorsRenamed.")
            .replace("FontWeight.Medium", "FontWeight.SemiBold")
        assertTrue(valueEdited != pill, "改写没生效：合成用例空转（源里没有可改的取值面？）")
        families.forEach { (family, rule) ->
            assertEquals(
                rule(pill),
                rule(valueEdited),
                "$family 的判定面在「就地改 $pillPath 的取值」之后变了——本棘轮不得挡 Task 14 改 PillButton 本体"
            )
        }
        assertEquals(
            1,
            scannedSources().sumOf { (_, source) -> countMatches(source, Regex("""(?m)^fun PillButton\(""")) },
            "PillButton 应恰一处顶层定义：Task 14 的处置是改本体，不是新建第二枚药片"
        )
        componentFamilySeeds.forEach { (family, seed) ->
            assertTrue(
                pillPath !in seed,
                "$family 的 seed 把 $pillPath 登记成债务：改既有组件的取值面不属本棘轮管辖（R-12.3 ⑤）"
            )
        }
        // ③ 反方向：负控必须打在**不调任何共享组件**的底本上。
        //    踩过的坑（修复轮 1 的 Fix 2）：原先往 `clean` 末尾追加私有副本，而族 1/2/4 的规则都带
        //    「本文件调了共享组件即合规」那一半 —— `clean` 里四枚共享调用全在，追加多少份私有副本都判合规，
        //    于是探针被当成「规则空转」报红。真相是**底本选错**，不是规则错：负控改用 `bare`（同样的私有写法，
        //    但不含任何共享调用），`clean` 只留给下面的正向控。
        val clean = """
            @Composable
            private fun SyntheticCleanScreen(title: String, lemma: String, gloss: String) {
                AppScreenHeader(title = title)
                InlineNotice(kind = InlineNoticeKind.Error, message = "出错了")
                NotConfiguredCard(title = "未配置", body = "先去设置里配置")
                WordRow(lemma = lemma, gloss = gloss)
            }
        """.trimIndent()
        val bare = """
            @Composable
            private fun SyntheticBareScreen(title: String, lemma: String, gloss: String) {
                Text(title)
            }
        """.trimIndent()
        families.forEach { (family, rule) ->
            assertEquals(false, rule(clean), "$family 把「全部改调共享组件」的合成屏判成违规：规则形状过宽（$family）")
            // 底本自检：`bare` 本身必须四族全绿，否则下面的负控是「底本就违规」的空转证明。
            assertEquals(false, rule(bare), "$family 把「没有第二份组件的合成屏」判成违规：负控底本自身不合规，探针失去意义")
        }
        assertTrue(isHeaderOffender(bare + "\n\n@Composable\nprivate fun SyntheticHeader() {\n    Text(\"t\", style = MaterialTheme.typography.headlineMedium)\n}"), "追加私有页头后族 1 未判违规：规则空转")
        assertTrue(isNoticeOffender(bare + "\n\n@Composable\nprivate fun SyntheticError() {\n    Text(\"boom\", color = MaterialTheme.colorScheme.error)\n}"), "追加错误色 Text 后族 2 未判违规：规则空转")
        assertTrue(isCardOffender(bare + "\n\n@Composable\nprivate fun NotConfiguredCard(reason: String) {\n    Text(reason)\n}"), "追加私有未配置卡后族 3 未判违规：规则空转")
        // 族 4 要的是顶层私有 `*Row*` 函数体窗口，故追加片段的右花括号必须写在第 0 列（本仓顶层函数的既有形状），
        // 否则不落 `rowFunctionBodies` 的窗口 —— 那是窗口规则的前提，不是本次要改的判定面。
        assertTrue(isWordRowOffender(bare + "\n\n@Composable\nprivate fun SyntheticWordRow(lemma: String, gloss: String) {\n    Text(lemma)\n    Text(gloss)\n}"), "追加私有词行后族 4 未判违规：规则空转")
        // 族 1/族 2 的「调了共享组件即合规」这一半也要能判红，否则规则退化成「只要出现 headline 就违规」。
        assertEquals(
            false,
            isHeaderOffender("@Composable\nprivate fun H() {\n    AppScreenHeader(title = \"t\")\n    Text(\"t\", style = MaterialTheme.typography.headlineMedium)\n}"),
            "调了 AppScreenHeader 仍被判私有页头：族 1 的合规出口失效"
        )
        assertEquals(
            false,
            isNoticeOffender("@Composable\nprivate fun N() {\n    InlineNotice(kind = InlineNoticeKind.Error, message = \"boom\")\n    Text(\"x\", color = MaterialTheme.colorScheme.error)\n}"),
            "调了 InlineNotice 仍被判私有提示条：族 2 的合规出口失效"
        )
        // 族 4 的窗口化自证：文件级提到 lemma、但私有 Row 体内没有词头行的两枚真实误伤源不得判违规。
        assertEquals(
            false,
            isWordRowOffender(
                """
                @Composable
                private fun SenseRow(partOfSpeech: String, meaningZh: String) {
                    Text(text = partOfSpeech)
                    Text(text = meaningZh)
                }
                val x = card.lemma
                """.trimIndent()
            ),
            "族 4 误伤：体内没有 lemma 词头行的私有 Row（CardDetailScreen.SenseRow 形状）被判违规——窗口化失效"
        )
        assertEquals(
            false,
            isWordRowOffender(
                """
                @Composable
                private fun DetailToggleRow(label: String, description: String) {
                    Text(label)
                    Text(description)
                }
                fun f(id: String) = repo.load(id.lemma, id.wordBookId)
                """.trimIndent()
            ),
            "族 4 误伤：体内没有 lemma 词头行的私有 Row（AppScreen.DetailToggleRow 形状）被判违规——窗口化失效"
        )
    }

    /**
     * 词行族的 seed 必须是 spec §3 点名的那 6 个文件，逐枚点名（防「规则形状改了、seed 数字凑巧还对」）。
     *
     * **本族规则形状的已知边界（修复轮 1 的 Fix 3 裁决，已在 `docs/verification/ui-redesign-2026-10.md` 登记）**：
     * spec 说 7 份 = 6 文件 + `GlobalVocabularySearchScreen.kt` 里的第二变体，但第二变体
     * （`:274-299`）是 `GlobalSearchHistory` 中一枚**无名内联 `Row { … }`**（写 `item.history.displayQuery` + 「搜索 N 次」），
     * 它不是顶层私有 `*Row*` 函数，**结构上就落在窗口规则之外**：本族规则看不见它。
     * 所以这里钉的是「该文件按窗口规则恰 **1** 处落窗」（`:319` 的 `private fun GlobalSearchResultRow(`，
     * 体内 `result.lemma` + `result.meaningZh` 两枚 Text），而不是硬凑 2。
     * **不得**为了凑数把窗口放宽成「任何 `Row {`」——那会立刻误伤 `CardDetailScreen.kt` 的 `SenseRow`
     * 与 `AppScreen.kt` 的 `DetailToggleRow`（两枚都不是词行），seed 会从 6 变 8。
     * 台账按**文件**计，故第二变体接线时（批 3）与第一变体在同一行里一并消失，无需为它单开 seed 条目。
     */
    @Test
    fun word_row_family_names_the_six_spec_files_and_pins_the_window_rule_boundary() {
        val seed = componentFamilySeeds.getValue("词行（应调 WordRow）")
        assertEquals(
            setOf(
                "FavoritesScreen.kt",
                "GlobalVocabularySearchScreen.kt",
                "LearningRecordsScreen.kt",
                "VocabularyScreen.kt",
                "WordBookWordsScreen.kt",
                "WrongWordsScreen.kt",
            ),
            seed,
            "词行族 seed 与 spec §3 点名的 6 个文件不符（改 seed 前先改规则形状并说明理由）"
        )
        val search = uiComponentSource("GlobalVocabularySearchScreen.kt", "/")
        val hits = rowFunctionBodies(search).count { body ->
            lemmaText.containsMatchIn(body) && anyText.findAll(body).count() >= 2
        }
        assertEquals(
            1,
            hits,
            "GlobalVocabularySearchScreen.kt 按窗口规则恰 1 处落窗（`:319` GlobalSearchResultRow）；" +
                "第二变体是无名内联 Row，本族规则抓不到，已作为限制登记进 docs/verification/ui-redesign-2026-10.md，" +
                "接线时（批 3）与第一变体一并消失。实际 $hits ——数值变了说明规则形状或该文件写法动了，先改文档再改本断言。"
        )
    }

    /**
     * **剥注释夹具自身的钉**（修复轮 1 的 Fix 1，Critical）。
     *
     * 旧 `stripComments` 用块注释正则、不认字符串字面量：SAF 的 MIME 数组字面量里那个「斜杠紧跟星号」
     * 被当成块注释起始，于是一路吞到下一条行尾注释的闭合序列，把中间几十行真代码当注释删掉 ——
     * `BackupRestoreScreen.kt` 的 `Text(it, color = MaterialTheme.colorScheme.error, …)` 就是这样从棘轮视野里消失的。
     * （本条注释本身刻意不写出那两个序列的本体：KDoc 支持嵌套块注释，写出来会把这段注释自己弄坏。）
     * 这条用例喂**合成输入**（不含任何本仓真实文件），同时钉住两半：
     *  - 字面量里的注释标记**不得**吞掉其后的代码（否则数到 0）；
     *  - 真注释仍须被剥掉（否则数到 2）。**别把 1 改成 2** —— 那等于放行「行注释剥不掉」这一半退化。
     */
    @Test
    fun stripComments_keeps_code_after_a_string_literal_that_contains_a_comment_marker() {
        val source = """
            val mime = arrayOf("application/zip", "*/*")
            val bad = Text(x, color = MaterialTheme.colorScheme.error)
            // colorScheme.error 在注释里，不该被算命中
        """.trimIndent()
        val stripped = stripComments(source)
        assertEquals(
            1,
            countMatches(stripped, Regex("""colorScheme\.error""")),
            "字符串字面量里的 \"*/*\" 不得把其后的真代码当块注释吞掉",
        )
        assertEquals(0, countMatches(stripped, Regex("""AppPalette\.ErrorText""")), "夹具空转自检用")
    }

    /**
     * 同一条修复的**真实文件锚**（Fix 1 的第二半）：把这次踩到的现场钉进台账，
     * 防止有人日后改写 `BackupRestoreScreen.kt` 的 MIME 字面量来「绕过」夹具，也防止夹具再退化成正则版
     * （退化后 :166 那枚错误色 `Text` 会重新被吞掉，本条先红）。
     */
    @Test
    fun notice_family_ledger_pins_the_backup_restore_site_that_comment_stripping_used_to_swallow() {
        val source = uiComponentSource("BackupRestoreScreen.kt", "/")
        assertEquals(
            1,
            countMatches(source, Regex("""AppPalette\.ErrorText|colorScheme\.error""")),
            "BackupRestoreScreen.kt 的错误色读点应恰 1 处（:166 渲染 state.errorMessage）；" +
                "0 处 = 剥注释夹具又把它吞了，2 处 = 现场多了裸用点，两种都要先改本条与 seed 说明",
        )
        assertTrue(
            offendersOf(::isNoticeOffender).contains("BackupRestoreScreen.kt"),
            "BackupRestoreScreen.kt:166 裸用 colorScheme.error 渲染提示，必须出现在提示条族台账里（剥注释夹具若退化，这里先红）",
        )
        assertTrue(
            "BackupRestoreScreen.kt" in componentFamilySeeds.getValue("错误提示条（应调 InlineNotice）"),
            "真实文件锚与 seed 台账脱节：seed 若删了这行，上面那条断言就变成没人认领的违规",
        )
    }
}
