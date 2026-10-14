package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.englishlearning.core.storage.entity.FavoriteWordEntity

@Dao
internal interface InternalFavoriteWordDao {
    /** 主键 (profileId, cardId) + IGNORE：同一张卡重复收藏不报错、不产生第二行。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(favorite: FavoriteWordEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_words WHERE profileId = :profileId AND cardId = :cardId)")
    suspend fun isFavorite(profileId: String, cardId: String): Boolean

    @Query("DELETE FROM favorite_words WHERE profileId = :profileId AND cardId = :cardId")
    suspend fun delete(profileId: String, cardId: String)

    @Query(
        "SELECT * FROM favorite_words WHERE profileId = :profileId " +
            "ORDER BY addedAtEpochMillis DESC, cardId ASC",
    )
    suspend fun listForProfile(profileId: String): List<FavoriteWordEntity>

    /** 保留面计数：「删除全部学习数据」确认框要明说保住了几条收藏（决策 §9）。 */
    @Query("SELECT COUNT(*) FROM favorite_words WHERE profileId = :profileId")
    suspend fun countForProfile(profileId: String): Int
}
