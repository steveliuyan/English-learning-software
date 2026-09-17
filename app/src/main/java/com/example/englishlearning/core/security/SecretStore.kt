package com.example.englishlearning.core.security

data class SecretReference(val alias: String)

/** Stores credentials without exposing plaintext through the business layer. */
interface SecretStore {
    fun save(
        reference: SecretReference,
        secret: CharArray,
    ): Result<Unit>

    fun delete(reference: SecretReference): Result<Unit>

    fun has(reference: SecretReference): Result<Boolean>
}
