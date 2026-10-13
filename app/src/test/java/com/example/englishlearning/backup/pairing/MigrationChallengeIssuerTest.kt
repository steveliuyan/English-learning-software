package com.example.englishlearning.backup.pairing

import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MigrationChallengeIssuerTest {
    private class FixedEntropy(private val pools: MutableList<ByteArray>) : PairingEntropySource {
        val requested = mutableListOf<Int>()
        override fun nextBytes(count: Int): ByteArray {
            requested += count
            check(pools.isNotEmpty()) { "entropy exhausted" }
            val next = pools.removeAt(0)
            check(next.size == count) { "unexpected entropy size ${next.size} for $count" }
            return next.copyOf()
        }
    }

    private class MutableClock(var now: Long) : PairingClock {
        override fun nowEpochSeconds(): Long = now
    }

    private class FakeKey(private var outcome: MigrationKeyOutcome) : MigrationSigningKey {
        override fun publicKeySpki(): MigrationKeyOutcome = outcome
        fun makeUnavailable() { outcome = MigrationKeyOutcome.Unavailable }
    }

    private val pubKey = ByteArray(91) { (it % 7).toByte() }
    private val nonceBytes = ByteArray(16) { (it + 1).toByte() }
    private val secretBytes = ByteArray(16) { (it + 101).toByte() }

    private fun issuer(
        entropy: PairingEntropySource,
        clock: PairingClock = MutableClock(1_700_000_000L),
        key: MigrationSigningKey = FakeKey(MigrationKeyOutcome.Available(pubKey)),
    ) = MigrationChallengeIssuer(entropy, clock, key)

    private fun beginAndAuthenticate(source: MigrationChallengeIssuer) {
        assertIs<PairingBeginResult.Started>(source.beginPairing())
    }

    @Test
    fun `issuing requires begin and successful authentication first`() {
        val source = issuer(FixedEntropy(mutableListOf(nonceBytes.copyOf(), secretBytes.copyOf())))
        assertIs<PairingIssueResult.IgnoredNotAwaitingAuthentication>(source.completeAuthentication())
        assertIs<PairingPhase.Idle>(source.phase())

        beginAndAuthenticate(source)
        assertIs<PairingPhase.Authenticating>(source.phase())
        val issued = assertIs<PairingIssueResult.Issued>(source.completeAuthentication())
        assertContentEquals(nonceBytes, issued.challenge.nonce)
        assertContentEquals(secretBytes, issued.challenge.secret)
        assertContentEquals(pubKey, issued.challenge.publicKeySpki)
        assertIs<PairingPhase.Active>(source.phase())
    }

    @Test
    fun `challenge expires exactly TTL seconds after issue`() {
        val source = issuer(FixedEntropy(mutableListOf(nonceBytes.copyOf(), secretBytes.copyOf())))
        beginAndAuthenticate(source)
        val issued = assertIs<PairingIssueResult.Issued>(source.completeAuthentication())
        assertEquals(1_700_000_000L, issued.challenge.issuedAtEpochSeconds)
        assertEquals(
            1_700_000_000L + MigrationChallengePolicy.CHALLENGE_TTL_SECONDS,
            issued.challenge.expiresAtEpochSeconds,
        )
        assertEquals(1_800L, MigrationChallengePolicy.CHALLENGE_TTL_SECONDS)
    }

    @Test
    fun `secret commitment is sha256 of the secret and secret is fixed size`() {
        val source = issuer(FixedEntropy(mutableListOf(nonceBytes.copyOf(), secretBytes.copyOf())))
        beginAndAuthenticate(source)
        val issued = assertIs<PairingIssueResult.Issued>(source.completeAuthentication())
        assertEquals(16, issued.challenge.nonce.size)
        assertEquals(16, issued.challenge.secret.size)
        assertContentEquals(sha256Of(secretBytes), issued.challenge.secretCommitment)
    }

    @Test
    fun `failed or cancelled authentication records rejection and issues nothing`() {
        for (reason in listOf(AuthenticationFailureReason.Failed, AuthenticationFailureReason.Cancelled)) {
            val source = issuer(FixedEntropy(mutableListOf()))
            beginAndAuthenticate(source)
            assertIs<PairingRejectResult.Recorded>(source.rejectAuthentication(reason))
            assertIs<PairingPhase.Idle>(source.phase())
            assertEquals(
                listOf(PairingLedgerEvent.AuthenticationRejected(reason)),
                source.events(),
            )
        }
    }

    @Test
    fun `missing signing key surfaces as key unavailable and never issues`() {
        val source = issuer(FixedEntropy(mutableListOf()), key = FakeKey(MigrationKeyOutcome.Unavailable))
        beginAndAuthenticate(source)
        assertIs<PairingIssueResult.KeyUnavailable>(source.completeAuthentication())
        assertIs<PairingPhase.Idle>(source.phase())
        assertTrue(source.events().isEmpty())
    }

    @Test
    fun `beginning a new pairing supersedes the active challenge`() {
        val entropy = FixedEntropy(
            mutableListOf(
                nonceBytes.copyOf(), secretBytes.copyOf(),
                nonceBytes.copyOf().also { it[0] = 42 }, secretBytes.copyOf().also { it[0] = 43 },
            ),
        )
        val source = issuer(entropy)
        beginAndAuthenticate(source)
        source.completeAuthentication()
        assertIs<PairingBeginResult.Started>(source.beginPairing())
        assertEquals(
            listOf(PairingLedgerEvent.Superseded(nonceBytes.toHexString(), SupersededReason.ReplacedByNewPairing)),
            source.events().filter { it is PairingLedgerEvent.Superseded },
        )
        assertIs<PairingPhase.Authenticating>(source.phase())
        val second = assertIs<PairingIssueResult.Issued>(source.completeAuthentication())
        assertEquals(42.toByte(), second.challenge.nonce[0])
    }

    @Test
    fun `begin while authenticating is ignored and keeps single pending session`() {
        val source = issuer(FixedEntropy(mutableListOf(nonceBytes.copyOf(), secretBytes.copyOf())))
        assertIs<PairingBeginResult.Started>(source.beginPairing())
        assertIs<PairingBeginResult.IgnoredAlreadyAuthenticating>(source.beginPairing())
        val issued = assertIs<PairingIssueResult.Issued>(source.completeAuthentication())
        assertContentEquals(nonceBytes, issued.challenge.nonce)
    }

    @Test
    fun `abandon discards an active challenge and records it`() {
        val source = issuer(FixedEntropy(mutableListOf(nonceBytes.copyOf(), secretBytes.copyOf())))
        beginAndAuthenticate(source)
        source.completeAuthentication()
        assertIs<PairingAbandonResult.Abandoned>(source.abandon())
        assertIs<PairingPhase.Idle>(source.phase())
        assertEquals(
            listOf(PairingLedgerEvent.Superseded(nonceBytes.toHexString(), SupersededReason.Abandoned)),
            source.events().filter { it is PairingLedgerEvent.Superseded },
        )
        assertIs<PairingAbandonResult.IgnoredNothingActive>(source.abandon())
    }

    @Test
    fun `status reports expired once the ttl boundary is reached`() {
        val clock = MutableClock(1_700_000_000L)
        val source = issuer(FixedEntropy(mutableListOf(nonceBytes.copyOf(), secretBytes.copyOf())), clock)
        beginAndAuthenticate(source)
        val issued = assertIs<PairingIssueResult.Issued>(source.completeAuthentication())
        clock.now = issued.challenge.expiresAtEpochSeconds - 1
        assertIs<PairingStatus.Active>(source.status())
        clock.now = issued.challenge.expiresAtEpochSeconds
        val expired = assertIs<PairingStatus.Expired>(source.status())
        assertContentEquals(nonceBytes, expired.challenge.nonce)
    }

    @Test
    fun `ledger events never carry the pairing secret or key material`() {
        val source = issuer(FixedEntropy(mutableListOf(nonceBytes.copyOf(), secretBytes.copyOf())))
        beginAndAuthenticate(source)
        source.completeAuthentication()
        val events = source.events()
        assertTrue(events.isNotEmpty())
        for (event in events) {
            for (property in event::class.memberProperties) {
                val name = property.name.lowercase()
                assertTrue("secret" !in name && "private" !in name && "spki" !in name, "leaky field $name")
                val value = property.getter.call(event)
                if (value is ByteArray) assertTrue(!value.contentEquals(secretBytes))
            }
        }
    }

    private fun sha256Of(bytes: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
