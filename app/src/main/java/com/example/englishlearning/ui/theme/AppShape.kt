package com.example.englishlearning.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 圆角档：2026-10-29 原型对齐批 0 取值（原型 `--r-sm/md/lg/xl/pill` + 贴纸徽标三档）。
 *
 * 原型徽标圆角随尺寸走：32dp→10、44dp→14、64dp→20；空状态大图 84dp 用 `Dialog` 的 26。
 * `Icon` / `Inline` 是迁移期兼容档（旧组件仍引用），批 4 随组件收口删除。
 */
object AppShape {
    /** 小按钮、32dp 徽标、字段选择标记（原型 `--r-sm`）。 */
    val Small = RoundedCornerShape(10.dp)
    /** 按钮、输入框、44dp 徽标（原型 `--r-md`）。 */
    val Button = RoundedCornerShape(14.dp)
    /** 卡片、64dp 徽标（原型 `--r-lg`）。 */
    val Card = RoundedCornerShape(20.dp)
    /** 对话框、底部面板、强卡、空状态大图（原型 `--r-xl`）。 */
    val Dialog = RoundedCornerShape(26.dp)

    val Badge = RoundedCornerShape(14.dp)
    val BadgeSmall = RoundedCornerShape(10.dp)
    val BadgeLarge = RoundedCornerShape(20.dp)
    val Pill = RoundedCornerShape(percent = 50)

    /** 迁移期兼容：旧 12dp 图标容器档。 */
    val Icon: Shape = RoundedCornerShape(12.dp)

    /** 迁移期兼容：旧 9dp 行内徽标档。 */
    val Inline: Shape = RoundedCornerShape(9.dp)
}
