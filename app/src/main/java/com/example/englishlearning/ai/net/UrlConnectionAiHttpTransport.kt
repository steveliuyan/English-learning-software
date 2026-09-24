package com.example.englishlearning.ai.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * [AiHttpTransport] 的零依赖实现：JDK 内置 [HttpURLConnection]。
 *
 * 只在 [ioDispatcher] 上执行阻塞 IO；所有失败都折叠成 [AiHttpResult] 的一个成员，
 * 不抛异常——调用方的 `when` 穷尽性就是全部错误路径的清单。
 */
class UrlConnectionAiHttpTransport(
    private val ioDispatcher: CoroutineDispatcher,
    private val maxResponseBytes: Int = 512 * 1024,
) : AiHttpTransport {
    override suspend fun send(request: AiHttpRequest): AiHttpResult = withContext(ioDispatcher) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                // 不跟随重定向：跟随会把「公网域名 → 私网地址」这条被 validateEndpoint 挡掉的路重新打开。
                instanceFollowRedirects = false
                connectTimeout = request.timeoutSeconds * 1000
                readTimeout = request.timeoutSeconds * 1000
                request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            if (request.method == "GET") {
                // GET 不声明 doOutput：HttpURLConnection 会拒绝带输出的 GET，而抓取本来就没有 body。
                check(request.body.isEmpty()) { "GET requests must not carry a body" }
            } else {
                connection.doOutput = true
                connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readCapped(it, maxResponseBytes) } ?: ReadOutcome.Text("")
            when (body) {
                is ReadOutcome.TooLarge -> AiHttpResult.ResponseTooLarge
                is ReadOutcome.Text -> AiHttpResult.Responded(AiHttpResponse(status, body.value))
            }
        } catch (timeout: SocketTimeoutException) {
            AiHttpResult.TimedOut
        } catch (cancellation: CancellationException) {
            // 必须在 IOException 之前：CancellationException 是它的子类。
            // 用户离开页面不等于网络坏了，二者不能折叠成同一个结果。
            AiHttpResult.Cancelled
        } catch (io: IOException) {
            AiHttpResult.NetworkUnavailable
        } finally {
            connection?.disconnect()
        }
    }

    private sealed interface ReadOutcome {
        data class Text(val value: String) : ReadOutcome
        data object TooLarge : ReadOutcome
    }

    /**
     * 逐块读并累计字节数，超过上限立即返回 [ReadOutcome.TooLarge]。
     * **不要**先 `readBytes()` 再判长度——那已经吃掉了整个响应体，上限就失去意义。
     */
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
        return ReadOutcome.Text(output.toString("UTF-8"))
    }
}
