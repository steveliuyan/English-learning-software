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
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO schema_meta(`key`, `value`) VALUES (?, ?)", arrayOf("migration-test", "preserved"))
            execSQL("INSERT INTO asset_records(id, sha256, relativePath, byteSize, createdAt) VALUES (?, ?, ?, ?, ?)", arrayOf("asset-1", "abc", "assets/a", 3, 1))
            execSQL("INSERT INTO key_aliases(purpose, alias, createdAt) VALUES (?, ?, ?)", arrayOf("ai", "alias-1", 1))
            close()
        }

        Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB,
        ).addMigrations(*AppDatabase.MIGRATIONS)
            .build()
            .openHelper
            .writableDatabase
            .apply {
                query("SELECT value FROM schema_meta WHERE `key` = 'migration-test'").use { cursor ->
                    check(cursor.moveToFirst() && cursor.getString(0) == "preserved")
                }
                query("SELECT sha256 FROM asset_records WHERE id = 'asset-1'").use { cursor -> check(cursor.moveToFirst() && cursor.getString(0) == "abc") }
                query("SELECT alias FROM key_aliases WHERE purpose = 'ai'").use { cursor -> check(cursor.moveToFirst() && cursor.getString(0) == "alias-1") }
                query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'local_profiles'").use { cursor -> check(cursor.moveToFirst()) }
                close()
            }
    }

    private companion object {
        const val TEST_DB = "app-database-migration-test"
    }
}
