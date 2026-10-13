package com.example.englishlearning.backup.domain

import com.example.englishlearning.backup.pairing.MigrationChallengePolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class BackupPackageValidator(
    private val limits: PackageLimits = BackupPackageFormat.DefaultLimits,
    private val json: Json = Json,
) {
    private val signatureVerifier = com.example.englishlearning.backup.pairing.JavaSecurityEcSignatureVerifier()
    fun validate(bytes: ByteArray): PackageValidationResult {
        val snapshot = bytes.copyOf()
        if (snapshot.size.toLong() > limits.maxArchiveBytes) return invalid(PackageValidationFailure.LimitExceeded)
        if (snapshot.size < 22 || !hasEndRecord(snapshot)) return invalid(PackageValidationFailure.InvalidArchive)

        return try {
            ZipInputStream(ByteArrayInputStream(snapshot)).use { zip ->
                var manifest: PackageManifest? = null
                var manifestRawBytes: ByteArray? = null
                var pairingRawBytes: ByteArray? = null
                var earlyFailure: PackageValidationFailure? = null
                val seen = mutableSetOf<String>()
                val actual = linkedMapOf<String, PackageEntry>()
                var totalBytes = 0L
                val buffer = ByteArray(8192)
                var entry = zip.nextEntry
                while (entry != null) {
                    val path = entry.name
                    if (entry.isDirectory || !isAllowedPackagePath(path)) return invalid(PackageValidationFailure.InvalidPath)
                    val normalized = path.replace('\\', '/')
                    if (!seen.add(normalized)) return invalid(PackageValidationFailure.DuplicateEntry)
                    if (path != "manifest.json" && path != BackupPackageFormat.PAIRING_ENTRY_PATH && actual.size >= limits.maxEntries) {
                        return invalid(PackageValidationFailure.LimitExceeded)
                    }

                    val digest = MessageDigest.getInstance("SHA-256")
                    val content = if (path == "manifest.json" || path == BackupPackageFormat.PAIRING_ENTRY_PATH) {
                        ByteArrayOutputStreamBounded(limits.maxEntryBytes)
                    } else {
                        null
                    }
                    var size = 0L
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > limits.maxEntryBytes) return invalid(PackageValidationFailure.LimitExceeded)
                        if (path != "manifest.json" && path != BackupPackageFormat.PAIRING_ENTRY_PATH) {
                            totalBytes += count
                            if (totalBytes > limits.maxTotalBytes) return invalid(PackageValidationFailure.LimitExceeded)
                        }
                        digest.update(buffer, 0, count)
                        content?.write(buffer, 0, count)
                    }
                    zip.closeEntry()
                    val sha = digest.digest().toHex()
                    if (path == "manifest.json") {
                        if (manifest != null) return invalid(PackageValidationFailure.DuplicateEntry)
                        val rawManifest = content!!.toByteArray()
                        val manifestText = try {
                            Charsets.UTF_8.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(rawManifest))
                                .toString()
                        } catch (_: CharacterCodingException) {
                            return invalid(PackageValidationFailure.InvalidManifest)
                        }
                        val probe = try {
                            json.parseToJsonElement(manifestText).jsonObject
                        } catch (_: SerializationException) {
                            return invalid(PackageValidationFailure.InvalidManifest)
                        } catch (_: IllegalArgumentException) {
                            return invalid(PackageValidationFailure.InvalidManifest)
                        }
                        val declaredVersion = probe["formatVersion"]?.jsonPrimitive?.intOrNull
                            ?: return invalid(PackageValidationFailure.InvalidManifest)
                        if (declaredVersion != BackupPackageFormat.SALVAGE_VERSION &&
                            declaredVersion != BackupPackageFormat.PAIRED_VERSION
                        ) {
                            // Fail closed on the declared version before trusting any v2/v3 fields.
                            earlyFailure = PackageValidationFailure.UnsupportedFormat
                            break
                        }
                        manifest = try {
                            json.decodeFromJsonElement<ManifestDto>(probe).toModel()
                        } catch (_: SerializationException) {
                            return invalid(PackageValidationFailure.InvalidManifest)
                        } catch (_: IllegalArgumentException) {
                            return invalid(PackageValidationFailure.InvalidManifest)
                        }
                        manifestRawBytes = rawManifest
                    } else if (path == BackupPackageFormat.PAIRING_ENTRY_PATH) {
                        if (pairingRawBytes != null) return invalid(PackageValidationFailure.DuplicateEntry)
                        pairingRawBytes = content!!.toByteArray()
                    } else {
                        actual[path] = PackageEntry(path, size, sha)
                        if (actual.size > limits.maxEntries) return invalid(PackageValidationFailure.LimitExceeded)
                    }
                    entry = zip.nextEntry
                }

                earlyFailure?.let { return invalid(it) }
                val parsed = manifest ?: return invalid(PackageValidationFailure.InvalidManifest)
                val isSalvageV2 = parsed.formatVersion == BackupPackageFormat.SALVAGE_VERSION
                if (!isSalvageV2 && parsed.formatVersion != BackupPackageFormat.PAIRED_VERSION) {
                    return invalid(PackageValidationFailure.UnsupportedFormat)
                }
                if (parsed.entries.any {
                        !isAllowedPackagePath(it.path) || it.path == "manifest.json" || it.path == BackupPackageFormat.PAIRING_ENTRY_PATH
                    }
                ) {
                    return invalid(PackageValidationFailure.InvalidPath)
                }
                if (parsed.entries.map { it.path }.toSet().size != parsed.entries.size) return invalid(PackageValidationFailure.DuplicateEntry)
                if (parsed.entries.size > limits.maxEntries) return invalid(PackageValidationFailure.LimitExceeded)
                if (isSalvageV2) {
                    if (pairingRawBytes != null) return invalid(PackageValidationFailure.InvalidPath)
                    if (parsed.pairing != null || parsed.encryption != null) return invalid(PackageValidationFailure.InvalidManifest)
                    if (parsed.signatureStatus != BackupPackageFormat.SIGNATURE_STATUS ||
                        parsed.authenticationStatus != BackupPackageFormat.AUTHENTICATION_STATUS ||
                        parsed.encryptionStatus != BackupPackageFormat.ENCRYPTION_STATUS
                    ) {
                        return invalid(PackageValidationFailure.InvalidManifest)
                    }
                } else {
                    if (parsed.signatureStatus != BackupPackageFormat.SIGNATURE_STATUS_V3 ||
                        parsed.authenticationStatus != BackupPackageFormat.AUTHENTICATION_STATUS_V3 ||
                        (parsed.encryptionStatus != BackupPackageFormat.ENCRYPTION_STATUS_V3_NONE &&
                            parsed.encryptionStatus != BackupPackageFormat.ENCRYPTION_STATUS_V3_AEAD)
                    ) {
                        return invalid(PackageValidationFailure.InvalidManifest)
                    }
                }
                if (parsed.createdAtEpochMillis < 0L) return invalid(PackageValidationFailure.InvalidManifest)
                if (parsed.scope.distinct().size != parsed.scope.size) return invalid(PackageValidationFailure.InvalidManifest)
                if (parsed.scope.any { it != BackupPackageFormat.MEDIA_SCOPE_TOKEN && !BackupRecordTypes.isValidType(it) }) {
                    return invalid(PackageValidationFailure.InvalidManifest)
                }
                if (parsed.scope != derivePackageScope(parsed.entries.map { it.path })) {
                    return invalid(PackageValidationFailure.InvalidManifest)
                }
                if (parsed.entries.map { it.path }.toSet() != actual.keys) return invalid(PackageValidationFailure.LengthMismatch)

                for (declared in parsed.entries) {
                    val found = actual.getValue(declared.path)
                    if (declared.byteSize != found.byteSize) return invalid(PackageValidationFailure.LengthMismatch)
                    if (!declared.sha256.equals(found.sha256, ignoreCase = true)) return invalid(PackageValidationFailure.IntegrityFailure)
                }
                // Canonical digest input: path-sorted UTF-8 lines of path<TAB>size<TAB>lowercase SHA-256<LF>.
                val canonical = actual.values.sortedBy { it.path }
                    .joinToString("") { "${it.path}\t${it.byteSize}\t${it.sha256}\n" }.toByteArray(Charsets.UTF_8)
                if (!parsed.payloadSha256.equals(canonical.sha256(), ignoreCase = true)) return invalid(PackageValidationFailure.IntegrityFailure)
                if (!isSalvageV2) {
                    when (val pairing = checkV3Pairing(parsed, pairingRawBytes, manifestRawBytes ?: ByteArray(0))) {
                        is PairingGate.Failure -> return invalid(pairing.reason)
                        PairingGate.Ok -> Unit
                    }
                }
                if (!hasConsistentCentralDirectory(snapshot)) return invalid(PackageValidationFailure.InvalidArchive)
                PackageValidationResult.Valid(parsed)
            }
        } catch (_: SerializationException) {
            invalid(PackageValidationFailure.InvalidManifest)
        } catch (_: IllegalArgumentException) {
            invalid(PackageValidationFailure.InvalidManifest)
        } catch (_: Exception) {
            invalid(PackageValidationFailure.InvalidArchive)
        }
    }

    /** v3 pairing block, `pairing.json` cross-check, pinned crypto parameters, and the ECDSA signature gate (§3.2/§6). */
    private fun checkV3Pairing(parsed: PackageManifest, pairingRawBytes: ByteArray?, manifestBytes: ByteArray): PairingGate {
        val block = parsed.pairing ?: return PairingGate.Failure(PackageValidationFailure.MissingPairingRecord)
        if (block.signatureAlgId != BackupCryptoPolicy.SIGNATURE_ALG_ID ||
            block.authenticationAlgId != BackupCryptoPolicy.AUTHENTICATION_ALG_ID ||
            block.signatureAlgId != parsed.signatureStatus ||
            block.authenticationAlgId != parsed.authenticationStatus
        ) {
            return PairingGate.Failure(PackageValidationFailure.UnsupportedCryptoParameters)
        }
        val spki = block.publicKeySpkiHex.decodeLowerHexOrNull() ?: return PairingGate.Failure(PackageValidationFailure.InvalidManifest)
        val nonce = block.nonceHex.decodeLowerHexOrNull() ?: return PairingGate.Failure(PackageValidationFailure.InvalidManifest)
        val commitment = block.secretCommitmentSha256Hex.decodeLowerHexOrNull() ?: return PairingGate.Failure(PackageValidationFailure.InvalidManifest)
        if (spki.size !in MigrationChallengePolicy.MIN_PUBLIC_KEY_SPKI_BYTES..MigrationChallengePolicy.MAX_PUBLIC_KEY_SPKI_BYTES ||
            nonce.size != MigrationChallengePolicy.NONCE_BYTES ||
            commitment.size != 32 ||
            block.issuedAtEpochSeconds <= 0L ||
            block.expiresAtEpochSeconds - block.issuedAtEpochSeconds != MigrationChallengePolicy.CHALLENGE_TTL_SECONDS
        ) {
            return PairingGate.Failure(PackageValidationFailure.InvalidManifest)
        }

        if (parsed.encryptionStatus == BackupPackageFormat.ENCRYPTION_STATUS_V3_AEAD) {
            val encryption = parsed.encryption ?: return PairingGate.Failure(PackageValidationFailure.InvalidManifest)
            if (!matchesPinnedEncryption(encryption)) return PairingGate.Failure(PackageValidationFailure.UnsupportedCryptoParameters)
            if (parsed.entries.any { it.byteSize < BackupCryptoPolicy.MIN_ENCRYPTED_ENTRY_BYTES }) {
                return PairingGate.Failure(PackageValidationFailure.LengthMismatch)
            }
        } else if (parsed.encryption != null) {
            return PairingGate.Failure(PackageValidationFailure.InvalidManifest)
        }

        val pairingFileBytes = pairingRawBytes ?: return PairingGate.Failure(PackageValidationFailure.MissingPairingRecord)
        val pairingText = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(pairingFileBytes))
                .toString()
        } catch (_: CharacterCodingException) {
            return PairingGate.Failure(PackageValidationFailure.PairingRecordMismatch)
        }
        val pairingEntry = try {
            json.decodeFromString<PairingEntryDto>(pairingText)
        } catch (_: Exception) {
            return PairingGate.Failure(PackageValidationFailure.PairingRecordMismatch)
        }
        if (pairingEntry.publicKeySpkiHex != block.publicKeySpkiHex ||
            pairingEntry.nonceHex != block.nonceHex ||
            pairingEntry.issuedAtEpochSeconds != block.issuedAtEpochSeconds ||
            pairingEntry.expiresAtEpochSeconds != block.expiresAtEpochSeconds ||
            pairingEntry.secretCommitmentSha256Hex != block.secretCommitmentSha256Hex ||
            pairingEntry.signatureAlgId != block.signatureAlgId ||
            pairingEntry.authenticationAlgId != block.authenticationAlgId
        ) {
            return PairingGate.Failure(PackageValidationFailure.PairingRecordMismatch)
        }
        val derSignature = pairingEntry.derSignatureHex.decodeLowerHexOrNull()
            ?: return PairingGate.Failure(PackageValidationFailure.PairingRecordMismatch)
        if (derSignature.isEmpty() || derSignature.size > MAX_DER_SIGNATURE_BYTES) {
            return PairingGate.Failure(PackageValidationFailure.PairingRecordMismatch)
        }
        val scope = BackupCryptoPolicy.SIGNATURE_SCOPE_PREFIX.toByteArray(Charsets.UTF_8) + manifestBytes
        if (!signatureVerifier.verify(spki, scope, derSignature)) {
            return PairingGate.Failure(PackageValidationFailure.SignatureInvalid)
        }
        return PairingGate.Ok
    }

    private fun matchesPinnedEncryption(encryption: ManifestEncryptionBlock): Boolean {
        val salt = encryption.kdfSaltHex.decodeLowerHexOrNull() ?: return false
        val wrapIv = encryption.dekWrapIvHex.decodeLowerHexOrNull() ?: return false
        val wrappedDek = encryption.dekWrapCiphertextHex.decodeLowerHexOrNull() ?: return false
        return encryption.kdfAlgId == BackupCryptoPolicy.KDF_ALG_ID &&
            encryption.dekWrapAlgId == BackupCryptoPolicy.AEAD_ALG_ID &&
            encryption.entryAlgId == BackupCryptoPolicy.AEAD_ALG_ID &&
            salt.size == BackupCryptoPolicy.KDF_SALT_BYTES &&
            wrapIv.size == BackupCryptoPolicy.AEAD_IV_BYTES &&
            wrappedDek.size == BackupCryptoPolicy.WRAPPED_DEK_BYTES &&
            encryption.kdfMemoryKiB == BackupCryptoPolicy.KDF_MEMORY_KIB &&
            encryption.kdfIterations == BackupCryptoPolicy.KDF_ITERATIONS &&
            encryption.kdfParallelism == BackupCryptoPolicy.KDF_PARALLELISM &&
            encryption.kdfOutputBytes == BackupCryptoPolicy.KDF_OUTPUT_BYTES &&
            encryption.entryIvBytes == BackupCryptoPolicy.AEAD_IV_BYTES &&
            encryption.entryTagBytes == BackupCryptoPolicy.AEAD_TAG_BYTES
    }

    private sealed interface PairingGate {
        data object Ok : PairingGate
        data class Failure(val reason: PackageValidationFailure) : PairingGate
    }

    fun readEntries(bytes: ByteArray): PackageReadResult {
        val snapshot = bytes.copyOf()
        val validation = validate(snapshot)
        if (validation is PackageValidationResult.Invalid) return PackageReadResult.Invalid(validation.reason)
        return try {
            val entries = linkedMapOf<String, ByteArray>()
            var totalBytes = 0L
            ZipInputStream(ByteArrayInputStream(snapshot)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name != "manifest.json") {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var entryBytes = 0L
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            totalBytes += count
                            if (entryBytes > limits.maxEntryBytes || totalBytes > limits.maxTotalBytes) {
                                return PackageReadResult.Invalid(PackageValidationFailure.LimitExceeded)
                            }
                            output.write(buffer, 0, count)
                        }
                        entries[entry.name] = output.toByteArray()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            PackageReadResult.Read(entries)
        } catch (_: Exception) {
            PackageReadResult.Invalid(PackageValidationFailure.InvalidArchive)
        }
    }

    private fun hasEndRecord(bytes: ByteArray): Boolean = findEndRecord(bytes) != null

    private fun hasConsistentCentralDirectory(bytes: ByteArray): Boolean {
        val end = findEndRecord(bytes) ?: return false
        val disk = readShortLE(bytes, end + 4)
        val centralDisk = readShortLE(bytes, end + 6)
        val diskEntries = readShortLE(bytes, end + 8)
        val totalEntries = readShortLE(bytes, end + 10)
        val centralSize = readIntLE(bytes, end + 12).toLong() and 0xffff_ffffL
        val centralOffset = readIntLE(bytes, end + 16).toLong() and 0xffff_ffffL
        if (disk != 0 || centralDisk != 0 || diskEntries != totalEntries || totalEntries == 0xffff) return false
        if (centralSize == 0xffff_ffffL || centralOffset == 0xffff_ffffL) return false
        if (centralOffset + centralSize != end.toLong() || centralOffset > Int.MAX_VALUE) return false

        val names = mutableSetOf<String>()
        var cursor = centralOffset.toInt()
        val centralEnd = end
        repeat(totalEntries) {
            if (cursor < 0 || cursor + 46 > centralEnd || readIntLE(bytes, cursor) != 0x02014b50) return false
            val flags = readShortLE(bytes, cursor + 8)
            val method = readShortLE(bytes, cursor + 10)
            val crc = readIntLE(bytes, cursor + 16).toLong() and 0xffff_ffffL
            val compressedSize = readIntLE(bytes, cursor + 20).toLong() and 0xffff_ffffL
            val uncompressedSize = readIntLE(bytes, cursor + 24).toLong() and 0xffff_ffffL
            val nameLength = readShortLE(bytes, cursor + 28)
            val extraLength = readShortLE(bytes, cursor + 30)
            val commentLength = readShortLE(bytes, cursor + 32)
            val startDisk = readShortLE(bytes, cursor + 34)
            val localOffset = readIntLE(bytes, cursor + 42).toLong() and 0xffff_ffffL
            val recordEnd = cursor.toLong() + 46 + nameLength + extraLength + commentLength
            if (recordEnd > centralEnd || startDisk != 0 || localOffset >= centralOffset) return false
            if (compressedSize == 0xffff_ffffL || uncompressedSize == 0xffff_ffffL || localOffset == 0xffff_ffffL) return false
            if (flags and 0x0001 != 0 || method !in setOf(0, 8)) return false
            val nameBytes = bytes.copyOfRange(cursor + 46, cursor + 46 + nameLength)
            val name = decodeZipName(nameBytes, flags) ?: return false
            if (!names.add(name)) return false

            val local = localOffset.toInt()
            if (local + 30 > centralOffset || readIntLE(bytes, local) != 0x04034b50) return false
            val localFlags = readShortLE(bytes, local + 6)
            val localMethod = readShortLE(bytes, local + 8)
            val localCrc = readIntLE(bytes, local + 14).toLong() and 0xffff_ffffL
            val localCompressed = readIntLE(bytes, local + 18).toLong() and 0xffff_ffffL
            val localUncompressed = readIntLE(bytes, local + 22).toLong() and 0xffff_ffffL
            val localNameLength = readShortLE(bytes, local + 26)
            val localExtraLength = readShortLE(bytes, local + 28)
            val localData = local.toLong() + 30 + localNameLength + localExtraLength
            if (localData > centralOffset || hasZip64Extra(bytes, local + 30 + localNameLength, localExtraLength)) return false
            if (localFlags != flags || localMethod != method || localData + compressedSize > centralOffset) return false
            if (localNameLength != nameLength) return false
            val localName = decodeZipName(bytes.copyOfRange(local + 30, local + 30 + localNameLength), localFlags) ?: return false
            if (localName != name) return false
            val usesDescriptor = flags and 0x0008 != 0
            if (!usesDescriptor && (localCrc != crc || localCompressed != compressedSize || localUncompressed != uncompressedSize)) return false
            if (usesDescriptor && ((localCrc != 0L && localCrc != crc) || (localCompressed != 0L && localCompressed != compressedSize) || (localUncompressed != 0L && localUncompressed != uncompressedSize))) return false
            if (hasZip64Extra(bytes, cursor + 46 + nameLength, extraLength)) return false
            cursor = recordEnd.toInt()
        }
        return cursor == centralEnd && names.size == totalEntries
    }

    private fun findEndRecord(bytes: ByteArray): Int? {
        val minimum = (bytes.size - 22).coerceAtLeast(0)
        val maximum = (bytes.size - 22 - 0xffff).coerceAtLeast(0)
        for (offset in minimum downTo maximum) {
            if (readIntLE(bytes, offset) == 0x06054b50 && offset + 22 <= bytes.size) {
                val commentLength = readShortLE(bytes, offset + 20)
                if (offset + 22 + commentLength == bytes.size) return offset
            }
        }
        return null
    }

    private fun decodeZipName(bytes: ByteArray, flags: Int): String? = try {
        val charset = if (flags and 0x0800 != 0) Charsets.UTF_8 else Charsets.US_ASCII
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun hasZip64Extra(bytes: ByteArray, offset: Int, length: Int): Boolean {
        var cursor = offset
        val end = offset + length
        while (cursor + 4 <= end) {
            val id = readShortLE(bytes, cursor)
            val size = readShortLE(bytes, cursor + 2)
            if (cursor + 4 + size > end) return true
            if (id == 0x0001) return true
            cursor += 4 + size
        }
        return cursor != end
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun readShortLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun invalid(reason: PackageValidationFailure) = PackageValidationResult.Invalid(reason)

    private companion object {
        /** DER-encoded P-256 ECDSA signatures are at most 72 bytes; 128 is a generous structural cap. */
        const val MAX_DER_SIGNATURE_BYTES = 128
    }

    private class ByteArrayOutputStreamBounded(private val maximum: Long) {
        private val output = java.io.ByteArrayOutputStream()
        fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (output.size().toLong() + length > maximum) throw IllegalArgumentException()
            output.write(bytes, offset, length)
        }
        fun toByteArray() = output.toByteArray()
    }

    @Serializable
    private data class ManifestDto(
        val formatVersion: Int,
        val createdAtEpochMillis: Long,
        val scope: List<String>,
        val signatureStatus: String,
        val authenticationStatus: String,
        val encryptionStatus: String,
        val entries: List<EntryDto>,
        val payloadSha256: String,
        val pairing: PairingBlockDto? = null,
        val encryption: EncryptionBlockDto? = null,
    ) {
        fun toModel() = PackageManifest(
            formatVersion = formatVersion,
            createdAtEpochMillis = createdAtEpochMillis,
            scope = scope,
            signatureStatus = signatureStatus,
            authenticationStatus = authenticationStatus,
            encryptionStatus = encryptionStatus,
            entries = entries.map { it.toModel() },
            payloadSha256 = payloadSha256,
            pairing = pairing?.toModel(),
            encryption = encryption?.toModel(),
        )
    }

    @Serializable
    private data class EntryDto(val path: String, val byteSize: Long, val sha256: String) {
        fun toModel() = PackageEntry(path, byteSize, sha256)
    }

    @Serializable
    private data class PairingBlockDto(
        val publicKeySpkiHex: String,
        val nonceHex: String,
        val issuedAtEpochSeconds: Long,
        val expiresAtEpochSeconds: Long,
        val secretCommitmentSha256Hex: String,
        val signatureAlgId: String,
        val authenticationAlgId: String,
    ) {
        fun toModel() = ManifestPairingBlock(
            publicKeySpkiHex,
            nonceHex,
            issuedAtEpochSeconds,
            expiresAtEpochSeconds,
            secretCommitmentSha256Hex,
            signatureAlgId,
            authenticationAlgId,
        )
    }

    @Serializable
    private data class PairingEntryDto(
        val publicKeySpkiHex: String,
        val nonceHex: String,
        val issuedAtEpochSeconds: Long,
        val expiresAtEpochSeconds: Long,
        val secretCommitmentSha256Hex: String,
        val signatureAlgId: String,
        val authenticationAlgId: String,
        val derSignatureHex: String,
    )

    @Serializable
    private data class EncryptionBlockDto(
        val kdfAlgId: String,
        val kdfSaltHex: String,
        val kdfMemoryKiB: Int,
        val kdfIterations: Int,
        val kdfParallelism: Int,
        val kdfOutputBytes: Int,
        val dekWrapAlgId: String,
        val dekWrapIvHex: String,
        val dekWrapCiphertextHex: String,
        val entryAlgId: String,
        val entryIvBytes: Int,
        val entryTagBytes: Int,
    ) {
        fun toModel() = ManifestEncryptionBlock(
            kdfAlgId,
            kdfSaltHex,
            kdfMemoryKiB,
            kdfIterations,
            kdfParallelism,
            kdfOutputBytes,
            dekWrapAlgId,
            dekWrapIvHex,
            dekWrapCiphertextHex,
            entryAlgId,
            entryIvBytes,
            entryTagBytes,
        )
    }
}

sealed interface PackageReadResult {
    data class Read(val entries: Map<String, ByteArray>) : PackageReadResult
    data class Invalid(val reason: PackageValidationFailure) : PackageReadResult
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
