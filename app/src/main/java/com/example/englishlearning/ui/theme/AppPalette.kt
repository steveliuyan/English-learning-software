package com.example.englishlearning.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 中性语义色：全局底色/表面/文字。批 1 起全 app 生效。
 * 本 object 是浅套静态源（2026-10-29 原型对齐批 0 取值，见
 * `docs/decisions/2026-10-29-ui-prototype-token-alignment.md`）；
 * 深色取值由 AppColorDark/LocalAppColors 在装配点提供，新组件一律走 appColors()。
 */
object AppPalette {
    val Background = Color(0xFFF1F4EF)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF15211C)
    val TextSecondary = Color(0xFF55665D)
    val Separator = Color(0xFFEEF2ED)
    val Scrim = Color(0x66000000)

    /** 浅薄荷主色容器上的文字色：与运行时 `brandDeep` 同值，压 `MintTint` 达标。 */
    val OnMintPrimary = Color(0xFF1A7A63)

    /** 错误状态文字（压白底 7.15:1），替代裸 `Color.Red`（≈3.7:1 且不可主题化）。 */
    val ErrorText = Color(0xFFA5291F)
    /** 成功/提示状态文字（深绿，白底 7.87:1）。 */
    val SuccessText = Color(0xFF1B5E20)

    /** 玻璃面板填充与高光描边（原型导航药片取 surface 88%）。 */
    val GlassFill = Color(0xE0FFFFFF)
    val GlassHighlight = Color(0x59FFFFFF)

    /**
     * 浅套静态源（深色由 AppColorDark/LocalAppColors 提供）。
     * `TextTertiary` 原型值为 #65756C：压 `surface` 4.87、压 `background` 4.39，
     * 只用于 11–13sp 辅助文字/装饰，不作正文；不得压 `tint` 以下的更暗容器补正文。
     */
    val Tint = Color(0xFFE8F2EA)
    val Tint2 = Color(0xFFDCEBE1)
    val Tint3 = Color(0xFFE4F4EC)
    val Outline = Color(0xFFDDE5DD)
    val OutlineSoft = Color(0xFFEEF2ED)
    val TextTertiary = Color(0xFF65756C)
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
