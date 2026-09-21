package com.example.englishlearning.reading

sealed interface ReadingAccess {
    data class Unlocked(val planId: String) : ReadingAccess

    data class Locked(
        val missingNew: Int,
        val missingDue: Int,
        val strict: Boolean,
    ) : ReadingAccess
}
