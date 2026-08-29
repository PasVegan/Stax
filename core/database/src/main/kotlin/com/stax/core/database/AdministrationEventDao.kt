package com.stax.core.database

import androidx.paging.PagingSource
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
     * A compound's dose history as pages, newest first (§4.3.8), optionally narrowed to the one
     * status §4.3.7's chip picked — a null [status] is the All chip and constrains nothing.
     *
     * Driven from `dose_component` because that is the table that knows about compounds; an event
     * logging two compounds at once (§4.10.3) therefore appears once in each of their histories, with
     * only its own component's dose. The site join is a LEFT one — an oral or skipped dose has none.
     *
     * A `PagingSource` rather than a `Flow<List<…>>`: a history is unbounded, and §2.3.2's scroll SLO
     * is about how many rows are read and composed at once, not about how many exist. The chip is a
     * SQL predicate for the same reason — filtering in memory would mean loading everything to throw
     * most of it away.
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
            AND (:status IS NULL OR e.status = :status)
        ORDER BY e.loggedAt DESC, e.id DESC
        """,
    )
    fun historyPagingSourceForCompound(
        compoundSupplyId: Long,
        status: AdministrationEventStatus?,
    ): PagingSource<Int, CompoundHistoryRow>

    /**
     * §4.3.6's badge: Taken + Partial components for this compound, all-time.
     *
     * Counted in SQL rather than off the history list, which no longer exists whole in memory — and
     * the badge is deliberately unmoved by §4.3.7's chip, so it was never the same question anyway.
     */
    @Query(
        """
        SELECT COUNT(*)
        FROM dose_component c
        INNER JOIN administration_event e ON e.id = c.administrationEventId
        WHERE c.compoundSupplyId = :compoundSupplyId
            AND e.status != 'SKIPPED'
        """,
    )
    fun observeLoggedDoseCountForCompound(compoundSupplyId: Long): Flow<Int>

    /**
     * One protocol's dose history as pages, newest first (§4.8.7).
     *
     * The compound query's twin, scoped by `dose_component.protocolId` instead: a dose logged against
     * this protocol, whichever compound it named. No status parameter, because §4.8.7 has no filter
     * chips — Protocol Detail shows the protocol's whole history.
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
        WHERE c.protocolId = :protocolId
        ORDER BY e.loggedAt DESC, e.id DESC
        """,
    )
    fun historyPagingSourceForProtocol(protocolId: Long): PagingSource<Int, CompoundHistoryRow>

    /** §4.8.7's badge: Taken + Partial components logged against this protocol, all-time. */
    @Query(
        """
        SELECT COUNT(*)
        FROM dose_component c
        INNER JOIN administration_event e ON e.id = c.administrationEventId
        WHERE c.protocolId = :protocolId
            AND e.status != 'SKIPPED'
        """,
    )
    fun observeLoggedDoseCountForProtocol(protocolId: Long): Flow<Int>
}
