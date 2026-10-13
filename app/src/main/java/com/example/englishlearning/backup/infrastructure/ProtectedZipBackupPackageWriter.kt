package com.example.englishlearning.backup.infrastructure

import com.example.englishlearning.backup.domain.BackupCryptoPolicy
import com.example.englishlearning.backup.domain.BackupAead
import com.example.englishlearning.backup.domain.BackupPackageFormat
import com.example.englishlearning.backup.domain.BackupPackageValidator
import com.example.englishlearning.backup.domain.BackupPasswordKdf
import com.example.englishlearning.backup.domain.BackupSecretEntropy
import com.example.englishlearning.backup.domain.MigrationSigner
import com.example.englishlearning.backup.domain.PackageEntry
import com.example.englishlearning.backup.domain.PackageLimits
import com.example.englishlearning.backup.domain.PackageValidationFailure
import com.example.englishlearning.backup.domain.PackageValidationResult
import com.example.englishlearning.backup.domain.ProtectedPackageInputs
import com.example.englishlearning.backup.domain.ProtectedPackageWriteFailure
import com.example.englishlearning.backup.domain.BackupManifestV3Serializer
import com.example.englishlearning.backup.domain.ManifestEncryptionBlock
import com.example.englishlearning.backup.domain.derivePackageScope
import com.example.englishlearning.backup.domain.isAllowedPayloadPath
import com.example.englishlearning.backup.domain.toLowerHex
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Assembles v3 paired packages: signed (and optionally Argon2id+AES-256-GCM protected) payloads with a
 * `pairing.json` record. All cryptographic parameters come from [BackupCryptoPolicy]; the finished archive
 * is re-validated by [BackupPackageValidator] before it is returned, mirroring the salvage writer.
 */
class ProtectedZipBackupPackageWriter(
    private val limits: PackageLimits = BackupPackageFormat.DefaultLimits,
) {
    data class Entry(val path: String, val bytes: ByteArray)

    data class Protection(
        val inputs: ProtectedPackageInputs,
        val signer: MigrationSigner,
        val passwordChars: CharArray? = null,
        val kdf: BackupPasswordKdf? = null,
        val aead: BackupAead? = null,
        val entropy: BackupSecretEntropy? = null,
    )

    sealed interface Result {
        data class Written(val bytes: ByteArray) : Result
        data class Failed(val reason: ProtectedPackageWriteFailure) : Result
    }

    fun write(entries: List<Entry>, createdAtEpochMillis: Long, protection: Protection): Result {
        if (createdAtEpochMillis < 0L) {
            return Result.Failed(ProtectedPackageWriteFailure.RejectedByValidator(PackageValidationFailure.InvalidManifest))
        }
        val password = protection.passwordChars
        val encrypted = password != null
        if (encrypted) {
            if (!BackupCryptoPolicy.isPasswordShapeValid(password.size)) {
                password.fill('\u0000')
                return Result.Failed(ProtectedPackageWriteFailure.PasswordRejected)
            }
            if (protection.kdf == null || protection.aead == null || protection.entropy == null) {
                password.fill('\u0000')
                return Result.Failed(ProtectedPackageWriteFailure.CryptoPortsMissing)
            }
        }

        val sorted = entries.sortedBy { it.path }
        val seen = mutableSetOf<String>()
        var totalBytes = 0L
        val descriptors = mutableListOf<Descriptor>()

        val salt: ByteArray?
        val kek: ByteArray?
        val dek: ByteArray?
        if (encrypted) {
            salt = protection.entropy!!.nextBytes(BackupCryptoPolicy.KDF_SALT_BYTES)
            if (salt.size != BackupCryptoPolicy.KDF_SALT_BYTES) {
                password!!.fill('\u0000')
                return Result.Failed(ProtectedPackageWriteFailure.CryptoPortsMissing)
            }
            kek = protection.kdf!!.deriveKeK(password!!, salt)
            password.fill('\u0000')
            if (kek == null || kek.size != BackupCryptoPolicy.KEK_BYTES) {
                return Result.Failed(ProtectedPackageWriteFailure.CryptoOperationFailed)
            }
            dek = protection.entropy.nextBytes(BackupCryptoPolicy.DEK_BYTES)
            if (dek.size != BackupCryptoPolicy.DEK_BYTES) {
                kek.fill(0)
                return Result.Failed(ProtectedPackageWriteFailure.CryptoPortsMissing)
            }
        } else {
            salt = null
            kek = null
            dek = null
        }

        try {
            for (entry in sorted) {
                if (!isAllowedPayloadPath(entry.path)) {
                    return Result.Failed(ProtectedPackageWriteFailure.RejectedByValidator(PackageValidationFailure.InvalidPath))
                }
                if (!seen.add(entry.path)) {
                    return Result.Failed(ProtectedPackageWriteFailure.RejectedByValidator(PackageValidationFailure.DuplicateEntry))
                }
                if (descriptors.size >= limits.maxEntries) {
                    return Result.Failed(ProtectedPackageWriteFailure.RejectedByValidator(PackageValidationFailure.LimitExceeded))
                }
                if (entry.bytes.size.toLong() > limits.maxEntryBytes) {
                    return Result.Failed(ProtectedPackageWriteFailure.RejectedByValidator(PackageValidationFailure.LimitExceeded))
                }
                val stored = if (encrypted) {
                    val aad = BackupCryptoPolicy.entryAad(
                        BackupPackageFormat.PAIRED_VERSION,
                        protection.inputs.nonceHex,
                        entry.path,
                    )
                    protection.aead!!.encrypt(dek!!, entry.bytes, aad)
                        ?: return Result.Failed(ProtectedPackageWriteFailure.CryptoOperationFailed)
                } else {
                    entry.bytes
                }
                totalBytes += stored.size.toLong()
                if (totalBytes > limits.maxTotalBytes) {
                    return Result.Failed(ProtectedPackageWriteFailure.RejectedByValidator(PackageValidationFailure.LimitExceeded))
                }
                descriptors += Descriptor(entry.path, stored, sha256(stored))
            }

            val pairing = protection.inputs.pairingBlock()
            val encryptionBlock = if (encrypted) {
                val wrappedDek = protection.aead!!.encrypt(
                    kek!!,
                    dek!!,
                    BackupCryptoPolicy.dekWrapAad(salt!!.toLowerHex()),
                ) ?: return Result.Failed(ProtectedPackageWriteFailure.CryptoOperationFailed)
                if (wrappedDek.size != BackupCryptoPolicy.WRAPPED_DEK_BYTES) {
                    return Result.Failed(ProtectedPackageWriteFailure.CryptoOperationFailed)
                }
                com.example.englishlearning.backup.domain.ManifestEncryptionBlock(
                    kdfAlgId = BackupCryptoPolicy.KDF_ALG_ID,
                    kdfSaltHex = salt.toLowerHex(),
                    kdfMemoryKiB = BackupCryptoPolicy.KDF_MEMORY_KIB,
                    kdfIterations = BackupCryptoPolicy.KDF_ITERATIONS,
                    kdfParallelism = BackupCryptoPolicy.KDF_PARALLELISM,
                    kdfOutputBytes = BackupCryptoPolicy.KDF_OUTPUT_BYTES,
                    dekWrapAlgId = BackupCryptoPolicy.AEAD_ALG_ID,
                    dekWrapIvHex = wrappedDek.copyOfRange(0, BackupCryptoPolicy.AEAD_IV_BYTES).toLowerHex(),
                    dekWrapCiphertextHex = wrappedDek.toLowerHex(),
                    entryAlgId = BackupCryptoPolicy.AEAD_ALG_ID,
                    entryIvBytes = BackupCryptoPolicy.AEAD_IV_BYTES,
                    entryTagBytes = BackupCryptoPolicy.AEAD_TAG_BYTES,
                )
            } else {
                null
            }

            val canonical = descriptors.joinToString("") { "${it.path}\t${it.bytes.size}\t${it.sha256}\n" }
            val scope = derivePackageScope(descriptors.map { it.path })
            val manifestBytes = com.example.englishlearning.backup.domain.BackupManifestV3Serializer.serialize(
                createdAtEpochMillis = createdAtEpochMillis,
                scope = scope,
                signatureStatus = BackupPackageFormat.SIGNATURE_STATUS_V3,
                authenticationStatus = BackupPackageFormat.AUTHENTICATION_STATUS_V3,
                encryptionStatus = if (encrypted) {
                    BackupPackageFormat.ENCRYPTION_STATUS_V3_AEAD
                } else {
                    BackupPackageFormat.ENCRYPTION_STATUS_V3_NONE
                },
                entries = descriptors.map { PackageEntry(it.path, it.bytes.size.toLong(), it.sha256) },
                payloadSha256 = sha256(canonical.toByteArray(Charsets.UTF_8)),
                pairing = pairing,
                encryption = encryptionBlock,
            )

            val scopeBytes = BackupCryptoPolicy.SIGNATURE_SCOPE_PREFIX.toByteArray(Charsets.UTF_8) + manifestBytes
            val derSignature = protection.signer.sign(scopeBytes)
            if (derSignature == null || derSignature.isEmpty() || derSignature.size > MAX_DER_BYTES) {
                return Result.Failed(ProtectedPackageWriteFailure.SigningKeyUnavailable)
            }
            val pairingJsonBytes = com.example.englishlearning.backup.domain.BackupManifestV3Serializer
                .serializePairingEntry(pairing, derSignature.toLowerHex())

            val bytes = try {
                val output = ByteArrayOutputStream()
                ZipOutputStream(output).use { zip ->
                    put(zip, "manifest.json", manifestBytes)
                    put(zip, BackupPackageFormat.PAIRING_ENTRY_PATH, pairingJsonBytes)
                    descriptors.forEach { put(zip, it.path, it.bytes) }
                }
                output.toByteArray()
            } catch (_: Exception) {
                return Result.Failed(ProtectedPackageWriteFailure.RejectedByValidator(PackageValidationFailure.InvalidArchive))
            }
            if (bytes.size.toLong() > limits.maxArchiveBytes) {
                return Result.Failed(ProtectedPackageWriteFailure.RejectedByValidator(PackageValidationFailure.LimitExceeded))
            }
            return when (val validation = BackupPackageValidator(limits).validate(bytes)) {
                is PackageValidationResult.Valid -> Result.Written(bytes)
                is PackageValidationResult.Invalid -> Result.Failed(ProtectedPackageWriteFailure.RejectedByValidator(validation.reason))
            }
        } finally {
            salt?.fill(0)
            kek?.fill(0)
            dek?.fill(0)
        }
    }

    private fun put(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        ZipEntry(path).also { it.time = 0L }.let { entry ->
            zip.putNextEntry(entry)
            zip.write(bytes)
            zip.closeEntry()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private data class Descriptor(val path: String, val bytes: ByteArray, val sha256: String)

    private companion object {
        const val MAX_DER_BYTES = 128
    }
}
