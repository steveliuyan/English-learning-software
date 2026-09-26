package com.example.englishlearning.ai.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 从 MiMo 非流式 chat/completions 响应里提取 base64 WAV 音频。
 *
 * 任何结构不符（缺 choices、缺 message.audio.data、base64 非法）一律返回 null，
 * 由调用方映射成播放失败——解析层的职责是「拿不到音频就不给音频」，不抛异常、
 * 不把响应原文带出去（响应体可能含服务端错误细节，向上传会变成泄露面）。
 */
object MiMoTtsResponseParser {
    fun extractWav(body: ByteArray): ByteArray? = try {
        val root = Json.parseToJsonElement(body.decodeToString()).jsonObject
        val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject ?: return null
        val data = message["audio"]?.jsonObject?.get("data")?.jsonPrimitive?.content ?: return null
        java.util.Base64.getDecoder().decode(data)
    } catch (_: Exception) {
        null
    }
}
