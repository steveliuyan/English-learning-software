package com.example.englishlearning.phototranslation

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiStatusFailure
import com.example.englishlearning.ai.ChatResponseEnvelope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 一次批量翻译的可绘制结果。
 *
 * [missing] 里的块**不覆盖**、其余照画（AC3-09）；整批结构不合格时这里根本不会产生，调用方拿到的是
 * 失败的 [Result]（AC3-08），不画半张图。
 */
data class BlockTranslationOutcome(
    val translations: Map<Int, String>,
    val missing: List<Int>,
) {
    val isComplete: Boolean get() = missing.isEmpty()
}

/**
 * 模型输出 → 块译文表。规则（顺序即优先级）：
 *
 *  1. 状态码先于解析，归哪一档只由 [AiStatusFailure] 说；
 *  2. 信封取不到正文、正文不是 JSON 数组、数组项不是对象、id 非法 → 整批 [AiFailure.InvalidResponse]；
 *  3. **多余**的条目（数组比请求多）、未知 id、重复 id → 整批失败（结构不可信，可能串行到别的块）；
 *  4. **缺少**某些 id（数组比请求少）或该条译文为空 → 该块归 [BlockTranslationOutcome.missing]；
 *  5. 单条译文超长或带脚本/框架等危险标记 → 整批失败，绝不渲染这半条。
 */
object BlockTranslationParser {
    const val maxTranslationLength = 8000

    private val dangerousMarkers = Regex("<script|<iframe|<img|<a\\s|javascript:|on\\w+\\s*=", RegexOption.IGNORE_CASE)

    fun parse(
        httpStatusCode: Int,
        body: String,
        requestedIds: List<Int>,
    ): Result<BlockTranslationOutcome> =
        runCatching {
            require(requestedIds.isNotEmpty()) { "requestedIds" }
            require(requestedIds.distinct().size == requestedIds.size) { "duplicate requestedIds" }
            AiStatusFailure.of(httpStatusCode)?.let { throw AiException(it) }

            val content =
                try {
                    ChatResponseEnvelope.extractContent(body)
                } catch (ai: AiException) {
                    throw ai
                } catch (_: Exception) {
                    throw AiException(AiFailure.InvalidResponse)
                }
            val array =
                try {
                    Json.parseToJsonElement(ChatResponseEnvelope.stripCodeFence(content).trim()) as? JsonArray
                } catch (_: Exception) {
                    null
                } ?: throw invalid()
            // 条目比请求多：说明模型自己编了块，编号与屏幕对不上，整批不可信。
            if (array.size > requestedIds.size) throw invalid()

            val requested = requestedIds.toSet()
            val translated = LinkedHashMap<Int, String>()
            for (element in array) {
                val item = element as? JsonObject ?: throw invalid()
                val id = idOf(item.get("id")) ?: throw invalid()
                // 未知 id / 重复 id 都指向同一件事：这份数组不能按编号映射到块。
                if (id !in requested || translated.containsKey(id)) throw invalid()
                val translation = (item.get("translation") as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content
                    ?.trim()
                    .orEmpty()
                // 空译文不是「翻错了」，是「这块暂时没有译文」——留给用户重试，其余照画。
                if (translation.isEmpty()) continue
                if (translation.length > maxTranslationLength || dangerousMarkers.containsMatchIn(translation)) throw invalid()
                translated[id] = translation
            }

            BlockTranslationOutcome(
                translations = requestedIds.mapNotNull { id -> translated[id]?.let { id to it } }.toMap(LinkedHashMap()),
                missing = requestedIds.filter { it !in translated },
            )
        }

    private fun idOf(element: kotlinx.serialization.json.JsonElement?): Int? {
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.content.trim().toIntOrNull()
    }

    private fun invalid() = AiException(AiFailure.InvalidResponse)
}
