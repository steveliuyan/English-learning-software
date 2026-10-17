package com.example.englishlearning.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 字体阶梯：系统 sans（不引入字体文件），层级靠字号/字重/字距。 */
object AppType {
    val Display = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = AppPalette.TextPrimary)
    val Headline = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = AppPalette.TextPrimary)
    val Title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppPalette.TextPrimary)
    val Body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = AppPalette.TextPrimary)
    val Footnote = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = AppPalette.TextSecondary)
    val Label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp, color = AppPalette.TextSecondary)
}

/**
 * 装进 M3 `Typography` 前必须摘掉六档自带的静态墨：`AppType` 各档的 `color` 是
 * `AppPalette.TextPrimary` / `TextSecondary`（浅套硬编码值），原样装配会让全部
 * `MaterialTheme.typography.*` 读点在深色模式下也带着近黑色，深色文字压深色底。摘色后前景由
 * `LocalContentColor` 决定，而它由 [MintGlassTheme] 装配的 `ColorScheme` 供给，深浅两套各自成立。
 *
 * 六档声明本身不动：`AppType.*` 的直接读点仍靠这些颜色取值，摘色只发生在装配路径上。
 */
private fun TextStyle.neutralForTheme(): TextStyle = copy(color = Color.Unspecified)

/**
 * 六档语义 → 十五个 M3 排印角色（spec §2.2 钉住六档；其余角色按最近字号从同六档派生，
 * 不引新字号、不混档改字重）。material3 新增的 `*Emphasized` 角色不在这里覆盖，取库内默认。
 *
 * 可见变化（真机核验清单，批 1 收口）：`titleLarge` 22→24sp Bold、`titleMedium` 14→17sp SemiBold、
 * `bodySmall` 12→13sp、`labelLarge` 14→12sp Medium、`titleSmall` 14→15sp。
 */
@Composable
fun appTypography(): Typography {
    return Typography(
        displayLarge = AppType.Display.neutralForTheme(),
        displayMedium = AppType.Display.neutralForTheme(),
        displaySmall = AppType.Display.neutralForTheme(),
        headlineLarge = AppType.Headline.neutralForTheme(),
        headlineMedium = AppType.Headline.neutralForTheme(),
        headlineSmall = AppType.Headline.neutralForTheme(),
        titleLarge = AppType.Headline.neutralForTheme(),
        titleMedium = AppType.Title.neutralForTheme(),
        titleSmall = AppType.Body.neutralForTheme(),
        bodyLarge = AppType.Body.neutralForTheme(),
        bodyMedium = AppType.Body.neutralForTheme(),
        bodySmall = AppType.Footnote.neutralForTheme(),
        labelLarge = AppType.Label.neutralForTheme(),
        labelMedium = AppType.Label.neutralForTheme(),
        labelSmall = AppType.Label.neutralForTheme(),
    )
}
