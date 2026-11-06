package com.example.englishlearning.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 批 B 自适应窗口契约（2026-11-06；口径见 `docs/specs/00-foundation-and-architecture.md` 的
 * 「窗口类别支持矩阵」与 `docs/decisions/2026-11-06-adaptive-window-layout.md`）。
 *
 * 这个文件管四件事，每件都对应一条「没人盯着就会长歪」的路径：
 *
 *  1. **断点与版面承诺与 Spec 原文同源**：`600 / 840` 两枚断点和「哪一档换侧栏、哪一档降 hero」
 *     写死在文档表格里；代码里改一档而文档不动，或文档改了代码不动，都必须当场见红。
 *     （反例不是假想：2026-10 那一轮的阈值就是「文档说小屏、代码只按 411dp 竖屏写」漂开的。）
 *  2. **判定权收口**：窗口类别只在 tab 宿主判定一次，读点白名单之外（屏侧任何 `.kt`）不许出现。
 *     屏侧各自量宽度 = 全站长出 N 套横屏版面，而每一套都没人验过。
 *  3. **底栏与侧栏同形**：侧边导航栏是底部导航的**换摆法**，不是第二套导航语义。
 *     逐枚锚点在两份文件的生效函数体内字面同形、计数相等，任一侧单独改动即红。
 *  4. **hero 降级长在生效路径上**：页头的降级判定必须在 `AppScreenHeader` 的函数体内恰一处，
 *     旧形 `if (hero) AppType.Headline` 整文件归零——否则「降级」只活在注释里。
 *
 * 与 [AppBottomBarContractTest] 同一套纪律：锚点一律在**剥注释后**的源码上计数（注释里的示例代码不算），
 * 每条归零断言都配一枚「反空转」探针（把违规形状喂进同一具计数器，必须数得出命中），
 * 扫描根缺失或扫到 0 个文件时响亮失败，绝不静默空扫。
 */
class AppWindowLayoutContractTest {

    // ---------- 夹具：读取 ----------

    /** `ui/` 根下按文件名取剥注释后的正文；位置守卫与 [AppBottomBarContractTest] 的私有版逐字同源。 */
    private fun uiRootSource(name: String): String {
        val files = uiFiles().filter { it.name == name }
        assertTrue(
            files.size == 1,
            "$name 应恰好一个，实际 ${files.size} 个（实现文件未创建 / 被改名 / 扫描错位？" +
                "期望目录 $UI_MAIN_SOURCE_PATH，JVM 工作目录 ${File(".").absolutePath}）",
        )
        val file = files.single()
        assertTrue(
            file.parentFile.relativeTo(File(UI_MAIN_SOURCE_PATH)).path.isEmpty(),
            "$name 不在 ui 根下（搬家会让本契约读错文件）：${file.path}",
        )
        return stripComments(file.readText(Charsets.UTF_8))
    }

    private fun specFile(path: String, hint: String): File {
        val file = File(path)
        assertTrue(
            file.isFile,
            "批 B 契约要读的文档缺失：${file.absolutePath}（JVM 工作目录 ${File(".").absolutePath}）。" +
                "$hint——文档不在场时本契约不许绿，因为「代码与 Spec 同源」这条判据没有对照物。",
        )
        return file
    }

    /** Spec 00 非功能要求里的窗口类别矩阵原文（判定列 + 版面承诺列都从这里取）。 */
    private fun specMatrixText(): String =
        specFile(
            "../docs/specs/00-foundation-and-architecture.md",
            "阶段 0 规格必须带着 2026-11-06 批 B 补写的「窗口类别支持矩阵」",
        ).readText(Charsets.UTF_8)

    /** 批 B 决策文档原文；存在性本身就是一道闸（跳过决策文档 = 当场红）。 */
    private fun decisionText(): String =
        specFile(
            "../docs/decisions/2026-11-06-adaptive-window-layout.md",
            "批 B 的四枚口径（离线依赖、断点、两栏高度门、内容上限）必须先落决策文档",
        ).readText(Charsets.UTF_8)

    /** 取矩阵里某一档的整行；行首标签写死，文档改表结构时响亮失败。 */
    private fun matrixRow(spec: String, label: String): String {
        val row = spec.lines().firstOrNull { it.trimStart().startsWith("| $label |") }
        assertTrue(
            row != null,
            "Spec 00 里找不到「| $label |」这一行窗口类别；矩阵改形（换档名/换列序）必须先改本契约的取行方式。",
        )
        return row
    }

    // ---------- 夹具：函数体提取与字面计数（与 AppBottomBarContractTest 同机制） ----------

    /**
     * 从 [stripped] 中取 [signature] 锚定函数的花括号体（不含最外层括号）。
     * 字符串字面量整体跳过；未闭合直接 fail——配对器失准时「体外计数」会把体内锚算成体外，必须报错不能猜。
     */
    private fun extractFunctionBody(stripped: String, signature: String): String {
        val start = stripped.indexOf(signature)
        assertTrue(start >= 0, "找不到函数签名 \"$signature\"：锚点搬家或改名，契约不许空转")
        val open = stripped.indexOf('{', start)
        assertTrue(open >= 0, "$signature 之后找不到函数体起始花括号")
        var depth = 0
        var i = open
        var inString = false
        var escaped = false
        while (i < stripped.length) {
            val c = stripped[i]
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
                    if (depth == 0) return stripped.substring(open + 1, i)
                }
            }
            i++
        }
        fail("$signature 的花括号未闭合：配对器无法界定函数体，本契约拒绝猜测")
    }

    private fun count(hay: String, needle: String): Int = Regex(needle, RegexOption.LITERAL).findAll(hay).count()

    // ---------- 1. 断点与版面承诺与 Spec 同源 ----------

    @Test
    fun `the three breakpoints and the nav and hero promises come straight from the spec matrix`() {
        val spec = specMatrixText()
        val medium = AppWindowLayoutPolicy.MEDIUM_MIN_WIDTH_DP.toInt()
        val wide = AppWindowLayoutPolicy.WIDE_MIN_WIDTH_DP.toInt()

        // 判定列：三档的区间表达式由代码里的两枚断点现拼，代码改值而文档不动 → 这里当场对不上。
        assertTrue(matrixRow(spec, "紧凑").contains("`< $medium`"), "紧凑档判定列与 MEDIUM_MIN_WIDTH_DP=$medium 不再同源")
        assertTrue(
            matrixRow(spec, "中等").contains("`$medium ≤ w < $wide`"),
            "中等档判定列与两枚断点（$medium/$wide）不再同源",
        )
        assertTrue(matrixRow(spec, "宽").contains("`≥ $wide`"), "宽档判定列与 WIDE_MIN_WIDTH_DP=$wide 不再同源")

        // 版面承诺列 × 代码派生函数：文档说什么档换侧栏，代码就必须在那个档换侧栏，两侧都要在场。
        assertTrue(matrixRow(spec, "紧凑").contains("底部四栏导航"), "紧凑档的版面承诺不再写底部四栏导航")
        assertFalse(matrixRow(spec, "紧凑").contains("侧边导航栏"), "紧凑档的版面承诺里冒出侧边导航栏：代码用不上，文档先改了口径")
        listOf("中等", "宽").forEach { label ->
            assertTrue(matrixRow(spec, label).contains("侧边导航栏"), "$label 档的版面承诺缺「侧边导航栏」")
        }
        assertFalse(AppWindowLayoutPolicy.usesSideNavigation(AppWindowLayout.Compact), "紧凑档不许上侧栏")
        assertTrue(AppWindowLayoutPolicy.usesSideNavigation(AppWindowLayout.Medium), "中等档必须换侧栏（Spec 00 矩阵第二行）")
        assertTrue(AppWindowLayoutPolicy.usesSideNavigation(AppWindowLayout.Wide), "宽档必须换侧栏（Spec 00 矩阵第三行）")

        // hero 档：只有宽档写「页头降级」，代码也只让宽档降级。
        assertTrue(matrixRow(spec, "宽").contains("页头降级"), "宽档的版面承诺缺「页头降级」")
        assertTrue(matrixRow(spec, "紧凑").contains("hero"), "紧凑档的版面承诺缺 hero 档")
        assertFalse(matrixRow(spec, "中等").contains("页头降级"), "中等档不该承诺页头降级（sw ≥ 600dp 的窄高窗口仍留 hero；手机横屏自决策 §10.2 起不开放）")
        assertFalse(AppWindowLayoutPolicy.downgradesHeroHeader(AppWindowLayout.Compact), "紧凑档不许降 hero")
        assertFalse(AppWindowLayoutPolicy.downgradesHeroHeader(AppWindowLayout.Medium), "中等档不许降 hero")
        assertTrue(AppWindowLayoutPolicy.downgradesHeroHeader(AppWindowLayout.Wide), "宽档必须降 hero（Spec 00 矩阵第三行）")

        // 反空转：把「代码改了文档没改」的形状喂进同一具取行器，必须对不上而不是恒真。
        val staleSpec = spec.replace("`$medium ≤ w < $wide`", "`640 ≤ w < 900`")
        assertFalse(
            matrixRow(staleSpec, "中等").contains("`$medium ≤ w < $wide`"),
            "反空转失控：改掉矩阵判定列后中等档断言仍然成立，那条断言是恒真",
        )
    }

    @Test
    fun `the decision document carries the same four numbers the policy uses`() {
        val text = decisionText()
        listOf(
            AppWindowLayoutPolicy.MEDIUM_MIN_WIDTH_DP.toInt().toString() to "中等档下界",
            AppWindowLayoutPolicy.WIDE_MIN_WIDTH_DP.toInt().toString() to "宽档下界",
            AppWindowLayoutPolicy.CONTENT_MAX_WIDTH_DP.toInt().toString() to "内容列上限",
            AppWindowLayoutPolicy.TWO_COLUMN_MIN_HEIGHT_DP.toInt().toString() to "两栏资格高度门",
        ).forEach { (number, label) ->
            assertTrue(
                text.contains(number),
                "批 B 决策文档里找不到 $label（$number）：口径搬家要同步文档，别让代码独自持有这个数。",
            )
        }
    }

    @Test
    fun `classification matches the matrix ranges on both sides of every breakpoint`() {
        assertEquals(AppWindowLayout.Compact, AppWindowLayoutPolicy.classify(0f))
        assertEquals(AppWindowLayout.Compact, AppWindowLayoutPolicy.classify(411f), "竖屏手机典型宽度")
        assertEquals(AppWindowLayout.Compact, AppWindowLayoutPolicy.classify(599.999f))
        assertEquals(AppWindowLayout.Medium, AppWindowLayoutPolicy.classify(600f), "下界归高档（与 Material Range 定义一致）")
        assertEquals(AppWindowLayout.Medium, AppWindowLayoutPolicy.classify(731f), "emulator-5554 横屏实测 w731dp")
        assertEquals(AppWindowLayout.Medium, AppWindowLayoutPolicy.classify(839.999f))
        assertEquals(AppWindowLayout.Wide, AppWindowLayoutPolicy.classify(840f))
        assertEquals(AppWindowLayout.Wide, AppWindowLayoutPolicy.classify(1280f), "中等档 + density 240 的换算读值")

        // 单调性：宽度越大档不能倒退，逐 1dp 扫到 2000。
        var previous = AppWindowLayout.Compact
        var w = 0f
        while (w <= 2000f) {
            val next = AppWindowLayoutPolicy.classify(w)
            assertTrue(
                next.ordinal >= previous.ordinal,
                "classify 在 ${w}dp 处档位倒退（$previous → $next）：分档必须随宽度单调不降",
            )
            previous = next
            w += 1f
        }

        // 只有三档：加一枚枚举会让「白名单 + 两栏高度门」这套口径重新排队。
        assertEquals(3, AppWindowLayout.entries.size, "窗口类别必须恰三档（紧凑/中等/宽）")

        // Dp 重载（宿主从 BoxWithConstraints.maxWidth 直接喂）与 Float 版必须同档：
        // 判定面只有 dp，像素与 Density 不参与分档。
        listOf(411 to AppWindowLayout.Compact, 731 to AppWindowLayout.Medium, 1280 to AppWindowLayout.Wide)
            .forEach { (widthDp, expected) ->
                assertEquals(expected, AppWindowLayoutPolicy.classify(widthDp.dp), "${widthDp}dp 的 Dp 重载与 Float 版不同档")
            }

        // 内容上限：紧凑档 = 不封顶（与今天的行为逐字等价），其余两档收进 CONTENT_MAX_WIDTH_DP。
        assertEquals(Dp.Infinity, AppWindowLayoutPolicy.contentMaxWidth(AppWindowLayout.Compact), "紧凑档不许封顶")
        listOf(AppWindowLayout.Medium, AppWindowLayout.Wide).forEach { layout ->
            assertEquals(
                AppWindowLayoutPolicy.CONTENT_MAX_WIDTH_DP.dp,
                AppWindowLayoutPolicy.contentMaxWidth(layout),
                "$layout 档的内容列必须收到 CONTENT_MAX_WIDTH_DP 上限",
            )
        }
    }

    // ---------- 2. 判定权收口 ----------

    private val readPointNeedles = listOf(
        "currentAppWindowLayout(",
        "LocalAppWindowLayout",
        "AppWindowLayoutPolicy.",
    )

    /** 允许读窗口类别的三枚文件：定义、宿主判定、页头消费。 */
    private val layoutReadWhitelist = setOf(
        "AppWindowLayout.kt",
        "AppScreen.kt",
        "AppScreenHeader.kt",
    )

    /** 扫描器本体：输入（文件名，剥注释正文），输出「白名单外的读点」。合成探针与真源码共用这一具。 */
    private fun offendingReadPoints(sources: List<Pair<String, String>>): List<String> =
        sources.flatMap { (name, text) ->
            if (name in layoutReadWhitelist) {
                emptyList()
            } else {
                readPointNeedles.filter { count(text, it) > 0 }.map { "$name → 「$it」${count(text, it)} 处" }
            }
        }

    @Test
    fun `only the host and the header may read the window layout`() {
        val sources = uiFiles().map { it.name to stripComments(it.readText(Charsets.UTF_8)) }
        assertTrue(
            sources.size >= 100,
            "窗口类别读点扫描只覆盖 ${sources.size} 个文件（今天 ui 根约 119 枚 .kt）：少于 100 说明扫描根错位，归零断言会恒真。" +
                "现算：find $UI_MAIN_SOURCE_PATH -name '*.kt' | wc -l",
        )
        val offenders = offendingReadPoints(sources)
        assertTrue(
            offenders.isEmpty(),
            "窗口类别读点溢出宿主层：\n  " + offenders.joinToString("\n  ") +
                "\nSpec 00 的口径是「矩阵只在 tab 宿主判定一次」；屏侧各自量宽度 = 全站长出没人验过的横屏版面。",
        )

        // 反空转（must-flag）：一枚假装在屏里判类别的合成文件必须被抓出来，否则上面的归零是恒真。
        val breach = listOf(
            "WordCardScreen.kt" to stripComments("val layout = currentAppWindowLayout()\nif (layout == AppWindowLayout.Wide) TODO()\n"),
        )
        assertEquals(1, offendingReadPoints(breach).size, "反空转失控：屏侧读点探针没被抓出")

        // 反空转（must-not-flag）：白名单三枚本体不该被抓；换掉名单后同一形状必须被抓。
        assertEquals(
            0,
            offendingReadPoints(listOf("AppScreen.kt" to "AppWindowLayoutPolicy.classify(maxWidth)")).size,
            "反空转失控：宿主白名单文件被自己的名单判成违规",
        )
        assertEquals(
            1,
            offendingReadPoints(listOf("AppScreenX.kt" to "AppWindowLayoutPolicy.classify(maxWidth)")).size,
            "反空转失控：名单守卫写歪，任何文件都能过",
        )
    }

    // ---------- 3. 底栏与侧栏同形 ----------

    private val bottomBarSlotSignature = "private fun RowScope.TabSlot("
    private val sideNavSlotSignature = "private fun SideNavSlot("

    /**
     * 导航语义锚：底栏 [AppBottomBarContractTest] 已经逐枚钉在 `TabSlot` 体内；
     * 本席钉的是「侧栏那一格与它字面同形、计数相等」，两侧任一动而不动另一侧即红。
     */
    private val sharedNavigationAnchors = listOf(
        "testTag(\"app_tab_\${tab.name.lowercase()}\")" to "testTag 命名（connected 与自动化都按它定位）",
        "contentDescription = tab.contentDescription" to "朗读标签",
        "role = Role.Tab" to "Role.Tab",
        "stateDescription = if (selected)" to "选中/未选中状态播报",
        "if (selected) appDomain(AppDomain.AiSpeech).deep else appColors().text2" to "选中墨（主题路径）",
        "val labelInk = if (selected && !LocalIsDark.current)" to "标签墨定义",
        "color = labelInk" to "标签墨消费",
        "InlineIconBadge(icon = tab.glyph, domain = AppDomain.AiSpeech" to "共享插画徽标",
        "backdrop = selected" to "选中档药片底",
        "Modifier.alpha(TabGlyphIdleAlpha)" to "未选中档半透明",
    )

    @Test
    fun `side nav is the bottom bar moved, not a second navigation semantics`() {
        val bar = uiRootSource("AppBottomBar.kt")
        val rail = uiRootSource("AppSideNav.kt")
        val barBody = extractFunctionBody(bar, bottomBarSlotSignature)
        val railBody = extractFunctionBody(rail, sideNavSlotSignature)

        sharedNavigationAnchors.forEach { (anchor, label) ->
            val inBar = count(barBody, anchor)
            val inRail = count(railBody, anchor)
            assertEquals(1, inBar, "底栏 TabSlot 体内「$label」应恰一处，实际 $inBar：本席的对照物没了")
            assertEquals(
                inBar,
                inRail,
                "侧栏 SideNavSlot 体内「$label」与底栏不同形（侧栏 $inRail / 底栏 $inBar）：" +
                    "导航换摆法可以，换语义不行（Spec 00 矩阵的「同一套导航语义」）。",
            )
            // 体外零处：同一形状不许在侧栏的函数体之外再留一份死副本。
            assertEquals(
                0,
                count(rail, anchor) - inRail,
                "侧栏文件里「$label」在 SideNavSlot 体之外还有 ${count(rail, anchor) - inRail} 处：死分支不算生效路径",
            )
        }

        // 同一张 tab 表、同一份字形表、同一组目的地：侧栏不许自己列一枚新目的地或删一格。
        assertEquals(1, count(rail, "AppTab.entries.forEach"), "侧栏必须由 AppTab.entries 遍历，与底栏同源，且恰一处")
        assertEquals(1, count(rail, "tab.glyph"), "侧栏字形必须走 AppTab.glyph 映射，不许自己点名 AppIcon.*")
        assertEquals(0, count(rail, "AppIcon."), "侧栏不得直接引用 AppIcon 枚名：字形表只许长在 AppBottomBar.kt 的 AppTab.glyph 上")

        // 触控下限：侧栏每格 48dp（F5-03 下限），底栏那 64dp 是横向条的高度，两档不同不并轨。
        assertEquals(1, count(railBody, "heightIn(min = 48.dp)"), "侧栏每格必须钉 48dp 触控下限一处")

        // 反空转：把「侧栏另起一套语义」的击穿形状喂进同一具计数器，必须不同形。
        val breachRail = "private fun SideNavSlot(tab: AppTab, selected: Boolean, onSelect: (AppTab) -> Unit) {\n" +
            "    Modifier.alpha(0.5f)\n    NavigationRailItem(selected = selected, icon = {})\n}\n"
        val breachBody = extractFunctionBody(breachRail, sideNavSlotSignature)
        assertEquals(0, count(breachBody, "Modifier.alpha(TabGlyphIdleAlpha)"), "反空转失控：散写 0.5f 竟满足半透明锚")
        assertEquals(0, count(breachBody, "role = Role.Tab"), "反空转失控：M3 组件默认语义竟满足 Role.Tab 锚")
    }

    // ---------- 4. 宿主接线 ----------

    @Test
    fun `the tab host measures content width once and swaps the navigation there`() {
        val host = uiRootSource("AppScreen.kt")
        listOf(
            "BoxWithConstraints(Modifier.fillMaxSize())" to "宿主实测内容宽度的容器",
            "AppWindowLayoutPolicy.classify(maxWidth)" to "按 maxWidth 的判定（不是 screenWidthDp 的换算值）",
            "CompositionLocalProvider(LocalAppWindowLayout provides windowLayout)" to "类别下发",
            "AppWindowLayoutPolicy.usesSideNavigation(windowLayout)" to "导航摆法派生",
            "AppWindowLayoutPolicy.contentMaxWidth(windowLayout)" to "内容列上限",
            "AppSideNav(selected = selectedTab, onSelect = { selectedTab = it })" to "侧栏接线",
            "AppBottomBar(selected = selectedTab, onSelect = { selectedTab = it })" to "底栏接线",
            "if (!usesSideNavigation) {" to "底栏的条件挂载",
        ).forEach { (anchor, label) ->
            assertEquals(1, count(host, anchor), "宿主里${label}必须恰一处，实际 ${count(host, anchor)}：「$anchor」")
        }
        // 旧形归零：底栏无条件挂载 = 宽窗里侧栏与底栏同时存在，两枚导航抢同一组 testTag。
        assertEquals(0, count(host, "bottomBar = { AppBottomBar"), "底栏不许回到无条件挂载：宽窗会同时出现两枚导航")
        // 两枚导航的 onSelect 必须是同一句话：换摆法不换行为。
        assertEquals(
            2,
            count(host, "onSelect = { selectedTab = it }"),
            "底栏与侧栏各接一次 selectedTab 赋值；少于 2 = 其中一枚导航点了不切页",
        )
    }

    // ---------- 5. hero 降级长在生效路径上 ----------

    private val heroGateAnchor =
        "val useHeroTitle = hero && !AppWindowLayoutPolicy.downgradesHeroHeader(currentAppWindowLayout())"

    private val heroStyleAnchor = "style = if (useHeroTitle) AppType.Headline else AppType.Title"

    @Test
    fun `hero headline is downgraded by the window layout inside the header live path`() {
        val header = uiComponentSource("AppScreenHeader.kt")
        assertEquals(1, count(header, heroGateAnchor), "页头的 hero 降级判定必须恰一处：「$heroGateAnchor」")
        assertEquals(1, count(header, heroStyleAnchor), "页头标题字级必须消费 useHeroTitle 恰一处")
        assertEquals(
            0,
            count(header, "if (hero) AppType.Headline"),
            "旧形 `if (hero) AppType.Headline` 还在：宽窗降级只活在注释里，没长在渲染路径上",
        )
        // 反空转： ungated 旧形满足字级锚吗？必须不满足（否则字级锚恒真）。
        val legacy = "style = if (hero) AppType.Headline else AppType.Title"
        assertEquals(0, count(legacy, heroStyleAnchor), "反空转失控：旧形竟满足消费锚")
        assertEquals(0, count(legacy, heroGateAnchor), "反空转失控：旧形竟满足降级判定锚")
        // 正向自命中：锚点写歪了要在这里露出来，而不是靠真实文件永远读不到。
        assertEquals(1, count(heroStyleAnchor, heroStyleAnchor), "反空转失控：字级锚不满足自身，锚字符串写歪了")
        assertEquals(1, count(heroGateAnchor, heroGateAnchor), "反空转失控：降级锚不满足自身，锚字符串写歪了")
    }

    @Test
    fun `the fallback keeps working when no host provides the layout`() {
        val policy = uiRootSource("AppWindowLayout.kt")
        // 默认 null（= 没人提供时按系统配置现算），connected 的单屏夹具与预览才不必各自补 provider。
        assertEquals(1, count(policy, "compositionLocalOf<AppWindowLayout?> { null }"), "LocalAppWindowLayout 默认必须是 null 哨兵")
        val body = extractFunctionBody(policy, "fun currentAppWindowLayout(): AppWindowLayout {")
        assertEquals(1, count(body, "LocalAppWindowLayout.current?.let"), "优先用宿主真值，恰一处")
        assertEquals(
            1,
            count(body, "LocalConfiguration.current.screenWidthDp"),
            "宿主缺席时要回落到系统配置宽度，恰一处：缺了它，预览与单屏夹具会拿到 null 而不是档位",
        )
        // 反空转：只留回落分支（宿主值被忽略）的形状必须不满足「宿主优先」锚。
        val fallbackOnly = "fun currentAppWindowLayout(): AppWindowLayout {\n" +
            "    return AppWindowLayoutPolicy.classify(LocalConfiguration.current.screenWidthDp.toFloat())\n}"
        assertEquals(0, count(fallbackOnly, "LocalAppWindowLayout.current?.let"), "反空转失控：忽略宿主值的形状竟满足宿主优先锚")
    }
}
