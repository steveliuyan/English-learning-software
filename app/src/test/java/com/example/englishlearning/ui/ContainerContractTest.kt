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
     * 用 map 而不是逐屏写死：批 1 余下两屏（我的 / 每日阅读）落地时只在这里加一行。
     */
    private val screens: Map<String, String> by lazy {
        listOf("TodayPlanScreen.kt", "WordBankScreen.kt")
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
        assertTrue(
            "colors.strongSurface" in containers,
            "强档卡底色必须取 appColors().strongSurface，不许回退到 surface + 描边",
        )
        assertEquals(
            0,
            countMatches(containers, Regex("""\bCard\(""")),
            "Containers.kt 不得退回 M3 Card：三档容器就是替代它的那一层",
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
