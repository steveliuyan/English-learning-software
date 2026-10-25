package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Task 27 的危险红归位契约（裁决 22-1 / 22-2 的实施面之一）。
 *
 * 两件事都在真机取证上看得见：
 *  - `AiProfileSettingsScreen` 的「清除密钥 / 删除配置 / 失败提示」全用 `Reading` 域色，危险动作
 *    借了阅读的紫色——用户读到的是「这按钮跟阅读有关」，而不是「这一步不可逆」；
 *  - 四个 AI 屏的出站确认按钮用 `Learn` 绿，与正文的「确认发送」语义错位；AI 请求属于
 *    `AiSpeech` 域，按钮与入口图标应当同色。
 *
 * 读法是源码文本（先过 `stripComments`）。本仓 JVM 单测没有渲染运行时，域色的「具名实参是谁」
 * 文本即契约；像素侧由 connected 用例与真机目视承担。
 */
class DangerInkPlacementContractTest {

    /** 五处危险/失败墨色：两处 `PillButton` 的 `domain =`、三处 `Text` 的 `appDomain(...)`。 */
    private val dangerSites = listOf(
        "ai_profiles_message",
        "ai_profile_key_clear",
        "ai_profile_field_error",
        "ai_profile_message",
        "ai_profile_delete",
    )

    /** 四屏出站确认按钮：每屏两枚（取消 / 确认），按钮域色与 AI 能力域一致。 */
    private val outboundScreens = mapOf(
        "ImageStudioScreen.kt" to listOf("image_studio_decline_outbound", "image_studio_confirm_outbound"),
        "PhotoTranslationScreen.kt" to listOf("photo_translation_decline_outbound", "photo_translation_confirm_outbound"),
        "SentenceAnalysisScreen.kt" to listOf("sentence_analysis_decline_outbound", "sentence_analysis_confirm_outbound"),
        "WordAiQaScreen.kt" to listOf("word_qa_decline_outbound", "word_qa_confirm_outbound"),
    )

    @Test
    fun ai_profile_destructive_and_feedback_inks_use_the_danger_domain() {
        val source = uiScreenSource("AiProfileSettingsScreen.kt")
        dangerSites.forEach { tag ->
            val window = tagWindow(source, tag)
            assertTrue(
                window.contains("AppDomain.Danger"),
                "「$tag」仍是阅读域色：危险动作与失败提示必须走 Danger 域（窗口：${window.trim()}）",
            )
            assertFalse(
                window.contains("AppDomain.Reading"),
                "「$tag」窗口里残留 AppDomain.Reading：危险墨色借了阅读紫",
            )
        }
        assertEquals(
            0,
            countMatches(source, readingDomainRule),
            "AiProfileSettingsScreen 里仍出现 AppDomain.Reading（该屏没有阅读语义的墨色需求）",
        )
        assertEquals(
            dangerSites.size,
            countMatches(source, dangerDomainRule),
            "AiProfileSettingsScreen 的 Danger 墨色应恰 ${dangerSites.size} 处（多一处 = 又给非危险动作染红）",
        )
    }

    @Test
    fun outbound_confirm_buttons_use_the_ai_speech_domain() {
        outboundScreens.forEach { (file, tags) ->
            val source = uiScreenSource(file)
            assertEquals(
                0,
                countMatches(source, learnDomainRule),
                "$file 的出站确认按钮仍在用 Learn 绿：AI 请求属于 AiSpeech 域",
            )
            assertEquals(
                tags.size,
                countMatches(source, aiSpeechDomainRule),
                "$file 的 AiSpeech 域色应恰 ${tags.size} 处（取消 + 确认）",
            )
            tags.forEach { tag ->
                val window = tagWindow(source, tag)
                assertTrue(
                    window.contains("AppDomain.AiSpeech"),
                    "「$tag」的域色不是 AiSpeech（窗口：${window.trim()}）",
                )
            }
        }
    }

    // ---------- 反空转 ----------

    @Test
    fun placement_rules_are_not_vacuous() {
        val dangerFixture = """
            PillButton(
                style = PillStyle.Text,
                domain = AppDomain.Danger,
                testTag = "ai_profile_delete",
            )
        """.trimIndent()
        assertTrue(tagWindow(dangerFixture, "ai_profile_delete").contains("AppDomain.Danger"), "合成正片段漏判：Danger 按钮没被认出")
        val readingFixture = """
            PillButton(
                style = PillStyle.Text,
                domain = AppDomain.Reading,
                testTag = "ai_profile_delete",
            )
        """.trimIndent()
        assertFalse(
            tagWindow(readingFixture, "ai_profile_delete").contains("AppDomain.Danger"),
            "合成负片段被放行：Reading 按钮竟满足 Danger 判据",
        )

        val outboundFixture = """
            PillButton(text = "确认发送", domain = AppDomain.AiSpeech, testTag = "x_confirm_outbound")
        """.trimIndent()
        assertEquals(1, countMatches(outboundFixture, aiSpeechDomainRule), "合成正片段漏判：AiSpeech 按钮没被数出来")
        assertEquals(0, countMatches(outboundFixture, learnDomainRule), "合成正片段越界：AiSpeech 按钮被算成 Learn")

        // 窗口必须真的围绕 tag，而不是把整份源码都算进来（否则「窗口里有 Danger」毫无约束力）。
        val farApart = buildString {
            append("AppDomain.Danger\n")
            repeat(200) { append("// padding line $it\n") }
            append("testTag = \"ai_profile_delete\"\n")
        }
        assertFalse(
            tagWindow(farApart, "ai_profile_delete").contains("AppDomain.Danger"),
            "合成负片段被放行：窗口把远处的 Danger 也算了进来，判据退化成全文搜索",
        )
    }

    // ---------- 夹具 ----------

    private val readingDomainRule = Regex("""AppDomain\.Reading""")
    private val dangerDomainRule = Regex("""AppDomain\.Danger""")
    private val learnDomainRule = Regex("""AppDomain\.Learn\b""")
    private val aiSpeechDomainRule = Regex("""AppDomain\.AiSpeech""")

    /**
     * 取 `testTag = "<tag>"` 两侧 [RADIUS] 字符的窗口。域色实参与 `testTag` 在同一个调用里，
     * 但顺序与换行不固定（`Text` 的 `color =` 在前、`PillButton` 的 `domain =` 也在前），
     * 取窗口比逐字钉顺序更稳。窗口半径刻意小：足以覆盖一个调用块、不足以跨到相邻站点。
     */
    private fun tagWindow(source: String, tag: String): String {
        val at = source.indexOf("\"$tag\"")
        assertTrue(at >= 0, "源码里找不到 testTag \"$tag\"：锚点失效，本契约失去对象")
        val from = maxOf(0, at - RADIUS)
        val to = minOf(source.length, at + tag.length + RADIUS)
        return source.substring(from, to)
    }

    private companion object {
        const val RADIUS = 220
    }
}
