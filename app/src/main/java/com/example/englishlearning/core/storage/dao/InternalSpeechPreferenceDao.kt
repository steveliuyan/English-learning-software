package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.englishlearning.core.storage.entity.SpeechPreferenceEntity

@Dao
internal interface InternalSpeechPreferenceDao {
    @Query("SELECT * FROM speech_preferences WHERE preferenceId = 'device' LIMIT 1")
    suspend fun find(): SpeechPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: SpeechPreferenceEntity)
}
