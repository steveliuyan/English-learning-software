package com.example.englishlearning.ai.domain

/**
 * 各协议族的语音角色目录（Talkify 式音色选择）。
 *
 * 空串始终代表「自动」：MiMo 按合成文本语言选冰糖/Mia，OpenAI 兼容走服务默认。
 * 目录只收录官方预置音色，自定义字符串一律在编辑器校验层拦下，不进请求。
 */
object AiVoiceCatalog {
    /** 小米 MiMo v2.5 官方预置音色。 */
    val XIAOMI_MIMO: List<String> = listOf("冰糖", "茉莉", "苏打", "白桦", "Mia", "Chloe", "Milo", "Dean")

    /** OpenAI 官方 TTS 预置音色；OpenAI 兼容中转大多沿用同一套。 */
    val OPENAI_COMPATIBLE: List<String> = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")

    fun optionsFor(kind: AiProviderKind): List<String> = when (kind) {
        AiProviderKind.OPENAI_COMPATIBLE -> OPENAI_COMPATIBLE
        AiProviderKind.XIAOMI_MIMO -> XIAOMI_MIMO
    }

    fun isValid(kind: AiProviderKind, voice: String): Boolean = voice.isBlank() || voice in optionsFor(kind)
}
