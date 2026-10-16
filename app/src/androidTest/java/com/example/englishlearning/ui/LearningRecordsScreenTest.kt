package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.LearningRecord
import com.example.englishlearning.learning.domain.CardFeedback
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearningRecordsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun twoTabsAreRendered() {
        composeRule.setContent { LearningRecordsScreen(LearningRecordsUiState(), {}, {}, {}, {}) }
        composeRule.onNodeWithTag("learning_records_screen").assertIsDisplayed()
        composeRule.onNodeWithText("今日已学").assertIsDisplayed()
        composeRule.onNodeWithText("历史记录").assertIsDisplayed()
        composeRule.onNodeWithText("全部单词").assertDoesNotExist()
    }

    @Test
    fun missingRecordIsVisibleButNotClickable() {
        val row = LearningRecord.missing("book:parent", CardFeedback.Fuzzy, Instant.EPOCH, LocalDate.of(2026, 9, 30), "plan-1")
        composeRule.setContent { LearningRecordsScreen(LearningRecordsUiState(today = listOf(row)), {}, {}, {}, {}) }
        composeRule.onNodeWithTag("learning_record_missing_parent").assertIsDisplayed()
        composeRule.onAllNodesWithTag("learning_record_row_parent").assertCountEquals(0)
    }

    /**
     * F5-03 / Task 10（B2 文字链）：学习记录页「← 返回」的触控目标必须 ≥48dp 见方。
     * 现状只有 `padding(8.dp)`（emulator-5574 RED 原文：高 32.380955dp，不足 48dp 下限）。
     *
     * 返回链在根 Column 的首行，不在滚动容器内（记录列表才是滚动区），节点常驻视口，
     * 因此不需要 `performScrollTo()` 锚点；brief 口径与 `getUnclippedBoundsInRoot` 口径各跑一遍。
     */
    @Test
    fun backLinkMeetsTheMinimumTouchTarget() {
        composeRule.setContent { LearningRecordsScreen(LearningRecordsUiState(), {}, {}, {}, {}) }

        composeRule.onNodeWithTag("learning_records_back").assertIsDisplayed()
        composeRule.onNodeWithTag("learning_records_back").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("learning_records_back").assertWidthIsAtLeast(48.dp)

        val bounds = composeRule.onNodeWithTag("learning_records_back").getUnclippedBoundsInRoot()
        assertTrue("learning_records_back 的触控目标应至少 48dp 高，实际=$bounds", bounds.bottom - bounds.top >= 48.dp)
        assertTrue("learning_records_back 的触控目标应至少 48dp 宽，实际=$bounds", bounds.right - bounds.left >= 48.dp)
    }

    /**
     * F5-03 终审 I-1（决策 §4 字面条款：「底部导航 `TabSlot` 与学习记录 `Tab`：
     * `role = Role.Tab` + `stateDescription` 选中态」）。
     *
     * 现状（BASE `11a71ef`，RED）：`LearningRecordsScreen.kt:90` 只有
     * `contentDescription = label; role = Role.Tab`，**没有 stateDescription**，
     * 所以切换页签后语义里查不到「已选中/未选中」文本（只有 M3 `selectable` 的 Selected
     * 布尔属性代偿，不是 §4 要求的播报文本）；同链先例 `AppBottomBar.kt:83-87` 两者齐备。
     * 读语义属性的写法照 `SpeechSettingsScreenTest.stateDescriptionOfRateRow()`。
     */
    @Test
    fun tabsAnnounceTheirSelectedState() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(LearningRecordsTab.TODAY) }
            LearningRecordsScreen(
                state = LearningRecordsUiState(selectedTab = selected),
                onBack = {},
                onSelectTab = { selected = it },
                onRetry = {},
                onOpenRecord = {},
            )
        }

        assertEquals("已选中", stateDescriptionOfTab("learning_records_tab_today"))
        assertEquals("未选中", stateDescriptionOfTab("learning_records_tab_history"))

        composeRule.onNodeWithTag("learning_records_tab_history").performClick()
        composeRule.waitForIdle()

        assertEquals("未选中", stateDescriptionOfTab("learning_records_tab_today"))
        assertEquals("已选中", stateDescriptionOfTab("learning_records_tab_history"))

        composeRule.onNodeWithTag("learning_records_tab_today").performClick()
        composeRule.waitForIdle()

        assertEquals("已选中", stateDescriptionOfTab("learning_records_tab_today"))
        assertEquals("未选中", stateDescriptionOfTab("learning_records_tab_history"))
    }

    private fun stateDescriptionOfTab(tag: String): String =
        composeRule.onNodeWithTag(tag)
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
            ?: ""
}
