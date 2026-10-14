package com.example.englishlearning.learning

import java.time.Instant

/**
 * F4-01 决策⑦：收藏词。`lemma`/`wordBookId` 是收藏时刻的快照——原词书被删
 * （软删，词条内容物理清理）后收藏行仍完整可读，列表仍能显示词条名，
 * 只有「点开详情」会诚实地提示内容不可得。
 */
data class FavoriteWord(
    val profileId: String,
    val cardId: String,
    val wordBookId: String,
    val lemma: String,
    val addedAt: Instant,
)

/**
 * 收藏仓储端口。沿用统计侧的 `Result` 口径：存储不可用映射为
 * `AppErrorException(StorageUnavailable)`，取消照常上抛。
 */
interface FavoriteWordRepository {
    /** 已收藏则取消，未收藏则登记（时间取注入时钟）；重复登记由主键幂等。 */
    suspend fun toggle(profileId: String, cardId: String, wordBookId: String, lemma: String): Result<Unit>

    suspend fun isFavorite(profileId: String, cardId: String): Result<Boolean>

    /** 按收藏时间降序；调用方（收藏页）依赖该顺序。 */
    suspend fun listFavorites(profileId: String): Result<List<FavoriteWord>>
}
