package com.example.englishlearning.backup.pairing

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PairedMigrationChallengeVerifierTest {
    private val pubKey = ByteArray(91) { (it % 13).toByte() }
    private val nonce = ByteArray(16) { (it + 2).toByte() }
    private val secret = ByteArray(16) { (it + 91).toByte() }
    private val issuedAt = 1_700_000_000L
    private val expiresAt = issuedAt + MigrationChallengePolicy.CHALLENGE_TTL_SECONDS
    private val scope = "elpair-v3/|canonical-manifest-bytes".toByteArray(Charsets.UTF_8)

    private val verifier = PairedMigrationChallengeVerifier(JavaSecurityEcSignatureVerifier())

    private fun code(
        spki: ByteArray = pubKey,
        codeNonce: ByteArray = nonce,
        codeSecret: ByteArray = secret,
        codeIssued: Long = issuedAt,
        codeExpires: Long = expiresAt,
    ) = MigrationCodeFields(spki, codeNonce, codeSecret, codeIssued, codeExpires)

    private fun record(
        spki: ByteArray = pubKey,
        recordNonce: ByteArray = nonce,
        issued: Long = issuedAt,
        expires: Long = expiresAt,
        commitment: ByteArray = sha256(secret),
        signedScope: ByteArray = scope,
        signer: java.security.PrivateKey? = null,
        signature: ByteArray? = signer?.signScope(signedScope),
    ) = PackagePairingRecord(
        publicKeySpki = spki,
        nonce = recordNonce,
        issuedAtEpochSeconds = issued,
        expiresAtEpochSeconds = expires,
        secretCommitmentSha256 = commitment,
        derSignature = signature ?: ByteArray(70) { (it % 5).toByte() },
        signedScopeBytes = signedScope,
    )

    @Test
    fun `valid pairing with live signature passes and reports the nonce`() {
        val pair = ecKeyPair()
        val realPub = pair.public.encoded
        val result = verifier.check(
            code(spki = realPub),
            record(spki = realPub, signer = pair.private),
            nowEpochSeconds = expiresAt - 1,
        )
        val paired = assertIs<PairingCheckResult.Paired>(result)
        assertEquals(nonce.toHexString(), paired.nonceHex)
    }

    @Test
    fun `expired boundary makes verification fail exactly at expiresAt`() {
        val pair = ecKeyPair()
        val realPub = pair.public.encoded
        val live = record(spki = realPub, signer = pair.private)
        val liveCode = code(spki = realPub)
        assertIs<PairingCheckResult.Paired>(verifier.check(liveCode, live, nowEpochSeconds = expiresAt - 1))
        assertIs<PairingCheckResult.Invalid>(verifier.check(liveCode, live, nowEpochSeconds = expiresAt))
            .let { assertEquals(PairingCheckFailure.PairingExpired, it.reason) }
        assertIs<PairingCheckResult.Invalid>(verifier.check(liveCode, live, nowEpochSeconds = expiresAt + 3_600))
    }

    @Test
    fun `any binding mismatch between code and package pairing block is rejected`() {
        val pair = ecKeyPair()
        val realPub = pair.public.encoded
        val liveRecord = record(spki = realPub, signer = pair.private)
        val cases = listOf(
            code(spki = ByteArray(91).also { pair.public.encoded.copyInto(it, 0); it[0] = (it[0] + 1).toByte() }) to liveRecord,
            code(spki = realPub, codeNonce = nonce.copyOf().also { it[15] = 99.toByte() }) to liveRecord,
            code(spki = realPub, codeIssued = issuedAt + 1) to liveRecord,
            code(spki = realPub, codeExpires = expiresAt + 1) to liveRecord,
            code(spki = realPub, codeSecret = secret.copyOf().also { it[0] = 7.toByte() }) to liveRecord,
            code(spki = realPub) to record(spki = realPub, signer = pair.private, recordNonce = nonce.copyOf().also { it[3] = 77.toByte() }),
            code(spki = realPub) to record(spki = realPub, signer = pair.private, commitment = sha256(secret.copyOf().also { it[1] = 8.toByte() })),
        )
        for ((index, casePair) in cases.withIndex()) {
            val result = verifier.check(casePair.first, casePair.second, nowEpochSeconds = expiresAt - 1)
            val invalid = assertIs<PairingCheckResult.Invalid>(result, "case $index should be a mismatch")
            assertEquals(PairingCheckFailure.PairingMismatch, invalid.reason, "case $index")
        }
    }

    @Test
    fun `tampered signed scope yields signature failure category`() {
        val pair = ecKeyPair()
        val realPub = pair.public.encoded
        val tamperedScope = scope.copyOf().also { it[0] = (it[0] + 1).toByte() }
        val result = verifier.check(
            code(spki = realPub),
            record(
                spki = realPub,
                signedScope = tamperedScope,
                signature = pair.private.signScope(scope),
            ),
            nowEpochSeconds = expiresAt - 1,
        )
        val invalid = assertIs<PairingCheckResult.Invalid>(result)
        assertEquals(PairingCheckFailure.PairingSignatureInvalid, invalid.reason)
    }

    @Test
    fun `signature from a foreign key over the same scope is rejected`() {
        val honest = ecKeyPair()
        val attacker = ecKeyPair()
        val result = verifier.check(
            code(spki = honest.public.encoded),
            record(spki = honest.public.encoded, signer = attacker.private),
            nowEpochSeconds = expiresAt - 1,
        )
        // spki binding mismatch fires before signature: either category is a rejection, never Paired.
        assertTrue(result is PairingCheckResult.Invalid)
    }

    @Test
    fun `garbage public key encoding cannot crash verification`() {
        val result = verifier.check(
            code(spki = ByteArray(4)),
            record(spki = ByteArray(4), commitment = sha256(secret)),
            nowEpochSeconds = expiresAt - 1,
        )
        assertIs<PairingCheckResult.Invalid>(result)
    }

    private fun ecKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun java.security.PrivateKey.signScope(bytes: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(this@signScope)
            update(bytes)
            sign()
        }

    private fun sha256(bytes: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
