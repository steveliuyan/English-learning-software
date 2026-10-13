package com.example.englishlearning.backup.domain

import com.example.englishlearning.backup.pairing.MigrationChallengePolicy
import java.security.MessageDigest

/**
 * Pinned cryptographic parameters for protected packages
 * (docs/decisions/2026-10-13-f4-04-crypto-and-pairing-design.md §3-§6).
 * The validator matches every value exactly; any deviation fails closed.
 */
object BackupCryptoPolicy {
    /** Domain separator prefixed to the manifest bytes that the migration key signs (§3.2). */
    const val SIGNATURE_SCOPE_PREFIX: String = "elpair-v3/"

    const val SIGNATURE_ALG_ID: String = "ecdsa-p256-sha256"
    const val AUTHENTICATION_ALG_ID: String = "paired-migration-v1"
    const val KDF_ALG_ID: String = "argon2id"
    const val AEAD_ALG_ID: String = "aes-256-gcm"

    const val KDF_SALT_BYTES: Int = 16
    const val KDF_MEMORY_KIB: Int = 65_536
    const val KDF_ITERATIONS: Int = 3
    const val KDF_PARALLELISM: Int = 1
    const val KDF_OUTPUT_BYTES: Int = 32

    const val KEK_BYTES: Int = 32
    const val DEK_BYTES: Int = 32
    const val AEAD_IV_BYTES: Int = 12
    const val AEAD_TAG_BYTES: Int = 16

    /** Wrapped DEK storage = iv(12) || ct(32) || tag(16); plaintext entries are never smaller than iv+tag. */
    const val WRAPPED_DEK_BYTES: Int = AEAD_IV_BYTES + DEK_BYTES + AEAD_TAG_BYTES
    const val MIN_ENCRYPTED_ENTRY_BYTES: Long = (AEAD_IV_BYTES + AEAD_TAG_BYTES).toLong() + 1

    const val PASSWORD_MIN_CHARS: Int = 10
    const val PASSWORD_MAX_CHARS: Int = 128

    /** Entry ciphertext is pinned to this pairing session and path (§5.1): `<formatVersion>|<N>|<path>`. */
    fun entryAad(formatVersion: Int, nonceHex: String, path: String): ByteArray =
        "$formatVersion|$nonceHex|$path".toByteArray(Charsets.UTF_8)

    /**
     * DEK-wrap AAD (§5.1 "encryption block canonical bytes"): the pinned encryption parameters plus this
     * package's KDF salt, excluding the wrap IV/ciphertext themselves (the GCM tag already protects them,
     * and including the IV would be self-referential). Blocks any parameter or salt substitution.
     */
    fun dekWrapAad(kdfSaltHex: String): ByteArray =
        (SIGNATURE_SCOPE_PREFIX + "dek|" + KDF_ALG_ID + "|$kdfSaltHex|$KDF_MEMORY_KIB|$KDF_ITERATIONS|" +
            "$KDF_PARALLELISM|$KDF_OUTPUT_BYTES|$AEAD_ALG_ID|$AEAD_IV_BYTES|$AEAD_TAG_BYTES")
            .toByteArray(Charsets.UTF_8)

    fun isPasswordShapeValid(length: Int): Boolean = length in PASSWORD_MIN_CHARS..PASSWORD_MAX_CHARS
}

/** Old-device signing port over the Keystore migration key; returns DER `SHA256withECDSA` bytes or null when unavailable. */
fun interface MigrationSigner {
    fun sign(scopeBytes: ByteArray): ByteArray?
}

/** Argon2id KEK derivation port; implementations must use only [BackupCryptoPolicy] parameters and clear all secrets. */
interface BackupPasswordKdf {
    fun deriveKeK(password: CharArray, salt: ByteArray): ByteArray?
}

/** AES-256-GCM port; storage format is `iv || ciphertext || tag` with a fresh 12-byte IV per message. Null means failure. */
interface BackupAead {
    fun encrypt(key: ByteArray, plaintext: ByteArray, additionalData: ByteArray): ByteArray?

    fun decrypt(key: ByteArray, storageBytes: ByteArray, additionalData: ByteArray): ByteArray?
}

/** Random source for salt/DEK/IV material; `SecureRandom` in production. */
interface BackupSecretEntropy {
    fun nextBytes(count: Int): ByteArray
}

/** Everything needed to place the manifest pairing block and sign it; mirrors the scanned migration code fields. */
data class ProtectedPackageInputs(
    val publicKeySpki: ByteArray,
    val nonce: ByteArray,
    val secret: ByteArray,
    val issuedAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
) {
    init {
        require(publicKeySpki.size in MigrationChallengePolicy.MIN_PUBLIC_KEY_SPKI_BYTES..MigrationChallengePolicy.MAX_PUBLIC_KEY_SPKI_BYTES)
        require(nonce.size == MigrationChallengePolicy.NONCE_BYTES)
        require(secret.size == MigrationChallengePolicy.SECRET_BYTES)
        require(issuedAtEpochSeconds > 0L)
        require(expiresAtEpochSeconds - issuedAtEpochSeconds == MigrationChallengePolicy.CHALLENGE_TTL_SECONDS)
    }

    val nonceHex: String get() = nonce.toLowerHex()
    val secretCommitmentSha256Hex: String get() = MessageDigest.getInstance("SHA-256").digest(secret).toLowerHex()

    fun pairingBlock(): ManifestPairingBlock = ManifestPairingBlock(
        publicKeySpkiHex = publicKeySpki.toLowerHex(),
        nonceHex = nonceHex,
        issuedAtEpochSeconds = issuedAtEpochSeconds,
        expiresAtEpochSeconds = expiresAtEpochSeconds,
        secretCommitmentSha256Hex = secretCommitmentSha256Hex,
        signatureAlgId = BackupCryptoPolicy.SIGNATURE_ALG_ID,
        authenticationAlgId = BackupCryptoPolicy.AUTHENTICATION_ALG_ID,
    )
}

/** Fixed, non-sensitive failure categories for protected packaging. */
sealed interface ProtectedPackageWriteFailure {
    data object SigningKeyUnavailable : ProtectedPackageWriteFailure
    data object PasswordRejected : ProtectedPackageWriteFailure
    data object CryptoPortsMissing : ProtectedPackageWriteFailure
    data object CryptoOperationFailed : ProtectedPackageWriteFailure
    data class RejectedByValidator(val reason: PackageValidationFailure) : ProtectedPackageWriteFailure
}

/** Everything the protected packager needs; `passwordChars` is consumed (zeroed) by the writer. */
data class ProtectedPackagingSpec(
    val inputs: ProtectedPackageInputs,
    val signer: MigrationSigner,
    val passwordChars: CharArray? = null,
    val kdf: BackupPasswordKdf? = null,
    val aead: BackupAead? = null,
    val entropy: BackupSecretEntropy? = null,
)

sealed interface ProtectedPackagePackagingOutcome {
    data class Written(val bytes: ByteArray) : ProtectedPackagePackagingOutcome
    data class Failed(val reason: ProtectedPackageWriteFailure) : ProtectedPackagePackagingOutcome
}

interface ProtectedPackagePackager {
    fun write(entries: List<PackagerEntry>, createdAtEpochMillis: Long, protection: ProtectedPackagingSpec): ProtectedPackagePackagingOutcome
}

/**
 * Canonical v3 manifest bytes: fixed key order, UTF-8, no surplus whitespace, exactly the fields the
 * signature scope covers (§3.2). `entries` are path-sorted already.
 */
object BackupManifestV3Serializer {
    fun serialize(
        createdAtEpochMillis: Long,
        scope: List<String>,
        signatureStatus: String,
        authenticationStatus: String,
        encryptionStatus: String,
        entries: List<PackageEntry>,
        payloadSha256: String,
        pairing: ManifestPairingBlock,
        encryption: ManifestEncryptionBlock?,
    ): ByteArray = buildString {
        append("{\"formatVersion\":")
        append(BackupPackageFormat.PAIRED_VERSION)
        append(",\"createdAtEpochMillis\":")
        append(createdAtEpochMillis)
        append(",\"scope\":[")
        scope.forEachIndexed { index, token -> if (index > 0) append(','); appendQuoted(token) }
        append("],\"signatureStatus\":")
        appendQuoted(signatureStatus)
        append(",\"authenticationStatus\":")
        appendQuoted(authenticationStatus)
        append(",\"encryptionStatus\":")
        appendQuoted(encryptionStatus)
        append(",\"entries\":[")
        entries.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            append("{\"path\":")
            appendQuoted(entry.path)
            append(",\"byteSize\":")
            append(entry.byteSize)
            append(",\"sha256\":")
            appendQuoted(entry.sha256)
            append('}')
        }
        append("],\"pairing\":{")
        append("\"publicKeySpkiHex\":")
        appendQuoted(pairing.publicKeySpkiHex)
        append(",\"nonceHex\":")
        appendQuoted(pairing.nonceHex)
        append(",\"issuedAtEpochSeconds\":")
        append(pairing.issuedAtEpochSeconds)
        append(",\"expiresAtEpochSeconds\":")
        append(pairing.expiresAtEpochSeconds)
        append(",\"secretCommitmentSha256Hex\":")
        appendQuoted(pairing.secretCommitmentSha256Hex)
        append(",\"signatureAlgId\":")
        appendQuoted(pairing.signatureAlgId)
        append(",\"authenticationAlgId\":")
        appendQuoted(pairing.authenticationAlgId)
        append('}')
        if (encryption != null) {
            append(",\"encryption\":{")
            append("\"kdfAlgId\":")
            appendQuoted(encryption.kdfAlgId)
            append(",\"kdfSaltHex\":")
            appendQuoted(encryption.kdfSaltHex)
            append(",\"kdfMemoryKiB\":")
            append(encryption.kdfMemoryKiB)
            append(",\"kdfIterations\":")
            append(encryption.kdfIterations)
            append(",\"kdfParallelism\":")
            append(encryption.kdfParallelism)
            append(",\"kdfOutputBytes\":")
            append(encryption.kdfOutputBytes)
            append(",\"dekWrapAlgId\":")
            appendQuoted(encryption.dekWrapAlgId)
            append(",\"dekWrapIvHex\":")
            appendQuoted(encryption.dekWrapIvHex)
            append(",\"dekWrapCiphertextHex\":")
            appendQuoted(encryption.dekWrapCiphertextHex)
            append(",\"entryAlgId\":")
            appendQuoted(encryption.entryAlgId)
            append(",\"entryIvBytes\":")
            append(encryption.entryIvBytes)
            append(",\"entryTagBytes\":")
            append(encryption.entryTagBytes)
            append('}')
        }
        append(",\"payloadSha256\":")
        appendQuoted(payloadSha256)
        append('}')
    }.toByteArray(Charsets.UTF_8)

    /** `pairing.json` payload: the manifest pairing fields plus the DER signature over `elpair-v3/` + manifest bytes. */
    fun serializePairingEntry(pairing: ManifestPairingBlock, derSignatureHex: String): ByteArray = buildString {
        append("{\"publicKeySpkiHex\":")
        appendQuoted(pairing.publicKeySpkiHex)
        append(",\"nonceHex\":")
        appendQuoted(pairing.nonceHex)
        append(",\"issuedAtEpochSeconds\":")
        append(pairing.issuedAtEpochSeconds)
        append(",\"expiresAtEpochSeconds\":")
        append(pairing.expiresAtEpochSeconds)
        append(",\"secretCommitmentSha256Hex\":")
        appendQuoted(pairing.secretCommitmentSha256Hex)
        append(",\"signatureAlgId\":")
        appendQuoted(pairing.signatureAlgId)
        append(",\"authenticationAlgId\":")
        appendQuoted(pairing.authenticationAlgId)
        append(",\"derSignatureHex\":")
        appendQuoted(derSignatureHex)
        append('}')
    }.toByteArray(Charsets.UTF_8)

    private fun StringBuilder.appendQuoted(value: String) {
        append('"').append(value).append('"')
    }
}

internal fun ByteArray.toLowerHex(): String = joinToString("") { "%02x".format(it) }

internal fun String.decodeLowerHexOrNull(): ByteArray? {
    if (length % 2 != 0) return null
    val bytes = ByteArray(length / 2)
    for (index in bytes.indices) {
        val high = Character.digit(this[index * 2].code, 16)
        val low = Character.digit(this[index * 2 + 1].code, 16)
        if (high < 0 || low < 0 || this[index * 2].isUpperCase() || this[index * 2 + 1].isUpperCase()) return null
        bytes[index] = ((high shl 4) or low).toByte()
    }
    return bytes
}
