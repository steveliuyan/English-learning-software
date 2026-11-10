package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 查词/词义详情页接上 AI 问词层（N-2 收口后的下一步）。
 *
 * 背景：词义详情屏 [CardDetailScreen] 的页末提示与「用 AI 追问这个词」按钮在 `onAskAi` 非空时才出现
 * （见 [CardDetailNoticeHonestyTest]）。搜索/查词详情这一宿主此前传 `onAskAi = null`，
 * 于是页末既无提示也无入口——诚实、但功能缺了一条主线。本份钉住：搜索/查词详情页
 * 与学习流、文章页一样，把 AI 问词层挂起来。
 *
 * 口径（与既有一致）：源码文本即契约，先剥注释再匹配，不锚行号。
 * 关键不变量：每个 AI 问词屏渲染点（`WordAiQaScreen(`）都必须由一处真正的
 * `onAskAi = {` 开启，二者计数相等；且 AppScreen 里不再残留 `onAskAi = null`。
 */
class AppScreenWordQaWiringTest {

    private val source: String by lazy { uiScreenSource("AppScreen.kt") }

    @Test
    fun `search and detail hosts all open the word ai qa overlay`() {
        val qaScreens = countMatches(source, Regex("""\bWordAiQaScreen\("""))
        val askOpeners = countMatches(source, Regex("""\bonAskAi = \{"""))
        assertTrue(qaScreens >= 3, "学习流、文章页、查词/词义详情都应渲染 AI 问词层，实得 $qaScreens 处")
        assertEquals(
            askOpeners,
            qaScreens,
            "每个 WordAiQaScreen 渲染点都必须由一处 onAskAi = { 开启：问词屏 $qaScreens 处 / onAskAi 真接 $askOpeners 处",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""onAskAi = null""")),
            "查词/词义详情页不再存在『无 AI 入口』的宿主：onAskAi 一律真接",
        )
    }
}
