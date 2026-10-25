package com.example.englishlearning.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.LocalIsDark

/**
 * Task 6 冻结路径数据的**运行时组装层**：把 `AppIcon.spec` 的两层路径数据 + 当前主题的颜色，
 * 组装成一枚可以交给 `Image` 画的 `ImageVector`。本文件不含任何几何：坐标只来自 `AppIcons.kt`，
 * 这里只做「解析、给画笔、按主题给不透明度」三件事。
 *
 * 为什么是运行时组装而不是直接内嵌静态 `ImageVector`（与计划 Task 3 同一理由）：颜色必须来自
 * `LocalAppColors` / 域色映射 / `LocalIsDark`，而静态 `val` 在类初始化时就把颜色钉死，
 * CompositionLocal 根本进不去——深色模式与七种域色会当场失效，只能再抄一份 hex（全局约束 6 禁止）。
 * 所以每枚字形都在组合期按 `(icon, tint, strokeScale, layer, isDark)` 现算并 `remember`：
 * 输入不变就复用同一个实例，输入一变（翻主题、换域色）就重建。
 *
 * `CircleFill(cx, cy, r)` / `CircleStroke(cx, cy, r)` 是 `AppIcons.kt` 里唯一两种非 SVG path 的形状
 * （视觉稿的 `<circle>` 转录成标记串），由本文件的 [circleFillPathData] / [circleStrokePathData]
 * 解释成**过上下极点的两段绝对圆弧**（`A` 命令）再走同一个解析器；两枚只是所属层不同（填充 / 描边），
 * 几何算法同源，`StrokeOnly` 档因此不再丢失点状识别特征（Task 27 / 裁决 22-1）。
 * 前缀命中却拿不到「有限的圆心 + 正的有限半径」就当场 `require` 抛错并带上原始串——不回落给 SVG 解析器：
 * 回落得到的只是解析器自己的 `Unknown command for: i`（开头的 `C` 被当成立方贝塞尔命令），
 * 消息里既没有字形名也没有坐标，认不出坏在哪一枚圆形，等于把几何错误推迟到运行时才炸。
 * 圆心两轴不要求为正：与画布边相切（`cx` 或 `cy` 为 0）是 24dp 栅格里的正常造型，正数约束只属于半径。
 *
 * 描边重量：视觉描边 2 随 [appImageVector] 的 `strokeScale` 缩放，圆头与圆角连接由渲染端统一给
 * （`StrokeCap.Round` / `StrokeJoin.Round`），路径数据里不写画笔参数——那是 Task 6 的度量基准。
 */

/**
 * 组装时取哪一层。
 *
 * 计划的 Interfaces 只给了「白色两层字形」与「行内单层描边」两种呈现，但同一枚 `appImageVector`
 * 无法同时满足：填充层是否出现是呈现选择，不是字形属性，所以在这里加一枚开关，
 * 默认 [Duotone]（保持计划原文的两层口径），行内徽标显式传 [StrokeOnly]。
 */
enum class AppGlyphLayer {
    /** 填充层（按主题取不透明度）+ 描边骨架层：≥40dp 入口容器的用法。 */
    Duotone,

    /** 只画描边骨架层：30dp 行内徽标的用法（浅底上实心层只会把字形糊成一坨）。 */
    StrokeOnly,
}

/** 标记串前缀（`AppIcons.kt` 的圆形填充写法），本文件是它唯一的解释处。 */
private const val CIRCLE_FILL_PREFIX = "CircleFill("

/**
 * 圆形描边标记的前缀（Task 27 / 裁决 22-1）：`StrokeOnly` 档只画 `strokePaths`，若识别特征（点、旋钮、
 * 圆章）只写在填充层，行内与弱档入口就会整层蒸发。两枚标记的几何**同源**（同一个圆 → 同样的两段弧），
 * 差别只在所属层由 `AppIcons.kt` 决定：`CircleFill` 只进 `fillPaths`，`CircleStroke` 只进 `strokePaths`。
 */
private const val CIRCLE_STROKE_PREFIX = "CircleStroke("

/** 字形画布与视口：24×24 网格，实际缩放交给 `Image` 的尺寸。 */
private const val GLYPH_VIEWPORT_SIZE = 24f

@Composable
fun appImageVector(
    icon: AppIcon,
    tint: Color,
    strokeScale: Float = 1f,
    layer: AppGlyphLayer = AppGlyphLayer.Duotone,
): ImageVector {
    val isDark = LocalIsDark.current
    return remember(icon, tint, strokeScale, layer, isDark) {
        buildAppImageVector(icon = icon, tint = tint, strokeScale = strokeScale, layer = layer, isDark = isDark)
    }
}

/**
 * 一枚图标的渲染出口：装饰性字形（`contentDescription = null`），语义由调用方承载
 * ——入口徽标旁边就有文案，读屏不需要念路径名，念了反而是噪声。
 */
@Composable
fun AppGlyph(
    icon: AppIcon,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    layer: AppGlyphLayer = AppGlyphLayer.Duotone,
    strokeScale: Float = 1f,
) {
    Image(
        imageVector = appImageVector(icon, tint, strokeScale, layer),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

internal fun buildAppImageVector(
    icon: AppIcon,
    tint: Color,
    strokeScale: Float,
    layer: AppGlyphLayer,
    isDark: Boolean,
): ImageVector {
    val spec = icon.spec
    val fillAlpha = DuotoneFillAlpha.forTheme(isDark)
    return ImageVector.Builder(
        name = spec.name,
        defaultWidth = GLYPH_VIEWPORT_SIZE.dp,
        defaultHeight = GLYPH_VIEWPORT_SIZE.dp,
        viewportWidth = GLYPH_VIEWPORT_SIZE,
        viewportHeight = GLYPH_VIEWPORT_SIZE,
    ).group {
        if (layer == AppGlyphLayer.Duotone) {
            spec.fillPaths.forEach { data ->
                addPath(pathData = pathNodesOf(data), fill = SolidColor(tint.copy(alpha = fillAlpha)))
            }
        }
        spec.strokePaths.forEach { data ->
            addPath(
                pathData = pathNodesOf(data),
                stroke = SolidColor(tint),
                strokeLineWidth = 2f * strokeScale,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}

/** 一条路径数据（SVG 串或圆形标记串）→ 解析后的节点表；标记串必须先解释，绝不让原始标记串进解析器。 */
private fun pathNodesOf(data: String): List<PathNode> = PathParser().parsePathString(
    when {
        isCircleFillMarker(data) -> circleFillPathData(data)
        isCircleStrokeMarker(data) -> circleStrokePathData(data)
        else -> data
    }
).toNodes()

/** 标记串判定：前缀命中就归 [circleFillPathData] 解释，不再存在「解析失败就当 SVG 用」的回落。 */
internal fun isCircleFillMarker(path: String): Boolean = path.startsWith(CIRCLE_FILL_PREFIX)

/** 描边侧圆形标记的判定；与 [isCircleFillMarker] 分属两层，前缀互不认（`CircleFill` 混进描边层由契约拦住）。 */
internal fun isCircleStrokeMarker(path: String): Boolean = path.startsWith(CIRCLE_STROKE_PREFIX)

/**
 * `CircleFill(cx, cy, r)` → 过上下极点 `(cx, cy-r)` / `(cx, cy+r)` 的两段绝对圆弧（同一个圆的 path 形）。
 *
 * 坏标记一律 `require` 抛错，消息里带原始串并点明哪一段缺什么条件：静默返回 null 让调用方回落，
 * 等于把「圆形根本没画出来」留给肉眼验收；这里抛错则让任何一枚解释不了的圆形在第一次组装时就暴露。
 *
 * 约束按位置分档：圆心两轴只要求**有限**（`cx` / `cy` 为 0 即与画布边相切，负值即部分出界，都是合法几何），
 * 只有半径（第 3 段）额外要求**为正**——半径为 0 时上下极点重合、两段弧退化成同一个点，画不出圆。
 */
internal fun circleFillPathData(path: String): String = expandCircleMarker(path, CIRCLE_FILL_PREFIX)

/**
 * `CircleStroke(cx, cy, r)` 的解释：与 [circleFillPathData] **逐字同算法**（裁决 22-1 要求两枚标记只是层不同），
 * 故共用一个私有实现；契约用例对同一 triple 比对两侧展开串相等。
 */
internal fun circleStrokePathData(path: String): String = expandCircleMarker(path, CIRCLE_STROKE_PREFIX)

/** 两枚圆形标记的共用展开：校验 → 过上下极点的两段绝对圆弧。 */
private fun expandCircleMarker(path: String, prefix: String): String {
    require(path.startsWith(prefix)) { "不是 $prefix 标记串，无从展开成圆弧：$path" }
    val body = path.removePrefix(prefix)
    require(body.endsWith(")")) { "$prefix 缺少右括号：$path" }
    val values = body.removeSuffix(")")
        .split(',')
        .map { it.trim().toDoubleOrNull() }
    require(values.size == 3 && values.all { it != null }) {
        "$prefix 需要 (cx, cy, 半径) 三个可解析的有限数值（前两段是圆心、第 3 段是半径）：$path"
    }
    val (cx, cy, radius) = values.map { requireNotNull(it) }
    // 「为正」只约束半径：圆心两轴允许 0 或负值（与画布边相切、或部分出界都是合法几何，视口自然裁掉）。
    require(cx.isFinite() && cy.isFinite() && radius.isFinite() && radius > 0.0) {
        "$prefix 的圆心（前两段）必须有限、半径（第 3 段）必须是正的有限数：$path"
    }
    val top = cy - radius
    val bottom = cy + radius
    return "M ${plain(cx)} ${plain(top)}" +
        " A ${plain(radius)} ${plain(radius)} 0 0 1 ${plain(cx)} ${plain(bottom)}" +
        " A ${plain(radius)} ${plain(radius)} 0 0 1 ${plain(cx)} ${plain(top)}"
}

/**
 * 整数就写整数（去掉 `11.0` 这类噪声尾巴），小数按 `Double.toString` 原样输出：
 * 因此小数串可能带浮点尾数、极小值还会落成科学计数法，产物形态不适合做字符串相等断言——
 * 本文件的契约只对「极点数值落在串里」与「解析后节点非空」两条负责。
 */
private fun plain(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()
