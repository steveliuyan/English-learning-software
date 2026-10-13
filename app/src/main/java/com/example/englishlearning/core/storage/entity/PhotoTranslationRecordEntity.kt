package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户明确保存的拍照翻译记录。只存文本、引擎枚举名、时间与原图资产 id 引用——
 * 图片字节走 asset_records + 私有文件区；Key、Endpoint 一概不入库（AGENTS.md）。
 */
@Entity(
    tableName = "photo_translation_records",
    indices = [Index(value = ["profileId", "createdAtEpochMillis"])],
)
data class PhotoTranslationRecordEntity(
    @PrimaryKey val recordId: String,
    val profileId: String,
    val sourceText: String,
    val translatedText: String,
    val engine: String,
    val imageAssetId: String?,
    val createdAtEpochMillis: Long,
)
