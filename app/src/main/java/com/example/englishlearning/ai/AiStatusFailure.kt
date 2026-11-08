package com.example.englishlearning.ai

/**
 * HTTP 状态码 → [AiFailure] 的**唯一权威**映射（决策
 * `docs/decisions/2026-11-08-http-403-auth-failure-single-source.md`）。
 *
 * 为什么会有这个文件：真机 2026-11-08 上，网关对失效密钥答 **403** +
 * `{"code":"GROUP_DELETED","message":"API Key 所属分组已删除"}`，请求根本没进模型，
 * 而六个带密钥的响应路径各自内联过一条「401/429/5xx」判据、**都漏了 403**，
 * 于是界面念成「返回内容不合格，可以重新生成」，把用户推向一条永远不会成功的路；
 * 同一套配置的「测试连接」却因为 `AiConnectionProbe` 写了 403 而报「鉴权失败」。
 * 两句并存本身就是证据：同一条规则抄了七遍，漏的那几遍就是缺陷。
 *
 * [of] 返回 `null` 而不是兜底 `InvalidResponse`，是为了不在共享层偷偷改变 3xx/400/404/405
 * 的现有归属（决策 §4：本批一字不改，如实登记为结转）。调用方拿到 `null` 之后走自己原来的路。
 *
 * 两处**有意不消费**这里：
 *  - `imagegen/GeneratedImageStore`：下载的是 CDN 返回的图片 URL，**不带密钥**，
 *    它的 401/403 说的是「那个链接不可取」，不是「你的密钥被拒」（决策 §5）；
 *  - `language/infrastructure/` 两个发音 Provider：`PronunciationResult` 没有失败分类位，
 *    接进来要先给发音加失败语义，那是另一个缺陷（决策 §4）。
 *
 * 判据不许长出第二份：`AiStatusFailureContractTest` 扫 `main` 源码钉住这一点。
 */
object AiStatusFailure {

    /**
     * 已知三档：鉴权（401 与 403 同档）、限流/额度（429）、服务端不可用（5xx）。
     * 403 与 401 归同一档不是省事——两者在这里是**同一个动作**（回「我的 → AI 服务」换密钥），
     * 而 `AiFailure.toUserAction()` 给 `Unauthorized` 的正是 `ConfigureProfile`，给
     * `InvalidResponse` 的是 `RegenerateContent`；分错档就是把用户支使去做错事。
     */
    fun of(statusCode: Int): AiFailure? = when (statusCode) {
        401, 403 -> AiFailure.Unauthorized
        429 -> AiFailure.RateLimited
        in 500..599 -> AiFailure.ServerUnavailable
        else -> null
    }
}
