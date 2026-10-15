package com.example.englishlearning.perf

import androidx.room.Room
import androidx.room.withTransaction
import com.example.englishlearning.backup.infrastructure.ZipBackupPackageWriter
import com.example.englishlearning.backup.restore.RestoredRecord
import com.example.englishlearning.backup.restore.StagedRestore
import com.example.englishlearning.backup.restore.StagedRestoreSession
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.CardReviewStateEntity
import com.example.englishlearning.core.storage.entity.TodayPlanEntity
import com.example.englishlearning.core.storage.entity.TodayPlanTaskEntity
import com.example.englishlearning.core.storage.entity.VocabularyEntryEntity
import com.example.englishlearning.learning.domain.WordCard
import kotlin.system.measureTimeMillis

/**
 * SP-B 性能基线夹具（androidTest）：为 [PerfSixMetricsInstrumentedTest] 提供
 * 批量种数与计时 helper。全部走生产写路径（DAO/仓储），不造旁路数据。
 */
object SeedData {
    const val PROFILE_ID = "perf-profile"
    const val WORD_BOOK_ID = "perf-book"

    /** 首轮生存线：防挂死。验收阈值见 [thresholdMillis]，口径 perf-baseline-2026-10.md。 */
    const val SURVIVAL_LINE_MS = 5_000L

    /**
     * SP-B Task 5 回填阈值：emulator-5574（API 30）实测中位数 ×2，
     * <1s 项取整到 50ms 档、≥1s 项到 500ms 档（决策 §1）。
     * 来源 docs/verification/perf-baseline-2026-10.md；真机 bf353dda 复测列 TODO-USER。
     */
    fun thresholdMillis(metric: String): Long = when (metric) {
        "plan_read" -> 50L
        "card_paging" -> 50L
        "word_lookup" -> 50L
        "event_append_100" -> 650L
        "backup_validate_500" -> 500L
        "restore_write_500" -> 350L
        else -> SURVIVAL_LINE_MS
    }

    fun newDatabaseName(prefix: String): String = "perf-$prefix-${System.nanoTime()}.db"

    fun newDatabase(context: android.content.Context, name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()

    fun vocabularyEntries(count: Int): List<VocabularyEntryEntity> =
        (1..count).map { i ->
            VocabularyEntryEntity(
                profileId = PROFILE_ID, wordBookId = WORD_BOOK_ID,
                cardId = "perf-card-%04d".format(i), addedAtEpochMillis = i.toLong(), lastFeedback = "Good",
            )
        }

    suspend fun seedVocabularyEntries(database: AppDatabase, count: Int) {
        val rows = vocabularyEntries(count)
        database.withTransaction { database.internalBackupRestoreDao().insertVocabularyEntries(rows) }
    }

    suspend fun seedPlanWithTasks(database: AppDatabase, taskCount: Int, localDate: String) {
        database.withTransaction {
            val dao = database.internalTodayPlanDao()
            dao.insertPlan(
                TodayPlanEntity(
                    planId = "perf-plan", profileId = PROFILE_ID, localDate = localDate, zoneId = "UTC",
                    activeWordBookId = WORD_BOOK_ID, newTarget = taskCount / 2, dueTarget = taskCount - taskCount / 2,
                    ruleVersion = "perf-rule", generatedAtEpochMillis = 1L,
                ),
            )
            dao.insertTasks(
                (1..taskCount).map { i ->
                    TodayPlanTaskEntity("perf-plan", "perf-card-%04d".format(i), if (i % 2 == 0) "NEW" else "DUE", i)
                },
            )
        }
    }

    /** 一半到期、一半未来：dueCardIds 的真实工作集。 */
    suspend fun seedReviewStates(database: AppDatabase, stateCount: Int, nowEpochMillis: Long) {
        database.withTransaction {
            val dao = database.internalBackupRestoreDao()
            dao.insertCardReviewStates(
                (1..stateCount).map { i ->
                    CardReviewStateEntity(
                        cardId = "perf-card-%04d".format(i), wordBookId = WORD_BOOK_ID, lastFeedback = "Good",
                        lastReviewedAtEpochMillis = nowEpochMillis - 864_000_000L,
                        nextReviewAtEpochMillis = if (i % 2 == 0) nowEpochMillis - 1L else nowEpochMillis + 1_000L + i,
                    )
                },
            )
        }
    }

    fun wordCards(count: Int): List<WordCard> = (1..count).map { i ->
        WordCard(
            cardId = "perf-card-%04d".format(i), wordBookId = WORD_BOOK_ID,
            lemma = "capword%04d".format(i), ipa = "/kæpwɜːd/", partOfSpeech = "n.",
            meaningZh = "性能基准词",
        )
    }

    /** 500 条 manifest 表项的合法未加密包字节：只测 [BackupPackageValidator.validate] 的结构校验。 */
    fun backupPackageBytes(entryCount: Int): ByteArray {
        val entries = (1..entryCount).map { i ->
            ZipBackupPackageWriter.Entry("records/vocabulary-entries/perf-$i.json", "{\"placeholder\":true}".encodeToByteArray())
        }
        val result = ZipBackupPackageWriter().write(entries, createdAtEpochMillis = 1_700_000_000_000L)
        return (result as ZipBackupPackageWriter.Result.Written).bytes
    }

    /**
     * 恢复写入计时的输入：只带 500 条 vocabulary-entries 的 [StagedRestoreSession]。
     * JSON 手写以匹配 RestoreEntityDecoder 的 DTO 契约（ignoreUnknownKeys=false，
     * 字段名漂移会在 writeReplacement 返回 null 时立刻暴露，不会测到假数据）。
     */
    fun restoreSession(recordCount: Int): StagedRestoreSession {
        val records = (1..recordCount).map { i ->
            val json = buildString {
                append("{\"profileId\":\"").append(PROFILE_ID)
                append("\",\"wordBookId\":\"").append(WORD_BOOK_ID)
                append("\",\"cardId\":\"perf-card-%04d\"".format(i))
                append(",\"addedAtEpochMillis\":").append(i)
                append(",\"lastFeedback\":\"Good\"}")
            }
            RestoredRecord(type = "vocabulary-entries", pathStableId = "perf-$i", json = json.encodeToByteArray())
        }
        val staged = StagedRestore(records = records, resources = emptyList())
        return StagedRestoreSession(
            sessionId = "perf-session",
            recordCount = recordCount,
            mediaCount = 0,
            plaintextBytes = staged.plaintextBytes,
            pairingIssuedAtEpochSeconds = 0L,
            pairingExpiresAtEpochSeconds = Long.MAX_VALUE / 1000L,
            staged = staged,
        )
    }

    suspend fun medianMillis(runs: Int = 5, block: suspend () -> Unit): Long =
        (1..runs).map { run -> measureTimeMillis { block() } }.sorted()[runs / 2]

    fun report(metric: String, medianMillis: Long, runs: Int) {
        val line = "PERF|$metric|$medianMillis|$runs"
        android.util.Log.i("PERF", line)
        println(line)
    }
}
