package com.example.englishlearning.wordqa

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.ChatResponseEnvelope

/** 解包后的裸回答。纯文本，尚未做大小与内容终检（终检也在本对象内完成）。 */
data class RawWordAnswer(val answer: String)

/**
 * HTTP 响应 → [RawWordAnswer]。
 *
 * 映射顺序与文章解析器一致：**状态码先于解析**。401/429/5xx 的响应体是服务端
 * 错误说明，解析无意义；其余不合规（坏 JSON、缺信封、空白回答、超长回答）统一
 * [AiFailure.InvalidResponse]——「返回了东西但没法用」的处置是同一个：重新提问。
 *
 * 回答是纯文本（本用例的系统提示不要求 JSON），只剥 ```` ``` ```` 围栏；
 * 模型多给的任何字段一律丢弃。
 */
object WordQaResponseParser {
    /** 不可信输入大小上限：一屏可读的问答足够，防止远端喂爆内存或 UI。 */
    const val maxAnswerLength = 4000

    fun parse(httpStatusCode: Int, body: String): Result<RawWordAnswer> = runCatching {
        when (httpStatusCode) {
            401 -> throw AiException(AiFailure.Unauthorized)
            429 -> throw AiException(AiFailure.RateLimited)
            in 500..599 -> throw AiException(AiFailure.ServerUnavailable)
            else -> Unit // 200 进解析；其余状态按不合规响应处理
        }
        try {
            val content = ChatResponseEnvelope.extractContent(body)
            val answer = ChatResponseEnvelope.stripCodeFence(content).trim()
            if (answer.isEmpty()) throw invalid()
            if (answer.length > maxAnswerLength) throw invalid()
            RawWordAnswer(answer)
        } catch (ai: AiException) {
            throw ai
        } catch (_: Exception) {
            throw AiException(AiFailure.InvalidResponse)
        }
    }

    private fun invalid() = AiException(AiFailure.InvalidResponse)
}
