package com.example.englishlearning.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 窗口类别三档（批 B，2026-11-06）。
 *
 * 判定面 = **内容宽度**，与 `docs/specs/00-foundation-and-architecture.md` 非功能要求里的
 * 「窗口类别支持矩阵」逐字同源（600 / 840 两枚断点由 `AppWindowLayoutContractTest` 直接读 Spec 原文钉住）。
 *
 * 为什么不引 `androidx.material3:material3-window-size-class`：该 artifact 不在本机 Gradle 缓存里，
 * 而本仓构建必须离线可跑（决策 `docs/decisions/2026-11-06-adaptive-window-layout.md` §2）。
 * 自有分档是纯函数，能在 JVM 单测里把断点两侧穷举到 bit；Material 那枚要跑在设备上才量得出来。
 *
 * `Medium` 与 `Wide` 差别只在**页头与两栏资格**，导航摆法两档一致（都上侧栏）：
 * 600dp 以上的手机横屏与平板在「底栏够不着」这件事上是同一处境。
 */
enum class AppWindowLayout {
    Compact,
    Medium,
    Wide,
}

/**
 * 三档的判定与派生版面。全部纯函数，输入只有宽度（与派生档位），因此不依赖渲染运行时。
 */
object AppWindowLayoutPolicy {

    /** [Medium] 下界：内容宽度 ≥ 600dp。矩阵原文「`600 ≤ w < 840`」。 */
    const val MEDIUM_MIN_WIDTH_DP = 600f

    /** [Wide] 下界：内容宽度 ≥ 840dp。矩阵原文「`≥ 840`」。 */
    const val WIDE_MIN_WIDTH_DP = 840f

    /**
     * 两栏资格的下界高度（批 B-2 消费）：横屏手机的根窗口实测只有 ≈339dp 高
     * （`.git/qa-scratch/b-baseline-landscape-ui.log` 那轮的 `w731dp h339dp` 读数），
     * 宽度落进 Medium 但高度装不下主从两栏，所以两栏必须另吃一条高度门，
     * 本批的导航摆法与内容上限不受它约束。
     */
    const val TWO_COLUMN_MIN_HEIGHT_DP = 480f

    /**
     * 非紧凑档的内容列宽度上限：超过这个宽度，单列卡片与正文会被拉成横条
     * （矩阵「不做」清单里那条「未接入两栏的屏不拉伸成横条」的落点）。
     * 取值是设计判断（Material 可读正文 600dp 的口径放宽到卡片与统计行），不是量出来的数，
     * 最终形态待批 B 截图目视确认。
     */
    const val CONTENT_MAX_WIDTH_DP = 720f

    /** 按内容宽度分档；断点归属与 Material 的 Range 定义一致（下界归高档）。 */
    fun classify(widthDp: Float): AppWindowLayout = when {
        widthDp >= WIDE_MIN_WIDTH_DP -> AppWindowLayout.Wide
        widthDp >= MEDIUM_MIN_WIDTH_DP -> AppWindowLayout.Medium
        else -> AppWindowLayout.Compact
    }

    /**
     * [BoxWithConstraints] 直接给的 `Dp` 走这枚重载：宿主层因此不必自己 `.value`，
     * 也不会有人顺手把 `Density` 换算混进「按 dp 分档」这条口径里（判定面只有 dp，像素不参与）。
     */
    fun classify(widthDp: Dp): AppWindowLayout = classify(widthDp.value)

    /** 侧边导航栏资格：非紧凑档一律换掉底栏，两档不做第二套导航语义。 */
    fun usesSideNavigation(layout: AppWindowLayout): Boolean = layout != AppWindowLayout.Compact

    /** 页头降级资格：只有 [Wide]（平板/折叠展开）把 hero 页头降成普通页头，横屏手机不降。 */
    fun downgradesHeroHeader(layout: AppWindowLayout): Boolean = layout == AppWindowLayout.Wide

    /**
     * 内容列上限：紧凑档返回 `Dp.Infinity`（= 不封顶，与今天的行为逐字等价，宿主层因此不需要再写一条 if），
     * 其余两档收进 [CONTENT_MAX_WIDTH_DP]。
     */
    fun contentMaxWidth(layout: AppWindowLayout): Dp =
        if (usesSideNavigation(layout)) CONTENT_MAX_WIDTH_DP.dp else Dp.Infinity
}

/**
 * 宿主层算出来的窗口类别。默认 `null` = 「没人提供，按 [LocalConfiguration] 现算」，
 * 这样单屏预览与既有 connected 夹具不必各自补一道 provider 也能拿到合理档位。
 */
val LocalAppWindowLayout = compositionLocalOf<AppWindowLayout?> { null }

/**
 * 当前窗口类别：优先用宿主 [BoxWithConstraints] 给的真值（能反映分屏与折叠的实时约束），
 * 没有 provider 时回落到 `LocalConfiguration.screenWidthDp`。
 *
 * 读点收口纪律：只许 [AppScreen] 的 tab 宿主层与 [com.example.englishlearning.ui.components.AppScreenHeader]
 * 两处消费，屏侧一律不读——由 `AppWindowLayoutContractTest` 的白名单闸钉住。
 */
@Composable
@ReadOnlyComposable
fun currentAppWindowLayout(): AppWindowLayout {
    LocalAppWindowLayout.current?.let { return it }
    return AppWindowLayoutPolicy.classify(LocalConfiguration.current.screenWidthDp.toFloat())
}
