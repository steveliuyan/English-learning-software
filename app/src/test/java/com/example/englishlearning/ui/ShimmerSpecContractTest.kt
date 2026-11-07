package com.example.englishlearning.ui

import com.example.englishlearning.ui.components.ShimmerSpec
import com.example.englishlearning.ui.theme.AppMotion
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 批 C-1 shimmer 规格契约（判据：`docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` 决定 2）。
 *
 * 这一族要证的是**一句实测结论的可设计化**。批 A 的字节码 + 设备实测（emulator-5554）证明了两件事：
 * Compose 自己遵守 `animator_duration_scale`，而**循环动画在 `scale=0` 时会落在一个周期的末尾值上然后不再要帧**
 * （`infiniteRepeatable(tween(600))` 在 scale=0 的 2.5s 窗口只观测到 3 次重组，对照 scale=1 的 150 次）。
 * 于是 shimmer 的设计对象不是「动起来好不好看」，而是「**末尾那一刻画面是什么**」：
 * 末尾如果停在半亮，用户关掉系统动画就得到一张永久糊在一半亮度的全站骨架——那是缺陷，不是降级。
 *
 * 所以本契约把相位→外观的换算抽成纯函数（`ShimmerSpec.alphaAt` / `centerAt`），当场钉三条：
 *  - **R1 定格即静息**：`alphaAt(0f) == 0f` 且 `alphaAt(1f) == 0f`（周期首末同值 ⇒ 既不会定格成半亮，
 *    循环重启时也不会跳一下）；中段峰值必须 > 0，否则判据可以被一份「全程无光」的实现绿糊弄过去。
 *  - **R2 行程完全出画**：末相位的**光带前缘**必须已经越过右边界（用带宽算，不是只看中心点），
 *    这样「alpha 归零」与「几何出画」两件事同时成立，定格画面与静息骨架逐像素等同。
 *  - **R3 时长只由 token 组成**：周期与停格时刻都必须是 `AppMotion` 的算式，
 *    且**末尾 1/4 周期就是停格段**（`cycleMillis - travelMillis == AppMotion.Slow`）；
 *    批 A 已删的两枚换算入口（`durationMs` / `animatorDurationScale`）不得在这里接回——
 *    平台已经乘过一次，我们再乘一次就是双重缩放（scale=0.5 会变 0.25）。
 *
 * 与批 A 那份限制的分工（`MotionSpecContractTest` 已知局限第 2 条写的是「定格值不可源码判定，归截图取证」）：
 * 本批把换算抽成纯函数之后，**相位语义**这一层改成可单测判定，截图只负责观感（光泽强弱、色带是否过曝）。
 * 两者不是同一件事，别把截图册那一步当已经做过。
 *
 * 形状沿用禁令族惯例：覆盖面锚点 + 合成双边（违例形状必须被命中、合规形状必须被放行）+ 真实文件零 offender。
 */
class ShimmerSpecContractTest {

    private fun offenders(fileName: String, source: String): List<String> {
        val stripped = stripComments(source)
        val found = mutableListOf<String>()
        rawColorLiteral.findAll(stripped).forEach {
            found += "$fileName: 裸 hex ${it.value}——光带取色只许走 appColors() 族（tint / outlineSoft）"
        }
        listOf("durationMs", "animatorDurationScale").forEach { helper ->
            val hits = stripped.lines().count { it.contains(helper) }
            if (hits > 0) found += "$fileName: 出现 $helper ×$hits——批 A R1，平台已按 animator_duration_scale 缩放，不得二次换算"
        }
        val textNodes = countMatches(stripped, Regex("""(?<![A-Za-z0-9_])Text\("""))
        if (textNodes > 0) found += "$fileName: 出现 Text( ×$textNodes——光带是修饰，不许承载文案或信息"
        val bareCycle = Regex("""durationMillis\s*=\s*\d+""").findAll(stripped).toList()
        if (bareCycle.isNotEmpty()) {
            found += "$fileName: ${bareCycle.size} 处把时长写成裸数字——周期必须是 AppMotion token 的算式（R3）"
        }
        return found
    }

    @Test
    fun `rule flags the four ways a shimmer spec can be written wrong`() {
        val violating = """
            val spec = keyframes<Float> {
                durationMillis = 1200
            }
            @Composable fun ShimmerBox() {
                Text("加载中")
                val c = Color(0x33FFFFFF)
                val d = AppMotion.durationMs(AppMotion.Slow, animatorDurationScale(context))
            }
        """.trimIndent()
        val found = offenders("FakeShimmer.kt", violating)
        assertTrue(found.any { it.contains("裸 hex") }, "裸 hex 未被命中：${found.joinToString()}")
        assertTrue(found.any { it.contains("durationMs") }, "换算入口未被命中：${found.joinToString()}")
        assertTrue(found.any { it.contains("Text(") }, "文案节点未被命中：${found.joinToString()}")
        assertTrue(found.any { it.contains("裸数字") }, "裸时长未被命中：${found.joinToString()}")
        val compliant = """
            // 反例存档：不许 Color(0x…)、不许 durationMs / animatorDurationScale、骨架里不许 Text。
            val cycleMillis = AppMotion.Slow * 4
            val travelMillis = AppMotion.Slow * 3
        """.trimIndent()
        assertTrue(offenders("CompliantShimmer.kt", compliant).isEmpty(), "合规形状被误判：${offenders("CompliantShimmer.kt", compliant).joinToString()}")
    }

    @Test
    fun `production shimmer and skeleton sources carry no offenders today`() {
        listOf("Shimmer.kt", "Skeleton.kt").forEach { name ->
            assertTrue(offenders(name, uiComponentSource(name)).isEmpty(), "$name 有 shimmer 规格违例")
        }
        // 覆盖面锚点：光带真正被骨架用上才算铺面，只有规格文件等于没接。
        assertTrue(
            countMatches(uiComponentSource("Skeleton.kt"), Regex("""skeletonShimmer\(""")) >= 1,
            "Skeleton.kt 里没有任何 `skeletonShimmer(` 调用：规格齐了但骨架没接上微光，等于批 C-1 只做了一半。",
        )
    }

    @Test
    fun `cycle ends at the resting appearance so scale zero freezes on the static skeleton`() {
        // R1：首末相位零光泽。scale=0 时循环动画停在**周期末尾值**（批 A 实测），
        // 末尾等于静息，用户看到的就是一张设计内静态骨架；两端同值同时保证循环重启不跳。
        assertTrue(
            abs(ShimmerSpec.alphaAt(0f)) < 1e-6f,
            "相位 0（循环重启位）的光泽不是 0：${ShimmerSpec.alphaAt(0f)}——重启瞬间会闪一下",
        )
        assertTrue(
            abs(ShimmerSpec.alphaAt(1f)) < 1e-6f,
            "相位 1（周期末尾值，即 scale=0 的定格位）的光泽不是 0：${ShimmerSpec.alphaAt(1f)}——" +
                "关掉系统动画的用户会得到一张永久半亮的骨架，那是缺陷不是降级",
        )
        // 中段必须有光：否则「首末为 0」可以被一份全程无光的实现白拿。
        assertTrue(
            ShimmerSpec.alphaAt(0.5f) > 0f,
            "周期中点光泽为 0：这条 shimmer 根本不会亮，判据成了空转",
        )
        // 扫光必须真的走一趟：单调升再单调降（中点两侧对称档比较）。
        assertTrue(
            ShimmerSpec.alphaAt(0.25f) < ShimmerSpec.alphaAt(0.5f) &&
                ShimmerSpec.alphaAt(0.75f) < ShimmerSpec.alphaAt(0.5f),
            "光泽在周期中点两侧不对称升起/落下：不是扫光而是整块呼吸",
        )
    }

    @Test
    fun `band leaves the frame completely at the end of the travel`() {
        // R2：定格画面与静息骨架逐像素等同——末相位连光带**前缘**都要越过右边界，
        // 只看中心点越过不够（带宽的一半还留在画面里，就是一条静态亮边）。
        val halfBand = ShimmerSpec.bandFraction / 2f
        val leadingEdge = ShimmerSpec.centerAt(1f) - halfBand
        val trailingEdge = ShimmerSpec.centerAt(0f) + halfBand
        assertTrue(
            leadingEdge >= 1f,
            "末相位光带前缘还在画面内（相对右边界 $leadingEdge < 1f）：定格会留下一条亮边",
        )
        assertTrue(
            trailingEdge <= 0f,
            "起相位光带后缘还在画面内（相对左边界 $trailingEdge > 0f）：循环重启时左边会先亮一下",
        )
        assertTrue(
            ShimmerSpec.centerAt(1f) > ShimmerSpec.centerAt(0f),
            "中心点不随相位前进：光带不会移动，微光是死的",
        )
    }

    @Test
    fun `cycle is composed from motion tokens and holds a resting quarter`() {
        // R3：时长只由 token 组成，且末尾 1/4 周期是停格段（静息位）。
        assertTrue(
            ShimmerSpec.cycleMillis == AppMotion.Slow * 4,
            "周期应等于 `AppMotion.Slow * 4`，实际 ${ShimmerSpec.cycleMillis}——" +
                "裸数字或改token都算范围变化，要先进决策册",
        )
        assertTrue(
            ShimmerSpec.cycleMillis - ShimmerSpec.travelMillis == AppMotion.Slow,
            "停格段应恰为一枚 `AppMotion.Slow`（周期的 1/4），实际 " +
                "${ShimmerSpec.cycleMillis - ShimmerSpec.travelMillis}——停格段就是 scale=0 的定格时间窗，" +
                "它变了等于改了静息比例",
        )
        assertTrue(
            ShimmerSpec.travelMillis in 1 until ShimmerSpec.cycleMillis,
            "行程时刻必须落在周期内：travel=${ShimmerSpec.travelMillis} cycle=${ShimmerSpec.cycleMillis}",
        )
    }

    @Test
    fun `shimmer is drawn as a gradient band, not a second layer of content`() {
        val source = uiComponentSource("Shimmer.kt")
        listOf(
            "rememberInfiniteTransition" to "循环相位必须由 infiniteTransition 驱动（Compose 才会吃 animator_duration_scale）",
            "infiniteRepeatable" to "周期必须无限重复；写成一次性动画就不是微光",
            "keyframes" to "必须用 keyframes 留出末尾停格段（tween 到点即重启，末值会落在峰值）",
            "drawBehind" to "光带必须是绘制层的修饰，不能是额外的内容节点",
            "AppMotion.EasingOut" to "缓动只许引 AppMotion token（批 A R3：裸 tween 会吃 Compose 默认曲线）",
        ).forEach { (anchor, why) ->
            assertTrue(
                countMatches(source, Regex(Regex.escape(anchor))) >= 1,
                "Shimmer.kt 缺锚 `$anchor`：$why",
            )
        }
        // 排期必须**用同一份纯函数常量**：光有 alphaAt/centerAt 说得对不够，keyframes 若把行程与周期
        // 写成别处的字面量，scale=0 的定格值就不再由 ShimmerSpec 决定，R1/R3 两条判据会对着
        // 一份没人执行的表绿。
        listOf(
            "durationMillis = ShimmerSpec.cycleMillis" to "周期长度取自 ShimmerSpec.cycleMillis",
            "at ShimmerSpec.travelMillis" to "行程时刻取自 ShimmerSpec.travelMillis",
            "at ShimmerSpec.cycleMillis" to "停格终点取自 ShimmerSpec.cycleMillis",
            "initialValue = 0f" to "相位区间必须从 0f 起",
            "targetValue = 1f" to "相位区间必须到 1f 止——定格值就是这个 target，" +
                "写成 0.5f 之类会让 scale=0 的用户看到一张永久半亮的骨架，而纯函数表对此一无所知",
        ).forEach { (anchor, why) ->
            assertTrue(
                countMatches(source, Regex(Regex.escape(anchor))) >= 1,
                "Shimmer.kt 的 keyframes 没引用 `$anchor`：$why——写死数字等于让定格值脱离纯函数表",
            )
        }
    }
}
