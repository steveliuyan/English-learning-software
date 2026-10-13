package com.example.englishlearning.backup.restore

import com.example.englishlearning.backup.domain.AssetRecordDto
import com.example.englishlearning.backup.domain.BackupAead
import com.example.englishlearning.backup.domain.BackupCryptoPolicy
import com.example.englishlearning.backup.domain.BackupPackageValidator
import com.example.englishlearning.backup.domain.BackupPasswordKdf
import com.example.englishlearning.backup.domain.BackupSecretEntropy
import com.example.englishlearning.backup.domain.BackupSnapshotBuilder
import com.example.englishlearning.backup.domain.BackupSourceRows
import com.example.englishlearning.backup.domain.MigrationSigner
import com.example.englishlearning.backup.domain.ProtectedPackageInputs
import com.example.englishlearning.backup.infrastructure.ProtectedZipBackupPackageWriter
import com.example.englishlearning.backup.infrastructure.ZipBackupPackageWriter
import com.example.englishlearning.backup.pairing.JavaSecurityEcSignatureVerifier
import com.example.englishlearning.backup.pairing.MigrationCodeCodec
import com.example.englishlearning.backup.pairing.MigrationCodeDecodeResult
import com.example.englishlearning.backup.pairing.MigrationCodeFields
import com.example.englishlearning.backup.pairing.PairedMigrationChallengeVerifier
import com.example.englishlearning.core.storage.entity.AssetRecordEntity
import com.example.englishlearning.core.storage.entity.CardReviewStateEntity
import com.example.englishlearning.core.storage.entity.LearningProfileEntity
import com.example.englishlearning.core.storage.entity.LocalProfileEntity
import com.example.englishlearning.core.storage.entity.TodayPlanEntity
import com.example.englishlearning.core.storage.entity.TodayPlanTaskEntity
import com.example.englishlearning.core.storage.entity.VocabularyEntryEntity
import com.example.englishlearning.core.storage.entity.WordBookEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * AC4-04/05 restore pipeline anchors: the paired restore stages plaintext only after
 * the §5.2 order passes, fails closed with fixed categories, clears the scratch
 * session on every failure, and never touches formal data before full validation.
 */
class BackupRestorePipelineTest {
    private val key = ecKeyPair()
    private val nonce = ByteArray(16) { (it + 1).toByte() }
    private val secret = ByteArray(16) { (it + 51).toByte() }
    private val issuedAt = 1_700_000_000L
    private val expiresAt = issuedAt + 1_800L
    private val resourceBytes = "resource-payload-01".toByteArray(Charsets.UTF_8)
    private val now = issuedAt + 60L

    private val rows = BackupSourceRows(
        profiles = listOf(LocalProfileEntity("p-1", "User", 1L)),
        learningProfiles = listOf(LearningProfileEntity("p-1", "wb-1", 10)),
        learningSettings = emptyList(),
        todayPlans = listOf(TodayPlanEntity("plan-1", "p-1", "2026-10-13", "Asia/Shanghai", "wb-1", 5, 3, "v1", 1L)),
        todayPlanTasks = listOf(TodayPlanTaskEntity("plan-1", "card-1", "NEW", 0)),
        learningEvents = emptyList(),
        cardReviewStates = listOf(CardReviewStateEntity("card-1", "wb-1", "KNOWN", 1L, 2L)),
        wordBooks = listOf(WordBookEntity("wb-1", "Core", "cet4", 100, "1", "src")),
        vocabularyEntries = listOf(VocabularyEntryEntity("p-1", "wb-1", "card-1", 1L, "NEW")),
        migrationAudits = emptyList(),
        articles = emptyList(),
        readingCompletions = emptyList(),
        readingPreferences = emptyList(),
        searchHistory = emptyList(),
        wordAiNotes = emptyList(),
        photoTranslations = listOf(
            com.example.englishlearning.core.storage.entity.PhotoTranslationRecordEntity(
                "rec-1", "p-1", "Birds fly.", "鸟儿会飞。", "MlKit", "asset-1", 1L,
            ),
        ),
        assetRecords = listOf(
            AssetRecordEntity("asset-1", resourceBytes.sha256(), "assets/asset-1", resourceBytes.size.toLong(), 1L),
        ),
    )

    private fun ecKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun signer(pair: KeyPair) = MigrationSigner { scope ->
        Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(scope)
            sign()
        }
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    private class FixedEntropy : BackupSecretEntropy {
        override fun nextBytes(count: Int): ByteArray = ByteArray(count) { (it % 251).toByte() }
    }

    private class FakeKdf : BackupPasswordKdf {
        override fun deriveKeK(password: CharArray, salt: ByteArray): ByteArray =
            ByteArray(BackupCryptoPolicy.KDF_OUTPUT_BYTES) {
                (salt[it % salt.size] + it + password.fold(0) { acc, c -> acc + c.code }).toByte()
            }
    }

    /** Working AEAD fake: 28-byte key+AAD digest header then plaintext. */
    private class FakeAead : BackupAead {
        override fun encrypt(keyBytes: ByteArray, plaintext: ByteArray, additionalData: ByteArray): ByteArray =
            header(keyBytes, additionalData) + plaintext

        override fun decrypt(keyBytes: ByteArray, storageBytes: ByteArray, additionalData: ByteArray): ByteArray? {
            if (storageBytes.size <= 28) return null
            if (!storageBytes.copyOfRange(0, 28).contentEquals(header(keyBytes, additionalData))) return null
            return storageBytes.copyOfRange(28, storageBytes.size)
        }

        private fun header(keyBytes: ByteArray, aad: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(keyBytes + aad).copyOfRange(0, 28)
    }

    /** AEAD that wraps the DEK fine but fails every entry decryption (tamper simulation). */
    private class EntryBreakingAead : BackupAead {
        private val inner = FakeAead()
        override fun encrypt(keyBytes: ByteArray, plaintext: ByteArray, additionalData: ByteArray): ByteArray =
            if (plaintext.size == BackupCryptoPolicy.DEK_BYTES) inner.encrypt(keyBytes, plaintext, additionalData)
            else inner.encrypt(keyBytes, plaintext, additionalData)

        override fun decrypt(keyBytes: ByteArray, storageBytes: ByteArray, additionalData: ByteArray): ByteArray? =
            if (storageBytes.size == BackupCryptoPolicy.WRAPPED_DEK_BYTES) {
                inner.decrypt(keyBytes, storageBytes, additionalData)
            } else {
                null
            }
    }

    private fun protectedWrite(
        password: CharArray? = null,
        signerKey: KeyPair = key,
        entriesOverride: List<ProtectedZipBackupPackageWriter.Entry>? = null,
    ): ByteArray {
        val records = BackupSnapshotBuilder.buildRecords(rows, includeMediaAssets = true)
        val entries = entriesOverride ?: buildList {
            records.forEach { add(ProtectedZipBackupPackageWriter.Entry(it.path, it.jsonBytes)) }
            add(ProtectedZipBackupPackageWriter.Entry("resources/asset-1", resourceBytes))
        }
        val result = ProtectedZipBackupPackageWriter().write(
            entries = entries,
            createdAtEpochMillis = 1_700_000_000_000L,
            protection = ProtectedZipBackupPackageWriter.Protection(
                inputs = ProtectedPackageInputs(signerKey.public.encoded, nonce, secret, issuedAt, expiresAt),
                signer = signer(signerKey),
                passwordChars = password?.copyOf(),
                kdf = if (password != null) FakeKdf() else null,
                aead = if (password != null) FakeAead() else null,
                entropy = if (password != null) FixedEntropy() else null,
            ),
        )
        return when (result) {
            is ProtectedZipBackupPackageWriter.Result.Written -> result.bytes
            is ProtectedZipBackupPackageWriter.Result.Failed -> error("fixture must build: ${result.reason}")
        }
    }

    private fun salvageWrite(): ByteArray {
        val records = BackupSnapshotBuilder.buildRecords(rows, includeMediaAssets = false)
        val result = ZipBackupPackageWriter().write(
            records.map { ZipBackupPackageWriter.Entry(it.path, it.jsonBytes) },
            createdAtEpochMillis = 1_700_000_000_000L,
        )
        return assertIs<ZipBackupPackageWriter.Result.Written>(result).bytes
    }

    private fun scannedCode(
        secretBytes: ByteArray = secret,
        nonceBytes: ByteArray = nonce,
        keyPair: KeyPair = key,
    ): MigrationCodeFields = MigrationCodeFields(
        publicKeySpki = keyPair.public.encoded,
        nonce = nonceBytes,
        secret = secretBytes,
        issuedAtEpochSeconds = issuedAt,
        expiresAtEpochSeconds = expiresAt,
    )

    private class FakeWorkspace : RestoreStagingWorkspace {
        val stagedSessions = mutableMapOf<String, StagedRestoreSession>()
        val cleared = mutableListOf<String>()
        var stageAccepted = true

        override fun stage(session: StagedRestoreSession): Boolean {
            if (!stageAccepted) return false
            stagedSessions[session.sessionId] = session
            return true
        }

        override fun clear(sessionId: String) {
            stagedSessions.remove(sessionId)
            cleared += sessionId
        }

        override fun exists(sessionId: String): Boolean = stagedSessions.containsKey(sessionId)
    }

    private class FakeSpaceChecker(var hasRoom: Boolean = true) : RestoreSpaceChecker {
        var requestedBytes: Long = -1L
        override fun hasRoomFor(requiredBytes: Long): Boolean {
            requestedBytes = requiredBytes
            return hasRoom
        }
    }

    private class FakeCommitGateway : RestoreCommitGateway {
        val committed = mutableListOf<StagedRestoreSession>()
        var nextResult: RestoreCommitResult = RestoreCommitResult.Restored

        override fun commit(session: StagedRestoreSession): RestoreCommitResult {
            committed += session
            return nextResult
        }
    }

    private fun coordinator(
        workspace: FakeWorkspace = FakeWorkspace(),
        space: FakeSpaceChecker = FakeSpaceChecker(),
        gateway: FakeCommitGateway = FakeCommitGateway(),
        clockSeconds: Long = now,
        aead: BackupAead = FakeAead(),
    ): BackupRestoreCoordinator = BackupRestoreCoordinator(
        validator = BackupPackageValidator(),
        pairingVerifier = PairedMigrationChallengeVerifier(JavaSecurityEcSignatureVerifier()),
        kdf = FakeKdf(),
        aead = aead,
        spaceChecker = space,
        staging = workspace,
        commitGateway = gateway,
        sessionIdProvider = { "session-1" },
        clock = { clockSeconds },
    )

    private fun staged(
        bytes: ByteArray = protectedWrite(),
        code: MigrationCodeFields = scannedCode(),
        password: CharArray? = null,
        workspace: FakeWorkspace = FakeWorkspace(),
        space: FakeSpaceChecker = FakeSpaceChecker(),
        gateway: FakeCommitGateway = FakeCommitGateway(),
        clockSeconds: Long = now,
        aead: BackupAead = FakeAead(),
    ): Pair<StagedRestoreSession, BackupRestoreCoordinator> {
        val engine = coordinator(workspace, space, gateway, clockSeconds, aead)
        val outcome = engine.prepare(bytes, code, password?.copyOf())
        val session = assertIs<RestoreOutcome.Staged>(outcome).session
        return session to engine
    }

    @Test
    fun `plain v3 package stages records and resources for the confirm step`() {
        val workspace = FakeWorkspace()
        val session = staged(protectedWrite(), workspace = workspace).first

        assertEquals("session-1", session.sessionId)
        assertEquals(9, session.recordCount)
        assertEquals(1, session.mediaCount)
        assertEquals(issuedAt, session.pairingIssuedAtEpochSeconds)
        assertTrue(workspace.exists("session-1"))
        assertEquals("records/profiles/p-1.json", session.staged.records.first { it.type == "profiles" }.let { "records/${it.type}/${it.pathStableId}.json" })
    }

    @Test
    fun `commit hands the staged session to the gateway exactly once`() {
        val workspace = FakeWorkspace()
        val gateway = FakeCommitGateway()
        val session = staged(protectedWrite(), workspace = workspace, gateway = gateway).first

        val result = coordinator(workspace = workspace, gateway = gateway).commit(session)

        assertEquals(RestoreCommitResult.Restored, result)
        assertEquals(1, gateway.committed.size)
        assertEquals(session.sessionId, gateway.committed.single().sessionId)
    }

    @Test
    fun `encrypted package requires the password and stages decrypted plaintext`() {
        val password = "correct horse battery".toCharArray()
        val bytes = protectedWrite(password)
        val workspace = FakeWorkspace()

        assertIs<RestoreOutcome.Failed>(
            coordinator(workspace = workspace).prepare(bytes, scannedCode(), null),
        ).let { assertEquals(RestoreFailure.PasswordRequired, it.reason) }
        assertFalse(workspace.exists("session-1"))

        val session = staged(bytes, password = password, workspace = workspace).first
        val profile = session.staged.records.single { it.type == "profiles" }
        assertEquals("{\"schemaVersion\":1,\"id\":\"p-1\",\"displayName\":\"User\",\"createdAt\":1}", profile.json.toString(Charsets.UTF_8))
    }

    @Test
    fun `wrong password fails closed with a fixed category and zeroizes input`() {
        val bytes = protectedWrite("correct horse battery".toCharArray())
        val wrong = "wrong horse battery!".toCharArray()

        val outcome = coordinator().prepare(bytes, scannedCode(), wrong)

        assertEquals(RestoreFailure.PasswordIncorrect, assertIs<RestoreOutcome.Failed>(outcome).reason)
        assertTrue(wrong.all { it == '\u0000' }, "password must be zeroized")
    }

    @Test
    fun `entry tampering after key unwrap is a decryption failure`() {
        val bytes = protectedWrite("correct horse battery".toCharArray())
        val outcome = coordinator(aead = EntryBreakingAead()).prepare(bytes, scannedCode(), "correct horse battery".toCharArray())

        assertEquals(RestoreFailure.DecryptionFailure, assertIs<RestoreOutcome.Failed>(outcome).reason)
    }

    @Test
    fun `expired pairing code is rejected`() {
        val outcome = coordinator(clockSeconds = expiresAt + 1).prepare(protectedWrite(), scannedCode(), null)

        assertEquals(RestoreFailure.PairingExpired, assertIs<RestoreOutcome.Failed>(outcome).reason)
    }

    @Test
    fun `foreign pairing code (wrong nonce or secret) is a mismatch`() {
        assertEquals(
            RestoreFailure.PairingMismatch,
            assertIs<RestoreOutcome.Failed>(coordinator().prepare(protectedWrite(), scannedCode(nonceBytes = ByteArray(16) { 9 }), null)).reason,
        )
        assertEquals(
            RestoreFailure.PairingMismatch,
            assertIs<RestoreOutcome.Failed>(coordinator().prepare(protectedWrite(), scannedCode(secretBytes = ByteArray(16) { 7 }), null)).reason,
        )
    }

    @Test
    fun `tampered payload byte fails integrity before staging`() {
        val entries = unzip(protectedWrite())
        entries["records/articles/a-1.json"] = "{\"article\":tru2}".toByteArray(Charsets.UTF_8)
        val tampered = repack(entries)

        val outcome = coordinator().prepare(tampered, scannedCode(), null)

        assertEquals(RestoreFailure.IntegrityFailure, assertIs<RestoreOutcome.Failed>(outcome).reason)
    }

    @Test
    fun `v2 salvage package answers not restorable without entering the chain`() {
        val outcome = coordinator().prepare(salvageWrite(), scannedCode(), null)

        assertEquals(RestoreFailure.SalvageNotRestorable, assertIs<RestoreOutcome.Failed>(outcome).reason)
    }

    @Test
    fun `unknown format version is unsupported`() {
        val entries = unzip(protectedWrite())
        val manifest = String(entries.getValue("manifest.json"), Charsets.UTF_8)
        entries["manifest.json"] = manifest.replaceFirst("\"formatVersion\":3,", "\"formatVersion\":4,").toByteArray(Charsets.UTF_8)

        val outcome = coordinator().prepare(repack(entries), scannedCode(), null)

        assertEquals(RestoreFailure.UnsupportedRestoreVersion, assertIs<RestoreOutcome.Failed>(outcome).reason)
    }

    @Test
    fun `asset record without its resource file is a resource relationship failure`() {
        val records = BackupSnapshotBuilder.buildRecords(rows, includeMediaAssets = true)
        val entries = records.map { ProtectedZipBackupPackageWriter.Entry(it.path, it.jsonBytes) }
        val bytes = protectedWrite(entriesOverride = entries)

        val outcome = coordinator().prepare(bytes, scannedCode(), null)

        assertEquals(RestoreFailure.ResourceRelationshipInvalid, assertIs<RestoreOutcome.Failed>(outcome).reason)
    }

    @Test
    fun `resource mismatching the asset record digest is a resource relationship failure`() {
        val bytes = protectedWrite(
            entriesOverride = BackupSnapshotBuilder.buildRecords(rows, includeMediaAssets = true).map {
                ProtectedZipBackupPackageWriter.Entry(it.path, it.jsonBytes)
            } + ProtectedZipBackupPackageWriter.Entry("resources/asset-1", "other-payload".toByteArray(Charsets.UTF_8)),
        )

        val outcome = coordinator().prepare(bytes, scannedCode(), null)

        assertEquals(RestoreFailure.ResourceRelationshipInvalid, assertIs<RestoreOutcome.Failed>(outcome).reason)
    }

    @Test
    fun `plan task referencing a missing plan is an entity reference failure`() {
        val records = BackupSnapshotBuilder.buildRecords(rows, includeMediaAssets = true)
        val entries = records.map {
            ProtectedZipBackupPackageWriter.Entry(
                it.path,
                if (it.type == "today-plan-tasks") {
                    it.jsonText().replace("\"planId\":\"plan-1\"", "\"planId\":\"plan-x\"").toByteArray(Charsets.UTF_8)
                } else {
                    it.jsonBytes
                },
            )
        } + ProtectedZipBackupPackageWriter.Entry("resources/asset-1", resourceBytes)

        val outcome = coordinator().prepare(protectedWrite(entriesOverride = entries), scannedCode(), null)

        assertEquals(RestoreFailure.EntityReferenceInvalid, assertIs<RestoreOutcome.Failed>(outcome).reason)
    }

    @Test
    fun `photo translation record referencing a missing profile is an entity reference failure`() {
        val records = BackupSnapshotBuilder.buildRecords(rows, includeMediaAssets = true)
        val entries = records.map {
            ProtectedZipBackupPackageWriter.Entry(
                it.path,
                if (it.type == "photo-translation-records") {
                    it.jsonText().replace("\"profileId\":\"p-1\"", "\"profileId\":\"ghost\"").toByteArray(Charsets.UTF_8)
                } else {
                    it.jsonBytes
                },
            )
        } + ProtectedZipBackupPackageWriter.Entry("resources/asset-1", resourceBytes)

        val outcome = coordinator().prepare(protectedWrite(entriesOverride = entries), scannedCode(), null)

        assertEquals(RestoreFailure.EntityReferenceInvalid, assertIs<RestoreOutcome.Failed>(outcome).reason)
    }

    @Test
    fun `insufficient storage space rejects before any scratch write`() {
        val workspace = FakeWorkspace()
        val space = FakeSpaceChecker(hasRoom = false)
        val gateway = FakeCommitGateway()

        val outcome = coordinator(workspace, space, gateway).prepare(protectedWrite(), scannedCode(), null)

        assertEquals(RestoreFailure.StorageInsufficient, assertIs<RestoreOutcome.Failed>(outcome).reason)
        assertTrue(workspace.stagedSessions.isEmpty() && gateway.committed.isEmpty())
        assertTrue(space.requestedBytes > 0L)
    }

    @Test
    fun `commit failure clears the scratch session`() {
        val workspace = FakeWorkspace()
        val gateway = FakeCommitGateway().apply { nextResult = RestoreCommitResult.Failed(RestoreFailure.RollbackVerification) }
        val session = staged(protectedWrite(), workspace = workspace, gateway = gateway).first

        val result = coordinator(workspace = workspace, gateway = gateway).commit(session)

        assertEquals(RestoreFailure.RollbackVerification, assertIs<RestoreCommitResult.Failed>(result).reason)
        assertFalse(workspace.exists("session-1"))
    }

    @Test
    fun `staging failure is storage insufficient and the session never appears`() {
        val workspace = FakeWorkspace().apply { stageAccepted = false }
        val gateway = FakeCommitGateway()

        val outcome = coordinator(workspace = workspace, gateway = gateway).prepare(protectedWrite(), scannedCode(), null)

        assertEquals(RestoreFailure.StorageInsufficient, assertIs<RestoreOutcome.Failed>(outcome).reason)
        assertTrue(gateway.committed.isEmpty())
    }

    @Test
    fun `abandon discards a staged session without touching the gateway`() {
        val workspace = FakeWorkspace()
        val gateway = FakeCommitGateway()
        val session = staged(protectedWrite(), workspace = workspace, gateway = gateway).first

        coordinator(workspace = workspace, gateway = gateway).abandon(session.sessionId)

        assertFalse(workspace.exists("session-1"))
        assertTrue(gateway.committed.isEmpty())
    }

    @Test
    fun `codec round-trips snapshot records and rejects unknown json fields`() {
        val records = BackupSnapshotBuilder.buildRecords(rows, includeMediaAssets = true)
        val entries = records.associate { it.path to it.jsonBytes }

        val decoded = RestoreRecordCodec.decode(entries)
        assertIs<RestoreRecordCodec.Result.Parsed>(decoded)
        assertEquals("p-1", decoded.rows.profiles.single().id)
        assertEquals("asset-1", decoded.rows.assetRecords.single().id)

        val withUnknown = entries.toMutableMap()
        withUnknown["records/profiles/profile-1.json"] =
            "{\"schemaVersion\":1,\"id\":\"p-1\",\"displayName\":\"User\",\"createdAt\":1,\"extra\":1}".toByteArray(Charsets.UTF_8)
        assertIs<RestoreRecordCodec.Result.Unreadable>(RestoreRecordCodec.decode(withUnknown))
    }

    @Test
    fun `scanned code round-trips through the codec for fixtures`() {
        val encoded = MigrationCodeCodec.encode(
            com.example.englishlearning.backup.pairing.MigrationChallenge(key.public.encoded, nonce, secret, issuedAt, expiresAt),
        )
        val parsed = assertIs<MigrationCodeDecodeResult.Parsed>(MigrationCodeCodec.decode(encoded))
        assertTrue(parsed.fields.secret.contentEquals(secret))    }

    @Test
    fun `asset record dto keeps only public fields`() {
        val dto = AssetRecordDto(AssetRecordEntity("asset-1", resourceBytes.sha256(), "assets/asset-1", resourceBytes.size.toLong(), 1L))
        val json = kotlinx.serialization.json.Json.encodeToString(AssetRecordDto.serializer(), dto)
        assertFalse("\"relativePath\"" in json)
        assertTrue("\"sha256\"" in json)
    }

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val output = ByteArrayOutputStream()
                zip.copyTo(output)
                map[entry.name] = output.toByteArray()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return map
    }

    private fun repack(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (path, value) ->
                zip.putNextEntry(ZipEntry(path).also { it.time = 0L })
                zip.write(value)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
