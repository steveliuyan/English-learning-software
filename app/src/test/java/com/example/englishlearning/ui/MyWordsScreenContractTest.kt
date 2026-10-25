package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 20-A（裁决①「我的词」一页三段）的**源码契约**：把屏的形状与入口挂载点钉在 JVM 侧，
 * connected 侧（`MyWordsScreenTest`）只补「同一次组合里三段都渲染」这一件实测。
 *
 * 分工（口径沿用批 1/批 2 的先例）：
 *  - 本文件管**形状**：枚举三枚、三段容器标签各恰一处、必须复用四枚共享组件、必须挂进词汇域入口面、
 *    墨色/排印/图标一律走 token（三张棘轮的口径在这里复述一遍，新增屏不得自带裸 hex 与静态深色读点）；
 *  - `MyWordsScreenTest` 管**行为**：一次 `setContent` 三段齐现、各段数据到位、出口回调通、空态与不可用态诚实降级、
 *    `section` 参把视口落到指定段。
 *
 * 为什么「三段齐现」不能只靠本文件：静态源码读得出「标签写了三次」，读不出「渲染时没有被 `section` 门控成单段 tab 页」；
 * 反过来 connected 读不出墨色（Task 9 实测口径）。两条各守一端。
 */
class MyWordsScreenContractTest {

    private val screen = uiScreenSource("MyWordsScreen.kt")

    /** 裁决①逐字：三段 = 收藏 · 生词本 · 错词；容器标签按 R-20A.2 的三条 testTag。 */
    private val sectionTags = listOf(
        "my_words_section_favorites",
        "my_words_section_wordbook",
        "my_words_section_wrong",
    )

    @Test
    fun declares_the_three_section_enum_exactly_once() {
        val enumRead = Regex("""(?m)^enum class MyWordsSection\b""")
        assertEquals(
            1,
            countMatches(screen, enumRead),
            "MyWordsScreen.kt 应恰一处顶层 `enum class MyWordsSection`（0 = 没声明或在别的文件里，" +
                "2 = 长出第二套段名口径，两边都让入口与屏失去共同语言）",
        )
        val body = Regex("""(?m)^enum class MyWordsSection[^{]*\{([^}]*)\}""").find(screen)
        val cases = body?.groupValues?.get(1).orEmpty()
        // m-4（修复轮 1）：R-20A.2 的「三段」是**闭集**——枚举体只许恰三枚常量，长出第四枚当场红。
        val constants = cases.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(
            3,
            constants.size,
            "MyWordsSection 枚举体必须恰为三枚段常量（收藏 / 生词本 / 错词），实际：$constants",
        )
        listOf("Favorites", "Wordbook", "Wrong").forEach { name ->
            assertTrue(
                name in constants,
                "枚举必须含 $name（R-20A.2 逐字口径 Favorites / Wordbook / Wrong），实际声明体：$cases",
            )
        }
    }

    /** 三段容器标签各恰一处：多一处 = 同一段落长出了第二个容器（滚动定位与用例都会指错那枚）。 */
    @Test
    fun every_section_has_exactly_one_labeled_container() {
        sectionTags.forEach { tag ->
            assertEquals(
                1,
                countMatches(screen, Regex(Regex.escape("\"$tag\""))),
                "$tag 应在本屏恰一处（0 = 该段没有可定位容器，2 = 同段两枚容器让 connected 的定位判据失效）",
            )
        }
    }

    /**
     * 「三段始终全部渲染」的静态一半：`section` 只许用于落位，不得当渲染开关。
     *
     * 行为那一半由 connected 的 `three_sections_render_in_one_composition` 承重（一次 `setContent` 三段齐现）。
     */
    @Test
    fun section_param_lands_instead_of_filtering() {
        assertEquals(
            0,
            countMatches(screen, Regex("""when\s*\(\s*section""")),
            "不得出现 `when (section)` 之类的段门控：R-20A.2 明写本屏不是「只显示一段的 tab 页」，" +
                "section 的语义是初始落位/聚焦，三段始终全部渲染",
        )
        // m-4（修复轮 1）：子串 contains 与本仓 countMatches(stripComments) 口径不齐——「留着但没用」的
        // scrollTo 字样（例如只写在标识符里）也能喂饱子串判定。改为按调用形状计数。
        assertTrue(
            countMatches(screen, Regex("""\.(?:scrollTo|scrollToItem|animateScrollTo)\(""")) >= 1,
            "section 落位必须真的滚动（.scrollTo(...)/.scrollToItem(...)/.animateScrollTo(...) 一族的调用，" +
                "剥注释后至少一处），否则形参是一个不起作用的摆设",
        )
    }

    /** 四枚共享组件必须复用，不得再长出私有份（份数棘轮的复述，方向与 `SharedComponentUniquenessRatchetTest` 一致）。 */
    @Test
    fun reuses_the_shared_components_instead_of_reimplementing_them() {
        assertEquals(
            1,
            countMatches(screen, Regex("""AppScreenHeader\(""")),
            "页头必须由共享 AppScreenHeader 承载，且本屏恰一处（页头族）",
        )
        // m-4（修复轮 1）：`WordRow(` 从「>= 1」收紧为**按段各锚一枚**——
        // 旧口径下把生词本段或错词段改回私有行仍然绿，那是给实现松绑而不是钉形状。
        assertTrue(
            countMatches(screen, Regex("""(^|[^A-Za-z0-9_])WordRow\(""")) >= 3,
            "词行必须改调共享 WordRow（词行族）：三段各至少一枚（本屏是新屏，不在 `SharedComponentUniquenessRatchetTest` " +
                "的词行 seed 台账里，写私有词行会当场判新增债务）",
        )
        listOf("my_words_favorite_row_", "my_words_wordbook_row_", "my_words_wrong_row_").forEach { prefix ->
            assertTrue(
                countMatches(screen, Regex(Regex.escape("\"$prefix"))) >= 1,
                "$prefix 标签必须在本屏至少一处：段行标签是 connected 按段定位词行的锚，" +
                    "缺一段 = 那一段不再保证用共享词行渲染",
            )
        }
        assertTrue(
            countMatches(screen, Regex("""ScreenUnavailable\(""")) >= 1,
            "不可用态必须复用共享 ScreenUnavailable，不得自搓一套降级卡",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""private fun NotConfiguredCard\(""")),
            "不得定义私有 NotConfiguredCard（未配置卡族没有合规出口）",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""MaterialTheme\.typography\.""")),
            "排印一律走 AppType 六档，不读库内档",
        )
    }

    /** token 纪律（三张棘轮的新屏口径）：零裸 hex、零静态深色读点、零库内图标、零 `Mint*` 快捷名。 */
    @Test
    fun consumes_only_tokens() {
        assertEquals(0, countMatches(screen, Regex("""Color\(0x[0-9A-Fa-f]{8}\)""")), "本屏不得写裸 Color(0xFF…)")
        assertEquals(
            0,
            countMatches(screen, Regex("""\b(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")),
            "本屏不得有 AppPalette./DomainColors. 静态读点（深色模式会浮浅套值，且会新增债务台账站点）",
        )
        assertEquals(0, countMatches(screen, Regex("""\bIcons\.""")), "图标一律 AppIcon + AppGlyph，禁 androidx Icons.")
        assertEquals(0, countMatches(screen, Regex("""\bMint[A-Z]\w*""")), "中性色一律 appColors()，不留 Mint* 快捷名")
        assertTrue(
            screen.contains("appColors()"),
            "本屏墨色必须来自 appColors()/appDomain()，否则上面的「零裸 hex」只是把颜色丢给了默认主题",
        )
    }

    /**
     * 入口挂载（R-20A.3「只建不摘」→ 22-A / 22-B 两席摘除 → 版式重做批 1 的 Task 4 + Task 5 合并）：
     * 词汇域现在只有**一条**入口面——学习页的「词库」行，落点是四分段 `WordBankScreen`。
     *
     * 期望值随形改的三处（判据形状一律保留「恰一处」的精确计数，不是放宽）：
     *  - 入口标签 `today_plan_open_my_words` → `today_plan_open_word_bank`（Task 4 把五个入口行换成
     *    两条裸行，词库行是这一族的唯一去向）；
     *  - 入口行标题「我的词」→ 四段枚举文案「词书 · 生词 · 错词 · 收藏」（词库页把三族词表并成四段后，
     *    学习页那行必须逐字说明行里有什么——旧名「我的词」消失，唯一的可发现性凭据就是这行副标题）；
     *  - 宿主挂载 `MyWordsScreen(` → `WordBankScreen(`（Task 5：合并页降为词库页的一段 pane）。
     *
     * 反向控同步翻面：`today_plan_open_wordbook_words` 从「必须在册」改成「必须离场」（Task 4 迁入口、
     * Task 5 把宿主那枚 `showWordBookWords` 并进 `showWordBank`）；`today_plan_search_entry` 仍在册
     * （查词条是四态常驻的第二段，与词库行并列）。
     */
    @Test
    fun the_new_entry_is_mounted_in_the_vocabulary_domain_surface() {
        val todayPlan = uiScreenSource("TodayPlanScreen.kt")
        assertTrue(
            "today_plan_open_word_bank" in todayPlan,
            "「词库」入口必须挂在 TodayPlanScreen 的词库裸行上（标签 today_plan_open_word_bank）",
        )
        assertTrue(
            countMatches(todayPlan, Regex(Regex.escape("\"词书 · 生词 · 错词 · 收藏\""))) >= 1,
            "词库行的副标题必须逐字列出四段（「词书 · 生词 · 错词 · 收藏」）：旧名「我的词」随入口合并消失后，" +
                "这是用户在学习页判断「这行里有什么」的唯一凭据（§3.2 第 4 块）",
        )
        // 反向控（Task 4/5 的迁移账）：词书单词那枚老入口已离场，谁把它补回学习页本条当场红。
        assertEquals(
            0,
            countMatches(todayPlan, Regex(Regex.escape("today_plan_open_wordbook_words"))),
            "老入口标签 today_plan_open_wordbook_words 已随五入口迁移离场（§3.2 移出清单），不得残留",
        )
        assertTrue(
            "today_plan_search_entry" in todayPlan,
            "查词入口必须仍在册：它是四态常驻的独立一段，不是词库行的一部分",
        )

        val appScreen = uiScreenSource("AppScreen.kt")
        assertEquals(
            1,
            countMatches(appScreen, Regex("""(^|[^A-Za-z0-9_])WordBankScreen\(""")),
            "AppScreen 必须挂载 WordBankScreen 恰一处（0 = 词库没有宿主，2 = 长出第二份挂载）",
        )
        // 两条打开通路都必须**同时**点名段位：只赋 showWordBank 不赋段位 = 「复习生词本」落到词书段，
        // 用户点的是「复习」却看到列表第一页。逐环点名形状而不是数计数：
        // 「点了真的会开吗、开在哪一段」的用户面由 connected `AppScreenTest` 的端到端用例承重。
        listOf(
            "onOpenWordBank = { wordBankSection = WordBankSection.Book; showWordBank = true }",
            "onReviewWords = { wordBankSection = WordBankSection.New; showWordBank = true }",
        ).forEach { wiring ->
            assertEquals(
                1,
                countMatches(appScreen, Regex(Regex.escape(wiring))),
                "入口接线「$wiring」必须恰一处：段位与开关同点同赋（0 = 这条通路断了，" +
                    "2 = 长出第二条打开通路）",
            )
        }
        val overlayOpenBlock = appScreen.substringAfter("val overlayOpen =").substringBefore("BackHandler(")
        assertTrue(
            "showWordBank" in overlayOpenBlock,
            "overlayOpen 聚合式里必须含 showWordBank：这是词库开着时返回键被叠层接管" +
                "（而不是退出整屏/穿透到底层导航）的唯一静态凭据",
        )
        assertEquals(
            1,
            countMatches(appScreen, Regex("""BackHandler\(\s*enabled = showWordBank[\s\S]*?showWordBank = false""")),
            "enabled 以 showWordBank 打头的 BackHandler 体内必须恰一处 `showWordBank = false`（0 = 返回键退不出这一页）",
        )
        assertEquals(
            1,
            countMatches(appScreen, Regex("""else if \(showWordBank\)[\s\S]*?WordBankScreen\(""")),
            "渲染分支 `else if (showWordBank)` 后必须紧跟 WordBankScreen 挂载（0 = 状态位与渲染脱节）",
        )
        // myWordsSummary 的「三段全 Ready 才非 null」规则本体仍在 MyWordsSummary.kt（判据 MyWordsSummaryTest），
        // 但它的**显示面**（旧「我的词」入口行的尾部计数，稿 mockups:916 的 `34 · 12 · 8`）已随 Task 4 的
        // 五入口迁移退役，四分段词库页按 spec §3.3 的版面（标题 + 词书名 + 分段，无计数）也不承接它。
        // 于是这条反向控从「宿主恰一处调用」翻成「宿主零调用」：规则不许另长一份内联实现，
        // 将来哪个显示面要这三枚计数，必须回来改这一行并指名那块面（不是把函数悄悄接回去）。
        assertEquals(
            0,
            countMatches(appScreen, Regex("""myWordsSummaryOf\(""")),
            "AppScreen 不得再调用 myWordsSummaryOf：旧「我的词」入口行的尾部计数已退役，" +
                "词库页版面不显示计数。接回必须同改本条并指明新的显示面，0 → 1 是一次有意的决定，不是顺手",
        )
    }

    /**
     * Task 22-B（RED-2 路由闭集）：宿主不再持有 `showFavorites` / `showWrongWords` 两条老通路。
     *
     * 判据形状写成「**不存在这两个标识符**」，而不是「存在某句否定注释」——
     * 22-B 把收藏页 / 错词页两条独立路由整条摘除后，合并页 `showMyWords` 一段是这三族词表能力的唯一出口，
     * 因此 `AppScreen.kt` 源码里 `showFavorites` / `showWrongWords` 这两个标识符必须为 0 处。
     * 摘除前它们仍逐枚在册（状态位、setValue、BackHandler、装载 effect、挂载分支）⇒ 本条先红；
     * 摘除后仍**有牙**：谁把任一标识符补回宿主，本条当场红，不靠注释自证。
     *
     * **覆盖面边界（22-B 修复轮 1 的 F3 / A-2）**：本条钉的是 22-B 那次下线动作不被回滚，
     * **不**钉「此后不许有第二个收藏/错词目的地」——判据读的是 `showFavorites` / `showWrongWords`
     * 这两枚**硬编码标识符**，以新名（如 `showFavoriteWords`）重建第二条宿主路由时本条照绿，改名重建不在其覆盖面；
     * 想钉「第二个目的地」本身得换判据形状（按能力面计数），那是另一件事，本条不冒充它。
     */
    @Test
    fun the_host_holds_no_favorites_or_wrong_words_routes() {
        val appScreen = uiScreenSource("AppScreen.kt")
        listOf("showFavorites", "showWrongWords").forEach { identifier ->
            assertEquals(
                0,
                countMatches(appScreen, Regex("""\b$identifier\b""")),
                "Task 22-B 后 AppScreen.kt 不得再持有 `$identifier`（0 处 = 老路由已整条摘除；" +
                    "非 0 = 收藏页/错词页那条独立通路又长回来了，合并页 showMyWords 一段才是这三族词表的唯一出口）",
            )
        }
    }

    /**
     * Task 22-B §二.4 / §四.3① 的**闭集判据**（下线型任务的 RED 形状：两枚屏文件还在树上 ⇒ 本条红；
     * 红样留档 `.superpowers/sdd/2026-10-17-ui-redesign/t22B-cont-RED1-jvm-closure.log`）。
     *
     * 两枚旧屏既要从磁盘消失，也要从 `ui/` 源码的**任何文本**里消失（含注释——KDoc 留着旧屏文件名
     * 就是「引用已不存在的文件」那种假线索，评审复核时会照着去读一个不存在的文件）。
     *
     * 为什么不能只靠 `SharedComponentUniquenessRatchetTest`：那条走 `uiScreenSource`，其内置的
     * `assertTrue(files.size == 1, …)` 会在「屏文件已删但名单还在册」时红；本条钉的是**反向**那一半——
     * 名单摘干净之后，谁再把两枚屏名写回生产码（新建屏、复活挂载、或把旧文件名留在注释里），本条点名文件。
     * 磁盘存在性用 `File(...).exists()` 直判，不借 `uiFiles()`：后者是扫描夹具，
     * 拿它判「不存在」会撞上「空扫即失败」的内置守卫而把判据变成永远红。
     *
     * **扫描口径（22-B 修复轮 1 的 F3 / A-2，现场读码确认）**：offenders 那一腿读的是 `file.readText()`，
     * **不过** [com.example.englishlearning.ui.stripComments] ⇒ 覆盖面确实**含注释**：
     * 「旧屏已于 22-B 下线」这类**正确的历史叙述**只要点名 `FavoritesScreen` / `WrongWordsScreen` 两个标识符本身，
     * 也一样进 offenders 判红。这是刻意的闭集面（假线索护栏：注释里的旧屏名会让复核席去读一个不存在的文件），
     * 代价是生产注释从此只能用中文页名 + 行号指代旧页，不许写这两个文件名。
     *
     * **覆盖面边界**：本条钉的是 22-B 那次下线动作不被回滚，**不**钉「此后不许有第二个收藏/错词目的地」——
     * 判据读的是**硬编码文件名集合**，以新名重建第二目的地（`FavoritesHubScreen.kt` 之类）时两枚磁盘判定与
     * 文本扫描全都绿；改名重建不在其覆盖面。真要钉「唯一目的地」得换成按能力/路由面计数的判据形状，本条不冒充它。
     */
    @Test
    fun the_two_legacy_screens_are_off_the_production_tree() {
        listOf("FavoritesScreen.kt", "WrongWordsScreen.kt").forEach { name ->
            assertTrue(
                !File(UI_MAIN_SOURCE_PATH, name).exists(),
                "Task 22-B 下线判据：$name 必须已从生产树摘除（JVM 工作目录 ${File(".").absolutePath}）——" +
                    "收藏与错词两族能力的唯一宿主是合并页 MyWordsScreen，屏文件留着就是第二个目的地",
            )
        }
        val offenders = uiFiles()
            .filter { file ->
                listOf("FavoritesScreen", "WrongWordsScreen").any { identifier -> identifier in file.readText() }
            }
            .map { it.name }
        assertTrue(
            offenders.isEmpty(),
            "ui/ 源码（含注释）里不得再出现 FavoritesScreen / WrongWordsScreen 字样——§四.3① 的闭集面，" +
                "旧屏名留在注释里等于给后来人留一条指向不存在文件的假线索。实际命中：$offenders",
        )
    }

    /**
     * I-4（修复轮 1，登记见 fix1 报告 §1「摘除前必须补齐的能力」）：合并页的生词本段必须补上
     * 旧生词页独有的两枚能力——段头「开始重新学习」与逐行「标记为已掌握」（共享
     * [com.example.englishlearning.ui.components.ConfirmDestructiveDialog] 二次确认）。
     * Task 22-A 摘 `today_plan_open_vocabulary` / `showVocabulary` 老路由的前置条件就是这两枚先在册
     * （该摘除已于 22-A 落地，本用例因此同时是「摘除当时成立」的凭据）；
     * 「点了真的通到重学页 / 真的把该行移出」的行为面由 connected
     * `MyWordsScreenTest.wordbook_offers_relearn_and_mastered_behind_confirmation` 承重。
     */
    @Test
    fun wordbook_section_supplies_the_capabilities_task_22_depends_on() {
        assertEquals(
            1,
            countMatches(screen, Regex("""onStartRelearn: \(\) -> Unit""")),
            "MyWordsScreen 形参 onStartRelearn 必须恰一处声明（0 = 整册重学动作没有进合并页，Task 22 便不能摘老路由）",
        )
        assertEquals(
            1,
            countMatches(screen, Regex("""onMarkMastered: \(VocabularyItem\) -> Unit""")),
            "MyWordsScreen 形参 onMarkMastered 必须恰一处声明（0 = 逐行标记已掌握缺位，同样摘不得老路由）",
        )
        assertEquals(
            1,
            countMatches(screen, Regex(Regex.escape("\"my_words_wordbook_start_relearn\""))),
            "重学按钮在生词本段必须恰一枚标签锚（connected 按它定位；2 = 同段长出两枚整册动作）",
        )
        assertTrue(
            countMatches(screen, Regex(Regex.escape("\"my_words_wordbook_mastered_"))) >= 1,
            "逐行「标记为已掌握」必须带生词本段的行标签锚（缺 = 该能力没长在行上）",
        )
        assertEquals(
            1,
            countMatches(screen, Regex("""ConfirmDestructiveDialog\(""")),
            "二次确认必须复用共享 ConfirmDestructiveDialog 且合并页恰一处（0 = 又自搓私有 AlertDialog，" +
                "对话框份数棘轮判新增债务；2 = 一页两枚确认框）",
        )
        val appScreen = uiScreenSource("AppScreen.kt")
        // Task 5 起合并页是词库页的一段 pane：挂载分支由 `showMyWords` 改名为 `showWordBank`，
        // 定界符随形改，判据形状（挂载段内逐条点名）一字未动。
        val myWordsMount = appScreen
            .substringAfter("else if (showWordBank)")
            .substringBefore("else if (showAiProfiles)")
        assertTrue(
            "onStartRelearn = { showVocabularyRelearn = true }" in myWordsMount,
            "合并页挂点的 onStartRelearn 必须接宿主现成的 showVocabularyRelearn 通路（不新建通路）",
        )
        assertTrue(
            "vocabularyViewModel?.markMastered(" in myWordsMount,
            "合并页挂点的 onMarkMastered 必须回调现成的 vocabularyViewModel?.markMastered（与老页挂载处同一回调）",
        )
    }

    /**
     * N-1（修复轮 3，复评 `task-20A-re-review-1.md` §C Important）：合并页必须**消费并复位**
     * `VocabularyMasteryState`，不能只调 `markMastered` 就把结果态丢在 ViewModel 里。
     *
     * 缺陷形状两条（复评逐字）：`_mastery` 只有 `clearMasteryMessage()` 会复位，合并页挂点既不读也不清 ⇒
     *  ① 两页并存期间，用户随后打开老生词页会弹出一枚**没有上下文**的结果框（那是另一页做过的动作）；
     *  ② `StorageUnavailable` 时 `_mastery = Failure` 且**不 reload**，合并页零反馈——点了确认、行还在、没有任何解释。
     *
     * 静态侧钉的是「接线与措辞同源」这四件事（行为侧由 connected 的三枚结果框用例承重）：
     *  - 屏侧两枚形参各恰一处声明（默认值保住既有 state-in 夹具不碎）；
     *  - 结果框走共享 [com.example.englishlearning.ui.components.glass.GlassDialog]，**不得**自搓裸 `AlertDialog`
     *    （对话框份数棘轮的同一口径）；
     *  - 成功/失败两态的四句措辞各恰一处且与老生词页 `VocabularyScreen.kt:81-97` 逐字同文——本页 KDoc 自述
     *    「用户在任何一处读到的解释必须还是同一句」；
     *  - 宿主 `MyWordsScreen(` 挂载段内**恰一处**把与老页 `:852/:853` 同源的状态与复位回调传进来（不新建通路）。
     *    口径用「挂载段 + countMatches」而不是全文件计数：老页那两行仍在册（Task 22 才摘），全文件计数必然 ≥2，
     *    写绝对枚数就是写死一份会自漂移的存量数。
     */
    @Test
    fun the_merged_page_consumes_and_resets_the_mastery_state() {
        assertEquals(
            1,
            countMatches(screen, Regex("""masteryState: VocabularyMasteryState = VocabularyMasteryState\.Idle""")),
            "MyWordsScreen 形参 masteryState 必须恰一处声明且默认 Idle（0 = 合并页读不到结果态，" +
                "失败静默、成功也无反馈，正是 N-1 点名的缺陷形状）",
        )
        assertEquals(
            1,
            countMatches(screen, Regex("""onDismissMasteryMessage: \(\) -> Unit""")),
            "MyWordsScreen 形参 onDismissMasteryMessage 必须恰一处声明（0 = 结果框没有复位出口，" +
                "读完还留在 Success/Failure，下一次进老生词页就弹出无上下文的框）",
        )
        assertEquals(
            1,
            countMatches(screen, Regex("""(^|[^A-Za-z0-9_])GlassDialog\(""")),
            "结果对话框必须复用共享 GlassDialog 且本屏恰一处（0 = 没有结果框或又自搓一份，" +
                "2 = 成功与失败各长出一套外壳，措辞从此两处可漂）",
        )
        assertEquals(
            0,
            countMatches(screen, Regex("""(^|[^A-Za-z0-9_])AlertDialog\(""")),
            "本屏不得出现裸 Material3 AlertDialog（对话框份数棘轮口径：本席不新建组件、不迁旧页那份）",
        )
        listOf(
            "已标记为掌握",
            "该词已从生词本移除，复习记录已保留。",
            "保存失败",
            "复习状态未更新，生词仍保留在生词本中。请重试。",
            "知道了",
        ).forEach { copy ->
            assertEquals(
                1,
                countMatches(screen, Regex(Regex.escape("\"$copy\""))),
                "结果框措辞「$copy」必须在本屏恰一处（0 = 说法与老生词页不再是同一句，2 = 同页两枚说法）",
            )
        }
        listOf("my_words_mastery_result_dialog", "my_words_mastery_result_dismiss").forEach { tag ->
            assertEquals(
                1,
                countMatches(screen, Regex(Regex.escape("\"$tag\""))),
                "$tag 标签锚必须恰一处（connected 按它定位结果框与「知道了」出口；缺 = 行为面没有可定位的锚）",
            )
        }

        val appScreen = uiScreenSource("AppScreen.kt")
        // Task 5：合并页降为词库页的复用面（三段共用同一处 `myWordsPane` 形参表），挂载分支改名
        // `showWordBank`；切片里仍必须恰一枚 `MyWordsScreen(`——它就是那块复用面本体。
        val myWordsMount = appScreen
            .substringAfter("else if (showWordBank)")
            .substringBefore("else if (showAiProfiles)")
        assertTrue(
            myWordsMount.contains("MyWordsScreen("),
            "挂载段定界符失效（`else if (showWordBank)` 之后必须紧跟 MyWordsScreen 复用面），" +
                "否则下面的恰一处判定会在错位切片上假绿",
        )
        assertEquals(
            1,
            countMatches(myWordsMount, Regex("""masteryState = vocabularyMasteryState""")),
            "合并页挂点必须恰一处把宿主同源状态喂进 masteryState（0 = 状态无人消费，" +
                "2 = 长出第二个真源）；与老生词页挂载处的 vocabularyMasteryState 同一来源，不新建通路",
        )
        assertEquals(
            1,
            countMatches(myWordsMount, Regex(Regex.escape("onDismissMasteryMessage = { vocabularyViewModel?.clearMasteryMessage() }"))),
            "合并页挂点必须恰一处接现成的复位回调（与老页 :853 逐字同形）；0 = 读完的结果态留在 ViewModel 里污染另一页",
        )
    }

    /**
     * m-7（修复轮 2）：三段空态的**逐字文案**与生词本那条的**专属朗读描述**都在源码里在册。
     *
     * 为什么要这一枚静态锚：文案本体的行为判据在 connected（`empty_sections_show_their_own_honest_copy`
     * 现在逐段 `assertTextEquals`），但描述层只在跑设备时才看得见；本锚让「把某句空态改成一句话」
     * 「把生词本那条的 contentDescription 删掉」在不接设备的门控里也当场红。
     * 三句文案逐字对齐三页旧文（旧收藏页 :101 / `VocabularyScreen.kt:63` / 旧错词页 :90——
     * 收藏页与错词页两枚屏已于 Task 22-B 下线，故这里只留中文页名与旧文行号，不再写会被当成现存文件的路径），
     * 描述串逐字对齐 `VocabularyScreen.kt:64`。
     */
    @Test
    fun empty_states_keep_the_copy_and_the_reading_layer() {
        listOf(
            "还没有收藏。在词卡详情页点星标即可收藏。",
            "还没有生词，提交“不认识”后会自动加入。",
            "当前没有待纠正的错词。复习中标记「不认识」的词会出现在这里，重新答对后自动消失。",
        ).forEach { copy ->
            assertEquals(
                1,
                countMatches(screen, Regex(Regex.escape("\"$copy\""))),
                "空态文案「$copy」必须在本屏恰一处（0 = 措辞被改写，与旧页不再是同一句；2 = 同一段两枚说法）",
            )
        }
        assertEquals(
            1,
            countMatches(screen, Regex(Regex.escape("\"生词本为空，提交不认识后会自动加入\""))),
            "生词本空态必须带旧页那条专属朗读描述（0 = TalkBack 档比旧页少一层，m-7 点名的缺陷形状）",
        )
    }

    /**
     * m-2（修复轮 1）：重试必须**带段标**——三段各回交自己的段枚举。
     *
     * 旧形状 `onRetry: () -> Unit` 让任一段的「重试」一次拉三册（另外两段被打回 Loading）。
     * 屏侧签名与三处回交各钉一枚；宿主侧按段 load 的行为面由 connected
     * `MyWordsScreenTest.unavailable_sections_each_offer_a_working_retry` 承重。
     */
    @Test
    fun retry_reports_which_section_was_retried() {
        assertEquals(
            1,
            countMatches(screen, Regex("""onRetry: \(MyWordsSection\) -> Unit""")),
            "MyWordsScreen 的 onRetry 形参必须恰一处声明为 (MyWordsSection) -> Unit（0 = 退回无参共刷三册）",
        )
        MyWordsSection.values().forEach { section ->
            assertTrue(
                countMatches(screen, Regex(Regex.escape("onRetry(MyWordsSection.${section.name})"))) >= 1,
                "${section.name} 段的降级面必须回交本段段标（0 = 该段又挂回整把刷三册的回调）",
            )
        }
    }
}
