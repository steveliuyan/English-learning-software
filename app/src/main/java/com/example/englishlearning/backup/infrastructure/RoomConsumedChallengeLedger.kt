package com.example.englishlearning.backup.infrastructure

import com.example.englishlearning.backup.pairing.ConsumedChallengeLedger
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.MigrationPairingLedgerEntity
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * AC4-05 消费台账的 Room 实现：落库的只有 nonce 的 SHA-256 十六进制摘要
 * （列名 `nonceHash`），nonce/secret 明文永不出现在行、日志或备份包里。
 *
 * 存储异常如实上抛——调用方（导出 ViewModel）以 fail-closed 处置：读不到就不签，
 * 登记失败就作废已产包。
 */
class RoomConsumedChallengeLedger(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) : ConsumedChallengeLedger {

    override suspend fun markConsumed(nonce: ByteArray) {
        withContext(ioDispatcher) {
            database.internalMigrationPairingLedgerDao()
                .insert(MigrationPairingLedgerEntity(nonceHash(nonce), clockMillis()))
        }
    }

    override suspend fun isConsumed(nonce: ByteArray): Boolean = withContext(ioDispatcher) {
        database.internalMigrationPairingLedgerDao().countFor(nonceHash(nonce)) > 0
    }

    private fun nonceHash(nonce: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(nonce).joinToString("") { "%02x".format(it) }
}
