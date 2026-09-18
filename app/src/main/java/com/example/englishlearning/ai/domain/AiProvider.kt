package com.example.englishlearning.ai.domain

/** Declares supported AI capabilities without exposing a provider transport. */
interface AiProvider {
    fun capabilities(): Set<AiCapability>
}

enum class AiCapability {
    Text,
    Vision,
    Speech,
}
