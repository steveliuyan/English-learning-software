package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * m01676 三条诉求的源码契约（评分条位置 / 详情页「下一个单词」退役 / 动效补齐）。
 *
 * 三条的落点全是**形状**，不是观感，所以都能在 JVM 侧按源码文本钉住：
 *
 *  1. **吸底槽是工具条，且换的是槽里的内容而不是整页**：`WordCardScreen.kt` 里恰好两枚
 *     `AnimatedVisibility`，可见性由 `receipt` 双向互斥决定，进出场走 `AppTransition.FooterSwapEnter/Exit`
 *     （只淡不位移）。两枚必须住在 [WordCardFooterSlot] 这枚**顶层** composable 里：写在 `Column` 的内容里，
 *     Kotlin 会解析到 `ColumnScope.AnimatedVisibility` 扩展重载，并判「外层隐式接收者不允许隐式调用」，
 *     编译当场报错（本轮实测到的就是这个错）。
 *  2. **详情页没有「下一个单词」**：学习流宿主的 `CardDetailScreen(...)` 调用传 `onNext = null`，
 *     且 `WordCardViewModel` 里 `hasNextDetail()` / `nextDetail()` 一并退役（留方法就是给「跳过评分
 *     直接看下一张释义」留后门）。
 *  3. **浮层是叠上来的第二层**：`AppScreen.kt` 的详情宿主包在 `AnimatedVisibility(enter = OverlayRiseEnter,
 *     exit = OverlayRiseExit)` 里，且退场期间仍画**上一张**卡（`overlayCard` 记住最后一次非空值），
 *     否则退场那 300ms 读的 `detailCard` 已经是 null，画面会先塌成空再消失。
 *
 * 反空转：每条判据都配一枚合成违例样本（内存假源码，不碰生产文件），形状改名/判据写反时必须数出 0。
 */
class DetailOverlayAndFooterSwapContractTest {

    private val wordCardSource = uiScreenSource("WordCardScreen.kt")

    private val appScreenSource = uiScreenSource("AppScreen.kt")

    private val transitionSource = stripComments(File(APP_TRANSITION_SOURCE_PATH).readText())

    private val viewModelSource = stripComments(File(WORD_CARD_VIEW_MODEL_PATH).readText())

    // ---------- 1. 吸底槽 ----------

    /** 两枚互斥 `AnimatedVisibility`，各用 `FooterSwap*` 规格（`receipt` 一直是判据）。 */
    private val footerSwapBar = Regex(
        """AnimatedVisibility\(\s*visible = receipt (?:!=|==) null,\s*enter = AppTransition\.FooterSwapEnter,""" +
            """\s*exit = AppTransition\.FooterSwapExit,\s*\)""",
    )

    @Test
    fun footerSlotSwapsTwoMutuallyExclusiveBars() {
        assertEquals(
            2,
            footerSwapBar.findAll(wordCardSource).count(),
            "吸底槽应是两枚互斥的 `AnimatedVisibility`（回执 / 反馈各一支），两支都必须走 FooterSwap 规格；" +
                "实际命中不是 2 枚 ⇒ 槽被拆回多支、或被改成别的动效规格",
        )
        assertEquals(
            2,
            Regex("""AnimatedVisibility\(""").findAll(wordCardSource).count(),
            "`WordCardScreen.kt` 只应有两枚 `AnimatedVisibility`（都在吸底槽）；多出来的会与页面转场打架",
        )
        assertTrue(
            wordCardSource.contains("visible = receipt != null") &&
                wordCardSource.contains("visible = receipt == null"),
            "两支的可见性必须是 `receipt` 的正反两面，否则淡出窗口里两支会同时可见（同一条回执上写第二个事件的门就开了）",
        )
    }

    @Test
    fun footerSlotLivesOutsideColumnScope() {
        val declaration = Regex("""@Composable\s*private fun WordCardFooterSlot\(""").find(wordCardSource)
        assertTrue(declaration != null, "吸底槽必须是一枚顶层 `@Composable private fun WordCardFooterSlot(`")
        val body = wordCardSource.substring(declaration!!.range.first)
        assertEquals(
            2,
            Regex("""AnimatedVisibility\(""").findAll(body).count(),
            "两枚 `AnimatedVisibility` 都要住在 `WordCardFooterSlot` 体内（写在 `Column` 内容 lambda 里会解析到" +
                "`ColumnScope` 重载，判「外层隐式接收者不允许隐式调用」而编译失败）",
        )
        assertEquals(
            0,
            Regex("""ColumnScope\s*\.\s*AnimatedVisibility""").findAll(wordCardSource).count(),
            "不得显式调 `ColumnScope.AnimatedVisibility`：那正是「换槽」被读成「加了一行」的形状",
        )
        // 反空转：`ColumnScope` 形态必须能被同一条正则数出来
        assertEquals(
            1,
            Regex("""ColumnScope\s*\.\s*AnimatedVisibility""")
                .findAll("ColumnScope.AnimatedVisibility(visible = x) { }")
                .count(),
            "`ColumnScope.AnimatedVisibility` 的负则正则必须仍能命中（否则「0 处」是空转）",
        )
        // 反空转：槽体写成 `Column` 直接子节点时，`AnimatedVisibility` 就不在 `WordCardFooterSlot(` 之后
        val badSample = """
            @Composable
            private fun WordCardFooterSlot(state: Int) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("x")
                }
            }
        """.trimIndent()
        assertEquals(
            0,
            Regex("""AnimatedVisibility\(""").findAll(badSample).count(),
            "槽体没搬动时判据必须数出 0（否则本契约对「搬没搬」不敏感）",
        )
    }

    // ---------- 2. 详情页没有「下一个单词」 ----------

    @Test
    fun learningFlowDetailOffersNoNextStep() {
        val anchor = appScreenSource.indexOf("visible = detailCard != null")
        assertTrue(anchor >= 0, "学习流详情宿主必须由 `AnimatedVisibility(visible = detailCard != null)` 承起来")
        val host = appScreenSource.substring(anchor, minOf(appScreenSource.length, anchor + 4000))
        assertTrue(
            host.contains("onNext = null"),
            "学习流详情宿主必须传 `onNext = null`（m01676：点模糊之后不得再点「下一个单词」）",
        )
        assertTrue(
            !host.contains("hasNextDetail") && !host.contains("nextDetail"),
            "宿主里不得再引用 `hasNextDetail()` / `nextDetail()`：那正是「跳过评分直接翻下一张」的后门",
        )
        assertEquals(
            0,
            Regex("""fun\s+(hasNextDetail|nextDetail)\s*\(""").findAll(viewModelSource).count(),
            "`WordCardViewModel` 的 `hasNextDetail()` / `nextDetail()` 必须已退役（留着就有调用点会长回来）",
        )
        // 反空转：同一条正则在合成样本上必须命中
        assertEquals(
            1,
            Regex("""fun\s+(hasNextDetail|nextDetail)\s*\(""").findAll("    fun nextDetail() {}").count(),
            "退役方法名的正则必须仍能命中（否则「0 处」是空转）",
        )
    }

    // ---------- 3. 浮层升起 ----------

    @Test
    fun detailOverlayRisesInAndKeepsLastCardForExit() {
        val anchor = appScreenSource.indexOf("visible = detailCard != null")
        assertTrue(anchor >= 0, "详情宿主必须由 `AnimatedVisibility(visible = detailCard != null)` 承起来")
        val host = appScreenSource.substring(maxOf(0, anchor - 1500), minOf(appScreenSource.length, anchor + 4000))
        assertTrue(
            host.contains("enter = AppTransition.OverlayRiseEnter") &&
                host.contains("exit = AppTransition.OverlayRiseExit"),
            "浮层进出场要走 `AppTransition.OverlayRiseEnter/Exit`（淡入 + 竖直 ⅛），不得裸 fade 或复用整页转场",
        )
        assertTrue(
            Regex("""LaunchedEffect\(detailCard\)\s*\{\s*detailCard\?\.let\s*\{\s*overlayCard = it\s*}""")
                .containsMatchIn(appScreenSource),
            "退场期间要画「上一张卡」：必须用 `overlayCard` 记住最后一次非空 `detailCard`，" +
                "否则读的是已经归 null 的 `detailCard`，浮层会先塌空再消失",
        )
        assertEquals(
            2,
            Regex("""overlayCard === card""").findAll(appScreenSource).count(),
            "两处发音代次闸都要改读 `overlayCard`（读 `detailCard` 的话，退场那 300ms 里点「播放发音」会被自己的代次闸吞掉）",
        )
    }

    // ---------- 4. 两套新规格 ----------

    @Test
    fun newTransitionsUseTokenEasingAndQuarterEighthOffsets() {
        assertEquals(
            1,
            Regex("""private const val OverlayOffsetDenominator = 8""").findAll(transitionSource).count(),
            "浮层竖直位移要有独立的 ⅛ 分母（页面那套水平 ¼ 是 PageOffsetDenominator）",
        )
        listOf(
            "val FooterSwapEnter = fadeIn(tween(AppMotion.Normal, easing = AppMotion.EasingOut))",
            "val FooterSwapExit = fadeOut(tween(AppMotion.Normal, easing = AppMotion.EasingIn))",
            "slideInVertically(tween(AppMotion.Slow, easing = AppMotion.EasingOut)) { it / OverlayOffsetDenominator }",
            "slideOutVertically(tween(AppMotion.Slow, easing = AppMotion.EasingIn)) { it / OverlayOffsetDenominator }",
        ).forEach { shape ->
            assertTrue(transitionSource.contains(shape), "`AppTransition.kt` 缺少规格：$shape")
        }
        assertEquals(
            2,
            Regex("""/ OverlayOffsetDenominator""").findAll(transitionSource).count(),
            "竖直位移必须进/出各一处（一处多出来说明有第三支在借这个分母）",
        )
    }

    // ---------- 5. 返回键：先关详情，再退学习流（m01889） ----------

    /**
     * 详情页必须自己有一条返回键处理器，且声明在学习流那条**之后**（本文件「后声明者优先」）。
     *
     * 缺这一条时真机上的表现是：在「词义详情」按返回，详情与整条学习流一起被收掉，用户回到首页、
     * 再进流已经翻过一张——详情明明只压了一层，返回却退了两层。
     */
    @Test
    fun learningDetailClosesItselfBeforeTheFlowExits() {
        val code = stripComments(appScreenSource)
        fun hitsIn(text: String, needle: String): Int = text.split(needle).size - 1

        assertEquals(
            1,
            hitsIn(code, "val learningDetailCard by wordCardViewModel.detailCard.collectAsState()"),
            "必须在宿主层收回这枚详情状态；只在学习分支里收，外面的返回键处理器就无从判断",
        )
        val handler = "BackHandler(enabled = learningDetailCard != null) { closeLearningDetail() }"
        assertEquals(
            1,
            hitsIn(code, handler),
            "缺了这枚处理器 ⇒ 返回键穿透到 exitLearning()，详情连整条学习流一起退",
        )

        val flowHandler = code.indexOf("BackHandler(enabled = showLearning)")
        val detailHandler = code.indexOf("BackHandler(enabled = learningDetailCard != null)")
        val qaHandler = code.indexOf("BackHandler(enabled = showWordQaCard != null)")
        assertTrue(flowHandler > 0 && detailHandler > flowHandler, "详情那条必须声明在学习流之后（后声明者优先）")
        assertTrue(qaHandler > detailHandler, "词 AI 问答是更内一层，必须仍压在详情之上")

        assertEquals(
            1,
            hitsIn(code, "val closeLearningDetail: () -> Unit = {"),
            "清场闭包只该有一枚（多出一枚＝两份清场逻辑迟早分叉）",
        )
        assertTrue(
            code.contains("onBack = closeLearningDetail,"),
            "详情的 onBack 必须指向同一枚闭包，不能自己再写一遍 clearDetail()",
        )

        val withoutHandler = code.replace(handler, "")
        assertEquals(
            0,
            hitsIn(withoutHandler, "BackHandler(enabled = learningDetailCard != null)"),
            "反空转：摘掉处理器后同一判据必须数出 0（判据写空才会数不出来）",
        )
    }
}

private const val APP_TRANSITION_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui/theme/AppTransition.kt"

private const val WORD_CARD_VIEW_MODEL_PATH = "src/main/java/com/example/englishlearning/ui/WordCardViewModel.kt"

