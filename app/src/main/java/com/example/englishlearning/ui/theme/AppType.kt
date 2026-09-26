package com.example.englishlearning.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

/** 字体阶梯：系统 sans（不引入字体文件），层级靠字号/字重/字距。 */
object AppType {
    val Display = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = AppPalette.TextPrimary)
    val Headline = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = AppPalette.TextPrimary)
    val Title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppPalette.TextPrimary)
    val Body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = AppPalette.TextPrimary)
    val Footnote = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = AppPalette.TextSecondary)
    val Label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp, color = AppPalette.TextSecondary)
}
