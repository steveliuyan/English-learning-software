package com.example.englishlearning.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * N-1（决策 `docs/decisions/2026-11-08-http-403-auth-failure-single-source.md`）：
 * 状态码 → 失败分类的唯一权威本身。
 *
 * 这里钉的是**归档**，不是文案：文案由 `AiFailureTest` 那族用例管。分档错一次，
 * 用户就会在密钥失效时被支使去「重新生成」——真机 2026-11-08 上正是这么发生的。
 */
class AiStatusFailureTest {

    @Test
    fun forbiddenAndUnauthorizedLandInTheSameAuthBucket() {
        assertEquals(AiFailure.Unauthorized, AiStatusFailure.of(401))
        // 真机取证的那一枚：网关对失效密钥答 403 + GROUP_DELETED，请求根本没进模型。
        assertEquals(AiFailure.Unauthorized, AiStatusFailure.of(403))
    }

    @Test
    fun rateLimitAndServerBucketsStaySeparate() {
        assertEquals(AiFailure.RateLimited, AiStatusFailure.of(429))
        assertEquals(AiFailure.ServerUnavailable, AiStatusFailure.of(500))
        assertEquals(AiFailure.ServerUnavailable, AiStatusFailure.of(503))
        assertEquals(AiFailure.ServerUnavailable, AiStatusFailure.of(599))
    }

    /**
     * 未知档一律 `null`，由调用方按自己原来的路走（决策 §4：本批不改 3xx/400/404/405 的归属）。
     * 共享层不许偷偷兜底成 `InvalidResponse`——那会把「这里没人管」伪装成「已经判过了」，
     * 六处消费点的 404 用例随之失去意义。
     */
    @Test
    fun unknownBucketsAreLeftToTheCaller() {
        for (statusCode in listOf(200, 201, 204, 301, 302, 307, 400, 402, 404, 405, 408, 428, 451, 600)) {
            assertNull(AiStatusFailure.of(statusCode), "status=$statusCode 不该由共享层归档")
        }
    }

    @Test
    fun theAuthBucketPointsTheUserBackAtTheProfile() {
        // 归档之所以要紧，是因为它决定下一步动作：换密钥，而不是白跑一次重新生成。
        assertEquals(UserAction.ConfigureProfile, AiStatusFailure.of(403)?.toUserAction())
        assertEquals(UserAction.RegenerateContent, AiFailure.InvalidResponse.toUserAction())
    }
}
