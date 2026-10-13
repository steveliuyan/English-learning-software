package com.example.englishlearning.backup.pairing

/**
 * QR payload for the migration challenge:
 * `ELPAIR1|b64url(publicKeySpki)|b64url(nonce)|b64url(secret)|issuedAt|expiresAt`.
 * Base64 URL-safe without padding; the secret appears only here, never in the package.
 */
data class MigrationCodeFields(
    val publicKeySpki: ByteArray,
    val nonce: ByteArray,
    val secret: ByteArray,
    val issuedAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
) {
    override fun equals(other: Any?): Boolean = this === other ||
        (
            other is MigrationCodeFields &&
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

sealed interface MigrationCodeDecodeResult {
    data class Parsed(val fields: MigrationCodeFields) : MigrationCodeDecodeResult
    data object Malformed : MigrationCodeDecodeResult
}

object MigrationCodeCodec {
    private const val PREFIX = "ELPAIR1"
    private const val SEGMENT_COUNT = 6
    private const val MAX_ENCODED_CHARS = 1024
    private const val MAX_TIMESTAMP_DIGITS = 12

    fun encode(challenge: MigrationChallenge): String = buildString {
        append(PREFIX).append('|')
        append(encodeBase64Url(challenge.publicKeySpki)).append('|')
        append(encodeBase64Url(challenge.nonce)).append('|')
        append(encodeBase64Url(challenge.secret)).append('|')
        append(challenge.issuedAtEpochSeconds).append('|')
        append(challenge.expiresAtEpochSeconds)
    }

    fun decode(text: String): MigrationCodeDecodeResult {
        if (text.length > MAX_ENCODED_CHARS) return MigrationCodeDecodeResult.Malformed
        val segments = text.split('|')
        if (segments.size != SEGMENT_COUNT || segments.first() != PREFIX) return MigrationCodeDecodeResult.Malformed
        val spki = decodeBase64Url(segments[1]) ?: return MigrationCodeDecodeResult.Malformed
        if (spki.size !in MigrationChallengePolicy.MIN_PUBLIC_KEY_SPKI_BYTES..MigrationChallengePolicy.MAX_PUBLIC_KEY_SPKI_BYTES) {
            return MigrationCodeDecodeResult.Malformed
        }
        val nonce = decodeBase64Url(segments[2]) ?: return MigrationCodeDecodeResult.Malformed
        if (nonce.size != MigrationChallengePolicy.NONCE_BYTES) return MigrationCodeDecodeResult.Malformed
        val secret = decodeBase64Url(segments[3]) ?: return MigrationCodeDecodeResult.Malformed
        if (secret.size != MigrationChallengePolicy.SECRET_BYTES) return MigrationCodeDecodeResult.Malformed
        val issuedAt = decodeEpochSeconds(segments[4]) ?: return MigrationCodeDecodeResult.Malformed
        val expiresAt = decodeEpochSeconds(segments[5]) ?: return MigrationCodeDecodeResult.Malformed
        if (expiresAt - issuedAt != MigrationChallengePolicy.CHALLENGE_TTL_SECONDS) {
            return MigrationCodeDecodeResult.Malformed
        }
        return MigrationCodeDecodeResult.Parsed(MigrationCodeFields(spki, nonce, secret, issuedAt, expiresAt))
    }

    private fun encodeBase64Url(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun decodeBase64Url(segment: String): ByteArray? {
        if (segment.isEmpty() || segment.contains('=') || segment.contains('+') || segment.contains('/')) return null
        return try {
            java.util.Base64.getUrlDecoder().decode(segment)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun decodeEpochSeconds(segment: String): Long? {
        if (segment.isEmpty() || segment.length > MAX_TIMESTAMP_DIGITS || !segment.all { it in '0'..'9' }) return null
        val value = segment.toLongOrNull() ?: return null
        return if (value > 0L) value else null
    }
}
