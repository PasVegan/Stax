package com.stax.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface CompoundSupplyDao {

    @Insert
    suspend fun insert(entity: CompoundSupplyEntity): Long

    @Update
    suspend fun update(entity: CompoundSupplyEntity): Int

    @Query("UPDATE compound_supply SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Instant): Int

    @Query(
        """
        SELECT * FROM compound_supply
        WHERE deletedAt IS NULL
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeActive(): Flow<List<CompoundSupplyEntity>>

    @Query("SELECT * FROM compound_supply WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<CompoundSupplyEntity?>

    @Query(
        """
        SELECT * FROM compound_supply
        WHERE deletedAt IS NULL
            AND numberOfContainers <= 0
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeLowStock(): Flow<List<CompoundSupplyEntity>>

    @Query(
        """
        SELECT * FROM compound_supply
        WHERE deletedAt IS NULL
            AND batchExpiryDate IS NOT NULL
            AND batchExpiryDate <= date('now', '+' || :days || ' days')
        ORDER BY batchExpiryDate ASC, name COLLATE NOCASE ASC
        """,
    )
    fun observeExpiringSoon(days: Int): Flow<List<CompoundSupplyEntity>>
}
