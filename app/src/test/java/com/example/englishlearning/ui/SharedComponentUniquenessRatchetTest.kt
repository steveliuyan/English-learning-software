package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
 * 第三条「不管什么」（**批 1 终审 F7 / D-I-1 登记**）：族 1/2/4 的判定面各带一个**合规出口** ——
 * 「本文件调了共享组件即整体合规」。裁决保留出口（它撑住「已接线但私有形态未删净」的中间态与
 * R-12.3 ⑤ 的取值面豁免），但必须写明它的后果：**接线之后，同一屏残留的私有 forbidden 形态对本棘轮
 * 整体隐形**，即「补一行 `AppScreenHeader(` 就能让该屏从债务视野里消失」。批 1 时点全仓没有任何业务屏
 * 调用那三枚共享组件，所以这是「批 2 一接线即激活」的潜在失明面，不是现行缺陷。
 * 出口语义由 [family compliance exits are a declared known blindness surface] 双向钉住；
 * **批 2 的接线判据因此是「该屏 forbidden 读点清零」，不是「出现共享调用即准删 seed 行」**。
 * 收紧路径（本轮不做，属计划修订事项）：把 seed 从「文件级 0/1」改成「每屏读点计数」，
 * 并同步四族台账与 `each_family_offender_set_equals_its_seed_exactly` 的差集口径。
 * **例外（R-13.1 / C-1 已落地）**：Task 13 首版正是从这条判据失守——页头族 seed 摘了 `AppScreen.kt`
 * 一行，而该文件的 headline 读数还在。所以页头族先单独把「摘行的合法性」补成可执行的门：
 * [removing_a_file_from_the_header_seed_requires_its_headline_reads_to_be_cleared] 只盯
 * 「`ui/` 根屏 + 已摘本族 seed 行 + 已调用 `AppScreenHeader(`」这一形态，要求 headline 读点为 0；
 * 提示条族与词行族的同款收紧仍属计划修订事项，归批 3。
 *
 * 三条实现口径（前几棒踩过的坑）：
 *  - **先剥注释再匹配**：`components/InlineNotice.kt` 的 KDoc 里逐字复述了本棘轮那条 grep 命令
 *    （含 `AppPalette.ErrorText\|colorScheme.error`），`AppScreen.kt` 也有一行注释提到 `colorScheme.error`；
 *    不剥注释就会把注释当债务、把契约写成空转。读法一律走共享夹具：组件文件走 [uiComponentSource]
 *    （目录守卫按 `ui/components/` 片段匹配），`ui/` **根**下的屏文件走 [uiScreenSource]
 *    （守卫按「父目录恰等于 `ui` 根」判，修复轮 2 的 Fix I-3 —— 原先传 `dirFragment = "/"` 等于把守卫取消）；
 *    两者最终都落到 [uiFiles] + [stripComments] 这同一个入口。
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

    /**
     * 族 1 页头：用了库内 `headline*` 版式却不调共享页头。
     *
     * 与预检 §3 的形状有一处**有意偏差**（修复轮 2 的 M-e，批 3 别按预检重新推导别的数）：预检把形状写成
     * `Text(… style = MaterialTheme.typography.headline…)`，本规则只锚「文件里出现 `MaterialTheme.typography.headline`」
     * 这半边——少锚一半即**更宽**，只会多抓不会漏抓（两席独立枚举的命中文件数一致，无失明风险），
     * 代价是不问这枚版式被用在哪儿；若哪天确证需要收紧，先改规则形状再同步本条与本族 seed。
     */
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
     * **M-2（批 1 终审 D-M-2）：本 KDoc 不再写死每族「N 枚」的绝对数。**
     * 台账本身就是唯一真值：行数 = 该族当前的债务份数，接线完毕就删一行（写死数字与本文件的设计直接对冲，
     * 上一次它把「错误 / 回执行 11 份」漂成了实际 12/13）。
     * 重算命令（要一个可核对的数就跑这条，别抄文档里的数；本机 gradle 路径解析方式见 skill
     * `android-repo-session-handoff`，**绝不用 `./gradlew`**，wrapper 会联网下载）：
     * ```
     * "$GRADLE" :app:testDebugUnitTest --offline --no-daemon --no-build-cache \
     *   --tests com.example.englishlearning.ui.SharedComponentUniquenessRatchetTest
     * ```
     * 判据用例 `each_family_offender_set_equals_its_seed_exactly` 红的时候会把**实际集合**与 seed 的差集
     * （`added` / `cleared`）逐枚点名，照它同步 seed 即可；
     * `wc -l` 式的粗算也可以用，但口径必须与本文件 [offendersOf] 一致（先 [stripComments] 再判形状，
     * 「本文件调了共享组件即整体合规」的出口钉在 [family compliance exits are a declared known blindness surface] 里，粗算时容易把它漏掉而多计）。
     *
     * 取值为控制者本席 grep 实测 + 本文件同口径复算（`task-12-precheck.md` §2/§3/§4/§6、计划 R-12.3）：
     *  - 页头族：R-12.3 ② 明写「批 1 断不出 14 → 0」，本族只钉实测存量、只减不增，清零归批 3；
     *  - 提示条族：R-12.3 ③ 订正了 Task 8 Step 4 点名的清单（`LearningRecordsScreen.kt` 与
     *    `SettingsScreen.kt` **不在**其中），以实测为准，清零归批 3；
     *  - 未配置卡族：R-12.3 ① 已核实（`ImageStudioScreen`/`PhotoTranslationScreen`/
     *    `SentenceAnalysisScreen`/`WordAiQaScreen` 各一份私有定义），批 2/批 3 接线时逐枚删；
     *  - 词行族：spec §3 点名的六个文件为**原始名单**；Task 20-B（计划 Step 4）把其中三屏
     *    （`FavoritesScreen`/`WrongWordsScreen`/`WordBookWordsScreen`）的行本体换成共享 `WordRow`，
     *    按上面「接线完毕就删一行」的口径把这三行从台账删除。剩余行的归属与收法见计划 Step 4b
     *    （`GlobalVocabularySearchScreen.kt` 归 Task 20-C，`LearningRecordsScreen.kt`/`VocabularyScreen.kt`
     *    归本批收口 Task 26 Step 3）。删行的合法性由
     *    [word_row family seed is the spec list minus the wired screens and pins the window rule boundary]
     *    当场复核（已摘行的屏必须真调 `WordRow(` 且私有词行窗口清零），不是「补一行调用即准删」。
     *
     * 台账**不含**任何组件文件：`components/` 下的组件是各族的唯一真值，它们出现在 seed 里就等于
     * 把「唯一份」登记成「债务」；[canonical component files exist with exactly one top level definition each]
     * 从反方向钉这件事。
     */
    private val componentFamilySeeds: Map<String, Set<String>> = mapOf(
        "页头（应调 AppScreenHeader）" to setOf(
            "AiFeatureScreen.kt",
            "AiLearningScreen.kt",
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
            "GlobalVocabularySearchScreen.kt",
            "LearningRecordsScreen.kt",
            "VocabularyScreen.kt",
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
            // 顶层定义锚点前提（批 1 终审 F8 的 M-5 后半，**登记不修**）：`(?m)^fun X(` 只认「行首 fun」。
            // 它漏计 `@Composable fun X(` 这种**注解与定义同行**的写法。本仓全部分支的约定是注解独立成行，
            // 所以现状无漏；批 2 若有组件改成同行写法，这条锚点会数到 0 → 本用例撞红（不会静默放行），
            // 届时按「先改锚点形状再补合成片段」处理，别顺手放宽成 `fun X\(`（那会把缩进的私有同形态份也数进来）。
            val total = sources.sumOf { (_, source) ->
                countMatches(source, Regex("""(?m)^fun $component\("""))
            }
            assertEquals(
                1,
                total,
                "共享组件 $component 在整个 ui/ 扫描面上应恰一处顶层定义（承载文件 $file），实际 $total 处——" +
                    "0 处 = 组件被删/改名，2 处及以上 = 长出了第二份同形态组件"
            )
            // M-5（批 1 终审 D-M-5）：归属定位用**精确相对路径相等**（key 与 seed 台账同口径，相对 `ui/` 根），
            // 旧的 `path.endsWith("/$file")` 只比尾巴：`components/glass/` 或未来任何子目录里出现同名文件时，
            // 它会捡到错误的那一枚，「唯一顶层定义在承载文件里」这条就指着别的文件绿。
            val ownerPath = "components/$file"
            val inOwner = sources.firstOrNull { (path, _) -> path == ownerPath }?.second
                ?: error(
                    "$component 的承载文件在扫描面上定位不到（精确相等口径）：期望 $ownerPath，" +
                        "扫描根 ${uiDir.path}，实际路径集 ${sources.map { (path, _) -> path }.sorted()}"
                )
            assertEquals(
                1,
                countMatches(inOwner, Regex("""(?m)^fun $component\(""")),
                "$component 的唯一顶层定义应在 $ownerPath 里（跑到别的文件即组件归属失唯一真值）"
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
        // 族 1/族 2/族 4 的「调了共享组件即合规」出口不在这里复述：
        // 那半边由 [family compliance exits are a declared known blindness surface] 单独承重
        // （批 1 终审 F7：出口必须配失明面声明，不能只留一条行为断言）。
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
     * **R-13.1 / C-1：把「删 seed 行的合法性」钉成可执行判据 —— 摘了 seed 行的屏，本族 forbidden 读点必须已清零。**
     *
     * 同族的形状自证用例 [family_compliance_exits_are_a_declared_known_blindness_surface] 只**声明**了失明面与
     * 批 2 的接线判据（计划 `:1303`「删四族 seed 行的判据是该屏 forbidden 读点清零……绝不是『补一行
     * `AppScreenHeader(` 就准删』」），但判据本身没有门。Task 13 首版正从这里失守：
     * 它删了页头族 seed 的 `"AppScreen.kt"` 一行，而该文件里 `ProfileCreationScreen` 的
     * `MaterialTheme.typography.headlineSmall` 读点还在（首版 `:1777`），
     * 只是被同文件词书选取屏那枚 `AppScreenHeader(` 合规出口整体遮蔽 —— 于是「摘行」与「清零」被当成同一件事。
     * 本条补上这道门。
     *
     * 覆盖面刻意收得很窄，只判**「已按摘行声明自己接线完毕」的那一形态**（三条都得成立）：
     *  ① 文件在 `ui/` **根**（路径不含 `/`；组件文件是各族唯一真值，不参与屏侧账）；
     *  ② 它**不在**页头族 seed 里（在册 = 登记中的债务，清零时机归批 3，本条不得抢先判红）；
     *  ③ 它调用了 `AppScreenHeader(`（这正是合规出口被激活的那个动作）。
     * 三条同时成立时，headline 读点必须为 0。
     *
     * 与本条一同落地的是 C-1 的处置本身：`ProfileCreationScreen` 的两行裸页头改由 `AppScreenHeader` 承载，
     * 而不是给残余读点补一枚「点名 == 1」的锚 —— 裁决写明那等于给批 3 留一件要拆的东西。
     * 收口判据（计划 R-13.1 逐字一致）：`AppScreen.kt` 的 headline 读点数 == 0，本条末尾单独点名钉住。
     */
    @Test
    fun removing_a_file_from_the_header_seed_requires_its_headline_reads_to_be_cleared() {
        val headerSeed = componentFamilySeeds.getValue("页头（应调 AppScreenHeader）")
        val wiredRootScreens = scannedSources().filter { (path, source) ->
            !path.contains('/') && path !in headerSeed && source.contains("AppScreenHeader(")
        }
        assertTrue(
            wiredRootScreens.isNotEmpty(),
            "本条空转：扫描面上没有一枚「已摘页头族 seed 行且调用 AppScreenHeader(」的根屏文件 —— " +
                "覆盖面为空时本门对「摘行未清零」永远绿，等于没有门（页头族 seed 现况 $headerSeed）",
        )
        val notCleared = wiredRootScreens.mapNotNull { (path, source) ->
            val reads = countMatches(source, headlineRead)
            if (reads > 0) "$path（headline 读点 $reads 处，seed 行已摘）" else null
        }
        assertTrue(
            notCleared.isEmpty(),
            "页头族 seed 行被摘，但本文件的 headline 读点没清零：$notCleared —— " +
                "族判定面带「本文件调了 AppScreenHeader( 即整体合规」出口，残余读点会跟着永久隐形（批 1 终审 F7）。" +
                "要么把这些读点改调 AppScreenHeader（R-13.1 的清零路线），要么把 seed 行放回去并说明理由；" +
                "不许给残余读点补「点名读点数」锚 —— 那是给批 3 留一件要拆的东西。",
        )
        assertEquals(
            0,
            countMatches(uiScreenSource("AppScreen.kt"), headlineRead),
            "R-13.1 的收口判据：AppScreen.kt 清零后的 headline 读点数应为 0（Task 13 首版残余 :1777 一处，被遮蔽至隐形）",
        )
    }

    /**
     * **F7（批 1 终审 D-I-1）：把「本文件调了共享组件即整体合规」的出口钉成显式契约。**
     *
     * 出口的形状是裁决**保留**的（族 1 [isHeaderOffender] 的 `&& !source.contains("AppScreenHeader(")`、
     * 族 2 [isNoticeOffender] 的 `InlineNotice(` 同形、族 4 [isWordRowOffender] 命中 `sharedWordRowCall`
     * 即 `return false`）：它让「屏已接线、私有形态还没删干净」的中间态不被判成新增债务，
     * 也让 R-12.3 ⑤ 的「就地改既有组件取值面」豁免有处可落。族 3（未配置卡）只看
     * 「出现 `private fun NotConfiguredCard(` 即违规」，**没有出口**，所以不在本条覆盖范围内。
     *
     * 代价必须写死，这是本条存在的全部理由：**出口同时是一只已知失明面**。同一文件只要留着那枚共享调用，
     * 该文件其余的 forbidden 读点对本棘轮整体不可见。批 1 时点全仓业务屏没有任何一枚共享调用
     * （precheck §3：`grep -rl 'AppScreenHeader('` 只命中组件自身），所以失明面今天不产生实际漏计，
     * **批 2 一接线即激活**。因此接线判据是「该屏 forbidden 读点清零」，**不是**
     * 「补一行 `AppScreenHeader(` 就准删 seed 行」（已同步写进计划批 2 的开工判据）。
     * 收紧路径 = 把 seed 形状从「文件级 0/1」改成「读点计数」并同步四族台账，属计划修订事项，本轮不做。
     * 页头族那一格已由 [removing_a_file_from_the_header_seed_requires_its_headline_reads_to_be_cleared] 提前钉上
     * （R-13.1 / C-1：Task 13 首版摘行未清零正是从本条声明的失明面漏走的），本条仍是**形状自证**，两条不互相替代。
     *
     * 断言的写法本身要防「恒真」：正反向只在**那枚共享调用是否存在**上不同，forbidden 读点两侧逐字相同，
     * 所以先用 `countMatches` 钉住「片段里确有 forbidden 读点且两侧等量」，再判合规翻转 ——
     * 判红只可能来自出口那一半被删/规则变严，或 forbidden 探针形状失控。
     */
    @Test
    fun family_compliance_exits_are_a_declared_known_blindness_surface() {
        val dropCall = { source: String, call: String ->
            source.lines().filterNot { it.contains(call) }.joinToString("\n")
        }
        val blindness = "已知失明面：留着该枚共享调用时，同文件的 forbidden 读点对本棘轮整体不可见。" +
            "批 2 接线判据是「该屏 forbidden 读点清零」，不是「补一行调用就准删 seed 行」。" +
            "若这条判红，说明出口被删或规则变严 —— 那要同步重算四族 seed，属计划修订事项。"

        // ---- 族 1 页头：AppScreenHeader( 出口 ----
        val headerWired = """
            @Composable
            private fun SyntheticWiredHeaderScreen() {
                AppScreenHeader(title = "今日计划")
                Text("今日计划", style = MaterialTheme.typography.headlineMedium)
            }
        """.trimIndent()
        val headerBare = dropCall(headerWired, "AppScreenHeader(")
        assertEquals(1, countMatches(headerWired, headlineRead), "合成片段自检：页头 forbidden 读点没写进去，本条空转")
        assertEquals(1, countMatches(headerBare, headlineRead), "反向片段自检：摘调用把读点也摘掉了，两侧不再等量")
        assertEquals(
            false,
            isHeaderOffender(headerWired),
            "族 1 的合规出口失效（接线屏被判违规）。$blindness"
        )
        assertTrue(
            isHeaderOffender(headerBare),
            "摘掉 AppScreenHeader( 后族 1 仍未判违规：出口那一半是恒真断言，本条失去意义"
        )

        // ---- 族 2 错误提示条：InlineNotice( 出口 ----
        val noticeWired = """
            @Composable
            private fun SyntheticWiredNoticeScreen() {
                InlineNotice(kind = InlineNoticeKind.Error, message = "同步失败")
                Text("同步失败", color = MaterialTheme.colorScheme.error)
            }
        """.trimIndent()
        val noticeBare = dropCall(noticeWired, "InlineNotice(")
        assertEquals(1, countMatches(noticeWired, errorInkRead), "合成片段自检：错误色读点没写进去，本条空转")
        assertEquals(1, countMatches(noticeBare, errorInkRead), "反向片段自检：摘调用把读点也摘掉了，两侧不再等量")
        assertEquals(
            false,
            isNoticeOffender(noticeWired),
            "族 2 的合规出口失效（接线屏被判违规）。$blindness"
        )
        assertTrue(
            isNoticeOffender(noticeBare),
            "摘掉 InlineNotice( 后族 2 仍未判违规：出口那一半是恒真断言，本条失去意义"
        )

        // ---- 族 4 词行：WordRow( 出口（窗口规则同样被出口整体豁免，接线屏残留私有词行即逃逸）----
        // 合成函数名刻意**不含** `WordRow(` 子串（`SyntheticWiredWordRow` 会让摘调用顺手摘掉声明行），
        // 但必须仍落 `^private fun …Row…\(` 的取样窗口。
        val wordRowWired = """
            @Composable
            private fun SyntheticWiredRow(lemma: String, gloss: String) {
                WordRow(lemma = lemma, gloss = gloss)
                Text(lemma)
                Text(gloss)
            }
        """.trimIndent()
        val wordRowBare = dropCall(wordRowWired, "WordRow(")
        assertEquals(1, rowFunctionBodies(wordRowWired).size, "合成片段自检：私有 *Row* 窗口没落进取样面，本条空转")
        assertEquals(1, rowFunctionBodies(wordRowBare).size, "反向片段自检：摘调用把窗口也摘掉了，两侧不再等量")
        assertEquals(
            false,
            isWordRowOffender(wordRowWired),
            "族 4 的合规出口失效（接线屏被判违规）。$blindness"
        )
        assertTrue(
            isWordRowOffender(wordRowBare),
            "摘掉 WordRow( 后族 4 仍未判违规：出口那一半是恒真断言，本条失去意义"
        )

        // ---- 族 3 没有出口：本条不得把它算进「接线即失明」的豁免面 ----
        val cardFragment = """
            @Composable
            private fun SyntheticWiredCardScreen() {
                NotConfiguredCard(title = "未配置", body = "先去设置里配置")
            }
            @Composable
            private fun NotConfiguredCard(title: String, body: String) {
                Text(title)
                Text(body)
            }
        """.trimIndent()
        assertTrue(
            isCardOffender(cardFragment),
            "族 3 出现私有 fun NotConfiguredCard( 却不判违规：本族没有合规出口，" +
                "把它当「接线即豁免」会让未配置卡的四枚债务在批 2 整体隐形"
        )
    }

    /**
     * 词行族 seed 必须**恰好**是 spec §3 点名的六枚原始名单减去已接线的三屏（防「规则形状改了、
     * seed 数字凑巧还对」，也防「摘了 seed 行但那一屏根本没接线」）。
     *
     * 20-B 之前的形态（逐字点名六枚）已由本条改成「seed ∪ 已接线三屏 == spec §3 六枚」的**并集精确相等**：
     * 台账会随接线递减（类 KDoc 与 [componentFamilySeeds] 的口径），把六枚硬钉在 seed 上会让每一次合法递减
     * 都变成改断言；改成并集后，「名单来源」这一半仍然一次都漂不掉（多一枚、少一枚、换成没在 spec 名单里的
     * 新屏都当场红），而「摘行」那一半改成要额外举证（下面三条），比旧写法更严不是更宽。
     *
     * 摘行的合法性（与页头族 [removing_a_file_from_the_header_seed_requires_its_headline_reads_to_be_cleared]
     * 同口径，R-13.1 给词行族补的那道门）：每枚已摘 seed 行的屏必须同时满足
     *  ① 文件里出现共享 `WordRow(`（真接了组件，不是删了行就完事）；
     *  ② 本族的取样窗口里**没有**「lemma 词头 Text + 第二枚 Text」形状的私有词行（forbidden 读点清零）。
     * 族 4 的判定面带「本文件调了 `WordRow(` 即整体合规」出口（见
     * [family compliance exits are a declared known blindness surface]），只看①会放行「接线了但私有行还留着」的
     * 中间态，所以②是必须的一半。
     *
     * **本族规则形状的已知边界（修复轮 1 的 Fix 3 裁决，已在 `docs/verification/ui-redesign-2026-10.md` 登记）**：
     * spec 说 7 份 = 6 文件 + `GlobalVocabularySearchScreen.kt` 里的第二变体，但第二变体
     * （`:274-299`）是 `GlobalSearchHistory` 中一枚**无名内联 `Row { … }`**（写 `item.history.displayQuery` + 「搜索 N 次」），
     * 它不是顶层私有 `*Row*` 函数，**结构上就落在窗口规则之外**：本族规则看不见它。
     * 所以这里钉的是「该文件按窗口规则恰 **1** 处落窗」（`:319` 的 `private fun GlobalSearchResultRow(`，
     * 体内 `result.lemma` + `result.meaningZh` 两枚 Text），而不是硬凑 2。
     * **不得**为了凑数把窗口放宽成「任何 `Row {`」——那会立刻误伤 `CardDetailScreen.kt` 的 `SenseRow`
     * 与 `AppScreen.kt` 的 `DetailToggleRow`（两枚都不是词行）。
     * 台账按**文件**计，故第二变体接线时（20-C）与第一变体在同一行里一并消失，无需为它单开 seed 条目。
     */
    @Test
    fun `word_row family seed is the spec list minus the wired screens and pins the window rule boundary`() {
        val specWordRowFiles = setOf(
            "FavoritesScreen.kt",
            "GlobalVocabularySearchScreen.kt",
            "LearningRecordsScreen.kt",
            "VocabularyScreen.kt",
            "WordBookWordsScreen.kt",
            "WrongWordsScreen.kt",
        )
        val wiredByTask20B = setOf(
            "FavoritesScreen.kt",
            "WordBookWordsScreen.kt",
            "WrongWordsScreen.kt",
        )
        val seed = componentFamilySeeds.getValue("词行（应调 WordRow）")
        // ① 名单来源仍然钉死：台账 ∪ 已摘行的三枚 == spec §3 的六枚，逐枚相等。
        assertEquals(
            specWordRowFiles,
            seed + wiredByTask20B,
            "词行族 seed 与「spec §3 点名的六枚减去本批已接线的三枚」不符（改 seed 前先改规则形状并说明理由；" +
                "把没在 spec 名单里的屏塞进 seed 也算不符）",
        )
        assertEquals(
            setOf("GlobalVocabularySearchScreen.kt", "LearningRecordsScreen.kt", "VocabularyScreen.kt"),
            seed,
            "词行族台账现况（重算命令见 [componentFamilySeeds] KDoc）：Task 20-B 摘三屏后剩这三枚，" +
                "多一枚即有新债未登记，少一枚即有人删行未举证",
        )
        // ② 摘行的合法性：已摘 seed 行的屏必须真调 WordRow(，且本族私有词行窗口清零。
        wiredByTask20B.forEach { name ->
            val source = uiScreenSource(name)
            assertTrue(
                sharedWordRowCall.containsMatchIn(source),
                "$name 已摘词行族 seed 行，却没有共享 WordRow( 调用：摘行必须对应真接线（R-13.1 给词行族的口径）",
            )
            val windows = rowFunctionBodies(source).count { body ->
                lemmaText.containsMatchIn(body) && anyText.findAll(body).count() >= 2
            }
            assertEquals(
                0,
                windows,
                "$name 摘了 seed 行但私有词行窗口还剩 $windows 处（族 4 的「本文件调 WordRow( 即合规」出口会把它遮蔽至隐形）：" +
                    "批 2 接线判据是「该屏 forbidden 读点清零」，不是「补一行调用就准删 seed 行」",
            )
        }
        // ③ 窗口规则的边界：词典页仍恰 1 处落窗（第二变体是无名内联 Row，规则抓不到，已登记为限制）。
        val search = uiScreenSource("GlobalVocabularySearchScreen.kt")
        val hits = rowFunctionBodies(search).count { body ->
            lemmaText.containsMatchIn(body) && anyText.findAll(body).count() >= 2
        }
        assertEquals(
            1,
            hits,
            "GlobalVocabularySearchScreen.kt 按窗口规则恰 1 处落窗（`:319` GlobalSearchResultRow）；" +
                "第二变体是无名内联 Row，本族规则抓不到，已作为限制登记进 docs/verification/ui-redesign-2026-10.md，" +
                "接线时（20-C）与第一变体一并消失。实际 $hits ——数值变了说明规则形状或该文件写法动了，先改文档再改本断言。"
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
     *
     * **末尾那行「尾部真块注释」是故意给的闭合序列**（修复轮 2 的 Fix I-1）：退化成正则版时，字面量里那枚
     * 「斜杠紧跟星号」要一路吞到下一个「星号紧跟斜杠」才算闭合；本夹具若无这个末尾闭合序列，正则版就
     * **什么都剥不到**，第一条断言照样绿，夹具对「改回正则版」这条退化只剩「行注释剥不掉」一半是敏感的。
     * 补上它之后，正则版会把中间那行真错误色读点一起吃掉 → 数到 0 → 当场红。
     * **别把这一行当冗余清理掉**，也别让它含 `colorScheme.error`（那会让数到 1 由注释本体自己贡献，判据失效）。
     */
    @Test
    fun stripComments_keeps_code_after_a_string_literal_that_contains_a_comment_marker() {
        val source = """
            val mime = arrayOf("application/zip", "*/*")
            val bad = Text(x, color = MaterialTheme.colorScheme.error)
            // colorScheme.error 在注释里，不该被算命中
            /** 尾部真块注释，提供闭合序列 */
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
     * **F6 防复发闸（批 1 终审 D-I-2）**：`app/src/test` 下的 `ui/` 测试目录里不得再长出私有的「块注释剥离」正则。
     *
     * 为什么这条闸存在：共享夹具 [stripComments] 早已是**字符串感知**的实现，而本仓曾有 7 份私有副本
     * （`Accessibility*ContractTest` 五份 + `PronunciationIdleFallbackContractTest` + `WordBookCardProgressContractTest`）
     * 加 `RestoreNoBypassContractTest.codeOf` 一共 8 处用正则版剥注释。正则版不认识字符串字面量，
     * `AppScreen.kt`/`BackupRestoreScreen.kt` 里 SAF 的 MIME 字面量会让块注释正则从那一行一路吞到下一条行注释的
     * 闭合序列，那几十行里的 forbidden 形态对全部 7 个契约同时隐形（失明窗口，实测窗口两处仍在现状里）。
     * 批 2/批 3 还会新增源码契约测试，没有这道闸就会再长出第 9 份，所以本条是**防复发**而不是修历史。
     *
     * 口径与本仓所有源码契约一致：先 [stripComments] 再匹配（注释里复述的形状不计入）。
     * 形状本体用 `"/" + "*"` 拼接构造，否则本文件会被自己的探针判红——这也是为什么本条注释
     * 刻意不写出那两个序列的本体。
     */
    @Test
    fun no_private_block_comment_stripper_survives_under_ui_test_sources() {
        val blockOpen = "/" + "*"   // 拼接构造：写成本体字面量会让本探针自锁
        val probes = listOf(
            "replace(Regex(块注释起始…)) 形状" to Regex("""replace\(\s*Regex\(\s*"/(\\\\)*\*"""),
            "Regex 本体以块注释起始开头" to Regex("""Regex\(\s*["]{0,3}\s*/(\\\\)*\*"""),
        )
        val dir = File(TEST_UI_SOURCE_DIR_PATH)
        assertTrue(
            dir.isDirectory,
            "测试侧源码目录缺失（期望 $TEST_UI_SOURCE_DIR_PATH，JVM 工作目录 ${File(".").absolutePath}）：" +
                "本闸会静默空扫，路径必须先修。"
        )
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "本闸空扫：$TEST_UI_SOURCE_DIR_PATH 下没有 .kt（文件数 0 不是合规，是漏扫）。")
        val offenders = files.flatMap { file ->
            val text = stripComments(file.readText())
            probes.mapNotNull { (shape, probe) ->
                val hits = probe.findAll(text).count()
                if (hits == 0) null else "${file.relativePathFrom(dir)}（$shape，$hits 处）"
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "测试源码里出现私有的块注释剥离正则 $offenders —— 剥注释只有共享夹具 [stripComments] 一份口径：" +
                "正则版不认字符串字面量，会吞掉 MIME 字面量之后的几十行真代码，令所有「先剥注释再匹配」的契约" +
                "在那一段区间集体失明（批 1 终审 F6）。删掉私有实现、直接调共享函数。"
        )
        // 合成负片段：含该形态的样例文本必须被判命中，否则本闸是空转。
        val syntheticNaive = "private fun strip(text: String): String = text\n" +
            "    .replace(Regex(\"$blockOpen[*]" + "\\\\s\\\\S]*?\")" + ", \"\")\n"
        assertTrue(
            probes.any { (_, probe) -> probe.containsMatchIn(syntheticNaive) },
            "合成负片段未被本闸判出：探针形状与私有条实现脱节（闸空转）"
        )
        assertEquals(
            0,
            probes.count { (_, probe) -> probe.containsMatchIn("val ok = stripComments(source)") },
            "合成正片段被误判：委托共享夹具的写法不得被抓"
        )
    }

    /**
     * **m6（批 1 终审，Seat B M-6 同源）**：[uiScreenSource] 只服务 `ui/` 根的**屏文件**。
     * 文件名形状钉在夹具入口里：批 2 会新增屏文件，把 `ViewModel.kt`/`AiFeature.kt` 这类同目录非屏文件
     * 误当屏文件读会让契约对着错的文件绿，所以形状必须当场判红（不只是靠父目录守卫）。
     */
    @Test
    fun screen_source_fixture_only_accepts_screen_files() {
        val screen = assertFailsWith<AssertionError> { uiScreenSource("WordRow.kt") }
        assertTrue(
            screen.message?.contains("Screen.kt") == true,
            "uiScreenSource(\"WordRow.kt\") 必须按「文件名以 Screen.kt 结尾」的形状钉判红，实际消息：${screen.message}"
        )
        val nonScreenInRoot = assertFailsWith<AssertionError> { uiScreenSource("AiFeature.kt") }
        assertTrue(
            nonScreenInRoot.message?.contains("Screen.kt") == true,
            "ui/ 根下的非屏文件（AiFeature.kt）也必须被形状钉挡住——只有父目录守卫时它会静默读到，" +
                "契约就对着一枚不是屏的文件绿。实际消息：${nonScreenInRoot.message}"
        )
        assertTrue(
            uiScreenSource("BackupRestoreScreen.kt").isNotBlank(),
            "合规屏文件必须读得到（夹具退化到谁都判红同样是事故）"
        )
    }

    /**
     * 同一条修复的**真实文件锚**（Fix 1 的第二半）：把这次踩到的现场钉进台账，
     * 防止有人日后改写 `BackupRestoreScreen.kt` 的 MIME 字面量来「绕过」夹具，也防止夹具再退化成正则版
     * （退化后 :166 那枚错误色 `Text` 会重新被吞掉，本条先红）。
     */
    @Test
    fun notice_family_ledger_pins_the_backup_restore_site_that_comment_stripping_used_to_swallow() {
        val source = uiScreenSource("BackupRestoreScreen.kt")
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
