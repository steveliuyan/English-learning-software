package com.example.englishlearning.ai

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * AI 调用失败必须能变成「用户下一步能做什么」，而不是一句原始错误。
 *
 * 这里同时守的是泄露面：`AiFailure` 的每个成员都是不带字段的 `data object`，用户可见文案只能
 * 来自枚举常量。因此「把 401 的原始响应体、Endpoint 或 Key 拼进提示」在类型上就做不出来；
 * 下面的文案扫描测试负责在有人改成带字段的类时立刻失败。
 */
class AiFailureTest {
    @Test
    fun everyFailureReportsItsOwnUserText() {
        allFailures.forEach { failure ->
            assertTrue(failure.uiText.message.isNotBlank(), "${failure::class.simpleName} 的文案不能为空")
        }
        assertEquals(AiFailure.NotConfigured.uiText, AiFailure.NotConfigured.uiText)
    }

    @Test
    fun eachFailureOwnsExactlyOnePieceOfUserText() {
        val used = allFailures.map { it.uiText }

        assertEquals(allFailures.size, used.toSet().size, "两个失败不能共用同一条文案，否则用户分不清发生了什么")
        assertEquals(
            AiFailureUiText.entries.toSet(),
            used.toSet(),
            "文案枚举和失败集合必须一一对应，新增任一侧都要补齐另一侧",
        )
    }

    @Test
    fun everyFailureMapsToItsDocumentedAction() {
        assertEquals(UserAction.ConfigureProfile, AiFailure.NotConfigured.toUserAction())
        assertEquals(UserAction.SwitchModel, AiFailure.CapabilityUnsupported.toUserAction())
        assertEquals(UserAction.CheckNetwork, AiFailure.NetworkUnavailable.toUserAction())
        assertEquals(UserAction.ConfigureProfile, AiFailure.Unauthorized.toUserAction())
        assertEquals(UserAction.RetryLater, AiFailure.RateLimited.toUserAction())
        assertEquals(UserAction.RetryLater, AiFailure.ServerUnavailable.toUserAction())
        assertEquals(UserAction.RetryLater, AiFailure.Timeout.toUserAction())
        assertEquals(UserAction.Dismiss, AiFailure.Cancelled.toUserAction())
        assertEquals(UserAction.RegenerateContent, AiFailure.InvalidResponse.toUserAction())
    }

    @Test
    fun everyActionIsReachableFromAtLeastOneFailure() {
        val reachable = allFailures.map { it.toUserAction() }.toSet()

        assertEquals(
            UserAction.entries.toSet(),
            reachable,
            "有动作永远没人用，说明映射漏了或动作多余了",
        )
    }

    @Test
    fun cancellationIsNotReportedAsANetworkProblem() {
        // 用户自己取消不该被说成「网络不可用」，否则会被引导去排查根本没坏的东西。
        assertNotEquals(AiFailure.NetworkUnavailable.toUserAction(), AiFailure.Cancelled.toUserAction())
        assertNotEquals(AiFailure.Timeout.toUserAction(), AiFailure.Cancelled.toUserAction())
    }

    @Test
    fun userTextCarriesNoUrlKeyOrRawResponse() {
        val forbidden = listOf("http", "://", "Bearer", "sk-", "api_key", "token", "{", "}", "\\", "\n", "\r")

        allFailures.forEach { failure ->
            assertHasNoLeak(failure.uiText.message, "${failure::class.simpleName} 的文案")
        }
        UserAction.entries.forEach { action ->
            assertHasNoLeak(action.label, "动作 ${action.name} 的按钮文案")
        }
        forbidden.forEach { fragment ->
            assertTrue(
                allFailures.none { it.uiText.message.contains(fragment, ignoreCase = true) },
                "任何用户可见文案都不该出现「$fragment」",
            )
        }
    }

    @Test
    fun userTextStaysShortEnoughForADialogAndAButton() {
        allFailures.forEach { failure ->
            assertTrue(
                failure.uiText.message.length <= 64,
                "${failure::class.simpleName} 的文案 ${failure.uiText.message.length} 字，对话框里会读不下去",
            )
        }
        UserAction.entries.forEach { action ->
            assertTrue(action.label.length <= 8, "按钮文案「${action.label}」太长，一行放不下")
        }
    }

    private fun assertHasNoLeak(text: String, what: String) {
        assertTrue(text.isNotBlank(), "$what 不能为空")
        assertTrue(!text.contains("  "), "$what 里有多余空白：$text")
    }

    private val allFailures = listOf(
        AiFailure.NotConfigured,
        AiFailure.CapabilityUnsupported,
        AiFailure.NetworkUnavailable,
        AiFailure.Unauthorized,
        AiFailure.RateLimited,
        AiFailure.ServerUnavailable,
        AiFailure.Timeout,
        AiFailure.Cancelled,
        AiFailure.InvalidResponse,
    )
}
