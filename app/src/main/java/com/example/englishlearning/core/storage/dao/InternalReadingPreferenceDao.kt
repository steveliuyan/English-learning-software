package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.englishlearning.core.storage.entity.ReadingPreferenceEntity

@Dao
internal interface InternalReadingPreferenceDao {
    @Query("SELECT * FROM reading_preferences WHERE profileId = :profileId LIMIT 1")
    suspend fun find(profileId: String): ReadingPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: ReadingPreferenceEntity)
}
