package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException

/** 允许出现的参数名。别的名字一律拒绝，包括 header、body、tools 这类越权字段。 */
private val ALLOWED_PARAMETER_NAMES = setOf(
    "temperature",
    "top_p",
    "max_tokens",
    "timeout_seconds",
    "system_prompt_template",
)

/**
 * 把调用方给的原始参数收敛成类型化的 [AiAdvancedParameters]。
 *
 * 返回值就是白名单本身：下游拿不到任意 Header、请求体片段、工具声明或 authorization，
 * 因为它们在结果类型里根本不存在。越界值不靠 `AiAdvancedParameters` 的 `require` 抛异常来挡，
 * 而是在构造前就判定并返回失败——`require` 抛出的 `IllegalArgumentException` 会一路冒到 UI 层。
 */
fun validateRequestParameters(parameters: Map<String, Any?>): Result<AiAdvancedParameters> {
    if (parameters.keys.any { it !in ALLOWED_PARAMETER_NAMES }) return invalidParameters()
    // 显式传 null 不能退回默认值：那会把「用户/上游想改这项但传错了」静默变成「用默认值跑」。
    if (parameters.values.any { it == null }) return invalidParameters()

    val defaults = AiAdvancedParameters()
    val temperature = when (val raw = parameters["temperature"]) {
        null -> defaults.temperature
        is Number -> raw.toDouble().takeIf { it.isFinite() } ?: return invalidParameters()
        else -> return invalidParameters()
    }
    val topP = when (val raw = parameters["top_p"]) {
        null -> defaults.topP
        is Number -> raw.toDouble().takeIf { it.isFinite() } ?: return invalidParameters()
        else -> return invalidParameters()
    }
    val maxTokens = when (val raw = parameters["max_tokens"]) {
        null -> defaults.maxTokens
        is Number -> raw.toExactIntOrNull() ?: return invalidParameters()
        else -> return invalidParameters()
    }
    val timeoutSeconds = when (val raw = parameters["timeout_seconds"]) {
        null -> defaults.timeoutSeconds
        is Number -> raw.toExactIntOrNull() ?: return invalidParameters()
        else -> return invalidParameters()
    }
    val systemPromptTemplateId = when (val raw = parameters["system_prompt_template"]) {
        null -> defaults.systemPromptTemplateId
        is String -> raw
        else -> return invalidParameters()
    }

    // 范围与「受控系统提示模板」由 AiAdvancedParameters 自己守；这里只是把它的 require 转成失败。
    return runCatching {
        AiAdvancedParameters(
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            timeoutSeconds = timeoutSeconds,
            systemPromptTemplateId = systemPromptTemplateId,
        )
    }.fold(onSuccess = { Result.success(it) }, onFailure = { invalidParameters() })
}

/** `1024.5` 不是合法的整数参数，`1024.0` 是。 */
private fun Number.toExactIntOrNull(): Int? {
    val value = toDouble()
    if (!value.isFinite()) return null
    if (value != kotlin.math.floor(value)) return null
    if (value < Int.MIN_VALUE.toDouble() || value > Int.MAX_VALUE.toDouble()) return null
    return value.toInt()
}

private fun invalidParameters(): Result<AiAdvancedParameters> =
    Result.failure(AppErrorException(AppError.InvalidAiConfiguration))
