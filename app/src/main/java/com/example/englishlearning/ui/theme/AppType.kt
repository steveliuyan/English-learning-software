package com.example.englishlearning.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字体阶梯：系统 sans（不引入字体文件），层级靠字号/字重/字距。
 *
 * ## 六档的墨：组合期读当前主题（批 3 Task 26 排印墨色族收口）
 *
 * 批 1–批 2 时期六档各自带一枚浅套静态墨（`AppPalette.TextPrimary` / `TextSecondary`），
 * 于是 `style = AppType.X` 的**直读**在深色档把近黑墨压到近黑底上。现在六档改成
 * `@Composable @ReadOnlyComposable` getter，墨由 `appColors().text1`（Display/Headline/Title/Body）
 * 与 `appColors().text2`（Footnote/Label）当场读取；浅套取值与旧静态值逐字相同，深套跟着换套。
 *
 * spec §2.2 只钉字号/字重/字距与 M3 角色对位，从未钉 `color`；这些钉值原样留在
 * [DisplaySpec] 等私有度量表里（`MaterialThemeAssemblyContractTest` 逐档钉住），摘色不构成规格改动。
 */
object AppType {
    val Display: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = DisplaySpec.copy(color = appColors().text1)

    val Headline: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = HeadlineSpec.copy(color = appColors().text1)

    val Title: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TitleSpec.copy(color = appColors().text1)

    val Body: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = BodySpec.copy(color = appColors().text1)

    val Footnote: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = FootnoteSpec.copy(color = appColors().text2)

    val Label: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LabelSpec.copy(color = appColors().text2)
}

/** spec §2.2 钉值：字号/字重/字距。墨不在这里，见 [AppType] 的 getter。 */
private val DisplaySpec = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum")
private val HeadlineSpec = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
private val TitleSpec = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
private val BodySpec = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal)
private val FootnoteSpec = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
private val LabelSpec = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)

/**
 * 装进 M3 `Typography` 前必须摘掉六档自带的墨：即便它已跟着主题换套，M3 角色的前景仍应由
 * `LocalContentColor` 决定——`Surface` / `Scaffold` 等容器会按 `contentColorFor` 覆盖一层，
 * 让面板/按钮上的排印跟着容器内容色走；不摘色时角色自带墨会压过容器语义。
 * 而 `MintGlassTheme` 已把根 `LocalContentColor` 提供为当前套的 `text1`，深浅两套各自成立。
 *
 * 六档声明本身不动：`AppType.*` 的直接读点仍靠 getter 当场取主题墨。
 */
private fun TextStyle.neutralForTheme(): TextStyle = copy(color = Color.Unspecified)

/**
 * 六档语义 → 十五个 M3 排印角色（spec §2.2 钉住六档；其余角色按最近字号从同六档派生，
 * 不引新字号、不混档改字重）。material3 新增的 `*Emphasized` 角色不在这里覆盖，取库内默认。
 *
 * 可见变化（核验落点：批 1 收口真机清单，Task 10）：
 * 1. 字号/字重换档：`titleLarge` 22→24sp Bold、`titleMedium` 14→17sp SemiBold、`bodySmall` 12→13sp、
 *    `labelLarge` 14→12sp Medium、`titleSmall` 14→15sp；除 `AppType.*` 直读点外，
 *    全部 `MaterialTheme.typography.*` 读点跟着换档。
 * 2. 库内每角色行高消失：六档不写 `lineHeight`，去色装配后行高回落到字体度量默认值，正文观感会变紧。
 *    本轮不擅自新增 15 个 `lineHeight` 取值（spec §2.2 只钉六档）；若真机核验判「太紧」，
 *    由批 2 任务在装配点加 `lineHeight` 并把取值写进 spec，不许在此静塞。
 * 3. 全部 M3 控件的默认色从 material3 库默认（紫色系）换成薄荷装配值：这既是目的也是可见改动，
 *    全部 `MaterialTheme.colorScheme.*` 直读点同样受影响。
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
