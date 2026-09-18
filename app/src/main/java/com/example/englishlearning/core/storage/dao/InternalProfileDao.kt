package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.englishlearning.core.storage.entity.LocalProfileEntity

@Dao
internal interface InternalProfileDao {
    @Query("SELECT * FROM local_profiles WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): LocalProfileEntity?

    @Upsert
    suspend fun upsert(profile: LocalProfileEntity)
}
