package com.example.englishlearning.backup

import com.example.englishlearning.backup.domain.BackupPackageFormat
import com.example.englishlearning.backup.domain.BackupPackageValidator
import com.example.englishlearning.backup.domain.PackageLimits
import com.example.englishlearning.backup.domain.PackageReadResult
import com.example.englishlearning.backup.domain.PackageValidationFailure
import com.example.englishlearning.backup.infrastructure.ZipBackupPackageWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ZipBackupPackageWriterTest {
    @Test
    fun `writes deterministic archive independent of caller order`() {
        val first = ZipBackupPackageWriter().write(
            listOf(
                ZipBackupPackageWriter.Entry("resources/audio", byteArrayOf(3, 2, 1)),
                ZipBackupPackageWriter.Entry("records/profile/profile-1.json", "{\"word\":\"hello\"}".toByteArray()),
            ),
        )
        val second = ZipBackupPackageWriter().write(
            listOf(
                ZipBackupPackageWriter.Entry("records/profile/profile-1.json", "{\"word\":\"hello\"}".toByteArray()),
                ZipBackupPackageWriter.Entry("resources/audio", byteArrayOf(3, 2, 1)),
            ),
        )

        assertIs<ZipBackupPackageWriter.Result.Written>(first)
        assertIs<ZipBackupPackageWriter.Result.Written>(second)
        assertContentEquals(first.bytes, second.bytes)
    }

    @Test
    fun `written archive validates and recovers exact fixture bytes`() {
        val profile = "{\"word\":\"hello\"}".toByteArray()
        val audio = byteArrayOf(3, 2, 1)
        val result = ZipBackupPackageWriter().write(
            listOf(
                ZipBackupPackageWriter.Entry("records/profile/profile-1.json", profile),
                ZipBackupPackageWriter.Entry("resources/audio", audio),
            ),
        )
        val written = assertIs<ZipBackupPackageWriter.Result.Written>(result)

        assertIs<com.example.englishlearning.backup.domain.PackageValidationResult.Valid>(
            BackupPackageValidator().validate(written.bytes),
        )
        val read = assertIs<PackageReadResult.Read>(BackupPackageValidator().readEntries(written.bytes))
        assertContentEquals(profile, read.entries.getValue("records/profile/profile-1.json"))
        assertContentEquals(audio, read.entries.getValue("resources/audio"))
    }

    @Test
    fun `rejects unsafe path before writing`() {
        val result = ZipBackupPackageWriter().write(
            listOf(ZipBackupPackageWriter.Entry("records/profile/../secret.json", byteArrayOf(1))),
        )

        assertEquals(ZipBackupPackageWriter.Result.Invalid(PackageValidationFailure.InvalidPath), result)
    }

    @Test
    fun `rejects duplicate path before writing`() {
        val result = ZipBackupPackageWriter().write(
            listOf(
                ZipBackupPackageWriter.Entry("resources/audio", byteArrayOf(1)),
                ZipBackupPackageWriter.Entry("resources/audio", byteArrayOf(2)),
            ),
        )

        assertEquals(ZipBackupPackageWriter.Result.Invalid(PackageValidationFailure.DuplicateEntry), result)
    }

    @Test
    fun `rejects source over per entry limit`() {
        val limits = PackageLimits(maxArchiveBytes = 4096, maxEntries = 10, maxEntryBytes = 2, maxTotalBytes = 10)
        val result = ZipBackupPackageWriter(limits).write(
            listOf(ZipBackupPackageWriter.Entry("resources/audio", byteArrayOf(1, 2, 3))),
        )

        assertEquals(ZipBackupPackageWriter.Result.Invalid(PackageValidationFailure.LimitExceeded), result)
    }

    @Test
    fun `rejects sources over aggregate limit`() {
        val limits = PackageLimits(maxArchiveBytes = 4096, maxEntries = 10, maxEntryBytes = 10, maxTotalBytes = 2)
        val result = ZipBackupPackageWriter(limits).write(
            listOf(
                ZipBackupPackageWriter.Entry("resources/a", byteArrayOf(1)),
                ZipBackupPackageWriter.Entry("resources/b", byteArrayOf(2)),
                ZipBackupPackageWriter.Entry("resources/c", byteArrayOf(3)),
            ),
        )

        assertEquals(ZipBackupPackageWriter.Result.Invalid(PackageValidationFailure.LimitExceeded), result)
    }
}
