package com.example.englishlearning.screentranslation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** F3-05：会话状态迁移必须是纯计算——界面与服务读到的是同一份事实（请求数、模型名、错误分类）。 */
class ScreenTranslationSessionTest {
    private val idle = ScreenTranslationSnapshot()

    private fun started(
        mode: ScreenTranslationMode = ScreenTranslationMode.Continuous,
        modelName: String? = "gpt-4o-mini",
    ): ScreenTranslationSnapshot = ScreenTranslationReducer.reduce(idle, ScreenTranslationEvent.Started(mode, modelName))

    @Test
    fun startingASessionResetsCountersAndShowsTheModel() {
        val state = started()

        assertTrue(state.running)
        assertEquals(ScreenTranslationMode.Continuous, state.mode)
        assertEquals(0, state.sentRequests)
        assertEquals("gpt-4o-mini", state.modelName)
        assertNull(state.notice)
    }

    @Test
    fun restartingASessionZeroesPreviousCounters() {
        val running = ScreenTranslationReducer.reduce(started(), ScreenTranslationEvent.FrameTranslated(drawnBlocks = 4, skippedBlocks = 1))

        val restarted = ScreenTranslationReducer.reduce(running, ScreenTranslationEvent.Started(ScreenTranslationMode.Once, "m"))

        assertEquals(0, restarted.sentRequests)
        assertEquals(0, restarted.drawnBlocks)
        assertEquals(0, restarted.skippedBlocks)
    }

    @Test
    fun eachTranslatedFrameCountsAsOneRequest() {
        val once = ScreenTranslationReducer.reduce(started(), ScreenTranslationEvent.FrameTranslated(3, 2))
        val twice = ScreenTranslationReducer.reduce(once, ScreenTranslationEvent.FrameTranslated(5, 0))

        assertEquals(2, twice.sentRequests)
        assertEquals(5, twice.drawnBlocks)
        assertEquals(0, twice.skippedBlocks)
    }

    @Test
    fun skippedFramesCostNothing() {
        val state = ScreenTranslationReducer.reduce(started(), ScreenTranslationEvent.FrameSkipped(CaptureSkipReason.Throttled))

        assertEquals(0, state.sentRequests)
    }

    @Test
    fun onlyFailuresThatReallyLeftTheDeviceAreCounted() {
        val beforeSending = ScreenTranslationReducer.reduce(
            started(),
            ScreenTranslationEvent.Failure(ScreenTranslationNotice.OcrUnavailable),
        )
        val afterSending = ScreenTranslationReducer.reduce(
            started(),
            ScreenTranslationEvent.Failure(ScreenTranslationNotice.TranslationFailed, countsAsRequest = true),
        )

        assertEquals(0, beforeSending.sentRequests)
        assertEquals(ScreenTranslationNotice.OcrUnavailable, beforeSending.notice)
        assertEquals(1, afterSending.sentRequests)
    }

    @Test
    fun ocrUnavailableIsNeverReportedAsATranslationFailure() {
        val state = ScreenTranslationReducer.reduce(started(), ScreenTranslationEvent.Failure(ScreenTranslationNotice.OcrUnavailable))

        assertEquals(ScreenTranslationNotice.OcrUnavailable, state.notice)
    }

    @Test
    fun aFrameWithoutEnglishKeepsTheSessionRunningSoTheUserCanSwitchScreens() {
        // 真机走查暴露：用户在自己的中文界面点开始，第一帧必然没有英文。
        // 「这一屏没读出字」只报一句，会话必须还活着（否则连续模式等于一开就死）。
        val state = ScreenTranslationReducer.reduce(started(), ScreenTranslationEvent.Failure(ScreenTranslationNotice.OcrFailed))

        assertTrue(state.running, "没读出字不能把会话判死")
        assertEquals(ScreenTranslationNotice.OcrFailed, state.notice)
        assertEquals(0, state.sentRequests)
    }

    @Test
    fun pendingConfirmationIsShownUntilTheNextSuccessfulFrame() {
        val pending = ScreenTranslationReducer.reduce(started(), ScreenTranslationEvent.ConfirmationRequired("api.example.com"))
        assertEquals("api.example.com", pending.pendingConfirmationHost)

        val translated = ScreenTranslationReducer.reduce(pending, ScreenTranslationEvent.FrameTranslated(1, 0))

        assertNull(translated.pendingConfirmationHost)
    }

    @Test
    fun stoppingTurnsTheModeOffButKeepsTheSessionLedger() {
        val running = ScreenTranslationReducer.reduce(started(), ScreenTranslationEvent.FrameTranslated(2, 1))

        val stopped = ScreenTranslationReducer.reduce(running, ScreenTranslationEvent.Stopped())

        assertEquals(ScreenTranslationPhase.Stopped, stopped.phase)
        assertEquals(ScreenTranslationMode.Off, stopped.mode)
        assertEquals(1, stopped.sentRequests)
        assertNull(stopped.pendingConfirmationHost)
    }

    @Test
    fun stoppingWithANoticeKeepsTheReasonForTheUi() {
        val stopped = ScreenTranslationReducer.reduce(started(), ScreenTranslationEvent.Stopped(ScreenTranslationNotice.ProjectionDenied))

        assertEquals(ScreenTranslationNotice.ProjectionDenied, stopped.notice)
    }
}
