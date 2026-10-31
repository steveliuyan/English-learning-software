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
 *  - 四个 AI 屏的出站确认按钮用 `Learn` 绿，与正文的「确认发送」语义错位。
 *
 * 读法是源码文本（先过 `stripComments`）。本仓 JVM 单测没有渲染运行时，域色的「具名实参是谁」
 * 文本即契约；像素侧由 connected 用例与真机目视承担。
 *
 * **出站确认这一族的墨色权威已搬家（2026-11-03，决策 `docs/decisions/2026-11-03-duplication-consolidation.md` §1）**：
 * 四屏的私写对话框并入共享件 `ui/components/OutboundConfirmDialog.kt` 后，屏侧不再画那两枚按钮，
 * 也就**没有** `domain =` 可写——「取消 / 确认」的取墨只剩 `dialogButtonInk` 那张角色表一处
 * （确认档 = `onMint` 容器 + `background` 前景，即装配点已钉的 `primary`/`onPrimary`）。
 * 于是 Task 27 的「与入口图标同色（`AiSpeech`）」这条**换成了更硬的口径**：
 *  ① 屏侧仍禁 `Learn` 绿（Task 27 的原始病灶，一字未放宽）；
 *  ② 屏侧的确认框调用块内零 `AppDomain.` 取墨点——屏只交 tag，不交颜色；
 *  ③ 共享件按 `DialogButtonRole.Confirm` / `.Dismiss` 各恰一枚取墨，且它自己也不按域取墨
 *    （否则角色表就有第二份实现）。
 * 这条搬家记在决策 §1 的影响面里，不是本测试单方面改判据。
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

    /** 四屏出站确认按钮：每屏两枚（取消 / 确认），tag 由各屏交给共享件，墨色由共享件的角色表定。 */
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
    fun outbound_confirm_inks_come_from_the_shared_role_table_not_from_screens() {
        outboundScreens.forEach { (file, tags) ->
            val source = uiScreenSource(file)
            assertEquals(
                0,
                countMatches(source, learnDomainRule),
                "$file 的出站确认按钮仍在用 Learn 绿：AI 请求属于 AiSpeech 域",
            )
            // ② 两枚 tag 是**实参**，不是屏内节点：屏只交出锚点，颜色交不出去。
            //    判据按位取（下标 0 = 取消，1 = 确认），不假设实参书写顺序——共享件的形参顺序
            //    是 body / confirm / dismiss，与各屏 `tags` 表的顺序相反，写死顺序的判据会在换行时就红。
            assertEquals(
                1,
                countMatches(source, Regex("""dismissTestTag\s*=\s*"${Regex.escape(tags[0])}"""")),
                "$file 必须把取消键的锚 `${tags[0]}` 经 `dismissTestTag` 交给共享件：connected 与各屏版式契约点名它，" +
                    "屏侧另画一枚按钮 = 出站确认长出第二份（U-1）",
            )
            assertEquals(
                1,
                countMatches(source, Regex("""confirmTestTag\s*=\s*"${Regex.escape(tags[1])}"""")),
                "$file 必须把确认键的锚 `${tags[1]}` 经 `confirmTestTag` 交给共享件（同上）",
            )
            // ① 取样收敛到共享件调用块（从 `OutboundConfirmDialog(` 到最后一枚 tag 之后一点）：
            // 块内出现 `AppDomain.` 就是屏侧又开始给确认框的两枚按钮挑颜色。
            // 区间外不承诺限制——入口行徽标那一族的域色由 `EntryRowInkTest` / 各屏版式契约分别钉
            // （批 4 Task 4 起 `PhotoTranslationScreen` 的行徽标也用 `AiSpeech`，全文件计数会被顶上去）。
            val dialogSpan = sharedDialogCallSpan(source, file, tags)
            assertEquals(
                0,
                countMatches(dialogSpan, appDomainReadRule),
                "$file 的出站确认调用块里还有 `AppDomain.` 取墨点：两枚按钮的墨色只由 " +
                    "`dialogButtonInk` 角色表决定，屏侧挑色 = 第二份取墨口径（U-1 的搬家结论）",
            )
        }

        // ③ 共享件这一侧：一档一枚，且它自己也不按域取墨。
        val component = uiComponentSource("OutboundConfirmDialog.kt")
        assertEquals(
            1,
            countMatches(component, Regex("""role = DialogButtonRole\.Confirm""")),
            "出站确认的确认键必须恰一处按 Confirm 档取墨（0 = 退回凑色，2 = 第二枚主操作）",
        )
        assertEquals(
            1,
            countMatches(component, Regex("""role = DialogButtonRole\.Dismiss""")),
            "出站确认的取消键必须恰一处按 Dismiss 档取墨",
        )
        assertEquals(
            0,
            countMatches(component, appDomainReadRule),
            "共享件不得按域取墨：确认档与破坏档的墨色只有 `dialogButtonInk` 一张表，组件里再写一次就是两处不一致",
        )
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

        // 出站确认这一族的判据换了对象（墨色权威搬家），反空转也跟着换：三条各配一对正负片段。
        val sharedCallFixture = """
            OutboundConfirmDialog(
                endpointHost = host,
                payloadKind = AiPayloadKind.Text,
                confirmTestTag = "x_confirm_outbound",
                dismissTestTag = "x_decline_outbound",
            )
        """.trimIndent()
        assertEquals(
            1,
            countMatches(sharedCallFixture, Regex("""confirmTestTag\s*=\s*"x_confirm_outbound""")),
            "合成正片段漏判：共享件调用里的确认锚没被认出，这条会红在合规屏上",
        )
        assertEquals(
            1,
            countMatches(sharedCallFixture, Regex("""dismissTestTag\s*=\s*"x_decline_outbound""")),
            "合成正片段漏判：共享件调用里的取消锚没被认出（同上）",
        )
        assertEquals(
            0,
            countMatches(sharedDialogCallSpan(sharedCallFixture, "夹具屏", listOf("x_decline_outbound", "x_confirm_outbound")), appDomainReadRule),
            "合成正片段越界：只把 tag 交给共享件的调用块被算成屏侧挑色",
        )

        // 负片段 A：屏侧回退成私写按钮（Task 27 的原始病灶）。
        val privateButtonFixture = """
            PillButton(text = "确认发送", style = PillStyle.Primary, domain = AppDomain.Learn, testTag = "x_confirm_outbound")
        """.trimIndent()
        assertEquals(
            1,
            countMatches(privateButtonFixture, learnDomainRule),
            "合成负片段被放行：Learn 绿没被数出来，「禁 Learn」那条是空转",
        )
        assertEquals(
            0,
            countMatches(privateButtonFixture, Regex("""confirmTestTag\s*=\s*"x_confirm_outbound""")),
            "合成负片段被放行：屏侧私写的 PillButton 竟满足「tag 由实参交给共享件」",
        )

        // 负片段 B：调用块之后紧接着给确认框挑色——取样区间必须真的盖住这一带，
        // 否则「块内零 AppDomain」会退化成一句只扫两个字符的空话。
        val inkDriftFixture = """
            OutboundConfirmDialog(
                endpointHost = host,
                confirmTestTag = "x_confirm_outbound",
            )
            Text("确认发送请求", color = appDomain(AppDomain.AiSpeech).deep)
        """.trimIndent()
        assertEquals(
            1,
            countMatches(sharedDialogCallSpan(inkDriftFixture, "夹具屏", listOf("x_confirm_outbound")), appDomainReadRule),
            "合成负片段被放行：调用块里冒出的 AppDomain 取墨点没被区间盖住，取样宽度需要重新定",
        )

        // 档位判据不许把破坏档当成出站确认档（两者都叫 Confirm，只有整词不同）。
        assertEquals(
            0,
            countMatches(
                """DialogButton(role = DialogButtonRole.DestructiveConfirm, label = "确认删除")""",
                Regex("""role = DialogButtonRole\.Confirm"""),
            ),
            "合成负片段被放行：DestructiveConfirm 被算成出站确认的 Confirm 档，两档混了",
        )

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

    /** 域色取墨点（`AppDomain.X` 的任意写法，含 `appDomain(AppDomain.X)`）；U-1 之后屏侧确认块里应为零。 */
    private val appDomainReadRule = Regex("""AppDomain\.""")

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

    /**
     * 共享出站确认框的取样区间：从 `OutboundConfirmDialog(` 起，到最后一枚 tag 之后一个 [RADIUS] 止。
     *
     * 起点绑调用点、终点绑 tag，两端都不许静默放宽（U-1 之后屏侧这一族唯一的形状就是「一次共享件调用」）：
     *  - 调用点找不到 ⇒ 响亮失败。给空串的话「块内零 `AppDomain.`」会恒真，判据当场变成摆设；
     *  - 任一枚 tag 不在调用点之后 ⇒ 响亮失败。tag 与调用点是「屏把锚交给共享件」这件事的两半，
     *    分家说明锚回到了屏自画的按钮上，那正是这条要拦的回退。
     *
     * 不假设实参顺序（各屏现在按 body / confirm / dismiss 写，与本表 [tags] 的取消 / 确认顺序相反），
     * 也不要求恰两枚 tag——份数归 `OutboundDialogFamilyTest` 逐屏钉，这里只负责把区间切准。
     */
    private fun sharedDialogCallSpan(source: String, file: String, tags: List<String>): String {
        val call = source.indexOf("OutboundConfirmDialog(")
        assertTrue(call >= 0, "$file 找不到共享件调用点 `OutboundConfirmDialog(`：区间切不出来，「块内零取墨点」就成了空话")
        assertTrue(tags.isNotEmpty(), "$file 的出站确认锚表为空：本契约失去对象")
        var lastTagEnd = call
        tags.forEach { tag ->
            val at = source.indexOf("\"$tag\"", call)
            assertTrue(
                at >= 0,
                "$file 的出站确认锚 `\"$tag\"` 不在 `OutboundConfirmDialog(` 调用之后（调用点 $call）：" +
                    "锚回到屏自画的按钮上 = 出站确认长出第二份（U-1）",
            )
            lastTagEnd = maxOf(lastTagEnd, at + tag.length + 2)
        }
        return source.substring(call, minOf(source.length, lastTagEnd + RADIUS))
    }

    private companion object {
        const val RADIUS = 220
    }
}
