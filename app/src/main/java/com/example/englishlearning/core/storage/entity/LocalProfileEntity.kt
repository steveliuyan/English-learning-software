package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_profiles")
data class LocalProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val createdAt: Long,
)
