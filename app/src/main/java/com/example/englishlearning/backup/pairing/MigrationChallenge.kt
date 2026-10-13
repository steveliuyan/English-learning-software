package com.example.englishlearning.backup.pairing

import java.security.MessageDigest

/**
 * Pinned pairing parameters (docs/decisions/2026-10-13-f4-04-crypto-and-pairing-design.md).
 * Any deviation is rejected fail-closed; changes require a new algorithm/format version.
 */
object MigrationChallengePolicy {
    const val CHALLENGE_TTL_SECONDS: Long = 1_800L
    const val NONCE_BYTES: Int = 16
    const val SECRET_BYTES: Int = 16
    const val MIN_PUBLIC_KEY_SPKI_BYTES: Int = 1
    const val MAX_PUBLIC_KEY_SPKI_BYTES: Int = 256
}

/**
 * One-time migration challenge issued by the old device after user authentication.
 * `secret` is the QR-only capability; the package stores only its SHA-256 commitment.
 */
data class MigrationChallenge(
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

    val secretCommitment: ByteArray
        get() = pairingSha256(secret)

    override fun equals(other: Any?): Boolean = this === other ||
        (
            other is MigrationChallenge &&
                issuedAtEpochSeconds == other.issuedAtEpochSeconds &&
                expiresAtEpochSeconds == other.expiresAtEpochSeconds &&
                publicKeySpki.contentEquals(other.publicKeySpki) &&
                nonce.contentEquals(other.nonce) &&
                secret.contentEquals(other.secret)
            )

    override fun hashCode(): Int {
        var result = publicKeySpki.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + secret.contentHashCode()
        result = 31 * result + issuedAtEpochSeconds.hashCode()
        result = 31 * result + expiresAtEpochSeconds.hashCode()
        return result
    }
}

internal fun pairingSha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

internal fun ByteArray.toPairingHex(): String = joinToString("") { "%02x".format(it) }
