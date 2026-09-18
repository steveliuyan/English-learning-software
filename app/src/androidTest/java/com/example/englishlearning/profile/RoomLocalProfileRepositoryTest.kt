package com.example.englishlearning.profile

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomLocalProfileRepositoryTest {
    @Test fun `room repository persists profile across reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "profile-repository-test.db"
        context.deleteDatabase(name)
        var first: AppDatabase? = null
        var reopenedDb: AppDatabase? = null
        try {
            first = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).build()
            RoomLocalProfileRepository(first, kotlinx.coroutines.Dispatchers.IO).save(LocalProfile("default", "持久学习者", Instant.ofEpochMilli(42)))
            first.close()
            reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).build()
            val profile = RoomLocalProfileRepository(reopenedDb, kotlinx.coroutines.Dispatchers.IO).getDefault()
            assertEquals(LocalProfile("default", "持久学习者", Instant.ofEpochMilli(42)), profile)
        } finally {
            first?.close()
            reopenedDb?.close()
            context.deleteDatabase(name)
        }
    }
}
