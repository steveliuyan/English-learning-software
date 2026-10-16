package com.example.englishlearning.language.infrastructure

interface AudioPlayer {
    /**
     * `rate` 为播放侧变速倍率（F3-02 四控之速，决策 2026-10-15 §2）：
     * 只改变本机播放速度，不影响已生成/已缓存音频本身，也不参与任何 AI 请求与缓存键。
     */
    suspend fun play(bytes: ByteArray, format: String, rate: Float = 1f): AudioPlaybackResult

    fun pauseCurrentPlayback(): Boolean = false
    fun resumeCurrentPlayback(): Boolean = false
    fun stopCurrentPlayback() { }
}

sealed interface AudioPlaybackResult {
    data object Played : AudioPlaybackResult
    data object Failed : AudioPlaybackResult
}
