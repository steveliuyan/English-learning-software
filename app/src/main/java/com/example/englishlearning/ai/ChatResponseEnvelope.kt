package com.example.englishlearning.ai

/**
 * chat/completions 响应信封的公共提取逻辑。
 *
 * 状态码映射由各调用方在自己 parse 入口完成（映射顺序：状态码先于解析）；
 * 这里只负责把 `choices[0].message.content` 安全取出并剥 ```` ``` ```` 围栏。
 * 任何结构不合规都抛 [AiFailure.InvalidResponse]——返回内容是**不可信输入**，
 * 「返回了东西但没法用」的处置统一是让用户重试。
 */
object ChatResponseEnvelope {

    /** 提取 assistant 文本内容。信封缺字段、类型不对、内容空白都按 InvalidResponse 处理。 */
    fun extractContent(body: String): String = try {
        extract(body)
    } catch (ai: AiException) {
        throw ai
    } catch (_: Exception) {
        throw invalid()
    }

    /** 模型常把内容包在 ```` ```text ```` 围栏里；不剥会让合格回答被判成畸形。 */
    fun stripCodeFence(content: String): String {
        val trimmed = content.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val lines = trimmed.lines()
        if (lines.size < 3 || lines.last().trim() != "```") return trimmed
        return lines.drop(1).dropLast(1).joinToString("\n")
    }

    private fun extract(body: String): String {
        val envelope = kotlinx.serialization.json.Json.parseToJsonElement(body)
            as? kotlinx.serialization.json.JsonObject ?: throw invalid()
        val choices = envelope.get("choices") as? kotlinx.serialization.json.JsonArray ?: throw invalid()
        val message = (choices.firstOrNull() as? kotlinx.serialization.json.JsonObject)
            ?.get("message") as? kotlinx.serialization.json.JsonObject ?: throw invalid()
        val content = (message.get("content") as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it.isString }?.content ?: throw invalid()
        return content
    }

    private fun invalid() = AiException(AiFailure.InvalidResponse)
}
