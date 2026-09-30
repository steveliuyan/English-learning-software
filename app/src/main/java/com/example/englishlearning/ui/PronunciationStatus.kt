package com.example.englishlearning.ui

import com.example.englishlearning.language.domain.PronunciationResult

enum class PronunciationStatus {
    Idle,
    Played,
    Unavailable,
    Failed,
}

fun PronunciationStatus.message(): String? =
    when (this) {
        PronunciationStatus.Idle -> null
        PronunciationStatus.Played -> "发音已播放。"
        PronunciationStatus.Unavailable -> "当前语音不可用，请检查语音设置。"
        PronunciationStatus.Failed -> "发音播放失败，请重试。"
    }

fun pronunciationStatus(result: PronunciationResult?): PronunciationStatus =
    when (result) {
        PronunciationResult.Played -> PronunciationStatus.Played
        is PronunciationResult.Unavailable -> PronunciationStatus.Unavailable
        is PronunciationResult.Failed -> PronunciationStatus.Failed
        null -> PronunciationStatus.Unavailable
    }
