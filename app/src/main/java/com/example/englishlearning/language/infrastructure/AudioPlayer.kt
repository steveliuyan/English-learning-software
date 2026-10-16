package com.example.englishlearning.language.infrastructure

interface AudioPlayer {
    suspend fun play(bytes: ByteArray, format: String): AudioPlaybackResult

    fun pauseCurrentPlayback(): Boolean = false
    fun resumeCurrentPlayback(): Boolean = false
    fun stopCurrentPlayback() { }
}

sealed interface AudioPlaybackResult {
    data object Played : AudioPlaybackResult
    data object Failed : AudioPlaybackResult
}
