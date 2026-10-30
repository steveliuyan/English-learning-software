package com.example.englishlearning.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 原型对齐批 0 的排印阶梯（原型 `assets/app.css` 字号/字重/字距/行高）。
 *
 * ## 字族
 * 英文标题、词形、数字走 [AppFont.Serif]（Newsreader）；正文/UI 走系统 sans。
 * 原型 `--serif` 只给 `.word-lemma`、`.strong-title`、`.article-title`、`.prose`、`.stat-num`
 * 等英文强调位，不铺给中文正文。
 *
 * ## 行高
 * 2026-10-29 起按原型写入行高（正文 1.6、标题 1.25、词形 1.05、文章 1.72、中文 1.75）；
 * 批 0 之前「六档不写 lineHeight」的旧口径作废，依据见
 * `docs/decisions/2026-10-29-ui-prototype-token-alignment.md` §4。
 *
 * ## 墨
 * 六档 + 新增档都是 `@Composable @ReadOnlyComposable` getter，墨按原型层级读当前主题：
 * 主文 `text1`、辅助 `text2`、三级/小字 `text3`。装进 M3 `Typography` 前摘色，
 * 由 `LocalContentColor` 决定容器内前景。
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

    val Eyebrow: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = EyebrowSpec.copy(color = appColors().text3)

    val Caption: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = CaptionSpec.copy(color = appColors().text3)

    val Meta: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = MetaSpec.copy(color = appColors().text2)

    val BodyLg: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = BodyLgSpec.copy(color = appColors().text1)

    val Lemma: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LemmaSpec.copy(color = appColors().text1)

    val StatNum: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = StatNumSpec.copy(color = appColors().text1)

    val PlanNum: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = PlanNumSpec.copy(color = appColors().text1)

    val Prose: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = ProseSpec.copy(color = appColors().text1)

    val ProseZh: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = ProseZhSpec.copy(color = appColors().text2)

    val NavLabel: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = NavLabelSpec.copy(color = appColors().text3)

    val StrongTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = StrongTitleSpec.copy(color = appColors().text1)

    val ArticleTitle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = ArticleTitleSpec.copy(color = appColors().text1)

    /**
     * 按钮文案档（原型 `.btn` = `--fs-body` 15 + `font-weight:600` + `letter-spacing:-.005em`）。
     *
     * 批 1 补：它不是新字号，是 Body 的字号配上按钮的字重/字距。墨在按钮里由
     * `pillButtonInk(...).foreground` 显式给出（本档 getter 的 `text1` 只是兜底），所以深浅两套的
     * 对比度由 `PillButtonInkTest` 负责，不由本档负责。
     */
    val ButtonLabel: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = ButtonLabelSpec.copy(color = appColors().text1)
}

/** 原型度量表：字号/字重/字族/字距/行高；墨不在这里，见 [AppType] 的 getter。 */
private val DisplaySpec = TextStyle(fontSize = 46.sp, fontWeight = FontWeight(500), fontFamily = AppFont.Serif, letterSpacing = (-1.15).sp, lineHeight = 48.sp, fontFeatureSettings = "tnum")
private val HeadlineSpec = TextStyle(fontSize = 24.sp, fontWeight = FontWeight(650), letterSpacing = (-0.24).sp, lineHeight = 30.sp)
private val TitleSpec = TextStyle(fontSize = 17.sp, fontWeight = FontWeight(650), letterSpacing = (-0.17).sp, lineHeight = 27.sp)
private val BodySpec = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp)
private val FootnoteSpec = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp)
private val LabelSpec = TextStyle(fontSize = 12.sp, fontWeight = FontWeight(600), letterSpacing = 0.24.sp, lineHeight = 16.sp)
private val EyebrowSpec = TextStyle(fontSize = 11.sp, fontWeight = FontWeight(600), letterSpacing = 1.54.sp, lineHeight = 14.sp, fontFeatureSettings = "tnum")
private val CaptionSpec = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 19.sp)
private val MetaSpec = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp)
private val BodyLgSpec = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp)
private val LemmaSpec = TextStyle(fontSize = 46.sp, fontWeight = FontWeight(500), fontFamily = AppFont.Serif, letterSpacing = (-1.15).sp, lineHeight = 48.sp, fontFeatureSettings = "tnum")
private val StatNumSpec = TextStyle(fontSize = 26.sp, fontWeight = FontWeight(500), fontFamily = AppFont.Serif, letterSpacing = (-0.52).sp, lineHeight = 29.sp, fontFeatureSettings = "tnum")
private val PlanNumSpec = TextStyle(fontSize = 32.sp, fontWeight = FontWeight(500), fontFamily = AppFont.Serif, letterSpacing = (-0.64).sp, lineHeight = 32.sp, fontFeatureSettings = "tnum")
private val ProseSpec = TextStyle(fontSize = 17.5.sp, fontWeight = FontWeight.Normal, fontFamily = AppFont.Serif, lineHeight = 30.sp)
private val ProseZhSpec = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 23.sp)
private val NavLabelSpec = TextStyle(fontSize = 11.sp, fontWeight = FontWeight(600), letterSpacing = 0.11.sp, lineHeight = 11.sp)
private val StrongTitleSpec = TextStyle(fontSize = 27.sp, fontWeight = FontWeight(500), fontFamily = AppFont.Serif, letterSpacing = (-0.27).sp, lineHeight = 32.sp)
private val ArticleTitleSpec = TextStyle(fontSize = 30.sp, fontWeight = FontWeight(500), fontFamily = AppFont.Serif, letterSpacing = (-0.6).sp, lineHeight = 35.sp)
/**
 * 原型 `.btn` 的按钮文案：15 / 600 / -.005em（= 15sp × -0.005 = -0.075sp）/ 行高 24 同 Body。
 * 见 [AppType.ButtonLabel]。
 */
private val ButtonLabelSpec = TextStyle(fontSize = 15.sp, fontWeight = FontWeight(600), letterSpacing = (-0.075).sp, lineHeight = 24.sp)

/**
 * 装进 M3 `Typography` 前摘掉各档自带的墨：M3 角色的前景应由 `LocalContentColor` 决定。
 */
private fun TextStyle.neutralForTheme(): TextStyle = copy(color = Color.Unspecified)

/**
 * 语义档 → 十五个 M3 排印角色（按最近语义派生，不引新字号）。
 * `display*` 取 46sp 的 Display 会偏大，但 word lemma 等直读点不走 M3；M3 的 display 角色在仓库内无读点。
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
