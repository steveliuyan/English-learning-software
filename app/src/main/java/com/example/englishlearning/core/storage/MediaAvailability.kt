package com.example.englishlearning.core.storage

sealed interface MediaAvailability {
    data object Available : MediaAvailability

    data object UnavailableRebuildable : MediaAvailability
}

data class AssetRecord(
    val id: String,
    val sha256: String,
)
