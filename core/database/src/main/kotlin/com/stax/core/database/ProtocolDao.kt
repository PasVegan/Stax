package com.stax.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface ProtocolDao {

    @Insert
    suspend fun insert(entity: ProtocolEntity): Long

    @Update
    suspend fun update(entity: ProtocolEntity): Int

    @Query("UPDATE protocol SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Instant): Int

    @Query("UPDATE protocol SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ProtocolStatus, updatedAt: Instant): Int

    @Query("SELECT * FROM protocol WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<ProtocolEntity?>

    /** Non-Flow point read for use inside transactions. */
    @Query("SELECT * FROM protocol WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProtocolEntity?

    @Transaction
    @Query(
        """
        SELECT * FROM protocol
        WHERE deletedAt IS NULL
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeActiveWithDosageTimes(): Flow<List<ProtocolWithDosageTimes>>

    @Transaction
    @Query("SELECT * FROM protocol WHERE id = :id LIMIT 1")
    fun observeByIdWithDosageTimes(id: Long): Flow<ProtocolWithDosageTimes?>

    @Transaction
    @Query(
        """
        SELECT * FROM protocol
        WHERE compoundSupplyId = :compoundSupplyId
            AND deletedAt IS NULL
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeByCompoundSupplyIdWithDosageTimes(compoundSupplyId: Long): Flow<List<ProtocolWithDosageTimes>>
}
