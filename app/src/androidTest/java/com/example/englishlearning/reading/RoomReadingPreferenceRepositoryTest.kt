package com.example.englishlearning.reading

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RoomReadingPreferenceRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ReadingPreferenceRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = RoomReadingPreferenceRepository(database, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun missingPreferenceReturnsDefaults() = runTest {
        val pref = repository.getPreference("p1").getOrThrow()

        assertEquals("p1", pref.profileId)
        assertEquals(ArticleType.STORY, pref.defaultArticleType)
        assertNull(pref.explicitLengthTier)
    }

    @Test
    fun saveThenReadReturnsSavedValues() = runTest {
        repository.savePreference(ReadingPreference("p1", ArticleType.NEWS, ArticleLengthTier.SHORT)).getOrThrow()

        val read = repository.getPreference("p1").getOrThrow()
        assertEquals(ArticleType.NEWS, read.defaultArticleType)
        assertEquals(ArticleLengthTier.SHORT, read.explicitLengthTier)
    }

    @Test
    fun saveOverwritesSameProfile() = runTest {
        repository.savePreference(ReadingPreference("p1", ArticleType.STORY, null)).getOrThrow()
        repository.savePreference(ReadingPreference("p1", ArticleType.NEWS, ArticleLengthTier.LONG)).getOrThrow()

        val read = repository.getPreference("p1").getOrThrow()
        assertEquals(ArticleType.NEWS, read.defaultArticleType)
        assertEquals(ArticleLengthTier.LONG, read.explicitLengthTier)
    }

    @Test
    fun preferencesAreScopedByProfile() = runTest {
        repository.savePreference(ReadingPreference("p1", ArticleType.NEWS, ArticleLengthTier.SHORT)).getOrThrow()
        repository.savePreference(ReadingPreference("p2", ArticleType.STORY, null)).getOrThrow()

        val p1 = repository.getPreference("p1").getOrThrow()
        val p2 = repository.getPreference("p2").getOrThrow()
        assertEquals(ArticleType.NEWS, p1.defaultArticleType)
        assertEquals(ArticleType.STORY, p2.defaultArticleType)
    }
}
