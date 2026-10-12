package com.example.englishlearning.backup

import com.example.englishlearning.backup.domain.BackupPackageValidator
import com.example.englishlearning.backup.domain.PackageLimits
import com.example.englishlearning.backup.domain.PackageValidationFailure
import com.example.englishlearning.backup.domain.PackageValidationResult
import com.example.englishlearning.backup.domain.PackageReadResult
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
                ZipBackupPackageWriter.Entry("records/profiles/profile-1.json", "{\"word\":\"hello\"}".toByteArray()),
            ),
            createdAtEpochMillis = 1_700_000_000_000L,
        )
        val second = ZipBackupPackageWriter().write(
            listOf(
                ZipBackupPackageWriter.Entry("records/profiles/profile-1.json", "{\"word\":\"hello\"}".toByteArray()),
                ZipBackupPackageWriter.Entry("resources/audio", byteArrayOf(3, 2, 1)),
            ),
            createdAtEpochMillis = 1_700_000_000_000L,
        )

        assertIs<ZipBackupPackageWriter.Result.Written>(first)
        assertIs<ZipBackupPackageWriter.Result.Written>(second)
        assertContentEquals(first.bytes, second.bytes)
    }

    @Test
    fun `different creation time produces different archive bytes`() {
        val entries = listOf(ZipBackupPackageWriter.Entry("records/profiles/profile-1.json", "{}".toByteArray()))
        val earlier = assertIs<ZipBackupPackageWriter.Result.Written>(
            ZipBackupPackageWriter().write(entries, createdAtEpochMillis = 1_700_000_000_000L),
        )
        val later = assertIs<ZipBackupPackageWriter.Result.Written>(
            ZipBackupPackageWriter().write(entries, createdAtEpochMillis = 1_700_000_000_001L),
        )

        assert(!earlier.bytes.contentEquals(later.bytes))
    }

    @Test
    fun `manifest carries honest protection status and derived scope`() {
        val written = assertIs<ZipBackupPackageWriter.Result.Written>(
            ZipBackupPackageWriter().write(
                listOf(
                    ZipBackupPackageWriter.Entry("records/articles/a-1.json", "{}".toByteArray()),
                    ZipBackupPackageWriter.Entry("resources/audio", byteArrayOf(1)),
                ),
                createdAtEpochMillis = 1_700_000_000_000L,
            ),
        )

        val valid = assertIs<PackageValidationResult.Valid>(BackupPackageValidator().validate(written.bytes))
        assertEquals(2, valid.manifest.formatVersion)
        assertEquals(1_700_000_000_000L, valid.manifest.createdAtEpochMillis)
        assertEquals(listOf("articles", "media"), valid.manifest.scope)
        assertEquals("not-protected", valid.manifest.signatureStatus)
        assertEquals("not-supported", valid.manifest.authenticationStatus)
        assertEquals("none", valid.manifest.encryptionStatus)
    }

    @Test
    fun `written archive validates and recovers exact fixture bytes`() {
        val profile = "{\"word\":\"hello\"}".toByteArray()
        val audio = byteArrayOf(3, 2, 1)
        val result = ZipBackupPackageWriter().write(
            listOf(
                ZipBackupPackageWriter.Entry("records/profiles/profile-1.json", profile),
                ZipBackupPackageWriter.Entry("resources/audio", audio),
            ),
            createdAtEpochMillis = 1_700_000_000_000L,
        )
        val written = assertIs<ZipBackupPackageWriter.Result.Written>(result)

        assertIs<PackageValidationResult.Valid>(BackupPackageValidator().validate(written.bytes))
        val read = assertIs<PackageReadResult.Read>(BackupPackageValidator().readEntries(written.bytes))
        assertContentEquals(profile, read.entries.getValue("records/profiles/profile-1.json"))
        assertContentEquals(audio, read.entries.getValue("resources/audio"))
    }

    @Test
    fun `rejects unsafe path before writing`() {
        val result = ZipBackupPackageWriter().write(
            listOf(ZipBackupPackageWriter.Entry("records/profiles/../secret.json", byteArrayOf(1))),
            createdAtEpochMillis = 1_700_000_000_000L,
        )

        assertEquals(ZipBackupPackageWriter.Result.Invalid(PackageValidationFailure.InvalidPath), result)
    }

    @Test
    fun `rejects non-whitelisted record type before writing`() {
        val result = ZipBackupPackageWriter().write(
            listOf(ZipBackupPackageWriter.Entry("records/credentials/c-1.json", byteArrayOf(1))),
            createdAtEpochMillis = 1_700_000_000_000L,
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
            createdAtEpochMillis = 1_700_000_000_000L,
        )

        assertEquals(ZipBackupPackageWriter.Result.Invalid(PackageValidationFailure.DuplicateEntry), result)
    }

    @Test
    fun `rejects source over per entry limit`() {
        val limits = PackageLimits(maxArchiveBytes = 4096, maxEntries = 10, maxEntryBytes = 2, maxTotalBytes = 10)
        val result = ZipBackupPackageWriter(limits).write(
            listOf(ZipBackupPackageWriter.Entry("resources/audio", byteArrayOf(1, 2, 3))),
            createdAtEpochMillis = 1_700_000_000_000L,
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
            createdAtEpochMillis = 1_700_000_000_000L,
        )

        assertEquals(ZipBackupPackageWriter.Result.Invalid(PackageValidationFailure.LimitExceeded), result)
    }
}
