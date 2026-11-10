package com.example.englishlearning.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * chat/completions 响应信封的公共提取逻辑。
 *
 * 状态码映射由各调用方在自己 parse 入口完成（映射顺序：状态码先于解析）；
 * 这里只负责把 assistant 的正文安全取出并剥 ```` ```` 围栏。
 * 任何结构不合规都抛 [AiFailure.InvalidResponse]——返回内容是**不可信输入**，
 * 「返回了东西但没法用」的处置统一是让用户重试。
 *
 * 取正文的顺序（真机观察：不同 OpenAI 兼容服务对同一个「合格回答」的写法并不一致）：
 *  1. `choices[0].message.content` 为字符串——最常见；
 *  2. `choices[0].message.content` 为 `[{type:"text",text:…}]` 数组——多模态 / 新版接口；
 *  3. `choices[0].message.reasoning_content`——推理模型把正文放这里、content 留空；
 *  4. `choices[0].text`——老式 completions 字段。
 * 只有这样，一份**内容合格**的回答才不会被误判成畸形；四路都取不到才 InvalidResponse。
 */
object ChatResponseEnvelope {

    /** 提取 assistant 文本内容。信封缺字段、类型不对、内容空白都按 [AiFailure.InvalidResponse] 处理。 */
    fun extractContent(body: String): String = try {
        extract(body)
    } catch (ai: AiException) {
        throw ai
    } catch (_: Exception) {
        throw invalid()
    }

    /**
     * 模型常把内容包在 ```` ```` 围栏里；不剥会让合格回答被判成畸形。
     *
     * 除了「整段就是一块围栏」的标准情形，还容忍围栏**前后夹了散文**（模型爱在 JSON 后面补一句
     * 「希望有帮助」）：此时取**第一块**围栏内的内容。没有围栏、或围栏没闭合（无法猜边界）时原样返回 trim。
     */
    fun stripCodeFence(content: String): String {
        val trimmed = content.trim()
        if (!trimmed.contains("```")) return trimmed
        val lines = trimmed.lines()
        val open = lines.indexOfFirst { it.trimStart().startsWith("```") }
        if (open < 0) return trimmed
        val close = (open + 1 until lines.size).firstOrNull { lines[it].trim() == "```" }
            ?: return trimmed
        return lines.subList(open + 1, close).joinToString("\n")
    }

    private fun extract(body: String): String {
        val envelope = Json.parseToJsonElement(body) as? JsonObject ?: throw invalid()
        val choices = envelope.get("choices") as? JsonArray ?: throw invalid()
        val first = choices.firstOrNull() as? JsonObject ?: throw invalid()

        val message = first.get("message") as? JsonObject
        if (message != null) {
            contentOf(message.get("content"))?.let { return it }
            stringValue(message.get("reasoning_content"))?.takeIf { it.isNotBlank() }?.let { return it }
        }
        // legacy completions：没有 message，正文直接挂 choices[0].text。
        stringValue(first.get("text"))?.takeIf { it.isNotBlank() }?.let { return it }
        throw invalid()
    }

    /**
     * message.content 取正文：字符串直接用；数组则把其中 `type=="text"`（或缺省 type）的 text 片段
     * 顺序拼起来，非文字片段（图片等）跳过。取不到可用文本返回 null。
     */
    private fun contentOf(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> contentString(element)
        is JsonArray -> element
            .mapNotNull { part ->
                val obj = part as? JsonObject ?: return@mapNotNull null
                val type = stringValue(obj.get("type"))
                if (type != null && type != "text") return@mapNotNull null
                stringValue(obj.get("text"))
            }
            .joinToString("")
            .takeIf { it.isNotBlank() }
        else -> null
    }

    private fun contentString(primitive: JsonPrimitive): String? =
        primitive.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun stringValue(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun invalid() = AiException(AiFailure.InvalidResponse)
}
