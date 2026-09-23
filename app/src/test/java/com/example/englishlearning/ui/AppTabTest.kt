package com.example.englishlearning.ui

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AppTabTest {
    @Test
    fun `bottom navigation exposes exactly the four agreed destinations in order`() {
        assertEquals(
            listOf(AppTab.LEARNING, AppTab.READING, AppTab.AI, AppTab.SETTINGS),
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

    /** 用户原话是「在应用底下也加个 ai 学」，这个标签就是那句需求的落点，改字会与需求不符。 */
    @Test
    fun `the ai destination is labelled exactly as the user asked`() {
        assertEquals("AI 学", AppTab.AI.label)
        assertEquals("AI 学", AppTab.AI.contentDescription)
    }
}
