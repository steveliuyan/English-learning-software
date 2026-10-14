package com.example.englishlearning.backup.restore

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.RoomAiProfileRepository
import com.example.englishlearning.backup.device.AndroidPostRestoreActions
import com.example.englishlearning.backup.device.AndroidRestoreSpaceChecker
import com.example.englishlearning.backup.device.RestoreEntityDecoder
import com.example.englishlearning.backup.device.RoomFormalDataHandle
import com.example.englishlearning.backup.device.RoomRestoreDataWriter
import com.example.englishlearning.backup.domain.BackupPackageValidator
import com.example.englishlearning.backup.infrastructure.BouncyCastleArgon2idKdf
import com.example.englishlearning.backup.infrastructure.FileRestoreStagingWorkspace
import com.example.englishlearning.backup.infrastructure.TinkAes256GcmAead
import com.example.englishlearning.backup.pairing.MigrationCodeCodec
import com.example.englishlearning.backup.pairing.MigrationCodeDecodeResult
import com.example.englishlearning.backup.pairing.PairedMigrationChallengeVerifier
import com.example.englishlearning.backup.pairing.JavaSecurityEcSignatureVerifier
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.time.SystemClockProvider
import com.example.englishlearning.learning.BundledWordBookIdSource
import com.example.englishlearning.learning.ImportedWordBookIdSource
import com.example.englishlearning.learning.RefreshVocabularySearchIndexUseCase
import com.example.englishlearning.learning.RoomLearningEventRepository
import com.example.englishlearning.learning.RoomLearningProfileRepository
import com.example.englishlearning.learning.RoomTodayPlanRepository
import com.example.englishlearning.learning.RoomVocabularySearchIndexRepository
import com.example.englishlearning.learning.bundledWordBookIds
import com.example.englishlearning.notification.RoomReminderPreferenceRepository
import com.example.englishlearning.notification.WorkManagerReminderScheduler
import com.example.englishlearning.wordbook.CompositeWordCardSource
import com.example.englishlearning.learning.WordBookMetadataAssetSource
import com.example.englishlearning.wordbook.importedBookDirectories
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * QA 第二轮 B1/B2：真机侧无法实时扫码（文本 QR 不可解码 + Done 后挑战码不再显示，
 * 见走查缺陷），本测试用旧设备真实导出的受保护包与真实挑战码（adb 推送到
 * /data/local/tmp，不落仓库）驱动与 AppModule 相同的生产装配，走完整恢复链：
 * 结构校验 → 配对绑定/验签 → 隔离暂存 → 替换提交 → 回滚点 + 进程内重开。
 */
@RunWith(AndroidJUnit4::class)
class DevicePairedRestoreE2ETest {

    /** QA 诊断装饰器：仅测试侧记录各阶段返回值，定位 commit 失败点（不改动生产语义）。 */
    private class LoggingFormalDataHandle(
        private val delegate: RoomFormalDataHandle,
    ) : FormalDataHandle by delegate {
        override fun checkpointAndClose(): Boolean =
            delegate.checkpointAndClose().also { android.util.Log.i("QA-B1", "checkpointAndClose=$it") }

        override fun reopenAndQuickCheck(): Boolean =
            delegate.reopenAndQuickCheck().also { android.util.Log.i("QA-B1", "reopenAndQuickCheck=$it") }
    }

    private class LoggingPostRestoreActions(
        private val delegate: PostRestoreActions,
        private val indexRefresh: RefreshVocabularySearchIndexUseCase,
        private val profiles: RoomLearningProfileRepository,
        private val cards: com.example.englishlearning.wordbook.CompositeWordCardSource,
        private val index: RoomVocabularySearchIndexRepository,
        private val bundledIds: BundledWordBookIdSource,
        private val importedIds: ImportedWordBookIdSource,
        private val database: AppDatabase,
    ) : PostRestoreActions by delegate {
        override fun rebuildSearchIndex(): Boolean = try {
            kotlinx.coroutines.runBlocking {
                try {
                    val rows = database.internalWordBookDao().listAll()
                    android.util.Log.i("QA-B1", "rawDao listAll ok rows=${rows.size} ids=${rows.map { it.id }}")
                } catch (t: Throwable) {
                    android.util.Log.e("QA-B1", "rawDao listAll threw: ${t}", t)
                }
                val all = when (val r = profiles.listWordBooks()) {
                    is com.example.englishlearning.learning.RepositoryResult.Success -> r.value
                    is com.example.englishlearning.learning.RepositoryResult.Failure -> {
                        android.util.Log.e("QA-B1", "step listWordBooks=Failure(${r.error})")
                        return@runBlocking false
                    }
                }
                val b = bundledIds.ids()
                val i = importedIds.ids()
                val visible = com.example.englishlearning.learning.WordBookVisibility.visible(all, b, i)
                android.util.Log.i(
                    "QA-B1",
                    "books all=${all.map { "${it.id}/${it.dataVersion}/${it.totalWords}" }} " +
                        "bundled=${b.size} imported=${i.size} " +
                        "visible=${visible.map { "${it.id}/${it.dataVersion}/${it.totalWords}" }}",
                )
                val versions = when (val r = index.indexedVersions()) {
                    is com.example.englishlearning.learning.RepositoryResult.Success -> r.value
                    is com.example.englishlearning.learning.RepositoryResult.Failure -> {
                        android.util.Log.e("QA-B1", "step indexedVersions=Failure(${r.error})")
                        return@runBlocking false
                    }
                }
                android.util.Log.i("QA-B1", "indexedVersions=$versions")
                for (book in visible) {
                    if (versions[book.id] == book.dataVersion) continue
                    val rawIds = cards.cardIds(book.id)
                    val ids = rawIds.filterNot { it.startsWith("placeholder:") }
                    android.util.Log.i(
                        "QA-B1",
                        "step book=${book.id} raw=${rawIds.size} ids=${ids.size} total=${book.totalWords}",
                    )
                    if (book.totalWords > 0 && ids.isEmpty()) {
                        android.util.Log.e("QA-B1", "step emptyIdsForNonEmptyBook -> StorageUnavailable")
                        return@runBlocking false
                    }
                    val content = cards.cards(ids).filter { it.wordBookId == book.id }
                    android.util.Log.i("QA-B1", "step book=${book.id} content=${content.size} of ${ids.size}")
                    if (content.size != ids.size) {
                        android.util.Log.e("QA-B1", "step incompleteContent -> StorageUnavailable")
                        return@runBlocking false
                    }
                }
                when (val r = indexRefresh()) {
                    is com.example.englishlearning.learning.RepositoryResult.Success -> {
                        android.util.Log.i("QA-B1", "rebuildSearchIndex=Success(${r.value})")
                        true
                    }
                    is com.example.englishlearning.learning.RepositoryResult.Failure -> {
                        android.util.Log.e("QA-B1", "rebuildSearchIndex=Failure(${r.error})")
                        false
                    }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("QA-B1", "rebuildSearchIndex threw", t)
            false
        }

        override fun rescheduleReminders(): Boolean =
            delegate.rescheduleReminders().also { android.util.Log.i("QA-B1", "rescheduleReminders=$it") }

        override fun eraseAiCredentials(): Boolean =
            delegate.eraseAiCredentials().also { android.util.Log.i("QA-B1", "eraseAiCredentials=$it") }

        override fun writeRestoreAudit(session: StagedRestoreSession): Boolean =
            delegate.writeRestoreAudit(session).also { android.util.Log.i("QA-B1", "writeRestoreAudit=$it") }
    }

    private fun productionCoordinator(app: Application): BackupRestoreCoordinator {
        val io = Dispatchers.IO
        val clock = SystemClockProvider()
        val database = Room.databaseBuilder(app, AppDatabase::class.java, "english-learning.db")
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
        val metadataSource = WordBookMetadataAssetSource {
            app.assets.open("wordbooks/metadata.json").bufferedReader().use { it.readText() }
        }
        val cards = CompositeWordCardSource(
            imported = com.example.englishlearning.wordbook.ImportedWordBookSource(
                File(app.filesDir, "wordbooks"), io,
            ),
            bundled = com.example.englishlearning.wordbook.BundledWordBookSource(
                assets = app.assets,
                root = File(app.filesDir, "bundled-wordbooks"),
                io = io,
            ),
        )
        val indexRefresh = RefreshVocabularySearchIndexUseCase(
            RoomLearningProfileRepository(database, io),
            cards,
            RoomVocabularySearchIndexRepository(database, io),
            BundledWordBookIdSource { bundledWordBookIds(metadataSource.read()) },
            ImportedWordBookIdSource { importedBookDirectories(File(app.filesDir, "wordbooks")).map { it.name }.toSet() },
        )
        return BackupRestoreCoordinator(
            validator = BackupPackageValidator(),
            pairingVerifier = PairedMigrationChallengeVerifier(JavaSecurityEcSignatureVerifier()),
            kdf = BouncyCastleArgon2idKdf(),
            aead = TinkAes256GcmAead(),
            spaceChecker = AndroidRestoreSpaceChecker(app),
            staging = FileRestoreStagingWorkspace(File(app.cacheDir, "restore-scratch")),
            commitGateway = SnapshotSwapRestoreCommitGateway(
                rollbackRoot = File(app.filesDir, "rollback"),
                scratchRoot = File(app.cacheDir, "restore-scratch"),
                targets = RoomRestoreDataWriter.swapTargets(app),
                database = LoggingFormalDataHandle(RoomFormalDataHandle(database)),
                writer = RoomRestoreDataWriter(app, RestoreEntityDecoder()),
                postActions = LoggingPostRestoreActions(
                    AndroidPostRestoreActions(
                        app,
                        indexRefresh,
                        WorkManagerReminderScheduler(
                            app,
                            RoomReminderPreferenceRepository(database, io),
                            RoomTodayPlanRepository(database, io),
                            RoomLearningEventRepository(database, io),
                            clock,
                        ),
                        RoomAiProfileRepository(database, io),
                        AiProfileSecretUseCase(com.example.englishlearning.core.security.AndroidKeyStoreSecretStore(app)),
                    ),
                    indexRefresh,
                    RoomLearningProfileRepository(database, io),
                    cards,
                    RoomVocabularySearchIndexRepository(database, io),
                    BundledWordBookIdSource { bundledWordBookIds(metadataSource.read()) },
                    ImportedWordBookIdSource { importedBookDirectories(File(app.filesDir, "wordbooks")).map { it.name }.toSet() },
                    database,
                ),
                clockMillis = { System.currentTimeMillis() },
            ),
            sessionIdProvider = { UUID.randomUUID().toString() },
            clock = { System.currentTimeMillis() / 1000 },
        )
    }

    private fun loadInputs(): Pair<ByteArray, com.example.englishlearning.backup.pairing.MigrationCodeFields> {
        val dir = File("/data/local/tmp")
        val pkg = File(dir, "qa-b1-package.zip").readBytes()
        val raw = File(dir, "qa-b1-code.txt").readText().trim()
        val parsed = MigrationCodeCodec.decode(raw)
        assertTrue("challenge code must parse", parsed is MigrationCodeDecodeResult.Parsed)
        return pkg to (parsed as MigrationCodeDecodeResult.Parsed).fields
    }

    @Test
    fun realPackageWithRealChallengeRestoresEndToEnd() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val coordinator = productionCoordinator(app)
        val (pkg, code) = loadInputs()

        val dbFile = app.getDatabasePath("english-learning.db")
        val rollbackRoot = File(app.filesDir, "rollback")
        val rollbacksBefore = rollbackRoot.listFiles()?.size ?: 0

        val staged = coordinator.prepare(pkg, code, passwordChars = null)
        assertTrue("prepare must stage, got $staged", staged is RestoreOutcome.Staged)
        val session = (staged as RestoreOutcome.Staged).session
        assertEquals(23, session.recordCount)

        val commit = coordinator.commit(session)
        assertTrue("commit must restore, got $commit", commit is RestoreCommitResult.Restored)

        val rollbacksAfter = rollbackRoot.listFiles()?.size ?: 0
        assertTrue("rollback point created", rollbacksAfter > rollbacksBefore)
        assertTrue("formal db present after swap", dbFile.length() > 0)
        assertTrue(
            "rollback point keeps old db triple",
            rollbackRoot.listFiles()!!.last().listFiles()!!.any { it.name == "english-learning.db" },
        )
    }

    @Test
    fun tamperedPackageIsRejectedWithoutTouchingFormalData() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val coordinator = productionCoordinator(app)
        val (pkg0, code) = loadInputs()
        val pkg = pkg0.copyOf()
        pkg[pkg.size / 2] = (pkg[pkg.size / 2].toInt() xor 0xFF).toByte()
        val dbFile = app.getDatabasePath("english-learning.db")
        val hashBefore = dbFile.readBytes().let { java.security.MessageDigest.getInstance("SHA-256").digest(it) }
        val outcome = coordinator.prepare(pkg, code, null)
        assertTrue("tampered package must fail", outcome is RestoreOutcome.Failed)
        val hashAfter = dbFile.readBytes().let { java.security.MessageDigest.getInstance("SHA-256").digest(it) }
        assertTrue("formal db untouched", hashBefore.contentEquals(hashAfter))
    }
}
