package com.example.englishlearning.ui.theme

import androidx.compose.ui.graphics.Color

/** 中性语义色：全局底色/表面/文字。批 1 起全 app 生效。 */
object AppPalette {
    val Background = Color(0xFFF5F5F7)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF1C1C1E)
    val TextSecondary = Color(0xFF6E6E73)
    val Separator = Color(0xFFE5E5EA)
    val Scrim = Color(0x66000000)

    /** 玻璃面板填充与高光描边。 */
    val GlassFill = Color(0xCCFFFFFF)
    val GlassHighlight = Color(0x59FFFFFF)
}

/** iOS 式域色：每个功能域一个强调色。base=填充/图标/主按钮，deep=深色文字与按压态。 */
data class DomainAccent(val base: Color, val deep: Color)

object DomainColors {
    val AiSpeech = DomainAccent(Color(0xFF2EC99C), Color(0xFF188F76))   // 薄荷绿（品牌锚点）
    val Learn = DomainAccent(Color(0xFF0A84FF), Color(0xFF075EB4))      // 学习
    val Review = DomainAccent(Color(0xFFFF9F0A), Color(0xFFC46A00))     // 复习
    val Library = DomainAccent(Color(0xFFBF5AF2), Color(0xFF8E34B8))    // 词库
    val Reading = DomainAccent(Color(0xFFFF375F), Color(0xFFC81E46))    // 阅读
    val Settings = DomainAccent(Color(0xFF8E8E93), Color(0xFF48484A))   // 设置/中性
}
