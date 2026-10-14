package com.example.englishlearning.backup.pairing

/**
 * AC4-05「已使用的配对凭据不得再次签发包」的消费台账端口（决策文档 §2.3 预留的
 * `migration_pairing_ledger` 的落地形态）。
 *
 * 语义：一次成功的受保护导出产包即视为该挑战 nonce 已消费；此后任何用同一 nonce
 * 再签新包的请求都必须被拒绝，用户须重新完成系统认证获取新配对码。
 *
 * 持久化侧只允许落 nonce 的单向摘要：secret 是 QR-only 机密、连 nonce 明文也不入账，
 * 台账泄漏不构成配对能力泄漏。
 */
interface ConsumedChallengeLedger {
    suspend fun markConsumed(nonce: ByteArray)

    suspend fun isConsumed(nonce: ByteArray): Boolean
}
