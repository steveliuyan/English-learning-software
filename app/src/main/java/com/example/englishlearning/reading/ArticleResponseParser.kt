package com.example.englishlearning.reading

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiStatusFailure
import com.example.englishlearning.ai.ChatResponseEnvelope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 模型输出解包后的裸文章。尚未做任何质量与安全校验，不得直接落库或渲染。 */
data class RawArticle(val title: String, val englishText: String, val chineseText: String)

/**
 * HTTP 响应 → [RawArticle]。
 *
 * 映射顺序是刻意的：**状态码先于解析**，但状态码归哪一档由唯一权威 [AiStatusFailure] 说
 * （这里曾把网关的 403 念成「内容不合格，重新生成」，见决策
 * `docs/decisions/2026-11-08-http-403-auth-failure-single-source.md`）。已知失败档时响应体是
 * 服务端的错误说明，解析它毫无意义；调用方需要的失败分类只由状态码决定。其余任何不合规（坏 JSON、
 * 缺信封、content 不是请求的 JSON、缺字段）统一 [AiFailure.InvalidResponse]——
 * 「返回了东西但没法用」对用户的处置是同一个：重新生成。
 *
 * 信封解包与围栏剥离复用 [ChatResponseEnvelope]（不在本文件留第二份 `choices[0].message.content`
 * 取法：一份规则的副本漏改一次，就是又一处把合格回答判成畸形的地方）。
 *
 * 防线：只读 `title`/`english`/`chinese` 三个字段，模型多给的任何字段（含坐标、
 * 标记、元数据）一律丢弃。高亮坐标由系统派生（spec F2-03），模型给的坐标不可信。
 */
object ArticleResponseParser {
    fun parse(httpStatusCode: Int, body: String): Result<RawArticle> = runCatching {
        AiStatusFailure.of(httpStatusCode)?.let { throw AiException(it) }
        try {
            extract(body)
        } catch (ai: AiException) {
            throw ai
        } catch (_: Exception) {
            throw AiException(AiFailure.InvalidResponse)
        }
    }

    private fun extract(body: String): RawArticle {
        // 信封解包（含数组 content / reasoning_content / legacy text 兜底）与围栏剥离都交给共享层。
        val content = ChatResponseEnvelope.extractContent(body)
        val article = parseJson(ChatResponseEnvelope.stripCodeFence(content).trim())
        return RawArticle(
            title = stringField(article, "title"),
            englishText = stringField(article, "english"),
            chineseText = stringField(article, "chinese"),
        )
    }

    private fun parseJson(text: String): JsonObject = try {
        Json.parseToJsonElement(text) as? JsonObject ?: throw invalid()
    } catch (ai: AiException) {
        throw ai
    } catch (_: Exception) {
        throw invalid()
    }

    private fun stringField(obj: JsonObject, key: String): String {
        val value = obj[key] as? JsonPrimitive ?: throw invalid()
        if (!value.isString) throw invalid()
        return value.content
    }

    private fun invalid() = AiException(AiFailure.InvalidResponse)
}
