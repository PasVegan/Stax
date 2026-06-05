package com.stax.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface ScheduledDoseDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(entity: ScheduledDoseEntity): Long

    @Query(
        """
        SELECT * FROM scheduled_dose
        WHERE protocolId = :protocolId
        ORDER BY scheduledAt ASC
        """,
    )
    fun observeByProtocolId(protocolId: Long): Flow<List<ScheduledDoseEntity>>

    @Query(
        """
        SELECT * FROM scheduled_dose
        WHERE protocolId = :protocolId
            AND status = 'PENDING'
            AND administrationEventId IS NULL
        ORDER BY scheduledAt ASC
        """,
    )
    fun observePendingByProtocolId(protocolId: Long): Flow<List<ScheduledDoseEntity>>

    @Transaction
    @Query(
        """
        DELETE FROM scheduled_dose
        WHERE protocolId = :protocolId
            AND status = 'PENDING'
            AND administrationEventId IS NULL
        """,
    )
    suspend fun deletePendingUnloggedForProtocol(protocolId: Long): Int

    @Query(
        """
        SELECT * FROM scheduled_dose
        WHERE scheduledAt >= :from
            AND scheduledAt < :until
        ORDER BY scheduledAt ASC
        """,
    )
    fun observeInRange(from: Instant, until: Instant): Flow<List<ScheduledDoseEntity>>

    @Query(
        """
        UPDATE scheduled_dose
        SET scheduledAt = :newScheduledAt
        WHERE id = :id
        """,
    )
    suspend fun updateScheduledAt(id: Long, newScheduledAt: Instant): Int
}
