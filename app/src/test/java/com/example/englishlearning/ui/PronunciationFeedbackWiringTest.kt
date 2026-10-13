package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 点击发音的即时反馈契约（2026-10-13 真机反馈：点了没反应）。
 *
 * AppScreen 是 Compose UI，JVM 侧无法渲染，沿用 [AppScreenProductionWiringTest]
 * 的源码契约风格：每个用户点击触发的发音 handler（onSpeak / onSpeakBritish）
 * 必须在 launch 调 provider 之前把状态置成 Playing——先给用户"正在播放"，
 * 慢也慢得有反馈，绝不能静默启动后让用户对着原界面发呆。
 */
class PronunciationFeedbackWiringTest {

    @Test
    fun `every user-click pronunciation handler shows Playing before launching the provider`() {
        val source = appScreenSource()

        var cursor = 0
        var checkedHandlers = 0
        while (true) {
            val start = HANDLER_MARKERS
                .map { source.indexOf(it, cursor) }
                .filter { it >= 0 }
                .minOrNull() ?: break
            val launch = source.indexOf("launch", start)
            assertTrue(launch > start, "handler @$start 之后找不到 launch：接线已改形态，请同步本测试")
            val segment = source.substring(start, launch)
            assertTrue(
                "PronunciationStatus.Playing" in segment,
                "点击发音 handler（offset $start）没有在进入 provider 前显示「正在播放」：\n${segment.take(300)}",
            )
            cursor = launch + 1
            checkedHandlers += 1
        }

        assertTrue(checkedHandlers >= 8, "预期至少 8 个点击发音 handler（查词/记录/学习详情/文章 × 美英），实到 $checkedHandlers 个——接线被改动，请同步本测试")
    }

    @Test
    fun `click handlers never park the status at Idle before speaking`() {
        val source = appScreenSource()

        var cursor = 0
        while (true) {
            val start = HANDLER_MARKERS
                .map { source.indexOf(it, cursor) }
                .filter { it >= 0 }
                .minOrNull() ?: break
            val launch = source.indexOf("launch", start)
            val segment = source.substring(start, launch)
            assertTrue(
                "PronunciationStatus.Idle" !in segment,
                "点击发音 handler（offset $start）在 launch 前把状态置回 Idle——真机上表现为点击无反馈：\n${segment.take(300)}",
            )
            cursor = launch + 1
        }
    }

    private fun appScreenSource(): String =
        listOf(
            File("src/main/java/com/example/englishlearning/ui/AppScreen.kt"),
            File("app/src/main/java/com/example/englishlearning/ui/AppScreen.kt"),
        ).first { it.isFile }.readText()

    private companion object {
        // 只查用户点击路径；onSpeakSentence 等长名 marker 不会被 "onSpeak = {" 前缀误伤。
        val HANDLER_MARKERS = listOf("onSpeak = {", "onSpeakBritish = {")
    }
}
