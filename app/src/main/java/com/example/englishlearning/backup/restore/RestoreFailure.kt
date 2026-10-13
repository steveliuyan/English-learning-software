package com.example.englishlearning.backup.restore

import com.example.englishlearning.backup.domain.PackageValidationFailure

/**
 * Fixed, non-sensitive restore failure categories
 * (docs/decisions/2026-10-13-f4-04-crypto-and-pairing-design.md §9.2 + §9.5).
 * Zero free text is ever attached; UI maps each category to an actionable message.
 */
sealed interface RestoreFailure {
    data object UnsupportedRestoreVersion : RestoreFailure
    data object SalvageNotRestorable : RestoreFailure
    data object ManifestSchemaInvalid : RestoreFailure
    data object PairingExpired : RestoreFailure
    data object PairingMismatch : RestoreFailure
    data object PairingSignatureInvalid : RestoreFailure
    data object PasswordRequired : RestoreFailure
    data object PasswordIncorrect : RestoreFailure
    data object DecryptionFailure : RestoreFailure
    data object IntegrityFailure : RestoreFailure
    data object ResourceRelationshipInvalid : RestoreFailure
    data object EntityReferenceInvalid : RestoreFailure
    data object StorageInsufficient : RestoreFailure
    data object RollbackVerification : RestoreFailure

    companion object {
        /** Deterministic mapping from package-level validation failures to restore categories. */
        fun from(reason: PackageValidationFailure): RestoreFailure = when (reason) {
            PackageValidationFailure.UnsupportedFormat -> UnsupportedRestoreVersion
            PackageValidationFailure.InvalidManifest,
            PackageValidationFailure.MissingPairingRecord,
            -> ManifestSchemaInvalid
            PackageValidationFailure.SignatureInvalid -> PairingSignatureInvalid
            PackageValidationFailure.PairingRecordMismatch -> PairingMismatch
            PackageValidationFailure.UnsupportedCryptoParameters -> UnsupportedRestoreVersion
            PackageValidationFailure.InvalidArchive,
            PackageValidationFailure.InvalidPath,
            PackageValidationFailure.DuplicateEntry,
            PackageValidationFailure.LimitExceeded,
            PackageValidationFailure.LengthMismatch,
            PackageValidationFailure.IntegrityFailure,
            -> IntegrityFailure
        }
    }
}
