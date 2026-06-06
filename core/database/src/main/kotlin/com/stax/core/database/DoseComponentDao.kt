package com.stax.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseComponentDao {

    @Insert
    suspend fun insert(entity: DoseComponentEntity): Long

    @Insert
    suspend fun insertAll(entities: List<DoseComponentEntity>)

    @Query(
        """
        SELECT * FROM dose_component
        WHERE administrationEventId = :administrationEventId
        """,
    )
    fun observeByAdministrationEventId(administrationEventId: Long): Flow<List<DoseComponentEntity>>

    @Query(
        """
        SELECT * FROM dose_component
        WHERE administrationEventId = :administrationEventId
        """,
    )
    suspend fun getByAdministrationEventId(administrationEventId: Long): List<DoseComponentEntity>

    @Query(
        """
        DELETE FROM dose_component
        WHERE administrationEventId = :administrationEventId
        """,
    )
    suspend fun deleteByAdministrationEventId(administrationEventId: Long): Int

    @Query(
        """
        SELECT * FROM dose_component
        WHERE scheduledDoseId = :scheduledDoseId
        LIMIT 1
        """,
    )
    suspend fun findByScheduledDoseId(scheduledDoseId: Long): DoseComponentEntity?
}
