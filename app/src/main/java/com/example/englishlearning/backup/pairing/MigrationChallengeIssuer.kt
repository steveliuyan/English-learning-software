package com.example.englishlearning.backup.pairing

/**
 * Old-device pairing state machine (decision doc §2.3): authentication gates issuance,
 * at most one challenge is active, starting a new pairing supersedes the previous
 * challenge, and the ledger never records the QR-only secret.
 */
sealed interface PairingPhase {
    data object Idle : PairingPhase
    data object Authenticating : PairingPhase
    data class Active(val challenge: MigrationChallenge) : PairingPhase
}

sealed interface PairingStatus {
    data object Idle : PairingStatus
    data object Authenticating : PairingStatus
    data class Active(val challenge: MigrationChallenge) : PairingStatus
    data class Expired(val challenge: MigrationChallenge) : PairingStatus
}

enum class SupersededReason { ReplacedByNewPairing, Abandoned }

enum class AuthenticationFailureReason { Failed, Cancelled }

sealed interface PairingLedgerEvent {
    data class Issued(
        val nonceHex: String,
        val keyFingerprintHex: String,
        val issuedAtEpochSeconds: Long,
        val expiresAtEpochSeconds: Long,
    ) : PairingLedgerEvent

    data class Superseded(val nonceHex: String, val reason: SupersededReason) : PairingLedgerEvent

    data class AuthenticationRejected(val reason: AuthenticationFailureReason) : PairingLedgerEvent
}

sealed interface PairingBeginResult {
    data object Started : PairingBeginResult
    data object IgnoredAlreadyAuthenticating : PairingBeginResult
}

sealed interface PairingIssueResult {
    data class Issued(val challenge: MigrationChallenge) : PairingIssueResult
    data object KeyUnavailable : PairingIssueResult
    data object IgnoredNotAwaitingAuthentication : PairingIssueResult
}

sealed interface PairingRejectResult {
    data object Recorded : PairingRejectResult
    data object IgnoredNotAwaitingAuthentication : PairingRejectResult
}

sealed interface PairingAbandonResult {
    data object Abandoned : PairingAbandonResult
    data object IgnoredNothingActive : PairingAbandonResult
}

class MigrationChallengeIssuer(
    private val entropy: PairingEntropySource,
    private val clock: PairingClock,
    private val signingKey: MigrationSigningKey,
) {
    private var currentPhase: PairingPhase = PairingPhase.Idle
    private val ledger = mutableListOf<PairingLedgerEvent>()

    fun phase(): PairingPhase = currentPhase

    fun events(): List<PairingLedgerEvent> = ledger.toList()

    fun status(): PairingStatus = when (val phase = currentPhase) {
        is PairingPhase.Idle -> PairingStatus.Idle
        is PairingPhase.Authenticating -> PairingStatus.Authenticating
        is PairingPhase.Active ->
            if (clock.nowEpochSeconds() >= phase.challenge.expiresAtEpochSeconds) {
                PairingStatus.Expired(phase.challenge)
            } else {
                PairingStatus.Active(phase.challenge)
            }
    }

    /** Starting a pairing supersedes any active challenge immediately. */
    fun beginPairing(): PairingBeginResult {
        if (currentPhase is PairingPhase.Authenticating) return PairingBeginResult.IgnoredAlreadyAuthenticating
        val active = currentPhase as? PairingPhase.Active
        active?.let {
            ledger += PairingLedgerEvent.Superseded(it.challenge.nonce.toPairingHex(), SupersededReason.ReplacedByNewPairing)
        }
        currentPhase = PairingPhase.Authenticating
        return PairingBeginResult.Started
    }

    fun completeAuthentication(): PairingIssueResult {
        if (currentPhase !is PairingPhase.Authenticating) return PairingIssueResult.IgnoredNotAwaitingAuthentication
        val key = when (val outcome = signingKey.publicKeySpki()) {
            is MigrationKeyOutcome.Available -> outcome.encodedSpki
            MigrationKeyOutcome.Unavailable -> {
                currentPhase = PairingPhase.Idle
                return PairingIssueResult.KeyUnavailable
            }
        }
        val nonce = takeBytes(MigrationChallengePolicy.NONCE_BYTES)
        val secret = takeBytes(MigrationChallengePolicy.SECRET_BYTES)
        val issuedAt = clock.nowEpochSeconds()
        val challenge = MigrationChallenge(
            publicKeySpki = key,
            nonce = nonce,
            secret = secret,
            issuedAtEpochSeconds = issuedAt,
            expiresAtEpochSeconds = issuedAt + MigrationChallengePolicy.CHALLENGE_TTL_SECONDS,
        )
        currentPhase = PairingPhase.Active(challenge)
        ledger += PairingLedgerEvent.Issued(
            nonceHex = nonce.toPairingHex(),
            keyFingerprintHex = pairingSha256(challenge.publicKeySpki).toPairingHex(),
            issuedAtEpochSeconds = challenge.issuedAtEpochSeconds,
            expiresAtEpochSeconds = challenge.expiresAtEpochSeconds,
        )
        return PairingIssueResult.Issued(challenge)
    }

    fun rejectAuthentication(reason: AuthenticationFailureReason): PairingRejectResult {
        if (currentPhase !is PairingPhase.Authenticating) return PairingRejectResult.IgnoredNotAwaitingAuthentication
        currentPhase = PairingPhase.Idle
        ledger += PairingLedgerEvent.AuthenticationRejected(reason)
        return PairingRejectResult.Recorded
    }

    /** User-cancelled flow: drop any active challenge; nothing leaks into the ledger secret-wise. */
    fun abandon(): PairingAbandonResult {
        val active = currentPhase as? PairingPhase.Active
        if (active == null) {
            if (currentPhase is PairingPhase.Authenticating) currentPhase = PairingPhase.Idle
            return PairingAbandonResult.IgnoredNothingActive
        }
        ledger += PairingLedgerEvent.Superseded(active.challenge.nonce.toPairingHex(), SupersededReason.Abandoned)
        currentPhase = PairingPhase.Idle
        return PairingAbandonResult.Abandoned
    }

    private fun takeBytes(count: Int): ByteArray {
        val bytes = entropy.nextBytes(count)
        check(bytes.size == count) { "entropy source violated its contract" }
        return bytes
    }
}
