package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Keystore reference metadata only. It never contains credential material. */
@Entity(tableName = "key_aliases")
data class KeyAliasEntity(
    @PrimaryKey val purpose: String,
    val alias: String,
    val createdAt: Long,
)
