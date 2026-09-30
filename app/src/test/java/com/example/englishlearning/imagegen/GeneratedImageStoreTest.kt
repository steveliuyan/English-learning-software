package com.example.englishlearning.imagegen

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.net.AudioHttpRequest
import com.example.englishlearning.ai.net.AudioHttpResult
import com.example.englishlearning.ai.net.AudioHttpTransport
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

/**
 * 生图产物落地契约：b64 直接解码落盘；url 型结果经**二进制**通道（HTTPS-only、不跟随
 * 重定向）取回字节再落盘——两条路都必须最终给出一张 cache 里的文件，否则用户看不到图。
 */
class GeneratedImageStoreTest {

    private val directory = Files.createTempDirectory("image-store-test").toFile()

    private class RecordingAudioTransport : AudioHttpTransport {
        val requests = mutableListOf<AudioHttpRequest>()
        var result: AudioHttpResult = AudioHttpResult.Success(byteArrayOf())

        override suspend fun send(request: AudioHttpRequest): AudioHttpResult {
            requests += request
            return result
        }
    }

    private fun store(transport: RecordingAudioTransport = RecordingAudioTransport()) =
        GeneratedImageStore(transport, directory) to transport

    @Test
    fun base64ImageIsWrittenIntoTheDirectory() = runTest {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
        val (store, transport) = store()

        val file = store.persist(GeneratedImage.Base64(java.util.Base64.getEncoder().encodeToString(bytes))).getOrThrow()

        assertEquals(directory.absolutePath, file.parentFile.absolutePath)
        assertTrue(bytes.contentEquals(file.readBytes()))
        assertEquals(0, transport.requests.size) // b64 不需要再出网
    }

    @Test
    fun remoteUrlIsFetchedOverTheBinaryTransportAndWritten() = runTest {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 9, 9)
        val transport = RecordingAudioTransport().apply { result = AudioHttpResult.Success(bytes.copyOf()) }
        val (store, _) = store(transport)

        val file = store.persist(GeneratedImage.Url("https://cdn.example.com/img.jpg")).getOrThrow()

        assertEquals(1, transport.requests.size)
        val request = transport.requests.single()
        assertEquals("https://cdn.example.com/img.jpg", request.url)
        assertTrue(request.headers.isEmpty()) // 生图服务的密钥不得送给图片托管域名
        assertTrue(bytes.contentEquals(file.readBytes()))
    }

    @Test
    fun fetchedBytesAreZeroedAfterSaving() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val transport = RecordingAudioTransport().apply { result = AudioHttpResult.Success(bytes) }
        val (store, _) = store(transport)

        store.persist(GeneratedImage.Url("https://cdn.example.com/img.png")).getOrThrow()

        assertTrue(bytes.all { it == 0.toByte() })
    }

    @Test
    fun insecureUrlIsRejected() = runTest {
        val transport = RecordingAudioTransport().apply { result = AudioHttpResult.InsecureUrl }
        val (store, _) = store(transport)

        assertEquals(AiFailure.InvalidResponse, failureOf(store, "https://cdn.example.com/img.png"))
    }

    @Test
    fun httpErrorsMapToDomainFailures() = runTest {
        val unauthorized = RecordingAudioTransport().apply { result = AudioHttpResult.HttpError(401, byteArrayOf()) }
        assertEquals(AiFailure.Unauthorized, failureOf(store(unauthorized).first, "https://cdn.example.com/img.png"))

        val limited = RecordingAudioTransport().apply { result = AudioHttpResult.HttpError(429, byteArrayOf()) }
        assertEquals(AiFailure.RateLimited, failureOf(store(limited).first, "https://cdn.example.com/img.png"))

        val broken = RecordingAudioTransport().apply { result = AudioHttpResult.HttpError(503, byteArrayOf()) }
        assertEquals(AiFailure.ServerUnavailable, failureOf(store(broken).first, "https://cdn.example.com/img.png"))

        val missing = RecordingAudioTransport().apply { result = AudioHttpResult.HttpError(404, byteArrayOf()) }
        assertEquals(AiFailure.InvalidResponse, failureOf(store(missing).first, "https://cdn.example.com/img.png"))
    }

    @Test
    fun transportFailuresMapToDomainFailures() = runTest {
        val network = RecordingAudioTransport().apply { result = AudioHttpResult.NetworkUnavailable }
        assertEquals(AiFailure.NetworkUnavailable, failureOf(store(network).first, "https://cdn.example.com/img.png"))

        val timeout = RecordingAudioTransport().apply { result = AudioHttpResult.TimedOut }
        assertEquals(AiFailure.Timeout, failureOf(store(timeout).first, "https://cdn.example.com/img.png"))

        val tooLarge = RecordingAudioTransport().apply { result = AudioHttpResult.ResponseTooLarge }
        assertEquals(AiFailure.InvalidResponse, failureOf(store(tooLarge).first, "https://cdn.example.com/img.png"))
    }

    @Test
    fun cancelledDownloadIsRethrownAsCancellation() = runTest {
        val transport = RecordingAudioTransport().apply { result = AudioHttpResult.Cancelled }
        val (store, _) = store(transport)

        var threw = false
        try {
            store.persist(GeneratedImage.Url("https://cdn.example.com/img.png"))
        } catch (_: CancellationException) {
            threw = true
        }
        assertTrue(threw, "取消必须原样抛出，不能被吞成失败")
    }

    private suspend fun failureOf(store: GeneratedImageStore, url: String): AiFailure = try {
        store.persist(GeneratedImage.Url(url)).getOrThrow()
        fail("expected failure for $url")
    } catch (expected: AiException) {
        expected.failure
    }
}
