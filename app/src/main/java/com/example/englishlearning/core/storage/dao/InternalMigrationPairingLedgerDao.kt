package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.englishlearning.core.storage.entity.MigrationPairingLedgerEntity

@Dao
interface InternalMigrationPairingLedgerDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: MigrationPairingLedgerEntity)

    @Query("SELECT COUNT(*) FROM migration_pairing_ledger WHERE nonceHash = :nonceHash")
    suspend fun countFor(nonceHash: String): Int
}
