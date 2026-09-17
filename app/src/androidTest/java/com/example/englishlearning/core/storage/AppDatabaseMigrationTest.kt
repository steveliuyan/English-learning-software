package com.example.englishlearning.core.storage

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @Test
    fun migrateAllHistoricalSchemasWithoutDestructiveFallback() {
        val helper =
            MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                requireNotNull(AppDatabase::class.java.canonicalName),
                FrameworkSQLiteOpenHelperFactory(),
            )
        helper.createDatabase(TEST_DB, 1).close()

        Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB,
        ).addMigrations(*AppDatabase.MIGRATIONS)
            .build()
            .openHelper
            .writableDatabase
            .close()
    }

    private companion object {
        const val TEST_DB = "app-database-migration-test"
    }
}
