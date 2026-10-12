package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.PronunciationAccent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

interface KokoroEngine {
    suspend fun prepare(): Result<Unit>

    fun isReady(): Boolean

    suspend fun synthesize(text: String, accent: PronunciationAccent): Result<ByteArray>

    suspend fun close()
}

/** 只有成功返回调用方才交接音频所有权；跨 dispatcher 返回被取消时清零。 */
internal suspend fun withOwnedAudio(
    dispatcher: CoroutineDispatcher,
    synthesize: suspend CoroutineScope.() -> Result<ByteArray>,
): Result<ByteArray> {
    var owned: ByteArray? = null
    var delivered = false
    try {
        val result = withContext(dispatcher) {
            synthesize().also { owned = it.getOrNull() }
        }
        delivered = true
        return result
    } finally {
        if (!delivered) owned?.fill(0)
    }
}
