package com.example.englishlearning.language.infrastructure

interface AudioPlayer {
    suspend fun play(bytes: ByteArray, format: String): AudioPlaybackResult
}

sealed interface AudioPlaybackResult {
    data object Played : AudioPlaybackResult
    data object Failed : AudioPlaybackResult
}
