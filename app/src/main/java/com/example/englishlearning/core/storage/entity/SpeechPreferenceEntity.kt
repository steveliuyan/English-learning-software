package com.example.englishlearning.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speech_preferences")
data class SpeechPreferenceEntity(
    @PrimaryKey val preferenceId: String = "device",
    val selectedEngine: String,
    val openAiProfileId: String?,
    val miMoProfileId: String?,
    /** v27 起存在；迁移对历史行回填 1（开），与新域默认值一致。 */
    @ColumnInfo(defaultValue = "1") val autoSpeakOnCardReady: Boolean = true,
    /** v32 起存在；播放侧变速参数（决策 §2），迁移对历史行回填 1.0。列名隐式驼峰，与本表 v27 先例一致。 */
    @ColumnInfo(defaultValue = "1.0") val speechRate: Float = 1.0f,
)
