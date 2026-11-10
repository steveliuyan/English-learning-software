package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * N-2（真机观察）：词义详情是「一份 UI、五种宿主」。搜索 / 查词侧那一路传 `onAskAi = null`，
 * 屏内不渲染 [AskAiButton]；但页末那句提示文案过去是**无条件**渲染的，于是它对着一个根本
 * 不存在的按钮说「AI 问词都从这里进入」——把用户引向一个点不到的入口。
 *
 * 修法：提示与入口同生同灭，都收进 `onAskAi?.let` 守卫。本判据钉住这个从属关系，
 * 防止将来有人把提示又挪回守卫外面（那正是缺陷复发的方式）。
 *
 * 2026-11-09 真人走查续：同一句里「长难句分析…都从这里进入」也是同一种谎——本页只有 AI 问词一枚按钮，
 * 长难句分析是 AI 助学里的独立一屏，这里进不去。文案收成只讲 AI 问词，断言随之换字面。
 */
class CardDetailNoticeHonestyTest {

    private val source: String by lazy { uiScreenSource("CardDetailScreen.kt") }

    private val notice = "\"AI 问词从这里进入，围绕这一个词继续追问。\""

    @Test
    fun `the ai entry notice only renders when the ai entry exists`() {
        val noticeAt = source.indexOf(notice)
        assertTrue(noticeAt >= 0, "页末提示文案缺失，判据会空转")

        val guardAt = source.lastIndexOf("onAskAi?.let", noticeAt)
        assertTrue(
            guardAt >= 0 && guardAt < noticeAt,
            "提示文案必须落在 onAskAi?.let 守卫里：搜索/查词详情 onAskAi=null，没有 AI 入口，无条件渲染就是谎报",
        )

        val askButtonAt = source.indexOf("AskAiButton(", noticeAt)
        assertTrue(askButtonAt > noticeAt, "提示之后必须紧跟 AI 追问按钮")
        assertTrue(
            notice in source.substring(guardAt, askButtonAt),
            "提示文案与 AI 追问按钮要落在同一个 onAskAi?.let 块里",
        )
    }
}
