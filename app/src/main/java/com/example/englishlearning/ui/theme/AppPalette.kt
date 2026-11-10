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
    // 2026-11-10 域色归一：色相按域保留，S/L 收到品牌档（浅 base 48/61、浅 deep 65/29）。
    // 与 AppColors.kt 的 DomainLight 逐值相同（契约测试双钉），裁决见
    // docs/decisions/2026-11-10-domain-color-normalization.md。
    val AiSpeech = DomainAccent(Color(0xFF6CCBAB), Color(0xFF1A7A63))   // 薄荷绿（品牌锚点，不动）
    val Learn = DomainAccent(Color(0xFF6C9FCB), Color(0xFF1A4D7A))      // 学习
    val Review = DomainAccent(Color(0xFFCBA76C), Color(0xFF7A551A))     // 复习
    val Library = DomainAccent(Color(0xFF926CCB), Color(0xFF411A7A))    // 词库
    val Reading = DomainAccent(Color(0xFFCB6C7C), Color(0xFF7A1A2B))    // 阅读
    val Danger = DomainAccent(Color(0xFFCB766C), Color(0xFF7A241A))     // 不可撤销的破坏性操作
    val Settings = DomainAccent(Color(0xFF96A29D), Color(0xFF464E4B))   // 设置/中性（S 6%，只对齐 L）
}
