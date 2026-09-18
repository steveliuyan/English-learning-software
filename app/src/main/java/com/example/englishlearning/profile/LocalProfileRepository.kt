package com.example.englishlearning.profile

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.LocalProfileEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant

data class LocalProfile(val id: String, val displayName: String, val createdAt: Instant = Instant.EPOCH)

interface LocalProfileRepository {
    suspend fun getDefault(): LocalProfile?
    suspend fun save(profile: LocalProfile)
}

class RoomLocalProfileRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : LocalProfileRepository {
    override suspend fun getDefault(): LocalProfile? = withContext(ioDispatcher) { database.internalProfileDao().findById(DEFAULT_ID)?.toDomain() }
    override suspend fun save(profile: LocalProfile) = withContext(ioDispatcher) { database.internalProfileDao().upsert(LocalProfileEntity(profile.id, profile.displayName, profile.createdAt.toEpochMilli())) }
    private fun LocalProfileEntity.toDomain() = LocalProfile(id, displayName, Instant.ofEpochMilli(createdAt))
    companion object { const val DEFAULT_ID = "default" }
}

class InMemoryLocalProfileRepository : LocalProfileRepository {
    private var profile: LocalProfile? = null
    override suspend fun getDefault(): LocalProfile? = profile
    override suspend fun save(profile: LocalProfile) { this.profile = profile }
}
