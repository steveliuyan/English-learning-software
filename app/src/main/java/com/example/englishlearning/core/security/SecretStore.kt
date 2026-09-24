package com.example.englishlearning.core.security

data class SecretReference(val alias: String)

/** Stores credentials without exposing plaintext through the business layer. */
interface SecretStore {
    fun save(
        reference: SecretReference,
        secret: CharArray,
    ): Result<Unit>

    /**
     * 读回先前 [save] 的内容。
     *
     * **返回的数组由调用方负责清零**（`result.getOrThrow().fill('\u0000')`）：它是专为这一次读取新建的，
     * 不会被本存储保留，所以清零它不会影响后续读取。这条约定让密钥明文在内存里的存活窗口尽可能短。
     *
     * 失败一律是 `AppError.KeyStoreUnavailable`：密文缺失、密文被篡改、密钥条目丢失都归为同一类，
     * 因为对用户的处置相同（重新录入）。**不要试图区分**，误导性的区分比不区分更糟。
     */
    fun read(reference: SecretReference): Result<CharArray>

    fun delete(reference: SecretReference): Result<Unit>

    fun has(reference: SecretReference): Result<Boolean>
}
