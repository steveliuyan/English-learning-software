package com.example.englishlearning.screentranslation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F3-05 的会话台账：谁清、什么时候清，是这条闭环唯一容易写错的地方。
 *
 * 关键一条：**开始会话不清理按域名的确认台账**——确认之后必须重新申请一次 MediaProjection 授权
 * 才能继续，那次重启如果顺手清了台账，用户会被反复问同一个域名。
 */
class ScreenTranslationControllerTest {
    private fun controller() = ScreenTranslationController()

    @Test
    fun confirmingAHostRemembersItForTheRestOfTheSession() {
        val controller = controller()

        controller.beginSession(ScreenTranslationMode.Continuous, "gpt-4o-mini")
        controller.confirmTextHost("api.example.com")

        assertEquals(setOf("api.example.com"), controller.confirmedTextHosts.value)
    }

    @Test
    fun restartingASessionKeepsTheConfirmationItJustCollected() {
        val controller = controller()

        controller.beginSession(ScreenTranslationMode.Once, null)
        controller.confirmTextHost("api.example.com")
        // 确认后为了拿新的 MediaProjection 令牌，会再开一次会话。
        controller.beginSession(ScreenTranslationMode.Once, null)

        assertEquals(setOf("api.example.com"), controller.confirmedTextHosts.value)
    }

    @Test
    fun endingASessionForgetsEveryConfirmedHost() {
        val controller = controller()

        controller.beginSession(ScreenTranslationMode.Continuous, null)
        controller.confirmTextHost("api.example.com")
        controller.endSession()

        assertTrue(controller.confirmedTextHosts.value.isEmpty())
    }

    @Test
    fun sessionStateGoesFromIdleToRunningToStopped() {
        val controller = controller()
        assertEquals(ScreenTranslationPhase.Idle, controller.state.value.phase)

        controller.beginSession(ScreenTranslationMode.Continuous, "gpt-4o-mini")
        assertTrue(controller.state.value.running)
        assertEquals("gpt-4o-mini", controller.state.value.modelName)

        controller.endSession(ScreenTranslationNotice.ProjectionDenied)
        assertEquals(ScreenTranslationPhase.Stopped, controller.state.value.phase)
        assertEquals(ScreenTranslationNotice.ProjectionDenied, controller.state.value.notice)
    }

    @Test
    fun dismissingAPendingConfirmationClearsItWithoutTouchingTheLedger() {
        val controller = controller()

        controller.beginSession(ScreenTranslationMode.Once, null)
        controller.dispatch(ScreenTranslationEvent.ConfirmationRequired("api.example.com"))
        assertEquals("api.example.com", controller.state.value.pendingConfirmationHost)

        controller.dismissPendingConfirmation()

        assertNull(controller.state.value.pendingConfirmationHost)
        assertTrue(controller.confirmedTextHosts.value.isEmpty(), "放弃确认不得写台账")
    }

    @Test
    fun stoppingKeepsThePendingHostUntilTheUserAnswers() {
        val controller = controller()

        controller.beginSession(ScreenTranslationMode.Continuous, null)
        controller.dispatch(ScreenTranslationEvent.ConfirmationRequired("api.example.com"))
        controller.dispatch(ScreenTranslationEvent.Stopped())

        // 服务停下来等确认时，域名必须还留着——否则界面弹不出确认框。
        assertEquals("api.example.com", controller.state.value.pendingConfirmationHost)
    }
}
