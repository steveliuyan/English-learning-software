package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 批 1 Task 12：学习设置屏（`AppScreen.kt` 的 `LearningSetupScreen`）对齐原型
 * `#/plan-edit`（学习计划）+ `#/card-settings`（词卡显示）的**合并屏**契约。
 *
 * 这一屏住在 `AppScreen.kt` 里（一个文件装十来屏），所以**所有判据都必须窗口化**：
 * 全文件级的 `Mint*` == 0 是判不了的（同文件的今日计划 / 学员档案等屏还在用静态桥）。
 * 取样窗口按「函数签名 → 它的花括号体」配对取（[windowOf]），与
 * [AppScreenOverlayContainerContractTest] 的 `learningSetupSignature` 同一套口径。
 *
 * 七条窗口：
 *  - [setup]：`LearningSetupScreen` 本体（页头 / 分组 / 术语 / 通知条）；
 *  - [headerSave]：`SetupHeaderSave`（原型 `.bar-action` 的页头保存位）；
 *  - [dailyChips]：`DailyTargetChips`（原型 `chip(n + ' 个')` 那一行，取代旧拖动滑杆）；
 *  - [reviewLimit]：`ReviewLimitSection`（原型 `.stat-num` + `.range` 那张 `.card`）；
 *  - [toggleRow]：`DetailToggleRow`（开关行，批 0 之后必须用共享 `AppSwitch`）；
 *  - [saveBar]：`SetupSaveBar`（原型 `.foot` 吸底条）；
 *  - [picker] / [pickRow] / [profileCreation]：选词书与首启建档（上一批的既有判据，逐条保留）。
 *
 * **合并的理由**（原型是两屏、本仓做一屏）：`sCardSettings` 的可迁移内容只有「评分之后怎么走」那一节
 * 三枚开关（其余三节在本仓没有对应能力，见下面 `the sections without a real backend stay hidden`），
 * 而这三枚开关本来就住在「学习计划」屏里；拆两屏等于给一屏一主操作的页面长出第二个页头与第二条保存路径。
 * 两屏的页头文案按原型取 `学习计划`（覆盖范围更大的那个名字）。
 *
 * 「行号不锚、注释不计」由 [uiScreenSource]（`stripComments`）与本文件取窗器保证。
 */
class LearningSetupLayoutContractTest {

    private val source: String by lazy { uiScreenSource("AppScreen.kt") }

    private val setup: String by lazy {
        windowOf("internal fun LearningSetupScreen(", "LearningSetupScreen 本体")
    }

    private val headerSave: String by lazy {
        windowOf("private fun SetupHeaderSave(", "SetupHeaderSave 本体")
    }

    private val dailyChips: String by lazy {
        windowOf("private fun DailyTargetChips(", "DailyTargetChips 本体")
    }

    private val reviewLimit: String by lazy {
        windowOf("private fun ReviewLimitSection(", "ReviewLimitSection 本体")
    }

    private val toggleRow: String by lazy {
        windowOf("private fun DetailToggleRow(", "DetailToggleRow 本体")
    }

    private val saveBar: String by lazy {
        windowOf("private fun SetupSaveBar(", "SetupSaveBar 本体")
    }

    private val picker: String by lazy {
        windowOf("internal fun WordBookPicker(", "WordBookPicker 本体")
    }

    private val pickRow: String by lazy {
        windowOf("private fun WordBookPickRow(", "WordBookPickRow 本体")
    }

    private val profileCreation: String by lazy {
        windowOf("private fun ProfileCreationScreen(", "ProfileCreationScreen 本体")
    }

    /** 本批要清零的静态 Mint 桥：屏侧只许 `appColors()` / `AppType`（批 2 其余五屏同口径）。 */
    private val staticMintTokens = listOf(
        "MintBackground",
        "MintSurface",
        "MintOutline",
        "MintPrimaryDark",
        "MintPrimary",
        "MintTextMuted",
        "MintTint",
        "AppleMintLight",
        "AppleMintGradient",
    )

    /** 裸 `Card(`：`AppCard(` / `SoftCard(` / `StrongCard(` 都含这个子串，必须用词界锚点区分。 */
    private val bareCardCall = Regex("""(?<![A-Za-z])Card\(""")

    /**
     * 共享 `AppCard` 的调用点。
     *
     * 锚的是「这个名字被调用」，**不锚 Kotlin 的调用语法**：`AppCard(` 与尾随 lambda 写法 `AppCard {`
     * 是同一条判据的两种形状，把 pattern 收成 `AppCard(` 会逼屏侧为了过测多写一层无意义的括号。
     * 与 `bareCardCall` 的关系不变：那条用词界锚点排除 `AppCard`，本条用字面名排除裸 `Card`。
     */
    private val appCardCall = Regex("""AppCard[\s(]""")

    /** 裸 `Switch(`：`AppSwitch(` 含子串 `Switch(`，共享件才是本屏的合法形状。 */
    private val bareSwitchCall = Regex("""(?<![A-Za-z])Switch\(""")

    // ---------- 页头：原型 `barTop({title:'学习计划', action: 保存})` ----------

    /**
     * 页名从「选好词书，开始今天的积累」换成原型的**学习计划**，保存位进页头 `action` 槽。
     *
     * 原型这一屏的顶部是 `barTop({title:'学习计划', action:'<button class="bar-action">保存</button>'})`：
     * 页头承担「我在哪 + 怎么提交」，吸底条只是同一条动作的第二枚入口。返回位仍由共享页头承载
     * （旧版那枚 `← 返回上一层` 的 `TextButton` 已退役，判据保留在第二条断言里）。
     */
    @Test
    fun `the header is the prototype plan title with its save action`() {
        assertTrue(setup.isNotEmpty(), "取不到 LearningSetupScreen 本体：判据会空转")
        assertEquals(
            1,
            countMatches(setup, Regex(Regex.escape("AppScreenHeader("))),
            "页头必须恰一处调用 `AppScreenHeader`",
        )
        assertEquals(
            1,
            countMatches(setup, Regex(Regex.escape("title = \"学习计划\""))),
            "页名必须逐字取原型 `barTop` 的「学习计划」，且只此一处",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("选好词书，开始今天的积累"))),
            "旧页名必须整文件清零：`AppScreenTest` / `SetupScreenScreenshotTest` / `WordbookPickerHeaderTest` " +
                "的锚随本批一起迁到「学习计划」，不许留第二套页名",
        )
        assertEquals(
            1,
            countMatches(setup, Regex(Regex.escape("action = { SetupHeaderSave("))),
            "保存位必须进页头的 `action` 槽（原型 `.bar-action`），而不是屏侧再搓一枚裸按钮",
        )
        val headerAt = setup.indexOf("AppScreenHeader(")
        val onBackAt = setup.indexOf("onBack = onCancel")
        assertTrue(
            headerAt >= 0 && onBackAt > headerAt,
            "`onBack = onCancel` 必须落在页头调用里：宿主回调接不上，返回位就是个死按钮",
        )
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("TextButton("))),
            "本屏不得再自带返回文字按钮：返回位归共享页头一处定义",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("contentDescription = \"返回上一层\""))),
            "旧返回位的语义必须清零：`AppScreen.kt` 里不得留着第二套返回朗读文案",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("← 返回上一层"))),
            "旧返回位的文案必须清零（`AppScreen.kt` 内已无此入口）",
        )
    }

    /**
     * 页头保存位（`.bar-action`）自己的形状：**文字动作**，不是第二枚实底按钮。
     *
     * 原型 `.bar-action` 是 `brand-deep` 的 13px/600 纯文字，页头右侧出现一枚带底的按钮会把
     * 「一屏一个主操作」的重量抢走（spec §4.1）。可及名必须与吸底条那枚**不同**——
     * 两处都叫「保存学习设置」会让 `onNodeWithContentDescription` 撞成多节点，connected 取样当场失败。
     */
    @Test
    fun `the header save action is a text action with its own accessible name`() {
        assertTrue(headerSave.isNotEmpty(), "取不到 SetupHeaderSave 本体：判据会空转")
        assertTrue("\"保存\"" in headerSave, "动作文案取原型的「保存」")
        assertEquals(
            1,
            countMatches(headerSave, Regex(Regex.escape("contentDescription = \"页头保存学习设置\""))),
            "页头那枚必须自带**不同于**吸底条的可及名：两枚同名字面会让 connected 的 `onNodeWithContentDescription` 判多节点",
        )
        assertEquals(
            0,
            countMatches(headerSave, Regex(Regex.escape("Button("))),
            "页头动作不得是 `Button(`：原型 `.bar-action` 是纯文字，带底就是第二枚主操作",
        )
        assertTrue("colors.brandDeep" in headerSave, "文字动作的墨色取 `brandDeep`（原型 `.bar-action` 同值）")
        assertTrue("clickable(" in headerSave, "`action` 槽的组件不代替点击：动作位必须自己可点")
        assertEquals(
            0,
            countMatches(headerSave, Regex(Regex.escape("RoundedCornerShape("))),
            "形状只走 `AppShape`（本枚干脆不画形状）",
        )
    }

    // ---------- 引言：原型 `.meta` 那一句 ----------

    /**
     * 页头下第一行是原型的 `.meta`：「计划只影响每天排几个词，不会清掉你已经学过的进度。」
     *
     * 旧版页头 `subtitle` 那句「每天一点点，学习会自然变成习惯。」是励志文案，不携带任何口径；
     * 原型这一句讲的是**这一屏改了什么、不改什么**——正是词书切换最怕被理解成「清进度」的地方，
     * 所以 subtitle 撤掉、`.meta` 顶上。排印取 `AppType.Meta`（13sp）与原型 `.meta` 同档。
     */
    @Test
    fun `the intro line is the prototype meta copy and the old subtitle is gone`() {
        assertEquals(
            1,
            countMatches(setup, Regex(Regex.escape("text = \"计划只影响每天排几个词，不会清掉你已经学过的进度。\""))),
            "原型 `.meta` 那一句必须逐字出现且恰一处",
        )
        assertTrue(
            countMatches(setup, Regex(Regex.escape("AppType.Meta"))) >= 1,
            "这一句的排印取 `AppType.Meta`（原型 `.meta` = 13px / ink-2）",
        )
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("每天一点点，学习会自然变成习惯。"))),
            "旧 subtitle 那句励志文案清零：页头只留标题，口径由 `.meta` 承担",
        )
    }

    // ---------- 分组：原型 `.sec-head` + `.sec-label` ----------

    /**
     * 每一节的标题走共享 `SectionHeading`（= 原型 `.sec-label` 眉标 + `.sec-note` 注），
     * 屏侧不再手搓 `AppType.Title` 组标题。
     *
     * 节数钉死为 5：选择词书 / 每日新增 / 复习上限 / 评分之后怎么走 / 搜索历史。
     * 多一节 = 把原型没有的东西塞回来；少一节 = 某一节的眉标又被手搓成了 `Title`。
     */
    @Test
    fun `every group heading is the shared eyebrow heading and the count is the prototype's`() {
        assertEquals(
            5,
            countMatches(setup, Regex(Regex.escape("SectionHeading("))),
            "本屏必须恰五节眉标：选择词书 / 每日新增 / 复习上限 / 评分之后怎么走 / 搜索历史",
        )
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("AppType.Title"))),
            "组标题一律交 `SectionHeading`：屏侧手搓 `AppType.Title` 就是第二套分组排印",
        )
        listOf("\"选择词书\"", "\"每日新增\"", "\"复习上限\"", "\"评分之后怎么走\"", "\"搜索历史\"").forEach { label ->
            assertTrue(label in setup, "本节标题必须逐字出现：$label")
        }
    }

    /**
     * 词书节的免责注必须留住（`sourcePolicy` 的产品红线：这些册是应用内分组，不是官方考纲）。
     * 原型 `.sec-note` 是 12px / ink-3，对应 `AppType.Caption` + `colors.text3`。
     */
    @Test
    fun `the wordbook section keeps its source policy note in the caption tier`() {
        assertEquals(
            1,
            countMatches(setup, Regex(Regex.escape("这是应用内学习分组，不是官方考试大纲。"))),
            "词书来源免责句必须仍在（恰一处）：它是 `WordBook.sourcePolicy` 的屏侧落点",
        )
        assertEquals(
            1,
            countMatches(setup, Regex(Regex.escape("WordBookPicker("))),
            "`LearningSetupScreen` 必须恰调一次 `WordBookPicker(`",
        )
    }

    // ---------- 每日新增：原型 `chip(n + ' 个')` 一行 ----------

    /**
     * 拖动滑杆 → 原型那四枚 chip（3 / 5 / 10 / 20 个），注记「当前 N 个」进眉标右侧。
     *
     * 为什么换：原型这一节 `section('每日新增', chips[3,5,10,20], '当前 ' + S.planNew + ' 个')` 给的是
     * **离散档位**——滑杆上的 7 词、13 词这类落点用户自己也不信；四枚 chip 让「今天学多少」变成一个
     * 可选的答案而不是一个可调的数。旧 `DailyTargetSection` 连同它的滑杆一起退役，
     * 「每日新词目标」这条滑杆可及名也随之下线（复习上限那条滑杆另起一条，见下一条用例）。
     */
    @Test
    fun `the daily new target is the prototype chip row`() {
        assertTrue(dailyChips.isNotEmpty(), "取不到 DailyTargetChips 本体：判据会空转")
        assertEquals(
            1,
            countMatches(dailyChips, Regex(Regex.escape("AppChip("))),
            "chip 必须用共享 `AppChip`（原型 `.chip`），不许屏侧再搓一枚药片",
        )
        assertEquals(
            1,
            countMatches(dailyChips, Regex(Regex.escape("FlowRow("))),
            "四枚 chip 用 `FlowRow` 承载（原型是 `flex-wrap`）：大字号下换行而不是挤破",
        )
        assertEquals(
            1,
            countMatches(dailyChips, Regex(Regex.escape("label = \"\$option 个\""))),
            "chip 文案必须是「N 个」（原型 `chip(n + ' 个')` 逐字），且只此一处",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("val DAILY_TARGET_OPTIONS = listOf(3, 5, 10, 20)"))),
            "档位集合由 `DAILY_TARGET_OPTIONS` 单点给出（原型 `var opts = [3, 5, 10, 20]`）",
        )
        assertEquals(
            1,
            countMatches(setup, Regex(Regex.escape("DailyTargetChips("))),
            "屏体必须恰一处使用 `DailyTargetChips`",
        )
        // 旧形态整体退役：函数名与滑杆可及名都不许留在文件里。
        listOf(
            "private fun DailyTargetSection(",
            "每日新词目标",
            "轻轻拖动，实时看到今天的学习量",
        ).forEach { retired ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(retired))),
                "旧每日新增滑杆的锚 `$retired` 必须清零：整节换成 chip 之后它就是死码",
            )
        }
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("Slider("))),
            "屏体不得直接放滑杆：唯一的滑杆在 `ReviewLimitSection` 里（复习上限）",
        )
    }

    /**
     * 眉标右侧的「当前 N 个」+ 词量预估那行 caption（原型 `note` 与 `.caption`）。
     *
     * 预估那一句在本仓**不许照抄原型的样例数字**（`四级 4308 词大约还要 756 天`）：
     * 数字必须由当前所选词书的真实剩余词量算出（决策 2026-10-29：样例指标一律绑真数据，
     * 读不到就不显示），所以屏侧只调 `planEstimateText(...)`、返回 null 时整句不渲染。
     */
    @Test
    fun `the daily new section states the current value and a real-data estimate`() {
        assertTrue(
            countMatches(setup, Regex(Regex.escape("trailing = \"当前 \${state.dailyNewTarget} 个\""))) >= 1,
            "「当前 N 个」必须进眉标 `trailing`（原型 `section(label, inner, note)` 的第三参）",
        )
        assertEquals(
            1,
            countMatches(setup, Regex(Regex.escape("planEstimateText("))),
            "预估句必须走纯函数 `planEstimateText(`：数字来自真实词量，不许把原型的样例数字抄进屏侧",
        )
    }

    // ---------- 复习上限：原型 `.card` + `.stat-num` + `.range` ----------

    /**
     * 复习上限一节照原型建：`.card` 里 `stat-num` + 「个 / 天」+ `range(10..80, step 5)` + caption。
     *
     * 常量必须与原型逐字对上（`min="10" max="80" step="5"`）；初值 30（原型 `S.planReview`）归域侧默认
     * （`LearningSettings.dailyReviewLimit`），屏侧不抄第二份——见本条用例末尾那条「屏侧零 DEFAULT_REVIEW_LIMIT」。
     * 滑杆可及名内嵌当前值（沿用 F5-03 规则 5 的口径，锚点从「每日新词目标」换成「每日复习上限」，
     * 判据本身不降级——见 `AccessibilityStateSemanticsContractTest`）。
     *
     * 2026-10-29 逐屏截图对照补上轨道色一条：原型那根 `.range` 的 CSS 只有 `accent-color: var(--brand-deep)`，
     * 本仓先前按批 5 的「深套换域色」口径画成了 Library 域紫（浅套还是 `strongSurface` 近黑绿），
     * 与原型同屏并排一眼可辨——原型这条不是一屏一域色的成员，是全局品牌色，故改钉 `brandDeep` + 中性 `outline`。
     */
    @Test
    fun `the review limit keeps the prototype number and range`() {
        assertTrue(reviewLimit.isNotEmpty(), "取不到 ReviewLimitSection 本体：判据会空转")
        assertEquals(
            1,
            countMatches(reviewLimit, appCardCall),
            "复习上限的容器是原型 `.card` = 共享 `AppCard`（surface + 1dp 描边 + 20dp），不是软卡",
        )
        assertEquals(
            0,
            countMatches(reviewLimit, bareCardCall),
            "复习上限不得用裸 M3 `Card(`：容器只走共享件",
        )
        assertEquals(
            0,
            countMatches(reviewLimit, Regex(Regex.escape("CardDefaults"))),
            "撤了裸 `Card(` 就不该再有 `CardDefaults` 的配色",
        )
        assertTrue(
            countMatches(reviewLimit, Regex(Regex.escape("AppType.StatNum"))) == 1,
            "数值排印取 `AppType.StatNum`（原型 `.stat-num` = 26px 衬线）",
        )
        assertTrue("\"个 / 天\"" in reviewLimit, "单位文案逐字取原型 `.stat-cap` 的「个 / 天」")
        assertEquals(
            1,
            countMatches(reviewLimit, Regex(Regex.escape("Slider("))),
            "本节恰一枚滑杆（原型 `.range`）",
        )
        assertTrue(
            "valueRange = MIN_REVIEW_LIMIT.toFloat()..MAX_REVIEW_LIMIT.toFloat()" in reviewLimit,
            "取值范围必须由常量给（原型 min=10 / max=80），不许写裸数字",
        )
        assertTrue("steps = REVIEW_LIMIT_STEPS" in reviewLimit, "步进由 `REVIEW_LIMIT_STEPS` 单点给（原型 step=5）")
        assertTrue(
            "contentDescription = \"每日复习上限，当前 \${displayed} 个\"" in reviewLimit,
            "滑杆可及名必须内嵌当前值：`AccessibilityStateSemanticsContractTest` 规则 5 按这一句取样",
        )
        assertTrue(
            "\"当前版本先记下这个上限，今日复习仍会排完全部到期词。\"" in reviewLimit,
            "上限说明那句 caption 必须说清**当前真的做什么**（用户 2026-10-29 裁决：先把文案改成不承诺）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("先复习最久没见的"))),
            "原型那句「到期词超过上限时，先复习最久没见的」是排序承诺，而 `dueTarget` 生成逻辑本轮零改动" +
                "（spec 01:28 V1 不设复习硬上限）——不许以任何形式留在本屏的可渲染文案里（口径：剥注释后的整份 `AppScreen.kt`）",
        )
        // 轨道色逐值取原型：`assets/app.css:761` 的 `.range { accent-color: var(--brand-deep) }`
        // 只管两件事——已填充的那段轨道与拇指是 brand-deep，未填充段留浏览器默认的中性浅灰。
        // 本仓对应 `colors.outline`（浅 #DDE5DD / 深 #2A3931），不是 tint 系（那是绿底，会把「未填」画成「已填」的淡化版）。
        listOf(
            "thumbColor = colors.brandDeep" to "拇指（原型 `.range` 的 accent 圆点）",
            "activeTrackColor = colors.brandDeep" to "已填充段（原型 accent-color 直接作用的那段）",
            "inactiveTrackColor = colors.outline" to "未填充段（原型是浏览器默认中性浅灰，非绿 tint）",
        ).forEach { (literal, what) ->
            assertEquals(
                1,
                countMatches(reviewLimit, Regex(Regex.escape(literal))),
                "复习上限滑杆的${what}必须逐字取 `${literal}`（原型 `.range` 的 accent-color = `--brand-deep`）",
            )
        }
        assertEquals(
            0,
            countMatches(reviewLimit, Regex(Regex.escape("LocalIsDark"))),
            "`brandDeep` 两套主题各自有值（浅 #1A7A63 / 深 #7FE3C1，原型 `:root` 与 `[data-theme=dark]` 各一条），" +
                "轨道色由 token 自己翻，屏侧不许再套一层深套三元式",
        )
        listOf(
            "const val MIN_REVIEW_LIMIT = 10",
            "const val MAX_REVIEW_LIMIT = 80",
            "const val REVIEW_LIMIT_STEP = 5",
            "const val REVIEW_LIMIT_STEPS = (MAX_REVIEW_LIMIT - MIN_REVIEW_LIMIT) / REVIEW_LIMIT_STEP - 1",
        ).forEach { literal ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape(literal))),
                "复习上限的**刻度**必须由这几条常量单点给出（原型 `min=10 max=80 step=5`），不许写裸数字：$literal",
            )
        }
        // 默认值 30 归**域侧**（`LearningSettings.dailyReviewLimit` 的默认），不在屏侧再抄一个 30：
        // 屏侧只画状态，「没存过时是多少」由域决定，两处各写 30 早晚漂移（判据见 LearningSetupViewModelTest）。
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("DEFAULT_REVIEW_LIMIT"))),
            "屏侧不得自带第二份复习上限默认值：默认归 `LearningSettings`，滑杆只画 state 里的值",
        )
        assertEquals(
            1,
            countMatches(setup, Regex(Regex.escape("ReviewLimitSection("))),
            "屏体必须恰一处使用 `ReviewLimitSection`",
        )
    }

    // ---------- 评分之后怎么走：原型那三行 + 走向 caption ----------

    /**
     * 原型的「评分之后怎么走」是**一张 `.card` 里的 `.row`**：第一行带开关，后两行是「始终」。
     *
     * 本仓三档反馈都有独立开关（`LearningSettings` 的三个字段真在驱动词卡屏，见
     * `WordCardViewModel` 的 `openDetailOn*` 判定），所以「始终」那两行改成真开关——
     * 这是**比原型更贴近实现**的偏离，记进批 1 报告。术语跟词卡屏对齐：第三档叫「不认识」，
     * 原型写「忘记」的地方一律叫「不认识」（同批沿用上一轮的术语判据）。
     */
    @Test
    fun `the reveal section is one card holding the three rating rows`() {
        assertEquals(
            2,
            countMatches(setup, appCardCall),
            "两节各一张 `.card` = 共享 `AppCard`：评分之后怎么走 + 搜索历史",
        )
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("SoftCard"))),
            "软卡（`.card-soft`）在本屏退役：原型这两节都是带描边的 `.card`",
        )
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("StrongCard("))),
            "本屏不该出现强档卡：一屏一个主操作（保存），强卡会把主操作抢走",
        )
        assertEquals(
            0,
            countMatches(setup, bareCardCall),
            "屏侧不得用裸 M3 `Card(`：容器只走共享件",
        )
        assertEquals(
            4,
            countMatches(setup, Regex(Regex.escape("DetailToggleRow("))),
            "四枚开关行一枚都不能少（三档详情 + 搜索次数）",
        )
        listOf(
            "label = \"评「认识」也展开释义\"",
            "label = \"评「模糊」也展开释义\"",
            "label = \"评「不认识」也展开释义\"",
            "description = \"关：认识直接进入下一个词。开：多看一眼释义与例句再继续。\"",
            "description = \"默认开启：没想起来的词需要先看一眼\"",
        ).forEach { literal ->
            assertTrue(literal in setup, "开关行的文案必须逐字取原型：$literal")
        }
        assertTrue("\"认识后停留\"" in setup && "\"认识后直接下一个\"" in setup, "眉标 trailing 的两档文案取原型")
        assertEquals(
            1,
            countMatches(setup, Regex(Regex.escape("revealCaptionText("))),
            "走向那句 caption 必须走纯函数 `revealCaptionText(`：文案随开关态变，判据归 `PlanEstimateTest`",
        )
    }

    /**
     * 术语统一为「不认识」：词卡屏三档反馈是「不认识 / 模糊 / 认识」，本屏不得写「忘记了」。
     */
    @Test
    fun `the third feedback tier is called unknown like the word card does`() {
        assertTrue(setup.isNotEmpty(), "取不到 LearningSetupScreen 本体：判据会空转")
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("忘记了"))),
            "「忘记了」必须清零：与词卡按钮的「不认识」对齐",
        )
        assertTrue(
            countMatches(setup, Regex(Regex.escape("不认识"))) >= 1,
            "必须至少有一处写「不认识」（三档详情的第三枚开关）",
        )
    }

    /**
     * 原型有、本仓**没有对应能力**的四节必须整节缺席——不许把做不到的开关画上去。
     *
     * 逐节的「为什么没有」：
     *  - `自评时先看哪一面`（先英文 / 先中文）：词卡的翻转方向由 `WordCardScreen` 固定为英→中，
     *    没有可持久化的偏好字段，加开关等于造一个点了没反应的控件；
     *  - `卡面上显示`（音标 / 例句 / 词组 / 近义词）：2026-10-14 的回忆→展开决策把卡面内容锁成
     *    一套（评分前只见词头，评完才展开），四枚显示开关与那条决策对冲；
     *  - `预览`：预览需要一张真词卡，本屏没有词卡数据源（做出来是假词卡，违反「不编造」）；
     *  - `习惯`（提醒与发音）：`ReminderSettingsScreen` 的入口在「我的」页，从本屏导航过去会把
     *    这一屏的分支顺序改掉（`AppScreen` 里设置分支先判 `showSetup`），做出来是死入口。
     */
    @Test
    fun `the sections without a real backend stay hidden`() {
        listOf(
            "自评时先看哪一面",
            "先英文",
            "先中文",
            "SegmentedControl(",
            "卡面上显示",
            "音标",
            "词组",
            "近义词",
            "提醒与发音",
            "预览",
            "习惯",
        ).forEach { hidden ->
            assertEquals(
                0,
                countMatches(setup, Regex(Regex.escape(hidden))),
                "本节 / 本控件在本屏没有真能力支撑，必须整节缺席：$hidden（理由见本用例 KDoc）",
            )
        }
    }

    // ---------- 通知条：原型 `notice(..., 'info')` ----------

    /**
     * 底部那句 info 提示照原型进共享 `InlineNotice`，但**第二句必须改成本仓真实行为**。
     *
     * 原型写「今日词单会立刻按新计划重排」，本仓不是这样：`GetOrCreateTodayPlanUseCase` 只在
     * 「同一本地日 + 同一活动词书」时复用旧快照——**换词书**会重排今日词单，**只改每日新增量**
     * 要等明天。照抄那句就是对用户撒了一个能证伪的谎（AGENTS.md「设置默认从次日生效」）。
     * 落点：`InlineNotice(kind = Info)`，族 2 的共享件承载，屏侧不写错误色裸 `Text`。
     */
    @Test
    fun `the info notice tells what saving actually does`() {
        // 期望值变化（1 → 2），不是放宽：原型 `sBookPicker` 自己就带一枚 info `notice`（换词书后进度保留），
        // 它随 #9 搬进了「选择词书」这一节（见 `the wordbook section ends with the prototype progress retention notice`）。
        // 两处讲的是两件事（保存时机 vs 进度保留），总数仍被钉死，各自的句子由本文件两条判据逐字钉住。
        assertEquals(
            2,
            countMatches(setup, Regex(Regex.escape("InlineNoticeKind.Info"))),
            "本屏两处 info 提示：页末的保存时机 + 选词书节的进度保留（原型的两条 `notice(..., 'info')`）",
        )
        assertEquals(
            1,
            countMatches(
                setup,
                Regex(Regex.escape("text = \"改完点右上角「保存」。换词书会立刻重排今日词单，调整每日量从次日生效。\"")),
            ),
            "提示句逐字钉住：前半句取原型，后半句按 `GetOrCreateTodayPlanUseCase` 的真实规则改写",
        )
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("今日词单会立刻按新计划重排"))),
            "原型那句「按新计划重排」在本仓不成立（只改目标量不会重排今天的快照），不得照抄",
        )
    }

    // ---------- 吸底保存条：原型 `.foot` ----------

    /**
     * 保存 CTA 从「滚动内容里的一枚按钮」改成原型 `.foot` 的**吸底条**：
     * 内容 `weight(1f).verticalScroll()`，条在滚动区之外，永远在屏底。
     *
     * 实现口径两条：
     *  - 用 `Column { content.weight(1f); bar }`，**不用** `align(Alignment.BottomCenter)`——
     *    后者要给 Box 铺 `fillMaxSize` 再叠内容，与 `verticalScroll` 的高度约束打架；
     *  - 条自铺页底色，所以槽顶必须一条 `hairlineTop`（`StickyFooterHairlineGuardTest` 同口径）。
     */
    @Test
    fun `the save action becomes the prototype sticky footer`() {
        assertTrue(saveBar.isNotEmpty(), "取不到 SetupSaveBar 本体：判据会空转")
        assertEquals(
            1,
            countMatches(saveBar, Regex(Regex.escape("""testTag("setup_save_bar")"""))),
            "吸底条必须恰一处 `setup_save_bar` 锚：connected 取样与发丝线闸都按它认站",
        )
        assertEquals(
            1,
            countMatches(saveBar, Regex(Regex.escape("hairlineTop("))),
            "自铺底色的吸底槽必须画槽顶发丝线（批 5 遗留 7 的口径，同 `BackupExportScreen.kt`）",
        )
        assertTrue("colors.background" in saveBar, "条底铺页底色 `background`：与原型 `.foot` 同为「压住滚动内容」的那一层")
        assertEquals(
            1,
            countMatches(saveBar, Regex(Regex.escape("contentDescription = \"保存学习设置\""))),
            "既有 connected 锚 `contentDescription = \"保存学习设置\"` 必须逐字留在吸底条那枚按钮上",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("选好词书"))),
            "旧页名的任何残留都会让本条与 `the header is the prototype plan title` 一起判红",
        )
    }

    /**
     * 吸底条里那枚按钮的整段调用（从既有锚往前配最近一枚裸 `Button(`）。
     *
     * 判据与上一批一致：单实底 + token 前景 + `AppShape.Button`，且禁用态仍读得出字。
     * 旧文案「保存并开始学习」换成原型的「保存计划」（本屏已经是设置页，不是首启引导）。
     */
    @Test
    fun `the sticky save button is a solid action that stays readable when disabled`() {
        val anchor = "contentDescription = \"保存学习设置\""
        val anchorAt = saveBar.indexOf(anchor)
        if (anchorAt < 0) fail("吸底条里没有 `保存学习设置` 锚：窗口取错或文案被改写")
        val button = buttonSpanBefore(saveBar, anchorAt)
        assertTrue(button.isNotEmpty(), "取不到保存按钮的 `Button(` 整段，判据会空转")
        assertTrue("保存计划" in button, "按钮文案取原型 `.foot` 的「保存计划」")
        assertEquals(
            0,
            countMatches(button, Regex(Regex.escape("保存并开始学习"))),
            "旧文案「保存并开始学习」退役：本屏是设置页，不是首启引导",
        )
        assertEquals(
            1,
            countMatches(button, Regex(Regex.escape("disabledContentColor = colors.text3"))),
            "禁用态要读得出字：按钮声明 `disabledContentColor = colors.text3`",
        )
        assertEquals(
            0,
            countMatches(button, bareInkPin),
            "按钮内的文案不得自钉 `color = colors.onStrong`：禁用时近白字压浅底，按钮看上去是空的——" +
                "前景色交给 `contentColor` / `disabledContentColor` 下发",
        )
        assertTrue(
            "LocalContentColor.current" in button,
            "文案前景色必须取 `LocalContentColor.current`：`AppType` 自带浅套墨，" +
                "在深实底上本就偏暗、禁用态更会压到浅底上读不出——两态都得跟着按钮下发的 ink 走",
        )
        assertTrue("colors.strongSurface" in button, "主 CTA 的实底必须读 `strongSurface` token")
        assertTrue("AppShape.Button" in button, "主 CTA 的形状必须走 `AppShape.Button`")
        assertEquals(
            0,
            countMatches(saveBar, Regex(Regex.escape("AppleMintGradient"))),
            "保存按钮不得再刷品牌渐变：本批的主 CTA 是单实底",
        )
        assertEquals(
            0,
            countMatches(saveBar, Regex(Regex.escape("Color.Transparent"))),
            "不得再用透明容器色绕过按钮底色（那是渐变的补丁）",
        )
        assertEquals(
            0,
            countMatches(saveBar, Regex(Regex.escape("RoundedCornerShape("))),
            "吸底条窗口内不得再手搓圆角：形状一律走 `AppShape`",
        )
    }

    /** 只认「裸」赋值：`contentColor = colors.onStrong` 是按钮自己的合法声明，不算屏侧重钉。 */
    private val bareInkPin = Regex("""(?<![A-Za-z])color = colors\.onStrong""")

    // ---------- 令牌与排印 ----------

    /**
     * 屏侧只许 `appColors()` / `AppType`：窗口内静态 Mint 桥与 M3 默认排印双双归零。
     *
     * 窗口化是本屏的必要条件——`AppScreen.kt` 同文件还有别的屏在用这些桥，
     * 全文件级清零会把那些屏一起判红，于是判据只能收窄到「本屏函数体」。
     */
    @Test
    fun `the screen reads theme tokens instead of the static mint bridge and material type`() {
        listOf(
            "屏体" to setup,
            "页头保存位" to headerSave,
            "chip 行" to dailyChips,
            "复习上限" to reviewLimit,
            "开关行" to toggleRow,
            "吸底条" to saveBar,
        ).forEach { (label, window) ->
            assertTrue(window.isNotEmpty(), "取不到 $label 本体：判据会空转")
            assertEquals(
                0,
                countMatches(window, Regex(Regex.escape("MaterialTheme.typography"))),
                "$label 不得再读 M3 默认排印：`AppType` 是唯一入口（spec §4.2）",
            )
            staticMintTokens.forEach { token ->
                assertEquals(
                    0,
                    countMatches(window, Regex(Regex.escape(token))),
                    "$label 不得读静态 Mint 桥 `$token`：只许组合期读 `appColors()`",
                )
            }
            assertEquals(
                0,
                countMatches(window, Regex(Regex.escape("RoundedCornerShape("))),
                "$label 不得手搓圆角：形状一律走 `AppShape`",
            )
        }
        assertTrue("appColors()" in setup, "屏侧必须消费 `appColors()`")
        assertTrue("colors.background" in setup, "页底必须读 `colors.background`（不再压 `MintBackground`）")
        assertTrue("AppType." in setup, "屏侧必须消费 `AppType.*`")
    }

    // ---------- 开关行：共享 AppSwitch + 原型 row 排印 ----------

    /**
     * `DetailToggleRow` 进 `.card` 之后，行本身不该再自带卡壳；开关必须是共享 `AppSwitch`
     * （原型 `.switch` = 46×28 药片），M3 `Switch` + `SwitchDefaults.colors` 那套自调轨道色退役。
     *
     * 可及名（`contentDescription = label`）必须保留：那是 F5-03 的既有要求，
     * 换控件不得顺手撕掉朗读标签。行的文案排印按原型 `.row`：标题 `.row-title` 15px = `AppType.Body`，
     * 副文案 `.row-sub` 12px / ink-3 = `AppType.Caption` + `colors.text3`。
     */
    @Test
    fun `the toggle row uses the shared switch and the prototype row tiers`() {
        assertTrue(toggleRow.isNotEmpty(), "取不到 DetailToggleRow 本体：判据会空转")
        assertEquals(
            1,
            countMatches(toggleRow, Regex(Regex.escape("AppSwitch("))),
            "每行恰一枚共享 `AppSwitch`（原型 `.switch`）",
        )
        assertEquals(
            0,
            countMatches(toggleRow, bareSwitchCall),
            "不得再用 M3 裸 `Switch(`：轨道色由共享件统一承担，屏侧自调就是第二套开关",
        )
        assertEquals(
            0,
            countMatches(toggleRow, Regex(Regex.escape("SwitchDefaults"))),
            "撤了 M3 `Switch` 就不该再有 `SwitchDefaults` 的配色",
        )
        assertEquals(
            0,
            countMatches(toggleRow, bareCardCall),
            "开关行不得再自带 M3 `Card`：它现在住在 `AppCard` 分组里，卡里套卡就是又一层卡片汤",
        )
        assertEquals(
            0,
            countMatches(toggleRow, Regex(Regex.escape("border("))),
            "开关行不得描边：层次由所在卡的描边承担，行内再描边会把卡片汤带回来",
        )
        assertEquals(
            1,
            countMatches(toggleRow, Regex(Regex.escape("hairlineTop("))),
            "组内多行靠一条发丝线分开（原型 `.row + .row` 的 `border-top`）",
        )
        assertTrue(
            "contentDescription = label" in toggleRow,
            "`AppSwitch` 必须仍带 `contentDescription = label`（F5-03：裸开关在 TalkBack 里只会念「开/关」）",
        )
        assertTrue("style = AppType.Body" in toggleRow, "行标题取 `AppType.Body`（原型 `.row-title` 15px）")
        assertTrue(
            "style = AppType.Caption" in toggleRow && "color = colors.text3" in toggleRow,
            "行副文案取 `AppType.Caption` + `colors.text3`（原型 `.row-sub` 12px / ink-3）",
        )
        assertTrue(
            "heightIn(min = " in toggleRow,
            "行的可点/可触高度必须显式给下限（大字号下不能塌）",
        )
    }

    // ---------- 主操作只剩一条路径：旧的回执行退役 ----------

    /**
     * 保存成功后那一行「已保存：… 每日新增 N 词」是**读不到的死码**：`onSaved` 立刻把这一屏收掉
     * （`AppScreen` 的 `showSetup = false`），用户永远看不到它。原型 `.foot` 也没有这一行。
     */
    @Test
    fun `the unreachable saved echo line is gone`() {
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("已保存："))),
            "保存回执行必须删掉：它在 `onSaved` 关屏之后根本没有可见窗口",
        )
        assertEquals(
            1,
            countMatches(setup, Regex(Regex.escape("InlineNoticeKind.Error"))),
            "错误提示仍走共享 `InlineNotice`（族 2），且恰一处",
        )
    }

    // ---------- 选词书：原型 `.pick.pick-radio` 通栏行列表（批 1 Task 13-A，作废批 5 spec §5 的两列色块） ----------

    /**
     * 选词书是原型画的那**一份**行列表：`app.js:587-606` 的 `sBookPicker` 与 `app.js:1467-1487`
     * 的 `sOnboarding2` 用的是同一个 `.pick.pick-radio`，原型没有一处把**词书**画成色块或网格
     * （`app.css` 里 `grid-template-columns` 0 命中；`app.js` 那 5 处内联网格是 AI 主页的卡片格与
     * 三列统计格——「原型没有网格」不是本条的依据，词书这一处才是）。
     *
     * **改判留因**（用户 2026-10-29 裁决：以原型文件为准）：批 5 spec §5 那句「选词书 2 列色块卡」
     * 在本轮作废，本条判据随之从「两列」翻成「通栏行」。翻的是版面形状，**不翻**两条与版面无关的护栏：
     *  1. 懒网格仍然禁止——整页已是 `Column(verticalScroll)`，把 `LazyVerticalGrid` 塞进竖向滚动容器
     *     会被测成无限高约束并抛 `Vertically scrollable component was measured with an infinity
     *     maximum height constraints`；词书是十位数以内的固定集合，本就不需要懒加载。
     *  2. 滑删（`SwipeToDismissBox` / `wordbook_swipe_`）继续为 0：删除入口是长按，见第四条判据。
     *
     * 两列时代的**机械形状**逐条钉成 0（`chunked(` / `weight(1f)` / `WORD_BOOK_TILE_*` / 四个旧锚前缀），
     * 而不是只钉「现在长得像行」：留一条后门，下一个人就能把网格原样搬回来而测试照样绿。
     * 旧容器函数名同样钉成 0——函数体留着等于两套选词书容器并存。
     */
    @Test
    fun `the word book list is the prototype pick radio rows`() {
        assertTrue(picker.isNotEmpty(), "取不到 WordBookPicker 本体：判据会空转")
        listOf(
            // 单列滑删时代
            "internal fun WordBookList(",
            "private fun WordBookListDivider(",
            "internal fun SwipableWordBookCard(",
            "private fun WordBookCard(",
            // 两列色块时代（批 5 Task 9 → 本轮作废）
            "private fun WordBookTile(",
            "private fun WordBookTileSkeleton(",
        ).forEach { signature ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(signature))),
                "旧选词书容器 `$signature` 必须下线：通栏行与色块网格、单列滑删卡不能并存",
            )
        }
        listOf(
            "chunked(",
            "WORD_BOOK_TILE_COLUMNS",
            "WordBookTileGap",
            "WordBookTileMinHeight",
            "WordBookTileShape",
            "wordbook_tile_",
            "wordbook_total_",
            "wordbook_learned_",
            "wordbook_progress_",
        ).forEach { literal ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(literal))),
                "两列色块时代的 `$literal` 必须全文件清零：原型没把词书画成网格，留半个符号就是留一条后门",
            )
        }
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("SwipeToDismissBox"))),
            "`SwipeToDismissBox` 必须仍为 0：删除入口是长按，滑删没有对应视觉",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("wordbook_swipe_"))),
            "`wordbook_swipe_` 锚必须退役（滑删锚已无对应交互）",
        )
        // 行列表本体：一册一行，行序即列表序，不切行、不均分。
        assertEquals(
            1,
            countMatches(picker, Regex(Regex.escape("WordBookPickRow("))),
            "选词书必须逐册渲染一枚 `WordBookPickRow`：行是本屏唯一的可选项形状",
        )
        assertTrue(
            "forEachIndexed" in picker,
            "必须按序号渲染（`forEachIndexed`）：原型 `.pick:first-child { border-top: 0 }` 要求只有首行不画分隔线",
        )
        assertEquals(
            0,
            countMatches(picker, Regex(Regex.escape("weight(1f)"))),
            "通栏行不分行：`weight(1f)` 是两列均分的手段，随网格一起退役",
        )
        assertEquals(
            1,
            countMatches(picker, Regex(Regex.escape("selectableGroup()"))),
            "单选组容器必须挂 `selectableGroup()`：TalkBack 靠它在册与册之间滑动画像「这是一组单选」",
        )
        assertEquals(
            1,
            countMatches(picker, Regex(Regex.escape("wordbook_row_skeleton_"))),
            "首帧骨架改成通栏行（`wordbook_row_skeleton_` 一处前缀 + 行号）：回填时形状不许跳",
        )
        assertTrue(
            "WORD_BOOK_PLACEHOLDER_ROWS" in picker,
            "骨架行数必须读生产常量 `WORD_BOOK_PLACEHOLDER_ROWS`，不许在屏侧另写一份数字",
        )
        // 与版面无关、但不许跟着翻回去的两条（见 KDoc）。
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("LazyVerticalGrid("))),
            "本屏不得引懒网格：整页已是 `verticalScroll`，嵌套懒容器会被测成无限高约束而崩（改判留因见上）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("GridCells."))),
            "既然不引懒网格，`GridCells` 也不许出现（半套写法最容易被下一个人照抄回来）",
        )
        assertTrue(
            "verticalScroll(" in setup,
            "内容段必须仍可滚（吸底条只负责「永远在屏底」，不负责替代滚动）",
        )
    }

    /**
     * 原型 `sBookPicker`（`app.js:588-606`）在行列表与 `.foot` 之间还有一句 `notice(…, 'info', 'shield')`：
     * 「换词书后，旧词书的进度会保留在本机，随时可以换回来。」
     *
     * 这句话在本仓是**真规则**（AGENTS.md：切换词书不得删除旧记录），也是用户换词书时最担心的一件事，
     * 所以照搬进「选择词书」这一节；页末那枚提示条讲的是保存时机（Task 12 第 8 行），两件事不重复。
     * 位置必须落在列表之后、下一节之前——漂到页末就等于把「属于这一节的风险说明」变成全屏附注。
     */
    @Test
    fun `the wordbook section ends with the prototype progress retention notice`() {
        assertEquals(
            1,
            countMatches(setup, Regex(Regex.escape("learning_setup_wordbook_notice"))),
            "选词书节的保留进度提示必须恰一处（`learning_setup_wordbook_notice`）",
        )
        assertTrue(
            "换词书后，旧词书的进度会保留在本机，随时可以换回来。" in setup,
            "提示必须逐字照原型那句：这是换词书时唯一能消除「进度会不会没了」担心的话",
        )
        assertTrue(
            countMatches(setup, Regex(Regex.escape("kind = InlineNoticeKind.Info"))) >= 2,
            "保留进度提示必须走共享 `InlineNotice` 的 Info 档（页末那枚之外再多一处）",
        )
        val pickerAt = setup.indexOf("WordBookPicker(")
        val noticeAt = setup.indexOf("learning_setup_wordbook_notice")
        val nextSectionAt = setup.indexOf("SectionHeading(text = \"每日新增\"")
        assertTrue(pickerAt > 0, "取不到 `WordBookPicker(` 调用点：判据会空转")
        assertTrue(nextSectionAt > pickerAt, "取不到「每日新增」节标题：判据会空转")
        assertTrue(pickerAt < noticeAt, "提示必须排在选词书列表之后")
        assertTrue(noticeAt < nextSectionAt, "提示必须留在「选择词书」这一节内，不许漂到页末变成全屏附注")
        // 反向钉（Task 22-J 第 3 行）：原型 `.foot` 那枚「选用这本书」是独立屏的第二条保存入口。
        // 本屏的选择是草稿、生效靠页头 / 吸底条同一条保存路径，补出来就多一条「点了以为当场生效」的假语义。
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("选用这本书"))),
            "独立屏的「选用这本书」不得补进合并屏：选择是草稿，保存只有一条路径",
        )
    }

    /**
     * 一行 = 共享 `PickRow` 的 `radio` 形，别的什么都不要（原型 `.pick`：标记 + `.row-title` +
     * `.row-sub` + 可选 `.row-value`，透明底、无描边、无插画、无进度条）。
     *
     * 「不借域色、不画卡底、不塞插画」这条是从批 5 那里**继承**下来的取舍（spec §5「不引封面图」），
     * 换版面不许把它悄悄换回来；`IllustratedIcon` / `LinearProgressIndicator` 归零是原型这一档的形状本身。
     *
     * 「使用中」逐字取原型 `.row-value`，但绑的是 `state.savedWordBookId`（当前已生效的活动册），
     * **不是**屏上的草稿选中 `selectedWordBookId`：原型按 `b.learnt > 0` 判（mock 里恰好只有选中的那本
     * 有进度，data.js:10-17），本仓要是照抄「有进度就算使用中」会把学过但没在用的册也念成使用中；
     * 而要是绑草稿选中，用户点另一本还没保存时屏上就会先写「使用中」。两条都不诚实，故绑活动册。
     */
    @Test
    fun `the pick row is the shared radio row without a tile`() {
        assertTrue(pickRow.isNotEmpty(), "取不到 WordBookPickRow 本体：判据会空转")
        assertEquals(
            1,
            countMatches(pickRow, Regex(Regex.escape("PickRow("))),
            "选择行必须走共享 `PickRow`（原型 `.pick` 的唯一定义在 `components/PickRow.kt`，屏侧不许再手搓一行）",
        )
        assertEquals(
            1,
            countMatches(pickRow, Regex(Regex.escape("radio = true"))),
            "词书是单选：`radio = true` 决定 22dp 圆标记（原型 `.pick-radio .pick-mark { border-radius: 50% }`）",
        )
        assertTrue(
            "checked = selected" in pickRow,
            "选中态由 `selected` 驱动标记（原型 `aria-checked`），不许另起一套布尔量",
        )
        assertTrue(
            "trailing = if (active) \"使用中\" else null" in pickRow,
            "尾部值逐字取原型 `.row-value` 的「使用中」，且只给当前活动词书（绑法理由见上）",
        )
        assertTrue(
            "activeBookId = state.savedWordBookId" in setup,
            "宿主必须把**已生效**的活动册传进来（`savedWordBookId`），不是草稿选中位",
        )
        assertTrue(
            "contentDescription = \"选择词书 \${book.displayName}\"" in pickRow,
            "读屏名称必须保持「选择词书 <书名>」这一形状：connected 侧按它取样（I-A2 同节点口径不变）",
        )
        assertTrue(
            "onClickLabel = \"选择词书\"" in pickRow,
            "点按的无障碍动词必须写「选择词书」：TalkBack 的双击提示由它给，不是由底色给",
        )
        assertTrue(
            "hairlineTop(if (topDivider) colors.outlineSoft else null" in pickRow,
            "分隔线走宿主侧 `hairlineTop(colors.outlineSoft)`：原型 `.pick { border-top: 1px solid var(--line-soft) }`，" +
                "首行由 `topDivider = false` 让线",
        )
        assertEquals(
            1,
            countMatches(pickRow, Regex(Regex.escape("wordbook_pick_"))),
            "每册一行的取样锚 `wordbook_pick_<id>` 恰一处：删除/选中/进度的 connected 判据都按它找",
        )
        listOf(
            "appDomain(" to "域色：原型 `.pick` 是透明底，颜色只出现在选中的标记上",
            "IllustratedIcon(" to "`Book` 插画：原型这一档只有标记 + 标题 + 副行 + 尾部值",
            "LinearProgressIndicator" to "进度条：通栏行塞一条会把行高顶起来，原型没有这一档",
            "animateColorAsState" to "选中动色：两列色块才需要动底/动边，行只动那一枚 22dp 标记",
            ".background(" to "行底色：`.pick { background: transparent }`",
            "border(" to "行描边：原型只有 `border-top` 那条分隔线，四周无框",
        ).forEach { (literal, what) ->
            assertEquals(
                0,
                countMatches(pickRow, Regex(Regex.escape(literal))),
                "选择行不得出现 `$literal` —— $what",
            )
        }
    }

    /**
     * `.row-sub` 那一条字符串承担全部进度口径：`4308 词 · 已学 3` / `4308 词 · 未开始`，
     * 降级态替换的是「· 已学 M」那一段（`4308 词 · 进度计算中`）。
     *
     * 逐字来源：`app.js:595` `b.words + ' 词' + (b.learnt ? ' · 已学 ' + b.learnt : ' · 未开始')`。
     * 「共」字是批 5 色块自己加的修饰，原型没有 → 随色块一起退役。
     *
     * 三态分开说这条口径不在本批重议（`WordBookCardProgressContractTest` 规则 1/2/3 管它）：
     * 换容器不许顺手把「不知道」写成「已学 0」，也不许把词量绑到进度上——首帧读到册就有词量。
     */
    @Test
    fun `the row subtitle carries the word count and the three progress states`() {
        assertTrue(pickRow.isNotEmpty(), "取不到 WordBookPickRow 本体：判据会空转")
        assertTrue(
            "\${book.totalWords} 词 · " in pickRow,
            "副行必须逐字取原型形状「<册元数据词量> 词 · <进度段>」：词量来自 `book.totalWords`，不挂进度",
        )
        listOf(
            "\"已学 \${progress.learned}\"" to "进度已算出",
            "\"未开始\"" to "读过进度但一词未学（原型 `b.learnt` 假值那一支）",
            "\"进度计算中\"" to "整页还在逐册回填",
            "\"暂时读不到进度\"" to "这一本读不出来——三种 null 态各自一条，不许合成「已学 0」",
        ).forEach { (literal, what) ->
            assertTrue(
                literal in pickRow,
                "副行必须逐字保留 $literal（$what）：换壳不许丢降级文案（`WordBookCardProgressContractTest` 规则 3）",
            )
        }
        assertTrue(
            "共 \${it.total}" !in pickRow,
            "词量必须仍取 `book.totalWords`：`共 \${it.total}` 是把词量绑到 progress 上的旧形状",
        )
        assertTrue(
            "?: 0f" !in pickRow,
            "无进度时不得画 0% 兜底轨道（`progress?.fraction ?: 0f` 同族禁令）：0% 等于把「不知道」说成「一点没学」",
        )
        assertEquals(
            0,
            countMatches(pickRow, Regex(Regex.escape("\"共 \""))),
            "「共 N 词」的「共」是色块时代的修饰，原型 `.row-sub` 没有：随色块一起退役",
        )
        assertTrue(
            "book.level" !in pickRow,
            "等级行（CET-4 / 博士）原型 `.pick` 里没有这一档：撤下，舍不得只能并进副行并记台账",
        )
    }

    /**
     * 删除入口仍走长按 + 读屏自定义动作，**两步确认红线不动**。
     *
     * 原型 `.pick` 行**没有画任何删除 affordance**（`app.js:587-606` 只有标记 / 标题 / 副行 / 尾部值），
     * 而本仓的删除只挂在这里——`WordBookWordsScreen.kt` 里 0 处「删除」（实跑 grep），
     * 撤掉长按等于砍掉唯一删除路径。按「原型没画到的状态先问」的口径问过了：用户 2026-10-29 裁定沿用长按，
     * **不加**原型没有的删除按钮。于是删除做成 `PickRow` 的「第二动作」：视觉上是长按，
     * 语义上是 `customActions` 里的「删除词书」（F5-03：手势不是可及路径），两者走同一个 `onRequestDelete`。
     *
     * 本条同时钉住**行侧不许自己挂手势**：`combinedClickable` / `selectable` 归共享件，
     * 屏侧再挂一层就是第二个可点面（I-A2 要的是手势、语义、锚在同一个节点上）。
     */
    @Test
    fun `the delete entry rides the row long press and keeps the confirmation chain`() {
        assertTrue(pickRow.isNotEmpty(), "取不到 WordBookPickRow 本体：判据会空转")
        assertTrue(
            "secondaryActionLabel = if (deletable) \"删除词书\" else null" in pickRow,
            "长按的动作名逐字保留「删除词书」：内置册（`deletable = false`）连长按入口都不存在",
        )
        assertTrue(
            "onSecondaryAction = if (deletable) onRequestDelete else null" in pickRow,
            "长按只**请求**删除（弹确认框），两步确认红线仍在宿主 `ConfirmDestructiveDialog`",
        )
        assertEquals(
            0,
            countMatches(pickRow, Regex(Regex.escape("combinedClickable("))),
            "行侧不得自己挂 `combinedClickable(`：手势归 `PickRow`，两处可点面会让语义节点与手势分家（I-A2）",
        )
        assertEquals(
            0,
            countMatches(pickRow, Regex(Regex.escape("selectable("))),
            "行侧不得自己挂 `selectable(`：同上，单选语义由共享件承担",
        )
        assertTrue(
            "irreversible = true" in setup,
            "宿主确认框必须仍声明 `irreversible = true`：删除词书不可撤销，换入口不许降级成普通确认",
        )
        assertTrue(
            "confirmLabel = \"删除\"" in setup,
            "宿主确认框的确认键文案必须仍是「删除」：`AppScreenOverlayContainerTest` 按它取样",
        )
    }

    // ---------- 首启建档：品牌渐变封面 + 静态 Mint 桥清零 ----------

    /**
     * 首启建档屏的封面（spec §5「启动/建档三屏内完成 + 品牌渐变封面」）。
     *
     * 这一屏是本文件里最后一个还在读静态 Mint 桥的屏（`MintBackground`/`MintSurface`/`MintPrimary*`/
     * `MintTextMuted` 合计 18 处），也是首启流程里唯一没有品牌视觉的一屏——`BrandLaunchSurface`
     * 只活了一帧就被换掉。收法是：顶部一段品牌渐变封面（`AppleMintGradient` + `ic_learning_mark` + 一句欢迎），
     * 正文全部改读 `appColors()` / `AppType`。
     *
     * `AppleMintGradient` 在窗口内**恰 1 处**：封面用它，按钮就不许再用（旧版按钮是
     * 「渐变底 + 透明容器色」绕过去的老皮肤）。既有的三枚 connected 锚（`创建资料` / `姓名输入` /
     * `请输入名字提示`）逐字保留——换壳不许把 `AppScreenTest` 的取样点一起换掉。
     */
    @Test
    fun `the profile creation screen wears the brand cover and drops the static mint bridge`() {
        assertTrue(profileCreation.isNotEmpty(), "取不到 ProfileCreationScreen 本体：判据会空转")
        assertEquals(
            1,
            countMatches(profileCreation, Regex(Regex.escape("AppleMintGradient"))),
            "封面必须恰一处 `AppleMintGradient`：它归封面，按钮不许再用渐变绕底色",
        )
        // `AppleMintGradient` 是唯一放行的静态 token：品牌渐变属 spec §2.1 的不动项，`AppleMint*` 四枚
        // 令牌本就要求静态（`MintPalette.kt`），封面的「品牌感」就靠它。它按上面那条**恰好 1 处**判，
        // 不在这轮清零清单里——剩下的中性底色/中性文字/主色才必须回 `appColors()`。
        staticMintTokens.filterNot { it == "AppleMintGradient" }.forEach { token ->
            assertEquals(
                0,
                countMatches(profileCreation, Regex(Regex.escape(token))),
                "建档屏不得再读静态 Mint 桥 `$token`：只许组合期读 `appColors()`",
            )
        }
        assertEquals(
            0,
            countMatches(profileCreation, Regex(Regex.escape("MaterialTheme.typography"))),
            "建档屏排印必须走 `AppType`：M3 默认排印是六档之外的第二套字阶",
        )
        assertTrue(
            countMatches(profileCreation, Regex(Regex.escape("AppType."))) >= 4,
            "建档屏至少四处排印取 `AppType.*`（封面欢迎语 / 副标题 / 提示句 / 按钮文案），实际 " +
                "${countMatches(profileCreation, Regex(Regex.escape("AppType.")))} 处",
        )
        listOf("\"创建资料\"", "\"姓名输入\"", "\"请输入名字提示\"").forEach { anchor ->
            assertTrue(
                anchor in profileCreation,
                "既有 connected 锚 $anchor 必须逐字保留：`AppScreenTest` 按它走首启流程",
            )
        }
        assertTrue(
            "\"先认识一下你\"" in profileCreation,
            "页头标题「先认识一下你」必须仍在：`AppScreenHeader` 是这一屏唯一的页头口径",
        )
    }

    // ---------- 取样助手 ----------

    /**
     * 按签名取顶层函数的**花括号体**（不含最外层括号）。
     *
     * 三个前提，缺一条都让判据空转，所以每一条都响亮失败：
     *  1. 签名必须找得到（改名 / 搬家时不能静默返回空串）；
     *  2. 签名之后的第一个 `{` 必须是函数体左花括号——本文件的顶层函数都没有默认 lambda 参数，
     *     但**不靠**这个巧合：配对器按字符串感知的花括号深度走，字符串里的 `${…}` 不参与计数；
     *  3. 体必须非空。
     */
    private fun windowOf(signature: String, label: String): String {
        val start = source.indexOf(signature)
        if (start < 0) fail("找不到 `$signature`：锚点搬家或改名，$label 的判据不许空转")
        val open = source.indexOf('{', start)
        if (open < 0) fail("`$signature` 之后找不到函数体起始花括号")
        var depth = 0
        var inString = false
        var escaped = false
        var index = open
        while (index < source.length) {
            val c = source[index]
            when {
                inString -> when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }

                c == '"' -> inString = true
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) {
                        val body = source.substring(open + 1, index)
                        assertTrue(body.isNotBlank(), "`$signature` 的函数体是空的：判据会空转")
                        return body
                    }
                }
            }
            index++
        }
        fail("从 `$signature` 起的花括号未闭合：配对器无法界定区间，本契约拒绝猜测")
    }

    /**
     * 从 [limit] 之前最近的一枚 `Button(` 起，取这枚调用的**整段**（参数圆括号 + 尾随 lambda）。
     *
     * 两点讲究：
     *  1. `PillButton(` 含子串 `Button(`，必须用前缀组排除——否则窗口会落到别的按钮上；
     *  2. 只取 lambda 体会把 `colors = ButtonDefaults.buttonColors(…)` 甩在窗口外，
     *     于是「禁用色声明了吗 / 文案自钉前景色了吗」两条判据各自半瞎。参数里的 lambda
     *     （`onClick = { … }`）先出现，所以只认圆括号深度归零之后的那枚 `{`。
     */
    private fun buttonSpanBefore(text: String, limit: Int): String {
        val callRegex = Regex("""(^|[^A-Za-z0-9_.])Button\(""")
        val last = callRegex.findAll(text).lastOrNull { it.range.first < limit } ?: return ""
        val at = last.range.last - "Button".length
        var depth = 0
        var inString = false
        var escaped = false
        var index = at
        while (index < text.length) {
            val c = text[index]
            when {
                inString -> when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }

                c == '"' -> inString = true
                c == '(' -> depth++
                c == ')' -> depth--
                c == '{' -> if (depth == 0) {
                    val close = balancedBodyEnd(text, index)
                    if (close < 0) return ""
                    return text.substring(at, close + 1)
                }
            }
            index++
        }
        return ""
    }

    /** 从一枚左花括号起按配对找右花括号下标（字符串感知；取不到 -1）。 */
    private fun balancedBodyEnd(text: String, open: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        var index = open
        while (index < text.length) {
            val c = text[index]
            when {
                inString -> when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }

                c == '"' -> inString = true
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return -1
    }
}
