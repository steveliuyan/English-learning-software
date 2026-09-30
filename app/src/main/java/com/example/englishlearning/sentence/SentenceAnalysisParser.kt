package com.example.englishlearning.sentence

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.ChatResponseEnvelope

/** 一个句子成分：语法角色 + 原句片段 + 中文解释。字段都是终态值，渲染层直接用。 */
data class SentenceSegment(
    val role: SentenceRole,
    val text: String,
    val explanation: String,
)

/** 行协议的角色白名单。模型输出任何白名单之外的「角色」都判整体失败。 */
enum class SentenceRole(val label: String) {
    Main("主句"),
    Clause("从句"),
    Phrase("短语"),
    Connector("连词"),
}

/**
 * HTTP 响应 → [SentenceSegment] 列表。
 *
 * 映射顺序与词问答一致：**状态码先于解析**。内容是行协议——每行
 * `角色\t原句片段\t中文解释`，角色只接受 [SentenceRole] 白名单。任何一行
 * 不合格（字段数不对、空白字段、超长、未知角色、行数/总长超限）都整体
 * [AiFailure.InvalidResponse]，绝不渲染半份数据。
 */
object SentenceAnalysisParser {
    const val maxTotalLength = 4000
    const val maxFieldLength = 300
    const val maxSegments = 20

    private val rolesByLabel = SentenceRole.entries.associateBy { it.label }

    fun parse(httpStatusCode: Int, body: String): Result<List<SentenceSegment>> = runCatching {
        when (httpStatusCode) {
            401 -> throw AiException(AiFailure.Unauthorized)
            429 -> throw AiException(AiFailure.RateLimited)
            in 500..599 -> throw AiException(AiFailure.ServerUnavailable)
            else -> Unit // 200 进解析；其余状态按不合规响应处理
        }
        try {
            val content = ChatResponseEnvelope.extractContent(body)
            parseLines(ChatResponseEnvelope.stripCodeFence(content))
        } catch (ai: AiException) {
            throw ai
        } catch (_: Exception) {
            throw invalid()
        }
    }

    private fun parseLines(content: String): List<SentenceSegment> {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) throw invalid()
        if (lines.size > maxSegments) throw invalid()
        if (lines.sumOf { it.length } > maxTotalLength) throw invalid()
        return lines.map { line ->
            val fields = line.split('\t')
            if (fields.size != 3) throw invalid()
            val role = rolesByLabel[fields[0].trim()] ?: throw invalid()
            val text = fields[1].trim()
            val explanation = fields[2].trim()
            if (text.isEmpty() || explanation.isEmpty()) throw invalid()
            if (text.length > maxFieldLength || explanation.length > maxFieldLength) throw invalid()
            SentenceSegment(role, text, explanation)
        }
    }

    private fun invalid() = AiException(AiFailure.InvalidResponse)
}
