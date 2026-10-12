package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.englishlearning.core.storage.entity.ReminderPreferenceEntity

@Dao
interface InternalReminderPreferenceDao {

    @Query("SELECT * FROM reminder_preferences WHERE reminderId = 'device' LIMIT 1")
    suspend fun find(): ReminderPreferenceEntity?

    @Upsert
    suspend fun upsert(entity: ReminderPreferenceEntity)
}
