package com.example.englishlearning.ui

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.DomainColors
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.domain.DailyLearningStats
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckInScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun ready_renders_statistics_and_calendar() {
        val today = LocalDate.of(2026, 9, 25)
        val stats = { date: LocalDate -> DailyLearningStats(date, 3, 1, 2, 4) }
        composeRule.setContent { CheckInScreen(CheckInUiState.Ready(stats(today), (0..6).map { stats(today.minusDays(it.toLong())) }, (1..today.lengthOfMonth()).map { stats(today.withDayOfMonth(it)) }, true), onBack = {}) }
        composeRule.onNodeWithTag("check_in_screen").assertExists()
        composeRule.onNodeWithText("完成任务 2/4", substring = true).assertExists()
        composeRule.onNodeWithTag("check_in_week_chart").assertExists()
        (0..6).forEach { index -> composeRule.onNodeWithTag("check_in_week_bar_$index").assertExists() }
        composeRule.onNodeWithTag("check_in_completion_ratio").assertExists()
        composeRule.onNodeWithTag("check_in_completion_percent").assertTextContains("50%")
        composeRule.onNodeWithTag("check_in_calendar").assertExists()
        composeRule.onNodeWithContentDescription("2026-09-25 已完成").assertExists()
        composeRule.onNodeWithContentDescription("2026-09-01 已完成").assertExists()
    }

    @Test fun review_progress_and_completed_dates_use_review_orange() {
        val date = LocalDate.of(2026, 9, 25)
        val stats = DailyLearningStats(date, 3, 1, 2, 4)
        composeRule.setContent { CheckInScreen(CheckInUiState.Ready(stats, List(7) { stats }, listOf(stats), true), onBack = {}) }
        val orange = DomainColors.Review.deep.toArgb()
        assertTrue("progress graphics need 3:1 against white", contrastRatio(orange, AndroidColor.WHITE) >= 3.0)
        assertContainsColor("check_in_week_bar_0", orange)
        assertArcColor(orange)
        assertContainsColor("check_in_completion_percent", orange)
        assertContainsColor("check_in_calendar", DomainColors.Review.deep.toArgb())
    }

    private fun contrastRatio(a: Int, b: Int): Double {
        fun luminance(color: Int): Double {
            val channels = listOf(AndroidColor.red(color), AndroidColor.green(color), AndroidColor.blue(color))
                .map { it / 255.0 }
                .map { if (it <= 0.04045) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
            return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
        }
        val (lighter, darker) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertArcColor(color: Int) {
        val bitmap = composeRule.onNodeWithTag("check_in_completion_arc").performScrollTo().captureToImage().asAndroidBitmap()
        val y = bitmap.height / 2
        val xRange = (bitmap.width * 11 / 12) until bitmap.width
        assertTrue("completion arc should render accessible review orange", xRange.any { x -> bitmap.getPixel(x, y) == color })
    }

    private fun assertContainsColor(tag: String, color: Int) {
        val bitmap = composeRule.onNodeWithTag(tag).performScrollTo().captureToImage().asAndroidBitmap()
        assertTrue("$tag should show review accent", (0 until bitmap.width).any { x ->
            (0 until bitmap.height).any { y -> bitmap.getPixel(x, y) == color }
        })
    }

    @Test fun unavailable_offers_retry() {
        var retried = 0
        composeRule.setContent { CheckInScreen(CheckInUiState.Unavailable, onBack = {}, onRetry = { retried++ }) }
        composeRule.onNodeWithTag("check_in_unavailable").assertExists()
        composeRule.onNodeWithContentDescription("重试").assertHasClickAction().performClick()
        assertEquals(1, retried)
    }

    @Test fun back_invokes_callback() {
        var backs = 0
        composeRule.setContent { CheckInScreen(CheckInUiState.Loading, onBack = { backs++ }) }
        composeRule.onNodeWithContentDescription("返回").assertHasClickAction().performClick()
        assertEquals(1, backs)
    }

    /**
     * Task 22-A §二.1（统一页头）：打卡页的页头必须由共享 [com.example.englishlearning.ui.components.AppScreenHeader]
     * 承载，屏侧不再留私有页头 `Row`，也不再自造 `check_in_back` 那枚文字返回节点——返回位改由组件的
     * 48dp 图标位承担（它没有 testTag，只带 `contentDescription = "返回"`，见
     * `AppScreenHeader.kt` 的 `:83-102` 返回位）。
     *
     * 本用例钉的是**交接本身**，不是放宽：① 组件节点在册（`app_screen_header`）；② 标题文案仍在页头上；
     * ③ 屏侧旧 tag 必须消失（留着它 = 私有页头窗口没清零，`SharedComponentUniquenessRatchetTest`
     * 的页头族删行门当场红）；④ 新的返回位真的可点且回调恰一次。
     */
    @Test fun headerIsCarriedByTheSharedAppScreenHeader() {
        var backs = 0
        composeRule.setContent {
            CheckInScreen(
                state = CheckInUiState.Loading,
                onBack = { backs++ },
                onOpenFavorites = {},
                onOpenWrongWords = {},
            )
        }
        composeRule.onNodeWithTag("app_screen_header").assertIsDisplayed()
        composeRule.onNodeWithText("学习打卡").assertIsDisplayed()
        composeRule.onNodeWithTag("check_in_back").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("返回").assertHasClickAction().performClick()
        assertEquals(1, backs)
    }

    /**
     * F5-03 / Task 10（B2 文字链）：打卡页头部的裸文字链触控目标必须 ≥48dp 见方——它们只有
     * `padding(10.dp)`（emulator-5574 RED 原文实测 36.190475dp 高，原先记录的「约 40dp」为目测值、已废弃），
     * 不达标准。
     *
     * Task 22-A §二.1 的**交接**：本用例原先遍历「收藏 / 错词 / 返回」三枚 tag，返回那一腿现在交回组件——
     * 屏侧只钉本屏自留的两条链（摘这两条链归 22-B，故它们必须**仍在册**）；返回位的 48dp 与可读描述由
     * `AppScreenHeaderTest.header_back_is_a_48dp_target_with_a_readable_description` 承重
     * （该类现盘重算：`grep -c "@Test" app/src/androidTest/java/com/example/englishlearning/ui/AppScreenHeaderTest.kt`）。
     *
     * 双口径（dispatch-notes §B）：brief 的 `assertHeightIsAtLeast/assertWidthIsAtLeast`（读裁剪后矩形，
     * 故先 `performScrollTo()` + `assertIsDisplayed()` 确认在视口内）与既有先例的
     * `getUnclippedBoundsInRoot`（断真实尺寸）各跑一遍。
     */
    @Test fun headerTextLinksMeetTheMinimumTouchTarget() {
        composeRule.setContent {
            CheckInScreen(
                state = CheckInUiState.Loading,
                onBack = {},
                onOpenFavorites = {},
                onOpenWrongWords = {},
            )
        }
        listOf("check_in_open_favorites", "check_in_open_wrong_words").forEach { tag ->
            assertMinTouchTarget(tag)
        }
    }

    private fun assertMinTouchTarget(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        composeRule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp)

        val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertTrue("$tag 的触控目标应至少 48dp 高，实际=$bounds", bounds.bottom - bounds.top >= 48.dp)
        assertTrue("$tag 的触控目标应至少 48dp 宽，实际=$bounds", bounds.right - bounds.left >= 48.dp)
    }

    @Test fun zero_target_shows_zero_percent() {
        val date = LocalDate.of(2026, 9, 25)
        val zero = DailyLearningStats(date, 0, 0, 0, 0)
        composeRule.setContent { CheckInScreen(CheckInUiState.Ready(zero, List(7) { zero }, listOf(zero), false), onBack = {}) }
        composeRule.onNodeWithTag("check_in_completion_percent").assertTextContains("0%")
        composeRule.onNodeWithText("目标 0 项，已完成 0 项", substring = true).assertExists()
    }

    /**
     * F5-03 / Task 11（§E.2，Task 10 评审 I-4 的护栏）+ Task 22-A §二.1 的改版口径。
     *
     * **本用例钉的形状已随统一页头改版换掉了宿主**：原先它钉的是屏侧私有页头 `Row` 的修饰符顺序
     * （`fillMaxWidth()` 在前、`horizontalScroll` 在后）与它带来的标题塌宽反例；Task 22-A 把页头交给共享
     * [com.example.englishlearning.ui.components.AppScreenHeader] 后，那条私有 `Row` 与 `horizontalScroll`
     * 都不存在了，`horizontalScroll` 反例形态的教训迁进了组件自己的宿主（`AppScreenHeader` KDoc 已写明
     * 「`Row` 显式 `fillMaxWidth()`：无界宽宿主下标题列会塌宽（`CheckInScreenTest` 钉过这个反例形态）」），
     * L-T11-1 的账由「换用统一页头组件」这一动作结。本用例因此改述为钉**改版后仍然成立**的两条可观测性质：
     *  1. `fontScale = 2.0`（Android「字体大小」上限档，AC-R-07 钉的口径）下本屏自留的两条文字链仍能
     *     `performScrollTo()` 后进入视口——口径照本文件既有 `assertMinTouchTarget`，钉「链不因头部改版而变得不可达」；
     *  2. 页头标题列不得塌成 0 宽：阈值 ≥48dp。反例形态是 `horizontalScroll().fillMaxWidth()`
     *     （emulator-5574 实测塌成 `DpRect(left = right = 20.190475.dp)` 即 0dp），组件的显式
     *     `fillMaxWidth()` 就是为它写的。**这条仍是本护栏的证伪力来源**，摘掉组件那枚 `fillMaxWidth()`
     *     会立刻被钉住。
     *
     * 返回位的 48dp 与描述在 2.0 档下不在本用例里钉（那一腿归组件，见上方
     * `headerTextLinksMeetTheMinimumTouchTarget` 的 KDoc）；若组件用例在 `fontScale 2.0` 下没钉过返回位，
     * 本席不扩面补，已登记给 Task 26。
     */
    @Test
    fun headerKeepsLinksReachableAndTitleReadableAtMaximumLargeFontScale() {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 2f)) {
                CheckInScreen(
                    state = CheckInUiState.Loading,
                    onBack = {},
                    onOpenFavorites = {},
                    onOpenWrongWords = {},
                )
            }
        }
        listOf("check_in_open_favorites", "check_in_open_wrong_words").forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo()
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
        val title = composeRule.onNodeWithText("学习打卡").getUnclippedBoundsInRoot()
        assertTrue(
            "fontScale=2.0 时页头标题不应塌成 0 宽（无界宽宿主反例实测 0dp），实际边界=$title",
            title.right - title.left >= 48.dp,
        )
    }
}
