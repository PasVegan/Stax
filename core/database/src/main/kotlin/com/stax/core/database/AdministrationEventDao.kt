package com.stax.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface AdministrationEventDao {

    @Insert
    suspend fun insert(entity: AdministrationEventEntity): Long

    @Update
    suspend fun update(entity: AdministrationEventEntity): Int

    @Query("SELECT * FROM administration_event WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AdministrationEventEntity?

    @Query("DELETE FROM administration_event WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query(
        """
        SELECT * FROM administration_event
        WHERE id = :id
        LIMIT 1
        """,
    )
    fun observeById(id: Long): Flow<AdministrationEventEntity?>

    @Query(
        """
        SELECT * FROM administration_event
        WHERE loggedAt >= :from
            AND loggedAt < :until
        ORDER BY loggedAt DESC
        """,
    )
    fun observeInRange(from: Instant, until: Instant): Flow<List<AdministrationEventEntity>>

    @Query(
        """
        SELECT * FROM administration_event
        WHERE injectionSiteId = :injectionSiteId
        ORDER BY loggedAt DESC
        """,
    )
    fun observeByInjectionSite(injectionSiteId: Long): Flow<List<AdministrationEventEntity>>

    @Query(
        """
        SELECT * FROM administration_event
        WHERE injectionSiteId = :injectionSiteId
        ORDER BY loggedAt DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestByInjectionSite(injectionSiteId: Long): AdministrationEventEntity?
}
