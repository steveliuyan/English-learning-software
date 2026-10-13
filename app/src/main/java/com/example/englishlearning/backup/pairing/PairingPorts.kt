package com.example.englishlearning.backup.pairing

/** Cryptographically strong byte source injected into the issuer (SecureRandom in production). */
interface PairingEntropySource {
    fun nextBytes(count: Int): ByteArray
}

/** Wall-clock source in Unix seconds; the device clock is the single time authority offline. */
interface PairingClock {
    fun nowEpochSeconds(): Long
}

sealed interface MigrationKeyOutcome {
    data class Available(val encodedSpki: ByteArray) : MigrationKeyOutcome
    data object Unavailable : MigrationKeyOutcome
}

/**
 * Read-only view of the non-exportable Keystore migration key. The private key never
 * leaves the Keystore; authentication enforcement happens in the Keystore specification.
 */
interface MigrationSigningKey {
    fun publicKeySpki(): MigrationKeyOutcome
}
