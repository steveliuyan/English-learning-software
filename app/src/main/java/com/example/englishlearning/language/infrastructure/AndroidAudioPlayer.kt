package com.example.englishlearning.language.infrastructure

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidAudioPlayer(private val context: Context) : AudioPlayer {
    override suspend fun play(bytes: ByteArray, format: String): AudioPlaybackResult =
        suspendCancellableCoroutine { continuation ->
            val player = MediaPlayer()
            fun finish(result: AudioPlaybackResult) {
                if (continuation.isActive) continuation.resume(result)
                player.release()
            }
            try {
                player.setDataSource(ByteArrayMediaDataSource(bytes))
                player.setOnPreparedListener { it.start() }
                player.setOnCompletionListener { finish(AudioPlaybackResult.Played) }
                player.setOnErrorListener { _, _, _ ->
                    finish(AudioPlaybackResult.Failed)
                    true
                }
                player.prepareAsync()
                continuation.invokeOnCancellation { player.release() }
            } catch (_: Exception) {
                finish(AudioPlaybackResult.Failed)
            }
        }

    private class ByteArrayMediaDataSource(private val bytes: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(size, bytes.size - position.toInt())
            bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
            return count
        }

        override fun getSize(): Long = bytes.size.toLong()
        override fun close() = Unit
    }
}
