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
 * `CircleFill(cx, cy, r)` 是 `AppIcons.kt` 里唯一非 SVG path 的形状（视觉稿的 `<circle>` 转录成标记串），
 * 由本文件的 [circleFillPathData] 解释成两段首尾相接的圆弧，再走同一个解析器；
 * 圆弧的写法（`0 0 1` 大弧/顺时针标志）与 `AppIcons.kt` 里圆环描边用的 idiom 同形，不改坐标数值。
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
        buildGlyphVector(icon = icon, tint = tint, strokeScale = strokeScale, layer = layer, isDark = isDark)
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

private fun buildGlyphVector(
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

/** 一条路径数据（SVG 串或圆形标记串）→ 解析后的节点表。 */
private fun pathNodesOf(data: String): List<PathNode> =
    PathParser().parsePathString(circleFillPathData(data) ?: data).toNodes()

/**
 * `CircleFill(cx, cy, r)` → 两段首尾相接的半圆弧（同一个圆，视觉稿 `<circle>` 的 path 形）；
 * 不是这个形状就返回 null，交给 SVG path 解析器。
 */
private fun circleFillPathData(path: String): String? {
    if (!path.startsWith(CIRCLE_FILL_PREFIX) || !path.endsWith(")")) return null
    val values = path.removePrefix(CIRCLE_FILL_PREFIX).removeSuffix(")")
        .split(',')
        .map { it.trim().toDoubleOrNull() }
    if (values.size != 3 || values.any { it == null }) return null
    val (cx, cy, radius) = values.map { requireNotNull(it) }
    val left = cx - radius
    val right = cx + radius
    return "M ${plain(left)} ${plain(cy)}" +
        " a ${plain(radius)} ${plain(radius)} 0 0 1 ${plain(right - left)} 0" +
        " a ${plain(radius)} ${plain(radius)} 0 0 1 ${plain(left - right)} 0"
}

/** 整数就写整数、小数按原样写：避免 `11.0` 这种噪声，也避免极小值被写成科学计数法。 */
private fun plain(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()
