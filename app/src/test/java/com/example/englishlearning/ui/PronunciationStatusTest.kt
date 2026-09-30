package com.example.englishlearning.ui

import com.example.englishlearning.language.domain.PronunciationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PronunciationStatusTest {
    @Test
    fun playedResultHasSuccessMessage() {
        assertEquals("发音已播放。", pronunciationStatus(PronunciationResult.Played).message())
    }

    @Test
    fun unavailableResultHidesProviderReason() {
        assertEquals(
            "当前语音不可用，请检查语音设置。",
            pronunciationStatus(PronunciationResult.Unavailable("secret endpoint")).message(),
        )
    }

    @Test
    fun failedResultUsesRetryMessageWithoutExposingCause() {
        assertEquals(
            "发音播放失败，请重试。",
            pronunciationStatus(PronunciationResult.Failed(IllegalStateException("secret"))).message(),
        )
    }

    @Test
    fun missingProviderIsUnavailableAndIdleHasNoMessage() {
        assertEquals(PronunciationStatus.Unavailable, pronunciationStatus(null))
        assertNull(PronunciationStatus.Idle.message())
    }
}
