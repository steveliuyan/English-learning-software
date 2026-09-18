package com.example.englishlearning.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.englishlearning.core.storage.dao.InternalAssetDao
import com.example.englishlearning.core.storage.dao.InternalLearningProfileDao
import com.example.englishlearning.core.storage.dao.InternalProfileDao
import com.example.englishlearning.core.storage.dao.InternalWordBookDao
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.KeyAliasEntity
import com.example.englishlearning.core.storage.entity.LearningProfileEntity
import com.example.englishlearning.core.storage.entity.LocalProfileEntity
import com.example.englishlearning.core.storage.entity.SchemaMetaEntity
import com.example.englishlearning.core.storage.entity.WordBookEntity

/**
 * Versioned Room metadata store. Every version transition must be supplied through [MIGRATIONS].
 * Destructive migration fallback is intentionally never configured by callers.
 */
@Database(
    entities = [
        SchemaMetaEntity::class,
        AssetRecordEntity::class,
        KeyAliasEntity::class,
        LocalProfileEntity::class,
        WordBookEntity::class,
        LearningProfileEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    internal abstract fun internalAssetDao(): InternalAssetDao

    internal abstract fun internalProfileDao(): InternalProfileDao

    internal abstract fun internalWordBookDao(): InternalWordBookDao

    internal abstract fun internalLearningProfileDao(): InternalLearningProfileDao

    companion object {
        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `local_profiles` " +
                            "(`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                    )
                }
            }

        val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `word_books` " +
                            "(`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                            "`level` TEXT NOT NULL, `totalWords` INTEGER NOT NULL, " +
                            "`dataVersion` TEXT NOT NULL, `sourceId` TEXT NOT NULL, " +
                            "PRIMARY KEY(`id`))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `learning_profiles` " +
                            "(`profileId` TEXT NOT NULL, `activeWordBookId` TEXT NOT NULL, " +
                            "`dailyNewTarget` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`profileId`), FOREIGN KEY(`activeWordBookId`) " +
                            "REFERENCES `word_books`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_learning_profiles_activeWordBookId` " +
                            "ON `learning_profiles` (`activeWordBookId`)",
                    )
                    createDailyTargetConstraintTriggers(db)
                }
            }

        internal val CONSTRAINT_CALLBACK: RoomDatabase.Callback =
            object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createDailyTargetConstraintTriggers(db)
                }
            }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        private fun createDailyTargetConstraintTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(DAILY_TARGET_INSERT_TRIGGER_SQL)
            db.execSQL(DAILY_TARGET_UPDATE_TRIGGER_SQL)
        }

        private const val DAILY_TARGET_INSERT_TRIGGER_SQL =
            "CREATE TRIGGER IF NOT EXISTS `learning_profiles_daily_target_insert_check` " +
                "BEFORE INSERT ON `learning_profiles` FOR EACH ROW " +
                "WHEN NEW.`dailyNewTarget` < 1 BEGIN " +
                "SELECT RAISE(ABORT, 'dailyNewTarget must be at least 1'); END"

        private const val DAILY_TARGET_UPDATE_TRIGGER_SQL =
            "CREATE TRIGGER IF NOT EXISTS `learning_profiles_daily_target_update_check` " +
                "BEFORE UPDATE OF `dailyNewTarget` ON `learning_profiles` FOR EACH ROW " +
                "WHEN NEW.`dailyNewTarget` < 1 BEGIN " +
                "SELECT RAISE(ABORT, 'dailyNewTarget must be at least 1'); END"
    }
}
