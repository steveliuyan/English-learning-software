package com.example.englishlearning.ui

import android.provider.Settings
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.screentranslation.ScreenTranslationMode
import com.example.englishlearning.screentranslation.ScreenTranslationNotice
import com.example.englishlearning.screentranslation.ScreenTranslationPhase
import com.example.englishlearning.screentranslation.ScreenTranslationSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 悬浮窗屏幕翻译入口段（F3-05）的 connected 形状：**出站确认框真的弹出、确认真的把域名交回宿主**，
 * 以及「会话进行中」那一帧的成本句与停止位。
 *
 * 为什么这一层必须有：服务在首次发送前会主动停会话、把域名塞进快照等确认（见
 * `ScreenTranslationService` 的 NeedsConfirmation 分支）。那段接线如果断了，用户看到的是
 * 「会话停了、什么都没说」——比报错更难查。这里用固定快照把「快照里域名 → 对话框 → 回调」这一段钉住，
 * 不依赖真机上有可用的 AI Profile。
 *
 * 确认之后**不能真的去申请截屏**：那会把系统授权弹窗拉起来、污染后续用例。所以点确认的那条用例
 * 先把 SYSTEM_ALERT_WINDOW 临时关掉——`requestSession` 在没权限时只写一句提示就返回，
 * 既验证了「确认回调收到了域名」，又证明了「没权限时不会去拉系统弹窗」。
 */
@RunWith(AndroidJUnit4::class)
class ScreenTranslationEntryTest {
    @get:Rule val composeRule = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @After
    fun restoreOverlayPermission() {
        setOverlayAllowed(true)
    }

    /** 快照里有待确认域名 → 共享出站确认框弹出，正文点名域名；「取消」只回调、不当确认。 */
    @Test
    fun theWaitingHostRaisesTheSharedOutboundDialogAndDismissingIsNotAConfirmation() {
        var declined = 0
        var confirmed: String? = null
        setScreen(
            state = ScreenTranslationSnapshot(pendingConfirmationHost = HOST),
            onConfirm = { confirmed = it },
            onDecline = { declined++ },
        )

        composeRule
            .onNodeWithTag("screen_translation_outbound_confirmation_text")
            .assertExists()
            .assertTextContains(HOST, substring = true)

        composeRule.onNodeWithTag("screen_translation_decline_outbound").performClick()
        composeRule.waitForIdle()

        assertEquals("取消只该回调一次 onDeclineOutbound", 1, declined)
        assertNull("取消不该被当成确认", confirmed)
    }

    /** 「确认」把待确认的域名原样交回宿主，且（无悬浮窗权限时）不会去拉截屏授权弹窗。 */
    @Test
    fun confirmingHandsTheHostBackWithoutStartingACaptureSession() {
        // 先关掉悬浮窗权限：requestSession 会因此只提示、不申请截屏，系统弹窗不会被拉起。
        setOverlayAllowed(false)
        var confirmed: String? = null
        setScreen(
            state = ScreenTranslationSnapshot(pendingConfirmationHost = HOST),
            onConfirm = { confirmed = it },
        )

        // 展开段内面板，好让「没权限 → 只提示」那句话真的被组合出来。
        composeRule.onNodeWithTag("screen_translation_entry").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("screen_translation_confirm_outbound").performClick()
        composeRule.waitForIdle()

        assertEquals("确认回调必须拿到那个域名", HOST, confirmed)
        assertTaggedShowsText("screen_translation_start_notice", "显示在其他应用上层")
    }

    /** 会话进行中：成本句必须同时给出「发了几次」与「发给哪个模型」，停止位可用，开始位不可用。 */
    @Test
    fun aRunningSessionShowsItsCostsAndCanBeStopped() {
        var stops = 0
        setScreen(
            state =
                ScreenTranslationSnapshot(
                    phase = ScreenTranslationPhase.Running,
                    mode = ScreenTranslationMode.Continuous,
                    sentRequests = 3,
                    drawnBlocks = 5,
                    skippedBlocks = 1,
                    modelName = "gpt-4o-mini",
                ),
            onStop = { stops++ },
        )

        composeRule.onNodeWithTag("screen_translation_entry").performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("screen_translation_cost_line")
            .assertExists()
            .assertTextContains("已发 3 次请求", substring = true)
        composeRule.onNodeWithTag("screen_translation_cost_line").assertTextContains("gpt-4o-mini", substring = true)
        composeRule.onNodeWithTag("screen_translation_once_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("screen_translation_continuous_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("screen_translation_stop_button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, stops)
    }

    /**
     * 「这一帧没有可翻的英文」只是提示，**不是会话级错误**（F3-05 真机走查后改的口径）。
     *
     * 旧行为是把 OcrFailed 当失败结束会话——连续模式下只要第一帧是中文界面，功能一开就死。
     * 这条用例把新口径钉在界面上：提示出现的同时，「会话进行中」与「停止屏幕翻译」都还在。
     */
    @Test
    fun aFrameWithoutEnglishOnlyWarnsAndKeepsTheSessionRunning() {
        setScreen(
            state =
                ScreenTranslationSnapshot(
                    phase = ScreenTranslationPhase.Running,
                    mode = ScreenTranslationMode.Continuous,
                    modelName = "gpt-4o-mini",
                    notice = ScreenTranslationNotice.OcrFailed,
                ),
        )

        composeRule.onNodeWithTag("screen_translation_entry").performClick()
        composeRule.waitForIdle()

        assertTaggedShowsText("screen_translation_notice", "这一屏没读出文字")
        composeRule.onNodeWithTag("screen_translation_stop_button").assertExists()
    }

    // ---- 夹具 ----

    private fun setScreen(
        state: ScreenTranslationSnapshot,
        onConfirm: (String) -> Unit = {},
        onDecline: () -> Unit = {},
        onStop: () -> Unit = {},
    ) {
        composeRule.setContent {
            ScreenTranslationSection(
                state = state,
                onConfirmOutbound = onConfirm,
                onDeclineOutbound = onDecline,
                onStop = onStop,
            )
        }
    }

    /**
     * 断言某个 testTag 的那一块里出现了含 [text] 的文字。
     *
     * 为什么要往下找一层：`InlineNotice` 的 testTag 挂在容器上，文字在子 Text 里；
     * 容器本身没有合并语义（不是可点节点），所以 `onNodeWithTag(tag).assertTextContains(...)` 会「节点存在但没文字」地失败。
     */
    private fun assertTaggedShowsText(
        tag: String,
        text: String,
    ) {
        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag(tag, useUnmergedTree = true)
            .assertExists()
            .assert(hasAnyDescendant(hasText(text, substring = true)))
    }

    /** 改这台设备的 SYSTEM_ALERT_WINDOW 授权状态（shell 命令）；appops 生效是异步的，轮询到一致为止。 */
    private fun setOverlayAllowed(allowed: Boolean) {
        val mode = if (allowed) "allow" else "deny"
        instrumentation.uiAutomation
            .executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $mode")
            .close()
        val deadline = System.currentTimeMillis() + 2_000
        while (Settings.canDrawOverlays(context) != allowed && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
    }

    private companion object {
        const val HOST = "api.example.com"
    }
}
