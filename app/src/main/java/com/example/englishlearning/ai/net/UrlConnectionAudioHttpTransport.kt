package com.example.englishlearning.ai.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** JDK-only HTTPS binary transport. */
class UrlConnectionAudioHttpTransport(
    private val ioDispatcher: CoroutineDispatcher,
    private val maxResponseBytes: Int = 5 * 1024 * 1024,
) : AudioHttpTransport {
    override suspend fun send(request: AudioHttpRequest): AudioHttpResult = withContext(ioDispatcher) {
        val url = try {
            URL(request.url)
        } catch (_: IOException) {
            return@withContext AudioHttpResult.NetworkUnavailable
        }
        if (url.protocol != "https") return@withContext AudioHttpResult.InsecureUrl

        var connection: HttpsURLConnection? = null
        try {
            connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = request.method
                instanceFollowRedirects = false
                connectTimeout = request.timeoutSeconds * 1000
                readTimeout = request.timeoutSeconds * 1000
                request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            if (request.body.isNotEmpty()) {
                connection.doOutput = true
                connection.outputStream.use { it.write(request.body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readCapped(it, maxResponseBytes) } ?: ReadOutcome.Bytes(byteArrayOf())
            when (body) {
                ReadOutcome.TooLarge -> AudioHttpResult.ResponseTooLarge
                is ReadOutcome.Bytes -> if (status in 200..299) {
                    AudioHttpResult.Success(body.value)
                } else {
                    AudioHttpResult.HttpError(status, body.value)
                }
            }
        } catch (_: SocketTimeoutException) {
            AudioHttpResult.TimedOut
        } catch (_: CancellationException) {
            AudioHttpResult.Cancelled
        } catch (_: IOException) {
            AudioHttpResult.NetworkUnavailable
        } finally {
            connection?.disconnect()
        }
    }

    private sealed interface ReadOutcome {
        data class Bytes(val value: ByteArray) : ReadOutcome
        data object TooLarge : ReadOutcome
    }

    private fun readCapped(stream: InputStream, cap: Int): ReadOutcome {
        val output = ByteArrayOutputStream(minOf(cap, 64 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > cap) return ReadOutcome.TooLarge
            output.write(buffer, 0, read)
        }
        return ReadOutcome.Bytes(output.toByteArray())
    }
}
