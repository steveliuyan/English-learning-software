package com.example.englishlearning.reading

import com.example.englishlearning.reading.domain.ReadingPreference

interface ReadingPreferenceRepository {
    suspend fun getPreference(profileId: String): Result<ReadingPreference>

    suspend fun savePreference(preference: ReadingPreference): Result<Unit>
}
