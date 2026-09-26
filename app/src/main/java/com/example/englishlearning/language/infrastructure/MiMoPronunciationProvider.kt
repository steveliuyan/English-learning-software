package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.net.AudioHttpTransport
import com.example.englishlearning.language.domain.PronunciationProvider

class MiMoPronunciationProvider(
    profiles: AiProfileRepository,
    secrets: AiProfileSecretUseCase,
    transport: AudioHttpTransport,
    player: AudioPlayer,
    profileId: String = DEFAULT_PROFILE_ID,
) : PronunciationProvider by OpenAiCompatiblePronunciationProvider(
    profileId = profileId,
    voice = VOICE,
    responseFormat = RESPONSE_FORMAT,
    profiles = profiles,
    secrets = secrets,
    transport = transport,
    player = player,
) {
    companion object {
        const val DEFAULT_PROFILE_ID = "mimo"
        private const val VOICE = "mimo"
        private const val RESPONSE_FORMAT = "mp3"
    }
}
