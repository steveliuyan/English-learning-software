package com.example.englishlearning.phototranslation

import com.example.englishlearning.language.domain.OcrEngineKind

/**
 * 一条用户明确保存的拍照翻译记录（AC3-05「图片默认不保存」的反面即「只有明确保存才留」）。
 * [imageAssetId] 为空表示该记录没有原图；图片字节存于私有资产区，绝不进 Room。
 */
data class SavedPhotoTranslation(
    val recordId: String,
    val profileId: String,
    val sourceText: String,
    val translatedText: String,
    val engine: OcrEngineKind?,
    val imageAssetId: String?,
    val createdAtEpochMillis: Long,
)

/**
 * 拍照翻译历史仓库。所有方法返回 [Result]：存储失败绝不抛穿 UI 层。
 * 边界约定与词笔记一致：引擎以固定枚举名入库，任意字符串不落库。
 */
interface PhotoTranslationHistoryRepository {
    suspend fun save(record: SavedPhotoTranslation): Result<Unit>

    /** 指定 profile 的记录，按创建时间降序；跨 profile 不可见。 */
    suspend fun listRecent(profileId: String): Result<List<SavedPhotoTranslation>>

    /** 删除单条记录；只删属于 [profileId] 的那条，并级联移除其原图资产。 */
    suspend fun delete(profileId: String, recordId: String): Result<Unit>

    /** 把原图字节写入私有资产区并登记 asset_records；返回资产 id。 */
    suspend fun storeImage(
        bytes: ByteArray,
    ): Result<String>

    /** 移除资产与登记；未知 id 视为成功空操作。 */
    suspend fun removeImage(assetId: String): Result<Unit>
}
