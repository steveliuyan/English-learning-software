package com.example.englishlearning.backup.restore

import com.example.englishlearning.backup.domain.BackupCryptoPolicy
import com.example.englishlearning.backup.domain.BackupPackageFormat
import com.example.englishlearning.backup.domain.PackageLimits
import com.example.englishlearning.backup.domain.PackageValidationFailure
import com.example.englishlearning.backup.domain.isAllowedPayloadPath
import com.example.englishlearning.backup.pairing.PackagePairingRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One decrypted plaintext record from `records/<type>/<id>.json`. */
data class RestoredRecord(val type: String, val pathStableId: String, val json: ByteArray)

/** One decrypted plaintext media payload keyed by its asset id. */
data class RestoredResource(val assetId: String, val bytes: ByteArray)

/** The full plaintext content of a validated package, ready for staging. */
data class StagedRestore(
    val records: List<RestoredRecord>,
    val resources: List<RestoredResource>,
) {
    val plaintextBytes: Long
        get() = records.sumOf { it.json.size.toLong() } + resources.sumOf { it.bytes.size.toLong() }
}

/** Everything the UI confirm step shows; all fields are non-secret. */
data class StagedRestoreSession(
    val sessionId: String,
    val recordCount: Int,
    val mediaCount: Int,
    val plaintextBytes: Long,
    val pairingIssuedAtEpochSeconds: Long,
    val pairingExpiresAtEpochSeconds: Long,
    val staged: StagedRestore,
)

sealed interface RestoreOutcome {
    data class Staged(val session: StagedRestoreSession) : RestoreOutcome
    data class Failed(val reason: RestoreFailure) : RestoreOutcome
}

sealed interface RestoreCommitResult {
    data object Restored : RestoreCommitResult
    data class Failed(val reason: RestoreFailure) : RestoreCommitResult
}

/** Persists the staged plaintext into the isolated scratch area; failure means disk-level rejection. */
interface RestoreStagingWorkspace {
    fun stage(session: StagedRestoreSession): Boolean
    fun clear(sessionId: String)
    fun exists(sessionId: String): Boolean
}

/** Capacity gate; called before any scratch write. */
fun interface RestoreSpaceChecker {
    fun hasRoomFor(requiredBytes: Long): Boolean
}

/**
 * Executes the second-confirmation replacement: rollback point, atomic replace,
 * verification and (on device) post-restore maintenance. Fixed categories only.
 */
interface RestoreCommitGateway {
    fun commit(session: StagedRestoreSession): RestoreCommitResult
}

/** Raw zip content captured after the validator passed; manifest raw bytes keep the signature scope verifiable. */
private data class RawPackage(
    val manifestBytes: ByteArray,
    val pairingJsonBytes: ByteArray?,
    val payload: Map<String, ByteArray>,
)

/**
 * Domain-side restore pipeline (§5.2 order, fail-closed, scratch-free until commit):
 * version probe → structural validation → pairing challenge binding → optional
 * password/KDF/AEAD → strict record decoding → resource relationships → entity
 * references → storage space. Any failure leaves formal data untouched.
 */
class BackupRestoreCoordinator(
    private val validator: com.example.englishlearning.backup.domain.BackupPackageValidator,
    private val pairingVerifier: com.example.englishlearning.backup.pairing.PairedMigrationChallengeVerifier,
    private val kdf: com.example.englishlearning.backup.domain.BackupPasswordKdf,
    private val aead: com.example.englishlearning.backup.domain.BackupAead,
    private val spaceChecker: RestoreSpaceChecker,
    private val staging: RestoreStagingWorkspace,
    private val commitGateway: RestoreCommitGateway,
    private val sessionIdProvider: () -> String,
    private val clock: () -> Long,
) {
    fun prepare(
        packageBytes: ByteArray,
        scannedCode: com.example.englishlearning.backup.pairing.MigrationCodeFields,
        passwordChars: CharArray?,
    ): RestoreOutcome {
        val raw = when (val read = readRaw(packageBytes)) {
            is RawOutcome.Rejected -> return RestoreOutcome.Failed(RestoreFailure.from(read.reason))
            is RawOutcome.Read -> read.raw
        }
        val version = probeFormatVersion(raw.manifestBytes) ?: return failManifest()
        if (version == BackupPackageFormat.SALVAGE_VERSION) return RestoreOutcome.Failed(RestoreFailure.SalvageNotRestorable)
        if (version != BackupPackageFormat.PAIRED_VERSION) return RestoreOutcome.Failed(RestoreFailure.UnsupportedRestoreVersion)

        val manifest = when (val validated = validator.validate(packageBytes)) {
            is com.example.englishlearning.backup.domain.PackageValidationResult.Invalid ->
                return RestoreOutcome.Failed(RestoreFailure.from(validated.reason))
            is com.example.englishlearning.backup.domain.PackageValidationResult.Valid -> validated.manifest
        }

        // Pairing challenge binding against the scanned code (constant-time), incl. signature re-verification.
        val pairingBlock = manifest.pairing ?: return failManifest()
        val pairingRecord = pairingRecord(pairingBlock, raw.manifestBytes, raw.pairingJsonBytes)
            ?: return RestoreOutcome.Failed(RestoreFailure.PairingMismatch)
        when (val check = pairingVerifier.check(scannedCode, pairingRecord, clock())) {
            is com.example.englishlearning.backup.pairing.PairingCheckResult.Invalid ->
                return RestoreOutcome.Failed(check.reason.toRestoreFailure())
            is com.example.englishlearning.backup.pairing.PairingCheckResult.Paired -> Unit
        }

        val stored = raw.payload
        val plaintext: Map<String, ByteArray> = if (manifest.encryptionStatus == BackupPackageFormat.ENCRYPTION_STATUS_V3_AEAD) {
            val password = passwordChars
            if (password == null) {
                passwordChars?.fill('\u0000')
                return RestoreOutcome.Failed(RestoreFailure.PasswordRequired)
            }
            if (!BackupCryptoPolicy.isPasswordShapeValid(password.size)) {
                password.fill('\u0000')
                return RestoreOutcome.Failed(RestoreFailure.PasswordIncorrect)
            }
            decryptOutcome(manifest, stored, password).let { outcome ->
                when (outcome) {
                    is DecryptOutcome.Ok -> outcome.plaintext
                    is DecryptOutcome.PasswordRejected -> return RestoreOutcome.Failed(RestoreFailure.PasswordIncorrect)
                    is DecryptOutcome.Tampered -> return RestoreOutcome.Failed(RestoreFailure.DecryptionFailure)
                }
            }
        } else {
            passwordChars?.fill('\u0000')
            stored
        }

        val decoded = when (val result = RestoreRecordCodec.decode(plaintext.filterKeys { it.startsWith("records/") })) {
            is RestoreRecordCodec.Result.Parsed -> result.rows
            is RestoreRecordCodec.Result.Unreadable -> return RestoreOutcome.Failed(RestoreFailure.IntegrityFailure)
        }

        val resourcesById = plaintext.filterKeys { it.startsWith("resources/") }
            .mapKeys { it.key.removePrefix("resources/") }
        when (val content = RestoreContentValidator.validate(decoded, resourcesById)) {
            is RestoreContentValidator.Result.Invalid ->
                return RestoreOutcome.Failed(content.reason.toRestoreFailure())
            is RestoreContentValidator.Result.Valid -> Unit
        }

        val staged = StagedRestore(
            records = decoded.allRecords.map { RestoredRecord(it.type, it.pathStableId, it.json) },
            resources = resourcesById.map { (assetId, bytes) -> RestoredResource(assetId, bytes) },
        )
        if (!spaceChecker.hasRoomFor(staged.plaintextBytes)) {
            return RestoreOutcome.Failed(RestoreFailure.StorageInsufficient)
        }
        val session = StagedRestoreSession(
            sessionId = sessionIdProvider(),
            recordCount = staged.records.size,
            mediaCount = staged.resources.size,
            plaintextBytes = staged.plaintextBytes,
            pairingIssuedAtEpochSeconds = pairingBlock.issuedAtEpochSeconds,
            pairingExpiresAtEpochSeconds = pairingBlock.expiresAtEpochSeconds,
            staged = staged,
        )
        if (!staging.stage(session)) {
            return RestoreOutcome.Failed(RestoreFailure.StorageInsufficient)
        }
        return RestoreOutcome.Staged(session)
    }

    private fun failManifest(): RestoreOutcome = RestoreOutcome.Failed(RestoreFailure.ManifestSchemaInvalid)

    private sealed interface DecryptOutcome {
        data class Ok(val plaintext: Map<String, ByteArray>) : DecryptOutcome
        data object PasswordRejected : DecryptOutcome
        data object Tampered : DecryptOutcome
    }

    /**
     * Unwraps the DEK (wrong password ⇒ tag failure ⇒ [DecryptOutcome.PasswordRejected]) then
     * decrypts every entry ([DecryptOutcome.Tampered] when a key-confirmed entry fails its tag).
     * The password and every derived key are zeroized on all paths.
     */
    private fun decryptOutcome(
        manifest: com.example.englishlearning.backup.domain.PackageManifest,
        stored: Map<String, ByteArray>,
        password: CharArray,
    ): DecryptOutcome {
        val encryption = manifest.encryption ?: return DecryptOutcome.Tampered
        val salt = encryption.kdfSaltHex.decodeLowerHexOrNull() ?: return DecryptOutcome.Tampered
        val wrappedDek = encryption.dekWrapCiphertextHex.decodeLowerHexOrNull() ?: return DecryptOutcome.Tampered
        val kek = kdf.deriveKeK(password, salt)
        password.fill('\u0000')
        if (kek == null || kek.size != BackupCryptoPolicy.KEK_BYTES) return DecryptOutcome.Tampered
        try {
            val dek = aead.decrypt(kek, wrappedDek, BackupCryptoPolicy.dekWrapAad(encryption.kdfSaltHex))
                ?: return DecryptOutcome.PasswordRejected
            if (dek.size != BackupCryptoPolicy.DEK_BYTES) return DecryptOutcome.Tampered
            try {
                val nonceHex = manifest.pairing?.nonceHex ?: return DecryptOutcome.Tampered
                val plaintext = linkedMapOf<String, ByteArray>()
                for ((path, bytes) in stored) {
                    val clear = aead.decrypt(dek, bytes, BackupCryptoPolicy.entryAad(BackupPackageFormat.PAIRED_VERSION, nonceHex, path))
                        ?: return DecryptOutcome.Tampered
                    plaintext[path] = clear
                }
                return DecryptOutcome.Ok(plaintext)
            } finally {
                dek.fill(0)
            }
        } finally {
            kek.fill(0)
        }
    }

    private fun com.example.englishlearning.backup.pairing.PairingCheckFailure.toRestoreFailure(): RestoreFailure = when (this) {
        com.example.englishlearning.backup.pairing.PairingCheckFailure.PairingMismatch -> RestoreFailure.PairingMismatch
        com.example.englishlearning.backup.pairing.PairingCheckFailure.PairingExpired -> RestoreFailure.PairingExpired
        com.example.englishlearning.backup.pairing.PairingCheckFailure.PairingSignatureInvalid -> RestoreFailure.PairingSignatureInvalid
    }

    private fun RestoreContentValidator.ContentFailure.toRestoreFailure(): RestoreFailure = when (this) {
        RestoreContentValidator.ContentFailure.ResourceRelationship -> RestoreFailure.ResourceRelationshipInvalid
        RestoreContentValidator.ContentFailure.EntityReference -> RestoreFailure.EntityReferenceInvalid
    }

    fun commit(session: StagedRestoreSession): RestoreCommitResult = when (val result = commitGateway.commit(session)) {
        is RestoreCommitResult.Restored -> RestoreCommitResult.Restored
        is RestoreCommitResult.Failed -> {
            staging.clear(session.sessionId)
            RestoreCommitResult.Failed(result.reason)
        }
    }

    fun abandon(sessionId: String) {
        staging.clear(sessionId)
    }

    private sealed interface RawOutcome {
        data class Read(val raw: RawPackage) : RawOutcome
        data class Rejected(val reason: PackageValidationFailure) : RawOutcome
    }

    /** Bounded zip scan capturing manifest/pairing raw bytes plus payload bytes under the package limits. */
    private fun readRaw(bytes: ByteArray): RawOutcome {
        if (bytes.size.toLong() > BackupPackageFormat.DefaultLimits.maxArchiveBytes) {
            return RawOutcome.Rejected(PackageValidationFailure.LimitExceeded)
        }
        return try {
            var manifestBytes: ByteArray? = null
            var pairingBytes: ByteArray? = null
            val payload = linkedMapOf<String, ByteArray>()
            var totalBytes = 0L
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                val buffer = ByteArray(8192)
                var entry = zip.nextEntry
                while (entry != null) {
                    val path = entry.name
                    val allowed = path == "manifest.json" ||
                        path == BackupPackageFormat.PAIRING_ENTRY_PATH ||
                        isAllowedPayloadPath(path)
                    if (entry.isDirectory || !allowed) {
                        return RawOutcome.Rejected(PackageValidationFailure.InvalidPath)
                    }
                    if (payload.containsKey(path) || path == "manifest.json" && manifestBytes != null ||
                        path == BackupPackageFormat.PAIRING_ENTRY_PATH && pairingBytes != null
                    ) {
                        return RawOutcome.Rejected(PackageValidationFailure.DuplicateEntry)
                    }
                    val sink = ByteArrayOutputStream()
                    var size = 0L
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > BackupPackageFormat.DefaultLimits.maxEntryBytes) {
                            return RawOutcome.Rejected(PackageValidationFailure.LimitExceeded)
                        }
                        totalBytes += count
                        if (totalBytes > BackupPackageFormat.DefaultLimits.maxTotalBytes) {
                            return RawOutcome.Rejected(PackageValidationFailure.LimitExceeded)
                        }
                        sink.write(buffer, 0, count)
                    }
                    zip.closeEntry()
                    when (path) {
                        "manifest.json" -> manifestBytes = sink.toByteArray()
                        BackupPackageFormat.PAIRING_ENTRY_PATH -> pairingBytes = sink.toByteArray()
                        else -> payload[path] = sink.toByteArray()
                    }
                    entry = zip.nextEntry
                }
            }
            val manifest = manifestBytes ?: return RawOutcome.Rejected(PackageValidationFailure.InvalidManifest)
            RawOutcome.Read(RawPackage(manifest, pairingBytes, payload))
        } catch (_: Exception) {
            RawOutcome.Rejected(PackageValidationFailure.InvalidArchive)
        }
    }

    private fun probeFormatVersion(manifestBytes: ByteArray): Int? = try {
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(manifestBytes))
            .toString()
        val probe = Json.parseToJsonElement(text)
        (probe as? kotlinx.serialization.json.JsonObject)?.get("formatVersion")
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }
    } catch (_: Exception) {
        null
    }

    private fun pairingRecord(
        block: com.example.englishlearning.backup.domain.ManifestPairingBlock,
        manifestBytes: ByteArray,
        pairingJsonBytes: ByteArray?,
    ): PackagePairingRecord? {
        val spki = block.publicKeySpkiHex.decodeLowerHexOrNull() ?: return null
        val nonce = block.nonceHex.decodeLowerHexOrNull() ?: return null
        val commitment = block.secretCommitmentSha256Hex.decodeLowerHexOrNull() ?: return null
        val raw = pairingJsonBytes ?: return null
        val pairingJson = runCatchingJsonDecode(raw) ?: return null
        val der = pairingJson.derSignatureHex.decodeLowerHexOrNull() ?: return null
        val scope = BackupCryptoPolicy.SIGNATURE_SCOPE_PREFIX.toByteArray(Charsets.UTF_8) + manifestBytes
        return PackagePairingRecord(spki, nonce, block.issuedAtEpochSeconds, block.expiresAtEpochSeconds, commitment, der, scope)
    }

    @Serializable
    private data class PairingJsonDto(
        val publicKeySpkiHex: String,
        val nonceHex: String,
        val issuedAtEpochSeconds: Long,
        val expiresAtEpochSeconds: Long,
        val secretCommitmentSha256Hex: String,
        val signatureAlgId: String,
        val authenticationAlgId: String,
        val derSignatureHex: String,
    )

    private fun runCatchingJsonDecode(raw: ByteArray): PairingJsonDto? = try {
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(raw))
            .toString()
        strictJson.decodeFromString<PairingJsonDto>(text)
    } catch (_: Exception) {
        null
    }

    private val strictJson = Json { ignoreUnknownKeys = false }

    private fun String.decodeLowerHexOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        val out = ByteArray(length / 2)
        for (index in out.indices) {
            val high = Character.digit(this[index * 2].code, 16)
            val low = Character.digit(this[index * 2 + 1].code, 16)
            if (high < 0 || low < 0 || this[index * 2].isUpperCase() || this[index * 2 + 1].isUpperCase()) return null
            out[index] = ((high shl 4) or low).toByte()
        }
        return out
    }

    @Suppress("unused")
    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
