package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpResponse
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.core.security.SecretReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 连接探活（`GET {endpoint}/models`）的编排契约。
 *
 * 判据顺序照 `SentenceAnalysisUseCaseTest` 那一族：**先问「有没有字节出去」，再问「回来的怎么分类」**。
 * 本用例的特殊性在于它只带一枚密钥出网、不带任何学习内容，而返回值会直接显示在「AI 服务」编辑页上——
 * 所以「诚实分类」与「零泄露」两头都必须有牙：
 *  - 未确认域名 ⇒ transport 一次都不许被调用（AGENTS.md 的首次调用域名确认）；
 *  - 超时 / 网络不可达 / 其他非 2xx 三者不许互相顶替（把 404 说成「网络不可达」就是撒谎）；
 *  - 除确认所需的 host 之外，结果成员一律无字段：响应体、Endpoint、密钥都不得顺着结果流到界面。
 */
class AiConnectionProbeTest {
    private val profile = AiProfile(
        profileId = "p1",
        displayName = "测试服务",
        websiteUrl = "https://example.com",
        endpoint = "https://api.test/v1",
        model = "gpt-x",
        capabilities = setOf(AiCapability.Text),
        secretReference = SecretReference("ai-profile-p1"),
        advancedParameters = AiAdvancedParameters(timeoutSeconds = 17),
    )

    private class RecordingTransport : AiHttpTransport {
        val requests = mutableListOf<AiHttpRequest>()
        var result: AiHttpResult = AiHttpResult.Responded(AiHttpResponse(200, """{"data":[]}"""))

        override suspend fun send(request: AiHttpRequest): AiHttpResult {
            requests += request
            return result
        }
    }

    private fun probe(transport: RecordingTransport) = AiConnectionProbe(transport)

    // ---------- 出站确认先于任何字节 ----------

    @Test
    fun unconfirmedHostStopsBeforeAnyByteLeaves() = runTest {
        val transport = RecordingTransport()

        val result = probe(transport).probe(profile, "k3y".toCharArray(), confirmedTextHost = null)

        assertEquals(ConnectionProbeResult.NeedsConfirmation("api.test", AiPayloadKind.Text), result)
        assertEquals(0, transport.requests.size, "域名未确认时探活必须零出站：确认是出站前的闸门，不是事后说明")
    }

    @Test
    fun confirmationAskedForAnotherHostDoesNotCarryOver() = runTest {
        val transport = RecordingTransport()

        val result = probe(transport).probe(profile, "k3y".toCharArray(), confirmedTextHost = "other.test")

        assertEquals(
            ConnectionProbeResult.NeedsConfirmation("api.test", AiPayloadKind.Text),
            result,
            "换过 Endpoint 之后旧域名的确认不能顺延，否则等于绕过披露确认",
        )
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun confirmedHostGoesOutOnceAndReportsReachable() = runTest {
        val transport = RecordingTransport()

        val result = probe(transport).probe(profile, "k3y".toCharArray(), confirmedTextHost = "api.test")

        assertEquals(ConnectionProbeResult.Reachable, result)
        assertEquals(1, transport.requests.size)
    }

    // ---------- 请求形状：最小 GET ----------

    @Test
    fun theProbeIsAMinimalGetAgainstTheModelsPath() = runTest {
        val transport = RecordingTransport()

        probe(transport).probe(profile, "k3y".toCharArray(), confirmedTextHost = "api.test")

        val request = transport.requests.single()
        assertEquals(
            "https://api.test/v1/models",
            request.url,
            "路径由应用决定：OpenAI 兼容的模型清单端点就是 {base}/models",
        )
        assertEquals("GET", request.method, "探活不得开聊天补全，也不得发任何会计费的生成请求")
        assertEquals("", request.body, "GET 不带请求体——`UrlConnectionAiHttpTransport` 对带 body 的 GET 直接判失败")
    }

    @Test
    fun timeoutSecondsComeFromTheProfileBeingTested() = runTest {
        val transport = RecordingTransport()

        probe(transport).probe(profile, "k3y".toCharArray(), confirmedTextHost = "api.test")

        assertEquals(
            17,
            transport.requests.single().timeoutSeconds,
            "等待上限取这份配置自己的 timeout_seconds，不另设一套",
        )
    }

    @Test
    fun onlyTheAuthorizationHeaderTravelsAndTheKeyStaysOutOfTheUrlAndBody() = runTest {
        val transport = RecordingTransport()

        probe(transport).probe(profile, "sk-secret".toCharArray(), confirmedTextHost = "api.test")

        val request = transport.requests.single()
        assertEquals(
            setOf("Authorization"),
            request.headers.keys,
            "GET 没有 body，Content-Type 属于多余声明；白名单之外的头一个都不许加",
        )
        assertEquals("Bearer sk-secret", request.headers["Authorization"])
        assertFalse(request.url.contains("sk-secret"), "密钥不进 URL——它会顺着地址进截图与历史记录")
        assertFalse(request.body.contains("sk-secret"))
    }

    @Test
    fun keyBufferIsClearedAfterTheProbe() = runTest {
        val transport = RecordingTransport()
        val key = "k3y".toCharArray()

        probe(transport).probe(profile, key, confirmedTextHost = "api.test")

        assertTrue(
            key.all { it == '\u0000' },
            "探活结束即清零：一次性凭据不在内存里多留（与 SentenceAnalysisUseCaseTest 同一条防线）",
        )
    }

    @Test
    fun emptyKeyIsRejectedAsProgrammingContract() = runTest {
        val transport = RecordingTransport()

        // 「没有密钥」由界面侧判定（决策 §3），域层收到空数组属于接线错误：必须响亮失败，
        // 而不是静默发一枚 `Bearer ` 空头的请求出去。
        assertFailsWith<IllegalArgumentException> {
            probe(transport).probe(profile, CharArray(0), confirmedTextHost = "api.test")
        }
        assertEquals(0, transport.requests.size)
    }

    // ---------- 状态分类 ----------

    @Test
    fun unauthorizedStatusesMapToAuthFailure() = runTest {
        listOf(401, 403).forEach { status ->
            val transport = RecordingTransport().apply { result = AiHttpResult.Responded(AiHttpResponse(status, "{}")) }

            val result = probe(transport).probe(profile, "k3y".toCharArray(), confirmedTextHost = "api.test")

            assertEquals(ConnectionProbeResult.Unauthorized, result, "HTTP $status 是密钥被拒，不是网络坏了")
        }
    }

    @Test
    fun transportTimeoutIsNotFoldedIntoNetworkUnavailable() = runTest {
        val transport = RecordingTransport().apply { result = AiHttpResult.TimedOut }

        val result = probe(transport).probe(profile, "k3y".toCharArray(), confirmedTextHost = "api.test")

        assertEquals(
            ConnectionProbeResult.Timeout,
            result,
            "超时的处置是「稍后再试」，连不上的处置是「查地址/查网络」；折叠成一条就失去可行动性",
        )
    }

    @Test
    fun connectionFailureMapsToNetworkUnavailable() = runTest {
        val transport = RecordingTransport().apply { result = AiHttpResult.NetworkUnavailable }

        val result = probe(transport).probe(profile, "k3y".toCharArray(), confirmedTextHost = "api.test")

        assertEquals(ConnectionProbeResult.NetworkUnavailable, result)
    }

    @Test
    fun otherErrorStatusesAreNeitherAuthFailureNorNetworkFailure() = runTest {
        listOf(404, 405, 429, 500, 503).forEach { status ->
            val transport = RecordingTransport().apply {
                result = AiHttpResult.Responded(AiHttpResponse(status, """{"error":{"message":"sk-secret-ish"}}"""))
            }

            val result = probe(transport).probe(profile, "k3y".toCharArray(), confirmedTextHost = "api.test")

            assertEquals(
                ConnectionProbeResult.ServiceError,
                result,
                "HTTP $status 说明对方活着但不是预期端点（404 最常见的是 base 填错）：" +
                    "归成「鉴权失败」会让人白换密钥，归成「网络不可达」会让人白查网线",
            )
        }
    }

    @Test
    fun oversizedResponseIsAServiceErrorNotASuccess() = runTest {
        val transport = RecordingTransport().apply { result = AiHttpResult.ResponseTooLarge }

        val result = probe(transport).probe(profile, "k3y".toCharArray(), confirmedTextHost = "api.test")

        assertEquals(
            ConnectionProbeResult.ServiceError,
            result,
            "响应体超上限即断开：这既是资源防御也是「回来的东西不对劲」，不许当可达成功报",
        )
    }

    @Test
    fun hostileEndpointCannotEvenProduceAConfirmationTarget() = runTest {
        listOf(
            "http://api.test/v1",
            "https://127.0.0.1/v1",
            "https://192.168.1.9/v1",
            "https://user:pass@api.test/v1",
            "https://api.test/v1?api_key=sk",
        ).forEach { endpoint ->
            val transport = RecordingTransport()

            val result = probe(transport).probe(
                profile.copy(endpoint = endpoint),
                "k3y".toCharArray(),
                confirmedTextHost = null,
            )

            assertEquals(ConnectionProbeResult.ConfigurationInvalid, result, "$endpoint 必须先被端点策略挡下")
            assertEquals(0, transport.requests.size, "不合法的 Endpoint 一个字节都不许出站")
        }
    }

    @Test
    fun userCancellationPropagatesInsteadOfFakingFailure() = runTest {
        val transport = RecordingTransport().apply { result = AiHttpResult.Cancelled }

        assertFailsWith<CancellationException> {
            probe(transport).probe(profile, "k3y".toCharArray(), confirmedTextHost = "api.test")
        }
    }
}