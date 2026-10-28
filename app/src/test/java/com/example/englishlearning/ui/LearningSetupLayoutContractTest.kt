package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 批 2 Task 6：学习设置屏（`AppScreen.kt` 的 `LearningSetupScreen`）新骨架契约。
 *
 * 这一屏住在 `AppScreen.kt` 里（一个文件装十来屏），所以**所有判据都必须窗口化**：
 * 全文件级的 `Mint*` == 0 是判不了的（同文件的今日计划 / 学员档案等屏还在用静态桥）。
 * 取样窗口按「函数签名 → 它的花括号体」配对取，与 [AppScreenOverlayContainerContractTest]
 * 的 `learningSetupSignature` 同一套口径。
 *
 * 三条窗口：
 *  - [setup]：`LearningSetupScreen` 本体（页头 / 分组 / 术语 / 保存按钮）；
 *  - [toggleRow]：`DetailToggleRow`（四行开关的承载件，本批撤掉它自带的卡壳）；
 *  - [dailyTarget]：`DailyTargetSection`（原 `DailyTargetCard`：撤掉自带卡壳后它不再是卡，
 *    名字必须跟着改——名字说「卡」而实现里没有卡，下一个人就会照名字把它改回去）。
 *
 * 「行号不锚、注释不计」由 [uiScreenSource]（`stripComments`）与本文件取窗器保证。
 */
class LearningSetupLayoutContractTest {

    private val source: String by lazy { uiScreenSource("AppScreen.kt") }

    private val setup: String by lazy {
        windowOf("internal fun LearningSetupScreen(", "LearningSetupScreen 本体")
    }

    private val toggleRow: String by lazy {
        windowOf("private fun DetailToggleRow(", "DetailToggleRow 本体")
    }

    private val dailyTarget: String by lazy {
        windowOf("private fun DailyTargetSection(", "DailyTargetSection 本体")
    }

    private val picker: String by lazy {
        windowOf("internal fun WordBookPicker(", "WordBookPicker 本体")
    }

    private val tile: String by lazy {
        windowOf("private fun WordBookTile(", "WordBookTile 本体")
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

    // ---------- 返回位 ----------

    /**
     * 返回**去重**：旧版一枚 `← 返回上一层` 的 `TextButton` 排在页头**之前**，而页头又传 `onBack = null`，
     * 于是「标题下面还有一枚返回」成了两个并排的东西。
     *
     * 收法是「返回位一处定义」：删掉那枚 `TextButton`，把宿主的 `onCancel` 接到共享页头 `AppScreenHeader`
     * 的 `onBack` 上——可点区、48dp 触控面、「返回」朗读文案全归组件。
     *
     * `onCancel == null` 的强制首次设置路径因此自动没有返回入口（`AppScreenHeader` 的 R-9.2：
     * `onBack == null` 整段不渲染），不需要屏侧再写一次 `if`。
     */
    @Test
    fun `the duplicated back button is gone and the shared header carries the return`() {
        assertTrue(setup.isNotEmpty(), "取不到 LearningSetupScreen 本体：判据会空转")
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("TextButton("))),
            "本屏不得再自带返回文字按钮：返回位归共享页头一处定义",
        )
        assertEquals(
            1,
            countMatches(setup, Regex(Regex.escape("AppScreenHeader("))),
            "页头必须恰一处调用 `AppScreenHeader`",
        )
        val headerAt = setup.indexOf("AppScreenHeader(")
        val onBackAt = setup.indexOf("onBack = onCancel")
        assertTrue(
            headerAt >= 0 && onBackAt > headerAt,
            "`onBack = onCancel` 必须落在页头调用里：宿主回调接不上，返回位就是个死按钮",
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

    // ---------- 分组 ----------

    /**
     * spec §5 逐屏表「学习设置 4 个详解开关收进『词卡显示』分组弱卡」。
     *
     * 旧版四枚开关各自套一张 M3 `Card` + 1dp 描边，屏上就是四张同权重的卡（「卡片汤」）。
     * 收法是**两枚 `SoftCard`**：一枚「词卡显示」承载三档详情开关（认识 / 模糊 / 不认识），
     * 一枚「搜索历史」承载「显示词汇搜索次数」——后者不属于「词卡显示」，硬塞进去是语义错装。
     *
     * `StrongCard(` 必须为 0：本屏的主操作是底部的保存按钮，不是一张深底卡。
     *
     * 取样按 `SoftCard` 这个词数、不带左括号：容器默认参数全缺省，调用点写的是尾随 lambda
     * `SoftCard { … }`，标着 `SoftCard(` 数会恒为 0（判据空转绿）。`StrongCard` 不含子串
     * `SoftCard`，所以「恰两枚软卡」这层判据不因此变松。
     */
    @Test
    fun `the four detail toggles are collected into two soft card groups`() {
        assertEquals(
            2,
            countMatches(setup, Regex(Regex.escape("SoftCard"))),
            "四枚开关必须收进**两枚** `SoftCard` 分组：三档详情一枚、搜索次数单列一枚",
        )
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("StrongCard("))),
            "本屏不该出现强档卡：一屏一个主操作（保存），强卡会把主操作抢走",
        )
        assertEquals(
            4,
            countMatches(setup, Regex(Regex.escape("DetailToggleRow("))),
            "四枚开关行一枚都不能少（三档详情 + 搜索次数）",
        )
        assertTrue("\"词卡显示\"" in setup, "三档详情那组必须叫「词卡显示」")
        assertTrue("\"搜索历史\"" in setup, "搜索次数那组必须单列（它不是词卡显示的一部分）")
        assertTrue(
            countMatches(setup, Regex(Regex.escape("AppType.Title"))) >= 2,
            "分组标题排印取 `AppType.Title`：六档里只有它够「组标题」这一档",
        )
    }

    // ---------- 术语 ----------

    /**
     * 术语统一为「不认识」：词卡屏三档反馈是「不认识 / 模糊 / 认识」，本屏却写「忘记了」，
     * 同一个动作在两个屏上两个名字，用户要重新猜一遍哪个是哪个。
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

    // ---------- 保存按钮 ----------

    /**
     * 保存按钮换单实底：旧版用 `AppleMintGradient` 硬刷背景 + `containerColor = Color.Transparent`
     * 绕过按钮自身的容器色，是「品牌渐变」这套老皮肤在本屏的最后一块残留。
     *
     * 判据是「渐变与透明底双双归零」+「实底与前景色都读 token」+「形状走 `AppShape.Button`」。
     */
    @Test
    fun `the save action is a single solid call to action`() {
        assertTrue(setup.isNotEmpty(), "取不到 LearningSetupScreen 本体：判据会空转")
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("AppleMintGradient"))),
            "保存按钮不得再刷品牌渐变：本批的主 CTA 是单实底",
        )
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("Color.Transparent"))),
            "不得再用透明容器色绕过按钮底色（那是渐变的补丁）",
        )
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("RoundedCornerShape("))),
            "窗口内不得再手搓圆角：形状一律走 `AppShape`",
        )
        assertTrue("colors.strongSurface" in setup, "主 CTA 的实底必须读 `strongSurface` token")
        assertTrue("colors.onStrong" in setup, "主 CTA 的前景色必须读 `onStrong`（深底深字即不可读）")
        assertTrue("AppShape.Button" in setup, "主 CTA 的形状必须走 `AppShape.Button`")
        assertTrue(
            "\"保存学习设置\"" in setup,
            "`contentDescription = \"保存学习设置\"` 是既有 connected 锚，一个都不能丢",
        )
    }

    // ---------- 令牌与排印 ----------

    /**
     * 屏侧只许 `appColors()` / `AppType`：窗口内静态 Mint 桥与 M3 默认排印双双归零。
     *
     * 窗口化是本屏的必要条件——`AppScreen.kt` 同文件还有别的屏在用这些桥，
     * 全文件级清零会把那些屏一起判红，于是判据只能收窄到「本屏函数体」。
     */
    @Test
    fun `the screen reads theme tokens instead of the static mint bridge and material type`() {
        assertTrue(setup.isNotEmpty(), "取不到 LearningSetupScreen 本体：判据会空转")
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("MaterialTheme.typography"))),
            "屏侧不得再读 M3 默认排印：六档 `AppType` 是唯一入口（spec §4.2）",
        )
        staticMintTokens.forEach { token ->
            assertEquals(
                0,
                countMatches(setup, Regex(Regex.escape(token))),
                "窗口内不得读静态 Mint 桥 `$token`：只许组合期读 `appColors()`",
            )
        }
        assertTrue("appColors()" in setup, "屏侧必须消费 `appColors()`")
        assertTrue("colors.background" in setup, "页底必须读 `colors.background`（不再压 `MintBackground`）")
        assertTrue("AppType." in setup, "屏侧必须消费 `AppType.*`")
    }

    // ---------- 开关行撤掉自有卡壳 ----------

    /**
     * `DetailToggleRow` 进分组之后，行本身就不该再自带一张卡：那会变成「卡里套卡」。
     *
     * 这一条同时替掉计划里「24dp 圆角改 20」那条写法——本批不是把 24 改成 20，
     * 而是**把自有卡壳整个撤掉**，圆角只剩 `SoftCard` 的 20dp 一档，比「改到同值」更彻底。
     *
     * `Switch` 的可及名（`contentDescription = label`）必须保留：那是 F5-03 的既有要求，
     * 撕掉卡壳不得顺手撕掉朗读标签。
     */
    @Test
    fun `the toggle row sheds its own card chrome and keeps its switch label`() {
        assertTrue(toggleRow.isNotEmpty(), "取不到 DetailToggleRow 本体：判据会空转")
        assertEquals(
            0,
            countMatches(toggleRow, Regex(Regex.escape("Card("))),
            "开关行不得再自带 M3 `Card`：它现在住在 `SoftCard` 分组里，卡里套卡就是又一层卡片汤",
        )
        assertEquals(
            0,
            countMatches(toggleRow, Regex(Regex.escape("CardDefaults"))),
            "撤了 `Card(` 就不该再有 `CardDefaults` 的配色",
        )
        assertEquals(
            0,
            countMatches(toggleRow, Regex(Regex.escape("border("))),
            "开关行不得描边：层次由分组的明度差表达，描边会把卡片汤带回来",
        )
        assertEquals(
            0,
            countMatches(toggleRow, Regex(Regex.escape("RoundedCornerShape("))),
            "开关行不得手搓圆角",
        )
        assertEquals(
            0,
            countMatches(toggleRow, Regex(Regex.escape("MaterialTheme.typography"))),
            "开关行排印也必须走 `AppType`",
        )
        staticMintTokens.forEach { token ->
            assertEquals(
                0,
                countMatches(toggleRow, Regex(Regex.escape(token))),
                "开关行不得读静态 Mint 桥 `$token`",
            )
        }
        assertEquals(
            1,
            countMatches(toggleRow, Regex(Regex.escape("Switch("))),
            "每行恰一枚 `Switch`",
        )
        assertTrue(
            "contentDescription = label" in toggleRow,
            "`Switch` 必须仍带 `contentDescription = label`（F5-03：裸开关在 TalkBack 里只会念「开/关」）",
        )
        assertTrue(
            "heightIn(min = " in toggleRow,
            "行的可点/可触高度必须显式给下限（大字号下不能塌）",
        )
    }

    // ---------- 每日新增区撤掉自有卡壳 ----------

    /**
     * 原 `DailyTargetCard` 是一张 `Card` + `MintSurface` 底 + 1dp `MintOutline` 描边 + 24dp 圆角，
     * 与四枚开关行同权——屏上因此有六张卡。本批把它降成**页面级小节**（无卡壳、无描边），
     * 于是层次只剩「两个分组（浅卡）+ 一条页面级控件」两层。
     *
     * 名字随实现改：`DailyTargetCard` → `DailyTargetSection`（名字说「卡」而实现里没有卡，
     * 下一个人就会照名字把它改回去）。既有锚点由 `AccessibilityStateSemanticsContractTest`
     * 同批改成新函数名，判据本身不降级。
     */
    @Test
    fun `the daily target section sheds its own card chrome`() {
        assertTrue(dailyTarget.isNotEmpty(), "取不到 DailyTargetSection 本体：判据会空转")
        assertEquals(
            0,
            countMatches(dailyTarget, Regex(Regex.escape("Card("))),
            "每日新增不得再自带 M3 `Card`：它是页面级小节，不是第六张卡",
        )
        assertEquals(
            0,
            countMatches(dailyTarget, Regex(Regex.escape("CardDefaults"))),
            "撤了 `Card(` 就不该再有 `CardDefaults` 的配色",
        )
        assertEquals(
            0,
            countMatches(dailyTarget, Regex(Regex.escape("border("))),
            "每日新增不得描边",
        )
        assertEquals(
            0,
            countMatches(dailyTarget, Regex(Regex.escape("RoundedCornerShape("))),
            "每日新增不得手搓圆角",
        )
        assertEquals(
            0,
            countMatches(dailyTarget, Regex(Regex.escape("MaterialTheme.typography"))),
            "每日新增排印也必须走 `AppType`",
        )
        staticMintTokens.forEach { token ->
            assertEquals(
                0,
                countMatches(dailyTarget, Regex(Regex.escape(token))),
                "每日新增不得读静态 Mint 桥 `$token`",
            )
        }
        assertTrue("\"每日新增\"" in dailyTarget, "「每日新增」是本节的定位文本，`SetupScreenScreenshotTest` 按它取样")
        assertTrue(
            "contentDescription = \"每日新词目标，当前 \${displayedTarget} 词\"" in dailyTarget,
            "Slider 的可及名必须原样保留（含当前值）：`AccessibilityStateSemanticsContractTest` 钉着它",
        )
    }

    // ---------- 主操作在禁用态仍读得出字 ----------

    /**
     * 保存按钮的**整段调用**：从既有锚 `contentDescription = "保存学习设置"` 往前找到最近的
     * `Button(`（`PillButton(` 不算），再跨过参数圆括号与尾随 lambda 取到 lambda 右括号。
     *
     * 只取 lambda 体会丢掉 `colors = ButtonDefaults.buttonColors(…)` 那一整段，
     * 「禁用色声明了吗」与「文案自钉前景色了吗」就落在两个不同窗口里、两条判据各自半瞎。
     */
    private val saveButton: String by lazy {
        val anchor = """contentDescription = "保存学习设置""""
        val anchorAt = setup.indexOf(anchor)
        if (anchorAt < 0) "" else buttonSpanBefore(setup, anchorAt)
    }

    /** 只认「裸」赋值：`contentColor = colors.onStrong` 是按钮自己的合法声明，不算屏侧重钉。 */
    private val bareInkPin = Regex("""(?<![A-Za-z])color = colors\.onStrong""")

    /**
     * 真机现形（与默写纸设置同一条）：保存中 / 删除中时按钮禁用，文案行自钉的 `onStrong`
     * 让近白字压在浅底上，按钮看上去是空的。禁用态的可读性必须由按钮自己下发。
     */
    @Test
    fun `the save action keeps its label readable when it is disabled`() {
        assertTrue(saveButton.isNotEmpty(), "取不到保存按钮的 `Button(` 按钮体，判据会空转")
        assertTrue("保存并开始学习" in saveButton, "按钮体里必须有「保存并开始学习」这枚文案")
        assertEquals(
            1,
            countMatches(saveButton, Regex(Regex.escape("disabledContentColor = colors.text3"))),
            "禁用态要读得出字：按钮声明 `disabledContentColor = colors.text3`",
        )
        assertEquals(
            0,
            countMatches(saveButton, bareInkPin),
            "按钮内的文案不得自钉 `color = colors.onStrong`：禁用时近白字压浅底，按钮看上去是空的——" +
                "前景色交给 `contentColor` / `disabledContentColor` 下发",
        )
        assertTrue(
            "LocalContentColor.current" in saveButton,
            "文案前景色必须取 `LocalContentColor.current`：`AppType.Label` 自带 `text2` 墨色，" +
                "在深实底上本就偏暗、禁用态更会压到浅底上读不出——两态都得跟着按钮下发的 ink 走",
        )
    }

    // ---------- 选词书：两列色块取代单列滑删列表（批 5 Task 9 / 对账 4） ----------

    /**
     * 单列滑删列表 → 两列色块网格。
     *
     * 为什么必须换：`SwipeToDismissBox` 只在**单列整行**上成立——横滑手势与两列并排的
     * 横向滚动/误触冲突，而「滑出红色删除底」在两列色块上根本没有对应视觉。spec §5 要的是
     * 「选词书改 2 列色块卡（域色 tint + `Book` + 书名 + 词数，不引封面图）」，这条判据就是那句话的机械读法。
     *
     * **判据 1 的改判（留因）**：计划写的是「`LazyVerticalGrid(` 或 `GridCells.Fixed(2)` 恰 1 处」，
     * 实施改成「`chunked(WORD_BOOK_TILE_COLUMNS)` 恰 1 处 + 列数常量钉死为 2 + 懒网格双双为 0」。
     * 原因是本屏整页已经是 `Column(verticalScroll)`（批 2 补的可滚），把 `LazyVerticalGrid` 塞进
     * 竖向滚动容器会被测成**无限高约束**，Compose 直接抛
     * `Vertically scrollable component was measured with an infinity maximum height constraints`；
     * 而词书是十位数以内的固定集合，本就不需要懒加载。判据强度不降：列数 2 被常量钉住（改成 1 或 3
     * 立刻红），行内两块必须 `weight(1f)` 均分（少一个权重就等于让单块撑满整行）。
     */
    @Test
    fun `the swipe list becomes a two column tile grid of word books`() {
        assertTrue(picker.isNotEmpty(), "取不到 WordBookPicker 本体：判据会空转")
        // 旧形态必须整体退役：函数体留着就等于两套选词书容器并存。
        listOf(
            "internal fun WordBookList(",
            "private fun WordBookListDivider(",
            "internal fun SwipableWordBookCard(",
            "private fun WordBookCard(",
        ).forEach { signature ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(signature))),
                "旧选词书容器 `$signature` 必须下线：两列色块与单列滑删卡不能并存",
            )
        }
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("SwipeToDismissBox"))),
            "`SwipeToDismissBox` 必须清零：两列布局下横滑删除没有对应视觉，且与列内横向手势冲突",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("wordbook_swipe_"))),
            "`wordbook_swipe_` 锚必须退役（删除入口改长按，滑删锚已无对应交互）",
        )
        // 列数钉死为 2：这是「两列」唯一的数字来源，改 1 变单列、改 3 变得比屏宽更挤。
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("const val WORD_BOOK_TILE_COLUMNS = 2"))),
            "两列网格的列数必须由 `const val WORD_BOOK_TILE_COLUMNS = 2` 单点给出",
        )
        assertEquals(
            1,
            countMatches(picker, Regex(Regex.escape("chunked(WORD_BOOK_TILE_COLUMNS)"))),
            "选词书必须按 `chunked(WORD_BOOK_TILE_COLUMNS)` 切行：切法只有一处",
        )
        assertTrue(
            countMatches(picker, Regex(Regex.escape("weight(1f)"))) >= 2,
            "同一行里的色块必须各自 `weight(1f)`：没权重的块会撑满整行，两列就退化成单列",
        )
        assertEquals(
            1,
            countMatches(picker, Regex(Regex.escape("wordbook_tile_skeleton_"))),
            "首帧骨架也走两列（`wordbook_tile_skeleton_` 一处前缀）：回填时列数不许跳",
        )
        assertEquals(
            1,
            countMatches(setup, Regex(Regex.escape("WordBookPicker("))),
            "`LearningSetupScreen` 必须恰调一次 `WordBookPicker(`",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("LazyVerticalGrid("))),
            "本屏不得引懒网格：整页已是 `verticalScroll`，嵌套懒容器会被测成无限高约束而崩（判据 1 改判留因见上）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("GridCells."))),
            "既然不引懒网格，`GridCells` 也不许出现（半套写法最容易被下一个人照抄回来）",
        )
    }

    /**
     * 色块取域色 + `Book` 插画，**不引封面图**（spec §5「不引封面图」）。
     *
     * 词书封面是一条诱人的路（每册一张 16:9 图，看起来更像书店），但应用里根本没有封面资产，
     * 引进来就得给 28 枚插画之外再造一套位图；域色 + 一枚 `Book` 插画已经足够把「这是一本书」
     * 与「这是哪个域」两件事说清。
     *
     * `painterResource(` / `Image(` 在窗口内必须为 0：插画一律走 `IllustratedIcon`（
     * 它内部才是 `Image(` + `painterResource(`，且已由 `AppIllustrationContractTest` 管住），
     * 屏侧自己再写一遍就等于绕过那套映射表。
     */
    @Test
    fun `the tile takes domain colour and the book illustration without cover art`() {
        assertTrue(tile.isNotEmpty(), "取不到 WordBookTile 本体：判据会空转")
        assertTrue(
            countMatches(tile, Regex(Regex.escape("appDomain("))) >= 1,
            "色块必须读域色（`appDomain(...)`）：两列的视觉秩序靠域色一档统一，不靠每册各配色",
        )
        assertTrue(
            countMatches(tile, Regex(Regex.escape("AppIcon.Book"))) >= 1,
            "色块必须用 `AppIcon.Book` 插画（28 枚位图里对应 `app_icon_books`）",
        )
        assertEquals(
            1,
            countMatches(tile, Regex(Regex.escape("IllustratedIcon("))),
            "插画入口必须恰一处 `IllustratedIcon(`：它是位图插画的唯一合法入口",
        )
        assertEquals(
            0,
            countMatches(tile, Regex(Regex.escape("painterResource("))),
            "色块不得直接 `painterResource(`：那会绕过 `AppIllustrations` 的枚举↔资源映射",
        )
        assertEquals(
            0,
            countMatches(tile, Regex(Regex.escape("Image("))),
            "色块不得自己写 `Image(`：不引封面图（spec §5），插画只走 `IllustratedIcon`",
        )
        assertEquals(
            1,
            countMatches(tile, Regex(Regex.escape("wordbook_tile_"))),
            "每个色块必须自带 `wordbook_tile_<id>` 锚：删除/选中/进度的 connected 判据都按它取样",
        )
    }

    /**
     * 四条进度字面迁进色块：`共 N 词` / `已学 N` / `进度计算中` / `暂时读不到进度`。
     *
     * 这三态的分口语义（`WordBookCardProgressContractTest` 规则 1/2/3）不在本批重议——
     * 换容器不得顺手把「不知道」写成「已学 0」。本条只钉「换了壳，四条字面跟着壳一起搬」：
     * 字面留在旧函数里而新壳重写一套缩写，`WordBookCardProgressContractTest` 的文件级扫描仍会绿，
     * 而屏上已经看不到那句降级说明了。
     */
    @Test
    fun `the four progress literals move into the tile with the container`() {
        assertTrue(tile.isNotEmpty(), "取不到 WordBookTile 本体：判据会空转")
        listOf(
            "text = \"共 \${book.totalWords} 词\"",
            "\"已学 \${progress.learned}\"",
            "\"进度计算中\"",
            "\"暂时读不到进度\"",
        ).forEach { literal ->
            assertTrue(
                tile.contains(literal),
                "色块里必须逐字保留 $literal —— 换壳不许丢降级文案（`WordBookCardProgressContractTest` 规则 3）",
            )
        }
        assertTrue(
            "共 \${it.total}" !in tile,
            "词量必须仍取 `book.totalWords`：`共 \${it.total}` 是把词量绑到 progress 上的旧形状",
        )
        assertTrue(
            "?: 0f" !in tile,
            "无进度时不得画 0% 兜底轨道（`progress?.fraction ?: 0f` 同族禁令）：0% 等于把「不知道」说成「一点没学」",
        )
    }

    /**
     * 删除入口从「左滑」换成「长按」，**两步确认红线不动**。
     *
     * `combinedClickable` 恰一处：一个色块只有一个长按入口，挂两处等于给同一册两条删除路径。
     *
     * TalkBack 只认「操作」，念不出也做不出长按手势——所以 `customActions` 里的「删除词书」必须还在
     * （它与长按走同一个 `onRequestDelete`，只弹确认框）。这条是 F5-03 的既有要求，换手势不得顺手撕掉。
     *
     * 判据还钉住宿主那句 `irreversible = true` 与 `confirmLabel = "删除"`：色块只负责「请求删除」，
     * 真正动手的是宿主 `ConfirmDestructiveDialog`，两段文案一个都不许跟着换壳漂走。
     */
    @Test
    fun `the delete entry moves to a long press and keeps the confirmation chain`() {
        assertTrue(tile.isNotEmpty(), "取不到 WordBookTile 本体：判据会空转")
        assertEquals(
            1,
            countMatches(tile, Regex(Regex.escape("combinedClickable("))),
            "色块的删除入口必须恰一处 `combinedClickable(`（点按选中 + 长按删除同一个节点）",
        )
        assertTrue(
            "onLongClick = " in tile,
            "长按必须接 `onLongClick =`（滑删退役后，长按是删除的唯一入口）",
        )
        assertTrue("\"删除词书\"" in tile, "无障碍自定义动作「删除词书」必须保留：读屏用户做不出长按手势")
        assertTrue(
            countMatches(tile, Regex(Regex.escape("customActions"))) >= 1,
            "必须把删除登记成 `customActions`（F5-03：手势不是可及路径）",
        )
        assertTrue(
            "stateDescription = if (selected) \"已选\" else \"未选\"" in tile,
            "色块的选中态必须进语义：`AccessibilityStateSemanticsContractTest` 规则 1 钉着这一句",
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
