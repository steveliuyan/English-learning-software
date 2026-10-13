package com.example.englishlearning.backup.infrastructure

import com.example.englishlearning.backup.domain.PackagerEntry
import com.example.englishlearning.backup.domain.PackageLimits
import com.example.englishlearning.backup.domain.ProtectedPackagePackager
import com.example.englishlearning.backup.domain.ProtectedPackagePackagingOutcome
import com.example.englishlearning.backup.domain.ProtectedPackagingSpec

class ZipProtectedPackagePackager(
    private val writer: ProtectedZipBackupPackageWriter = ProtectedZipBackupPackageWriter(),
) : ProtectedPackagePackager {
    override fun write(
        entries: List<PackagerEntry>,
        createdAtEpochMillis: Long,
        protection: ProtectedPackagingSpec,
    ): ProtectedPackagePackagingOutcome = when (
        val result = writer.write(
            entries = entries.map { ProtectedZipBackupPackageWriter.Entry(it.path, it.bytes) },
            createdAtEpochMillis = createdAtEpochMillis,
            protection = ProtectedZipBackupPackageWriter.Protection(
                inputs = protection.inputs,
                signer = protection.signer,
                passwordChars = protection.passwordChars,
                kdf = protection.kdf,
                aead = protection.aead,
                entropy = protection.entropy,
            ),
        )
    ) {
        is ProtectedZipBackupPackageWriter.Result.Written -> ProtectedPackagePackagingOutcome.Written(result.bytes)
        is ProtectedZipBackupPackageWriter.Result.Failed -> ProtectedPackagePackagingOutcome.Failed(result.reason)
    }
}
