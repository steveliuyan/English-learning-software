package com.example.englishlearning.language

import com.example.englishlearning.language.domain.SpeechPreference

interface SpeechPreferenceRepository {
    suspend fun get(): Result<SpeechPreference>

    suspend fun save(preference: SpeechPreference): Result<Unit>
}
