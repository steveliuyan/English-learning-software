package com.example.englishlearning.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 中性语义色：全局底色/表面/文字。批 1 起全 app 生效。
 * 本 object 是浅套静态源（spec §2.1 浅值 + 决策 2026-10-18 裁决校准）；
 * 深色取值由 AppColorDark/LocalAppColors 在装配点提供，新组件一律走 appColors()。
 */
object AppPalette {
    val Background = Color(0xFFF1FBF5)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF1C1C1E)
    val TextSecondary = Color(0xFF4E756A)
    val Separator = Color(0xFFE4EFE9)
    val Scrim = Color(0x66000000)

    /** 浅薄荷主色容器上的文字色：与 MintPrimary 约 4.6:1（AA 正文），替代「白字压浅底」。 */
    val OnMintPrimary = Color(0xFF174C44)

    /** 错误状态文字（= DomainColors.Danger.deep，白底 ≥7:1），替代裸 `Color.Red`（≈3.7:1 且不可主题化）。 */
    val ErrorText = Color(0xFFB30000)
    /** 成功/提示状态文字（深绿，白底 ≥7:1）。 */
    val SuccessText = Color(0xFF1B5E20)

    /** 玻璃面板填充与高光描边。 */
    val GlassFill = Color(0xEFFFFFFF)
    val GlassHighlight = Color(0x59FFFFFF)

    /**
     * 批 1 补齐的浅套静态源（深色由 AppColorDark/LocalAppColors 提供）。
     * TextTertiary 按 2026-10-18 裁决 1 只可用于 ≥24sp 大数字/装饰与纯图标描边，不得作正文前景。
     */
    val Tint = Color(0xFFDDF7E8)
    val Tint2 = Color(0xFFDCEAE1)
    val Tint3 = Color(0xFFCDEDD9)
    val Outline = Color(0xFFADE7D2)
    val OutlineSoft = Color(0xFFE4EFE9)
    val TextTertiary = Color(0xFF7A9088)
    val OnBrand = Color(0xFF04241C)
    val ErrorFill = Color(0xFFFF3B30)
}

/** iOS 式域色：每个功能域一个强调色。base=填充/图标/主按钮，deep=深色文字与按压态。 */
data class DomainAccent(val base: Color, val deep: Color)

object DomainColors {
    val AiSpeech = DomainAccent(Color(0xFF2EC99C), Color(0xFF188F76))   // 薄荷绿（品牌锚点）
    val Learn = DomainAccent(Color(0xFF0A84FF), Color(0xFF075EB4))      // 学习
    val Review = DomainAccent(Color(0xFFFF9F0A), Color(0xFFAB5700))     // 复习
    val Library = DomainAccent(Color(0xFFBF5AF2), Color(0xFF8E34B8))    // 词库
    val Reading = DomainAccent(Color(0xFFFF375F), Color(0xFFC81E46))    // 阅读
    val Danger = DomainAccent(Color(0xFFFF3B30), Color(0xFFB30000))      // 不可撤销的破坏性操作
    val Settings = DomainAccent(Color(0xFF8E8E93), Color(0xFF48484A))   // 设置/中性
}
