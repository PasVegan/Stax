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

    /**
     * A compound's dose history, newest first (§4.3.8).
     *
     * Driven from `dose_component` because that is the table that knows about compounds; an event
     * logging two compounds at once (§4.10.3) therefore appears once in each of their histories, with
     * only its own component's dose. The site join is a LEFT one — an oral or skipped dose has none.
     */
    @Query(
        """
        SELECT e.id AS eventId, e.loggedAt AS loggedAt, e.status AS status,
            c.actualDoseValue AS actualDoseValue, c.actualDoseUnit AS actualDoseUnit,
            c.concentrationAmountValue AS concentrationAmountValue,
            c.concentrationAmountUnit AS concentrationAmountUnit,
            c.concentrationPerValue AS concentrationPerValue,
            c.concentrationPerUnit AS concentrationPerUnit,
            s.name AS injectionSiteName
        FROM dose_component c
        INNER JOIN administration_event e ON e.id = c.administrationEventId
        LEFT JOIN injection_site s ON s.id = e.injectionSiteId
        WHERE c.compoundSupplyId = :compoundSupplyId
        ORDER BY e.loggedAt DESC, e.id DESC
        """,
    )
    fun observeHistoryForCompound(compoundSupplyId: Long): Flow<List<CompoundHistoryRow>>
}
