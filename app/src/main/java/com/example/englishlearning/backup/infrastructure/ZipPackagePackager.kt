package com.example.englishlearning.backup.infrastructure

import com.example.englishlearning.backup.domain.PackagePackager
import com.example.englishlearning.backup.domain.PackagePackagingOutcome
import com.example.englishlearning.backup.domain.PackagerEntry

/** Adapts the deterministic ZIP writer to the domain packager boundary. */
class ZipPackagePackager : PackagePackager {
    private val writer = ZipBackupPackageWriter()

    override fun write(entries: List<PackagerEntry>, createdAtEpochMillis: Long): PackagePackagingOutcome =
        when (val result = writer.write(entries.map { ZipBackupPackageWriter.Entry(it.path, it.bytes) }, createdAtEpochMillis)) {
            is ZipBackupPackageWriter.Result.Written -> PackagePackagingOutcome.Written(result.bytes)
            is ZipBackupPackageWriter.Result.Invalid -> PackagePackagingOutcome.Invalid(result.reason)
        }
}
