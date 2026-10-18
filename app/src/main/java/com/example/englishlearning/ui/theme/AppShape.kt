package com.example.englishlearning.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object AppShape {
    val Dialog = RoundedCornerShape(24.dp)
    val Card = RoundedCornerShape(20.dp)
    val Button = RoundedCornerShape(14.dp)
    val Pill = RoundedCornerShape(percent = 50)

    /** 图标容器档（视觉稿 §4 的 12px 小控件）：数值与 Material `AppShapes.extraSmall` 相同，但语义归本设计系统，供 ≥40dp 入口与 30dp 行内徽标共用，不复用 extraSmall 槽位。 */
    val Icon: Shape = RoundedCornerShape(12.dp)
}
