package com.example.englishlearning.backup.pairing

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Pairing block stored inside the package (`pairing.json`). `signedScopeBytes` is the
 * canonical byte range the migration key signed (decision doc §3.2); the signature is
 * verified against `publicKeySpki` over exactly those bytes.
 */
data class PackagePairingRecord(
    val publicKeySpki: ByteArray,
    val nonce: ByteArray,
    val issuedAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val secretCommitmentSha256: ByteArray,
    val derSignature: ByteArray,
    val signedScopeBytes: ByteArray,
)

sealed interface PairingCheckFailure {
    data object PairingMismatch : PairingCheckFailure
    data object PairingExpired : PairingCheckFailure
    data object PairingSignatureInvalid : PairingCheckFailure
}

sealed interface PairingCheckResult {
    data class Paired(val nonceHex: String) : PairingCheckResult
    data class Invalid(val reason: PairingCheckFailure) : PairingCheckResult
}

/** ECDSA verification port; the default implementation is the platform one. */
fun interface EcSignatureVerifier {
    fun verify(publicKeySpki: ByteArray, scopeBytes: ByteArray, derSignature: ByteArray): Boolean
}

/**
 * Platform SHA256withECDSA verification over X.509/SPKI encoded keys. Identical code path
 * on JVM tests and device; malformed material fails closed to `false`, never an exception.
 */
class JavaSecurityEcSignatureVerifier : EcSignatureVerifier {
    override fun verify(publicKeySpki: ByteArray, scopeBytes: ByteArray, derSignature: ByteArray): Boolean = try {
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeySpki))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(scopeBytes)
            verify(derSignature)
        }
    } catch (_: Exception) {
        false
    }
}

/**
 * Verifies the scanned migration code against the package pairing block, in the decision-doc
 * order: field bindings (constant-time) -> secret commitment -> expiry -> signature.
 * Categories only; no failure detail is propagated.
 */
class PairedMigrationChallengeVerifier(private val signatureVerifier: EcSignatureVerifier) {
    fun check(code: MigrationCodeFields, record: PackagePairingRecord, nowEpochSeconds: Long): PairingCheckResult {
        val bindingsMatch = MessageDigest.isEqual(code.publicKeySpki, record.publicKeySpki) &&
            MessageDigest.isEqual(code.nonce, record.nonce) &&
            code.issuedAtEpochSeconds == record.issuedAtEpochSeconds &&
            code.expiresAtEpochSeconds == record.expiresAtEpochSeconds &&
            MessageDigest.isEqual(pairingSha256(code.secret), record.secretCommitmentSha256)
        if (!bindingsMatch) return PairingCheckResult.Invalid(PairingCheckFailure.PairingMismatch)
        if (nowEpochSeconds >= record.expiresAtEpochSeconds) {
            return PairingCheckResult.Invalid(PairingCheckFailure.PairingExpired)
        }
        if (!signatureVerifier.verify(record.publicKeySpki, record.signedScopeBytes, record.derSignature)) {
            return PairingCheckResult.Invalid(PairingCheckFailure.PairingSignatureInvalid)
        }
        return PairingCheckResult.Paired(nonceHex = record.nonce.toPairingHex())
    }
}
