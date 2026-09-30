package com.example.englishlearning.ai.domain

/** Declares supported AI capabilities without exposing a provider transport. */
interface AiProvider {
    fun capabilities(): Set<AiCapability>
}

enum class AiCapability {
    Text,
    Vision,
    Speech,

    /** 文生图（OpenAI 兼容 images/generations）。与 Vision（读图）是两个方向。 */
    ImageGeneration,
}
