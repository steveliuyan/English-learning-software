package com.example.englishlearning.ai.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 计划原文用 `com.sun.net.httpserver`，但本项目 `sourceCompatibility = 17` 使 Kotlin 编译带上
 * `-Xjdk-release=17`，ct.sym 不含 `com.sun.*` 内部包，该 API 在 unit test 源集里不可见（2026-09-24 实测）。
 * 因此改用手写 [FakeHttpServer]：同为 JDK 内置（java.net），零新依赖，行为只覆盖本测试需要的一小角。
 */
class UrlConnectionAiHttpTransportTest {
    private var server: FakeHttpServer? = null
    private var port = 0
    private var lastSeenBody = ""
    private var lastSeenAuth = ""
    private var lastSeenMethod = ""

    @BeforeTest
    fun start() {
        server = FakeHttpServer { method, path, headers, body ->
            lastSeenMethod = method
            if (path.startsWith("/ok")) {
                lastSeenBody = body
                lastSeenAuth = headers["authorization"] ?: ""
                FakeResponse(200, """{"choices":[{"message":{"content":"hi"}}]}""")
            } else if (path.startsWith("/unauthorized")) {
                FakeResponse(401, """{"error":{"message":"bad key"}}""")
            } else if (path.startsWith("/redirect")) {
                FakeResponse(302, "", headers = mapOf("Location" to "/ok"))
            } else if (path.startsWith("/slow")) {
                Thread.sleep(2000)
                FakeResponse(200, "late")
            } else if (path.startsWith("/huge")) {
                FakeResponse(200, "x".repeat(600 * 1024))
            } else {
                FakeResponse(404, "no route")
            }
        }.also {
            it.start()
            port = it.port
        }
    }

    @AfterTest
    fun stop() {
        server?.stop()
        server = null
    }

    private val transport = UrlConnectionAiHttpTransport(Dispatchers.IO)

    private fun request(path: String, timeout: Int = 5) = AiHttpRequest(
        url = "http://127.0.0.1:$port$path",
        headers = mapOf("Content-Type" to "application/json", "Authorization" to "Bearer test-key"),
        body = """{"model":"m"}""",
        timeoutSeconds = timeout,
    )

    private fun getRequest(path: String) = AiHttpRequest(
        url = "http://127.0.0.1:$port$path",
        headers = emptyMap(),
        body = "",
        timeoutSeconds = 5,
        method = "GET",
    )

    @Test
    fun postsTheBodyAndReturnsTheStatusCodeAndBody() = runTest {
        val result = transport.send(request("/ok"))
        val responded = result as AiHttpResult.Responded
        assertEquals(200, responded.response.statusCode)
        assertTrue(responded.response.body.contains("hi"))
        assertEquals("""{"model":"m"}""", lastSeenBody)
        assertEquals("Bearer test-key", lastSeenAuth)
    }

    @Test
    fun sendsAGetRequestWithoutABodyWhenAskedTo() = runTest {
        // 抓取外刊走 GET：无 body、无凭据头。method 由调用方显式给出，传输层不猜。
        val result = transport.send(getRequest("/ok"))
        val responded = result as AiHttpResult.Responded
        assertEquals(200, responded.response.statusCode)
        assertEquals("GET", lastSeenMethod)
        assertEquals("", lastSeenBody)
    }

    @Test
    fun returnsNon2xxAsRespondedSoTheCallerCanMapIt() = runTest {
        val responded = transport.send(request("/unauthorized")) as AiHttpResult.Responded
        assertEquals(401, responded.response.statusCode)
        assertTrue(responded.response.body.contains("bad key"))
    }

    @Test
    fun doesNotFollowRedirects() = runTest {
        // 跟随重定向会让攻击者用公网域名把请求引到私网地址，正是 validateEndpoint 要挡的事。
        val responded = transport.send(request("/redirect")) as AiHttpResult.Responded
        assertEquals(302, responded.response.statusCode)
    }

    @Test
    fun mapsAReadTimeoutToTimedOut() = runTest {
        assertEquals(AiHttpResult.TimedOut, transport.send(request("/slow", timeout = 1)))
    }

    @Test
    fun mapsAConnectionFailureToNetworkUnavailable() = runTest {
        val dead = AiHttpRequest(
            url = "http://127.0.0.1:1/nothing", headers = emptyMap(), body = "", timeoutSeconds = 2,
        )
        assertEquals(AiHttpResult.NetworkUnavailable, transport.send(dead))
    }

    @Test
    fun refusesResponsesOverTheCap() = runTest {
        assertEquals(AiHttpResult.ResponseTooLarge, transport.send(request("/huge")))
    }
}

private class FakeResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)

private class FakeHttpServer(
    private val route: (method: String, path: String, headers: Map<String, String>, body: String) -> FakeResponse,
) {
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    val port: Int get() = requireNotNull(serverSocket).localPort

    fun start() {
        val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        serverSocket = socket
        acceptThread = Thread {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    return@Thread
                }
                Thread { serve(client) }.apply {
                    isDaemon = true
                    start()
                }
            }
        }.also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } finally {
            acceptThread?.join(1000)
        }
    }

    private fun serve(client: Socket) {
        try {
            client.use { connection ->
                connection.soTimeout = 5000
                val input = connection.getInputStream()
                // 全程字节级读行：BufferedReader 的内部缓冲会把 body 字节一并吞掉，
                // 之后直接 read() body 就会阻塞到客户端超时——2026-09-24 实测踩过。
                while (true) {
                    val request = readRequest(input) ?: return
                    val response = route(request.method, request.path, request.headers, request.body)
                    writeResponse(connection, response)
                }
            }
        } catch (_: Exception) {
            // 客户端在读满上限后主动断开（ResponseTooLarge）或超时，都会把这里的读写打断——不是错误。
        }
    }

    /** ISO-8859-1 语义的字节读行：byte↔char 一一对应，绝不预读超出本行的数据。 */
    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (line.isEmpty()) null else line.toString()
            if (byte == '\n'.code) return line.toString()
            if (byte != '\r'.code) line.append(byte.toChar())
        }
    }

    private fun readRequest(input: InputStream): FakeRequest? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(" ")
        if (parts.size < 2) return null
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
            }
        }
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) {
            val buffer = ByteArray(contentLength)
            var offset = 0
            while (offset < contentLength) {
                val read = input.read(buffer, offset, contentLength - offset)
                if (read < 0) break
                offset += read
            }
            String(buffer, 0, offset, Charsets.ISO_8859_1)
        } else {
            ""
        }
        return FakeRequest(parts[0], parts[1], headers, body)
    }

    private fun writeResponse(connection: Socket, response: FakeResponse) {
        val bytes = response.body.toByteArray(Charsets.ISO_8859_1)
        val head = buildString {
            append("HTTP/1.1 ").append(response.statusCode).append(" fake\r\n")
            response.headers.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.ISO_8859_1)
        connection.getOutputStream().use { output ->
            output.write(head)
            output.write(bytes)
            output.flush()
        }
    }

    private data class FakeRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
    )
}
