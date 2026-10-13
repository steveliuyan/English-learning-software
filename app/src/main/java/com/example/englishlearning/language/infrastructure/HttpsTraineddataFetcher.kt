package com.example.englishlearning.language.infrastructure

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Streams the pinned English traineddata artifact over HTTPS only. Integrity is not trusted from the
 * transport: [FileEnglishOcrModelAssets] verifies size and SHA-256 against [ENGLISH_TESSDATA_FAST_PIN]
 * before the file becomes visible to the engine.
 */
class HttpsTraineddataFetcher(
    private val url: URL = DEFAULT_URL,
) : TraineddataFetcher {
    override suspend fun fetch(
        onBytesRead: (Int) -> Unit,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            require(url.protocol.equals("https", ignoreCase = true)) { "Model downloads must use HTTPS" }
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = true
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw java.io.IOException("Model download failed with HTTP ${connection.responseCode}")
                }
                require(connection.url.protocol.equals("https", ignoreCase = true)) { "Redirects to non-HTTPS are rejected" }
                val buffer = ByteArrayOutputStream(STREAM_BUFFER)
                connection.inputStream.use { input ->
                    val chunk = ByteArray(STREAM_BUFFER)
                    var total = 0
                    while (true) {
                        ensureActive()
                        val read = input.read(chunk)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) throw java.io.IOException("Model download exceeded the size limit")
                        buffer.write(chunk, 0, read)
                        onBytesRead(total)
                    }
                }
                buffer.toByteArray()
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        val DEFAULT_URL =
            URL("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/eng.traineddata")
        const val TIMEOUT_MS = 15_000
        const val STREAM_BUFFER = 64 * 1024
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}
