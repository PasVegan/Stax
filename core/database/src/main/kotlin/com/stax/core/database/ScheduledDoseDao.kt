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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertManyOrIgnore(entities: List<ScheduledDoseEntity>): List<Long>

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

    /**
     * The earliest still-pending dose of every protocol that has one — the next-dose chip of every
     * card on the protocols list (§4.7.3) in a single read, rather than one query per row.
     *
     * The correlated `MIN` is the greatest-n-per-group form; `GROUP BY` then collapses the tie two
     * rows sharing a `scheduledAt` would produce.
     */
    @Query(
        """
        SELECT * FROM scheduled_dose AS d
        WHERE d.status = 'PENDING'
            AND d.administrationEventId IS NULL
            AND d.scheduledAt = (
                SELECT MIN(scheduledAt) FROM scheduled_dose
                WHERE protocolId = d.protocolId
                    AND status = 'PENDING'
                    AND administrationEventId IS NULL
            )
        GROUP BY d.protocolId
        """,
    )
    fun observeNextPendingPerProtocol(): Flow<List<ScheduledDoseEntity>>

    @Query(
        """
        SELECT * FROM scheduled_dose
        WHERE status = 'PENDING'
            AND administrationEventId IS NULL
            AND scheduledAt >= :from
            AND scheduledAt < :until
        ORDER BY
            CASE WHEN hasTimeOfDay THEN 0 ELSE 1 END ASC,
            scheduledAt ASC
        """,
    )
    fun observePendingForDate(from: Instant, until: Instant): Flow<List<ScheduledDoseEntity>>

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

    @Query(
        """
        SELECT * FROM scheduled_dose
        WHERE id = :id
        """,
    )
    suspend fun getById(id: Long): ScheduledDoseEntity?

    @Query(
        """
        UPDATE scheduled_dose
        SET scheduledAt = :newScheduledAt
        WHERE id = :id
            AND status = 'PENDING'
            AND administrationEventId IS NULL
        """,
    )
    suspend fun updatePendingScheduledAt(id: Long, newScheduledAt: Instant): Int

    @Query(
        """
        UPDATE scheduled_dose
        SET status = :status,
            administrationEventId = :administrationEventId
        WHERE id = :id
            AND status = 'PENDING'
            AND administrationEventId IS NULL
        """,
    )
    suspend fun updatePendingStatus(id: Long, status: ScheduledDoseStatus, administrationEventId: Long?): Int

    @Query(
        """
        UPDATE scheduled_dose
        SET status = 'PENDING',
            administrationEventId = NULL
        WHERE id = :id
            AND administrationEventId = :administrationEventId
        """,
    )
    suspend fun resetLinkedEvent(id: Long, administrationEventId: Long): Int
}
