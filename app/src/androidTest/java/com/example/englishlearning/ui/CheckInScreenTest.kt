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
        // 批 1 Task 8（§5）：连续天数是唯一大数字；完成度环整块删除。
        composeRule.onNodeWithTag("check_in_totals").assertExists()
        composeRule.onNodeWithTag("check_in_streak_days").assertExists()
        composeRule.onNodeWithTag("check_in_streak_text").assertExists()
        composeRule.onNodeWithTag("check_in_totals_text").assertExists()
        composeRule.onNodeWithTag("check_in_completion_ratio").assertDoesNotExist()
        composeRule.onNodeWithTag("check_in_completion_percent").assertDoesNotExist()
        composeRule.onNodeWithTag("check_in_completion_arc").assertDoesNotExist()
        composeRule.onNodeWithTag("check_in_week_chart").assertExists()
        (0..6).forEach { index -> composeRule.onNodeWithTag("check_in_week_bar_$index").assertExists() }
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
        // 环删除后，强调色的三处落点是：周柱、连续天数（唯一大数字）、月历已打卡日期。
        assertContainsColor("check_in_streak_days", orange)
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
            )
        }
        composeRule.onNodeWithTag("app_screen_header").assertIsDisplayed()
        composeRule.onNodeWithText("学习打卡").assertIsDisplayed()
        composeRule.onNodeWithTag("check_in_back").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("返回").assertHasClickAction().performClick()
        assertEquals(1, backs)
    }

    /**
     * Task 22-B（RED-1 闭集判据）：打卡页页头不再承载「我的收藏 / 错词本」两条裸文字链。
     *
     * RED 阶段（BASE）页头 action 槽里仍钉着这两枚 tag，本条 `assertDoesNotExist` 先红；
     * GREEN 阶段 22-B 把两枚形参与两条链一并摘除（编译面已咬死：`CheckInScreen` 不再接受
     * `onOpenFavorites` / `onOpenWrongWords`），这里只以 `onBack` 组页头，两条 tag 必须不存在，
     * 作为「链不被悄悄补回 action 槽」的闭集护栏保留。收藏 / 错词的全站唯一出口是合并页 `MyWordsScreen`。
     */
    @Test fun openFavoritesAndWrongWordsLinksAreNotRenderedOnCheckInHeader() {
        composeRule.setContent {
            CheckInScreen(
                state = CheckInUiState.Loading,
                onBack = {},
            )
        }
        composeRule.onNodeWithTag("check_in_open_favorites").assertDoesNotExist()
        composeRule.onNodeWithTag("check_in_open_wrong_words").assertDoesNotExist()
    }

    /**
     * 零目标日的空态：旧版这里断言的是完成度环上的「0%」；批 1 Task 8 把环删了，
     * 空态的真实证据改由两处承担——连续天数的 Display 数字仍是「0」（不因零目标而消失），
     * 今日卡显示「完成任务 0/0」，而环的三枚锚一枚都不许回来。
     */
    @Test fun zero_target_shows_zero_counts_without_a_completion_ring() {
        val date = LocalDate.of(2026, 9, 25)
        val zero = DailyLearningStats(date, 0, 0, 0, 0)
        composeRule.setContent { CheckInScreen(CheckInUiState.Ready(zero, List(7) { zero }, listOf(zero), false), onBack = {}) }
        composeRule.onNodeWithTag("check_in_streak_days").assertTextContains("0")
        composeRule.onNodeWithText("完成任务 0/0", substring = true).assertExists()
        composeRule.onNodeWithTag("check_in_completion_ratio").assertDoesNotExist()
    }

    /**
     * F5-03 / Task 11（§E.2，Task 10 评审 I-4 的护栏）+ Task 22-A §二.1 的改版口径 + Task 22-B 摘链后的口径。
     *
     * **本用例钉的形状已随统一页头改版换掉了宿主**：原先它钉的是屏侧私有页头 `Row` 的修饰符顺序
     * （`fillMaxWidth()` 在前、`horizontalScroll` 在后）与它带来的标题塌宽反例；Task 22-A 把页头交给共享
     * [com.example.englishlearning.ui.components.AppScreenHeader] 后，那条私有 `Row` 与 `horizontalScroll`
     * 都不存在了，`horizontalScroll` 反例形态的教训迁进了组件自己的宿主（`AppScreenHeader` KDoc 已写明
     * 「`Row` 显式 `fillMaxWidth()`：无界宽宿主下标题列会塌宽（`CheckInScreenTest` 钉过这个反例形态）」），
     * L-T11-1 的账由「换用统一页头组件」这一动作结。
     *
     * Task 22-B（R-22.1）摘除打卡页两条裸文字链后，本用例不再钉「链可达」那一腿（链已不存在，钉它的
     * `headerTextLinksMeetTheMinimumTouchTarget` 已随摘链一并删除），只保留改版后仍然成立、且仍是本护栏
     * 证伪力来源的一条可观测性质：
     *  1. 页头标题列不得塌成 0 宽：阈值 ≥48dp。反例形态是 `horizontalScroll().fillMaxWidth()`
     *     （emulator-5574 实测塌成 `DpRect(left = right = 20.190475.dp)` 即 0dp），组件的显式
     *     `fillMaxWidth()` 就是为它写的。摘掉组件那枚 `fillMaxWidth()` 会立刻被钉住。
     *
     * 返回位（48dp 图标位）的可达性归组件用例 `AppScreenHeaderTest`，本用例不扩面；
     * 收藏 / 错词入口在 22-B 后已整体下线，其能力护栏见 `MyWordsScreenTest`。
     */
    @Test
    fun headerTitleStaysReadableAtMaximumLargeFontScale() {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 2f)) {
                CheckInScreen(
                    state = CheckInUiState.Loading,
                    onBack = {},
                )
            }
        }
        val title = composeRule.onNodeWithText("学习打卡").getUnclippedBoundsInRoot()
        assertTrue(
            "fontScale=2.0 时页头标题不应塌成 0 宽（无界宽宿主反例实测 0dp），实际边界=$title",
            title.right - title.left >= 48.dp,
        )
    }
}
