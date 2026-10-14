package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AC4-05 已消费配对码台账行（决策文档 §2.3 预留的 `migration_pairing_ledger`）。
 *
 * 只存 nonce 的 SHA-256 摘要：secret 是 QR-only 机密，连 nonce 明文都不入账，
 * 台账本身泄露不构成任何配对能力。行不参与备份导出（新设备从空台账开始）。
 */
@Entity(tableName = "migration_pairing_ledger")
data class MigrationPairingLedgerEntity(
    @PrimaryKey val nonceHash: String,
    val consumedAtEpochMillis: Long,
)
