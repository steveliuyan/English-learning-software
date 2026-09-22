package com.example.englishlearning.ai.domain

import com.example.englishlearning.core.security.SecretReference

 data class AiAdvancedParameters(
    val temperature: Double = 0.7,
    val topP: Double = 1.0,
    val maxTokens: Int = 1024,
    val timeoutSeconds: Int = 30,
    val systemPromptTemplateId: String = "default-reading-v1",
) {
    init {
        require(temperature in 0.0..2.0)
        require(topP in 0.0..1.0)
        require(maxTokens in 1..4096)
        require(timeoutSeconds in 5..120)
        require(systemPromptTemplateId == "default-reading-v1")
    }
}

data class AiProfile(
    val profileId: String,
    val displayName: String,
    val websiteUrl: String,
    val endpoint: String,
    val model: String,
    val capabilities: Set<AiCapability>,
    val secretReference: SecretReference,
    val advancedParameters: AiAdvancedParameters = AiAdvancedParameters(),
)
