package com.example.englishlearning.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.FavoriteWordEntity
import com.example.englishlearning.core.time.FixedClockProvider
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F4-01 决策⑦收藏真库流程（androidTest）：mockk 只能钉仓储接线，这里钉 SQL 本身——
 * 主键 (profileId, cardId) 的重复收藏幂等、列表按 addedAt 降序、以及
 * 「删词书（软删）后收藏保留 lemma」的 Spec「保留」语义。
 */
@RunWith(AndroidJUnit4::class)
class RoomFavoritesFlowTest {
    private val profileId = "default"
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun toggleFavoritesAndKeepThemAfterWordBookDeletion(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "favorites-flow-${System.nanoTime()}.db"
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
        try {
            val clock = FixedClockProvider(Instant.ofEpochMilli(1_000L), zone)
            val favorites = RoomFavoriteWordRepository(database, clock, Dispatchers.Unconfined)
            val profiles = RoomLearningProfileRepository(database, Dispatchers.Unconfined)

            // 收藏两次同一张卡：主键给出幂等，只留一行。
            assertEquals(Unit, favorites.toggle(profileId, "imported:cat", "imported", "cat").getOrThrow())
            database.internalFavoriteWordDao().insertIgnore(
                FavoriteWordEntity(profileId, "imported:cat", "imported", "cat", 500L),
            )
            assertEquals(1, database.internalFavoriteWordDao().listForProfile(profileId).size)
            // 已收藏 → 再 toggle 即取消收藏。
            assertTrue(favorites.isFavorite(profileId, "imported:cat").getOrThrow())
            assertEquals(Unit, favorites.toggle(profileId, "imported:cat", "imported", "cat").getOrThrow())
            assertFalse(favorites.isFavorite(profileId, "imported:cat").getOrThrow())

            // 重新收藏 cat（addedAt=1000），再用直插造出严格递增的时间，排序断言才确定。
            assertEquals(Unit, favorites.toggle(profileId, "imported:cat", "imported", "cat").getOrThrow())
            database.internalFavoriteWordDao().insertIgnore(
                FavoriteWordEntity(profileId, "imported:run", "imported", "run", 2_000L),
            )
            database.internalFavoriteWordDao().insertIgnore(
                FavoriteWordEntity(profileId, "imported:big", "imported", "big", 9_000L),
            )
            val ordered = favorites.listFavorites(profileId).getOrThrow()
            assertEquals(
                "列表按收藏时间降序",
                listOf("imported:big", "imported:run", "imported:cat"),
                ordered.map { it.cardId },
            )

            // 删词书（软删 + 词条物理删除）后：收藏行与 lemma 快照原样保留。
            profiles.upsertWordBook(WordBook("imported", "导入册", "基础", 1, "v1", "test"))
            profiles.save(LearningProfile(profileId, "other-book", 10))
            assertEquals(RepositoryResult.Success(Unit), profiles.deleteWordBook(profileId, "imported"))
            val afterDeletion = favorites.listFavorites(profileId).getOrThrow()
            assertEquals(3, afterDeletion.size)
            assertTrue(afterDeletion.all { it.wordBookId == "imported" })
            assertEquals(listOf("big", "run", "cat"), afterDeletion.map { it.lemma })
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(name)
        }
    }
}
