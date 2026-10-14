package com.example.englishlearning.backup.infrastructure

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.AppDatabase
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AC4-05 消费台账的 Room 实现走查（真库 Room，非 mock）：
 * 消费过的 nonce 可回读、未消费的保持未消费、重复登记幂等，
 * 且落库列只能是 nonce 的 SHA-256 十六进制摘要——nonce/secret 明文永不进行。
 */
@RunWith(AndroidJUnit4::class)
class RoomConsumedChallengeLedgerTest {

    private fun newLedger(): Pair<AppDatabase, RoomConsumedChallengeLedger> {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
        return database to RoomConsumedChallengeLedger(database, Dispatchers.IO) { 1_700_000_000_000L }
    }

    @Test
    fun consumedNonceReadsBackTrueWhileOtherNoncesStayUnconsumed() = runBlocking {
        val (database, ledger) = newLedger()
        try {
            val nonce = ByteArray(16) { (it + 1).toByte() }
            assertFalse(ledger.isConsumed(nonce))
            ledger.markConsumed(nonce)
            assertTrue(ledger.isConsumed(nonce))
            assertFalse(ledger.isConsumed(ByteArray(16) { (it + 2).toByte() }))
        } finally {
            database.close()
        }
    }

    @Test
    fun markingTheSameNonceTwiceStaysIdempotent() = runBlocking {
        val (database, ledger) = newLedger()
        try {
            val nonce = ByteArray(16) { it.toByte() }
            ledger.markConsumed(nonce)
            ledger.markConsumed(nonce)
            database.openHelper.writableDatabase
                .query("SELECT COUNT(*) FROM migration_pairing_ledger")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("重复消费同一 nonce 不得产生第二行", 1, cursor.getInt(0))
                }
        } finally {
            database.close()
        }
    }

    @Test
    fun rowStoresOnlyTheSha256HexDigestOfTheNonce() = runBlocking {
        val (database, ledger) = newLedger()
        try {
            val nonce = ByteArray(16) { (it * 7 + 3).toByte() }
            ledger.markConsumed(nonce)
            val expectedHex = MessageDigest.getInstance("SHA-256").digest(nonce)
                .joinToString("") { "%02x".format(it) }
            val rawNonceHex = nonce.joinToString("") { "%02x".format(it) }
            database.openHelper.writableDatabase
                .query("SELECT nonceHash, consumedAtEpochMillis FROM migration_pairing_ledger")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    val stored = cursor.getString(0)
                    assertEquals(expectedHex, stored)
                    assertEquals(64, stored.length)
                    assertFalse("落库值必须是摘要，不能是 nonce 本身的十六进制", stored == rawNonceHex)
                    assertEquals(1_700_000_000_000L, cursor.getLong(1))
                    assertFalse("表里只应有一行", cursor.moveToNext())
                }
        } finally {
            database.close()
        }
    }
}
