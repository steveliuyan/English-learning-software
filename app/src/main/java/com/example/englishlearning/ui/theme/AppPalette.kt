package com.example.englishlearning.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 中性语义色：全局底色/表面/文字。批 1 起全 app 生效。
 * 本 object 是浅套静态源（spec §2.1 浅值 + 决策 2026-10-18 裁决校准）；
 * 深色取值由 AppColorDark/LocalAppColors 在装配点提供，新组件一律走 appColors()。
 */
object AppPalette {
    val Background = Color(0xFFF6F9F6)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF1F2B26)
    val TextSecondary = Color(0xFF566B62)
    val Separator = Color(0xFFEBF1ED)
    val Scrim = Color(0x66000000)

    /** 浅薄荷主色容器上的文字色：压 `MintTint` 8.65:1（AA 正文），替代「白字压浅底」。 */
    val OnMintPrimary = Color(0xFF174C44)

    /** 错误状态文字（白底 7.20:1），替代裸 `Color.Red`（≈3.7:1 且不可主题化）。 */
    val ErrorText = Color(0xFFB30000)
    /** 成功/提示状态文字（深绿，白底 7.87:1）。 */
    val SuccessText = Color(0xFF1B5E20)

    /** 玻璃面板填充与高光描边。 */
    val GlassFill = Color(0xEFFFFFFF)
    val GlassHighlight = Color(0x59FFFFFF)

    /**
     * 浅套静态源（深色由 AppColorDark/LocalAppColors 提供）。
     * TextTertiary 按 2026-10-18 裁决 1（该裁决已于批 1 终审 Seat A I-2 收窄底限定）只可用于
     * `surface`/`background` 底上的 ≥24sp 大数字/装饰与纯图标描边（实测 3.35/3.16，过装饰档 3.1）；
     * **不得作任何正文或标签前景**，亦不得作图标描边压 `tint`/`tint-2`/`tint-3`（实测 2.98/2.85/2.71，
     * 后两枚连非文本 3:1 都不过，`tint` 只差 0.12 才到 3.1——2026-10-27 轻松档把余量收到临界，别再加亮）。
     */
    val Tint = Color(0xFFE9F4EE)
    val Tint2 = Color(0xFFE4EFE9)
    val Tint3 = Color(0xFFDEEAE4)
    val Outline = Color(0xFFD3E7DC)
    val OutlineSoft = Color(0xFFEBF1ED)
    val TextTertiary = Color(0xFF7C9188)
    val OnBrand = Color(0xFF04241C)
    val ErrorFill = Color(0xFFFF3B30)
}

/** iOS 式域色：每个功能域一个强调色。base=填充/图标/主按钮，deep=深色文字与按压态。 */
data class DomainAccent(val base: Color, val deep: Color)

object DomainColors {
    val AiSpeech = DomainAccent(Color(0xFF6CCBAB), Color(0xFF1A7A63))   // 薄荷绿（品牌锚点）
    val Learn = DomainAccent(Color(0xFF7BB4E6), Color(0xFF175A93))      // 学习
    val Review = DomainAccent(Color(0xFFEBC68B), Color(0xFF8A5712))     // 复习
    val Library = DomainAccent(Color(0xFFB7A0D9), Color(0xFF65478A))    // 词库
    val Reading = DomainAccent(Color(0xFFE297A4), Color(0xFF9C3B4F))    // 阅读
    val Danger = DomainAccent(Color(0xFFDD8B81), Color(0xFF9E2B24))     // 不可撤销的破坏性操作
    val Settings = DomainAccent(Color(0xFFA6B0AC), Color(0xFF4A5551))   // 设置/中性
}
