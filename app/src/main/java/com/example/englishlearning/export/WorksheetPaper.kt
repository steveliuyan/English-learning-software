package com.example.englishlearning.export

/**
 * 纸张与表格的几何基准（单位 pt）。
 *
 * 渲染器与「版式不得回退」的仪器测试读同一份数字：测试要按这些坐标在渲染出的位图上
 * 取样，验证表格边框确实闭合到左上角。如果测试里再写一套坐标，改版式后就会误报。
 */
object WorksheetPaper {
    const val A4_WIDTH = 595
    const val A4_HEIGHT = 842

    /** 表格外框左上角：三份模板最外侧的边框都从这里开始。 */
    const val TABLE_LEFT = 42f
    const val TABLE_TOP = 66f

    const val TABLE_WIDTH = 511f
    const val TABLE_BOTTOM = 800f
}
