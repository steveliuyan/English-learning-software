package com.example.englishlearning.ai.net

/** 二进制音频请求。仅允许 HTTPS URL。 */
data class AudioHttpRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val timeoutSeconds: Int = 15,
    val method: String = "GET",
    val body: ByteArray = byteArrayOf(),
)

sealed interface AudioHttpResult {
    data class Success(val body: ByteArray) : AudioHttpResult
    data class HttpError(val statusCode: Int, val body: ByteArray) : AudioHttpResult
    data object InsecureUrl : AudioHttpResult
    data object NetworkUnavailable : AudioHttpResult
    data object TimedOut : AudioHttpResult
    data object Cancelled : AudioHttpResult
    data object ResponseTooLarge : AudioHttpResult
}

interface AudioHttpTransport {
    suspend fun send(request: AudioHttpRequest): AudioHttpResult
}
