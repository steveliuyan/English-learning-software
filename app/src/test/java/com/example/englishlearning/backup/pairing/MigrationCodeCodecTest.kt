package com.example.englishlearning.backup.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MigrationCodeCodecTest {
    private val pubKey = ByteArray(91) { (it % 11).toByte() }
    private val nonce = ByteArray(16) { (it + 1).toByte() }
    private val secret = ByteArray(16) { (it + 61).toByte() }
    private val issuedAt = 1_700_000_000L
    private val expiresAt = issuedAt + MigrationChallengePolicy.CHALLENGE_TTL_SECONDS

    private fun challenge() = MigrationChallenge(pubKey, nonce, secret, issuedAt, expiresAt)

    @Test
    fun `round trip preserves all challenge fields`() {
        val encoded = MigrationCodeCodec.encode(challenge())
        val parsed = assertIs<MigrationCodeDecodeResult.Parsed>(MigrationCodeCodec.decode(encoded))
        assertEquals(pubKey.toList(), parsed.fields.publicKeySpki.toList())
        assertEquals(nonce.toList(), parsed.fields.nonce.toList())
        assertEquals(secret.toList(), parsed.fields.secret.toList())
        assertEquals(issuedAt, parsed.fields.issuedAtEpochSeconds)
        assertEquals(expiresAt, parsed.fields.expiresAtEpochSeconds)
    }

    @Test
    fun `encoded payload is prefixed deterministic and padding-free`() {
        val first = MigrationCodeCodec.encode(challenge())
        val second = MigrationCodeCodec.encode(challenge())
        assertEquals(first, second)
        assertTrue(first.startsWith("ELPAIR1|"))
        assertTrue('=' !in first)
        assertEquals(6, first.split('|').size)
    }

    @Test
    fun `rejects wrong prefix wrong segment count and oversized input`() {
        val good = MigrationCodeCodec.encode(challenge())
        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode(good.replace("ELPAIR1", "ELPAIR2")))
        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode(good.substringBeforeLast('|')))
        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode(good + "|extra"))
        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode("ELPAIR1|" + "A".repeat(2_000)))
        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode(""))
    }

    @Test
    fun `rejects malformed base64 and wrong material sizes`() {
        fun withParts(spki: String = enc(pubKey), noncePart: String = enc(nonce), secretPart: String = enc(secret)) =
            "ELPAIR1|$spki|$noncePart|$secretPart|$issuedAt|$expiresAt"

        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode(withParts(noncePart = "not*valid!")))
        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode(withParts(noncePart = enc(nonce.copyOf(15)))))
        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode(withParts(secretPart = enc(secret.copyOf(17)))))
        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode(withParts(spki = "")))
        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode(withParts(spki = enc(ByteArray(300)))))
    }

    @Test
    fun `rejects timestamps that deviate from the pinned ttl or shape`() {
        fun withTimes(issued: String, expires: String) =
            "ELPAIR1|${enc(pubKey)}|${enc(nonce)}|${enc(secret)}|$issued|$expires"

        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode(withTimes("1700000000", "1700000600")))
        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode(withTimes("1700000000", "1700003601")))
        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode(withTimes("-5", "1700001800")))
        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode(withTimes("0", "1800")))
        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode(withTimes("1700000000.5", "1700001800")))
        assertIs<MigrationCodeDecodeResult.Malformed>(MigrationCodeCodec.decode(withTimes("1700000000", "1699999999")))
        // expiresAt == issuedAt + TTL is the only accepted window shape.
        assertIs<MigrationCodeDecodeResult.Parsed>(MigrationCodeCodec.decode(withTimes("1700000000", "1700001800")))
    }

    @Test
    fun `constructed challenge validates its own invariants`() {
        assertFailsWith<IllegalArgumentException> {
            MigrationChallenge(pubKey, nonce.copyOf(15), secret, issuedAt, expiresAt)
        }
        assertFailsWith<IllegalArgumentException> {
            MigrationChallenge(pubKey, nonce, secret, issuedAt, issuedAt + 60)
        }
        assertFailsWith<IllegalArgumentException> {
            MigrationChallenge(ByteArray(0), nonce, secret, issuedAt, expiresAt)
        }
    }

    private fun enc(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
