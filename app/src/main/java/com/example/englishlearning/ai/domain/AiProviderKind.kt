package com.example.englishlearning.ai.domain

/**
 * 识别 Profile 走哪一套出站协议。协议决定请求形态（URL 拼接、鉴权头、请求体结构），
 * 因此必须随 Profile 持久化，而不是由引擎槽位隐式假定——否则把 MiMo 预设绑进
 * OpenAI 槽位时会对 `chat/completions` 端点发 `/audio/speech` 请求，必然 404。
 */
enum class AiProviderKind {
    /** OpenAI 兼容协议：`{endpoint}/audio/speech` + `Authorization: Bearer`。 */
    OPENAI_COMPATIBLE,

    /** 小米 MiMo 协议：`{endpoint}`（完整 chat/completions URL）+ `api-key` 头。 */
    XIAOMI_MIMO,
}
