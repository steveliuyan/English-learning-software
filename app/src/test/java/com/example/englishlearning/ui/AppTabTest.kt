package com.example.englishlearning.ui

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AppTabTest {
    @Test
    fun `bottom navigation exposes exactly the four agreed destinations in order`() {
        assertEquals(
            listOf(AppTab.LEARNING, AppTab.READING, AppTab.AI, AppTab.PROFILE),
            AppTab.entries.toList(),
        )
    }

    @Test
    fun `learning tab comes first so the app keeps opening on the today plan`() {
        assertEquals(AppTab.LEARNING, AppTab.entries.first())
    }

    @Test
    fun `labels never collide so the bar reads unambiguously`() {
        val labels = AppTab.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size, "栏目标签重复：$labels")
    }

    @Test
    fun `every tab carries a non blank label and content description`() {
        AppTab.entries.forEach { tab ->
            assertTrue(tab.label.isNotBlank(), "${tab.name} 的 label 不能为空")
            assertTrue(tab.contentDescription.isNotBlank(), "${tab.name} 的 contentDescription 不能为空")
        }
    }

    /**
     * 用户原话是「在应用底下也加个 ai 学」，这个标签就是那句需求的落点，改字会与需求不符。
     *
     * 2026-11-02 用户对「这一格到底叫什么」给出定名：**「AI 助学」**。所以下面钉的是定名，
     * 不是最初那句口语需求——「AI 学」三字已退役，改回即与定名不符。
     */
    @Test
    fun `the ai destination is labelled exactly as the user asked`() {
        assertEquals("AI 助学", AppTab.AI.label)
        assertEquals("AI 助学", AppTab.AI.contentDescription)
    }

    /**
     * 2026-11-02 用户裁定：设置不是一级目的地，它住在「我的」里。
     *
     * 上一批把设置升成第五栏是「在旧版上做加法」，与参考的四栏形状不符，也与
     * 「我的里放设置」的常规心智不符（`docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`）。
     * 这条同时防止有人再往枚举里加回第五项。
     */
    @Test
    fun `the fourth destination is the profile hub and settings is not a tab`() {
        assertEquals("我的", AppTab.PROFILE.label)
        assertEquals("我的", AppTab.PROFILE.contentDescription)
        assertEquals(AppTab.PROFILE, AppTab.entries.last())
        assertTrue(
            AppTab.entries.none { it.name == "SETTINGS" },
            "设置不得回到底导：它是「我的」里的子页（2026-11-02 用户裁定）",
        )
    }
}
