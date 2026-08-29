package com.stax.core.domain.repository

import androidx.paging.PagingData
import com.stax.core.domain.AdministrationEvent
import com.stax.core.domain.AdministrationEventStatus
import com.stax.core.domain.CompoundHistoryEntry
import com.stax.core.domain.DataError
import com.stax.core.domain.DoseComponent
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.Result
import com.stax.core.domain.Route
import com.stax.core.domain.SiteUse
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

data class AdministrationEventEdit(
    val loggedAt: Instant,
    val route: Route,
    val status: AdministrationEventStatus,
    val injectionSiteId: Long?,
    val notes: String?,
    val components: List<DoseComponent>,
)

/** Coordinates administration logs, component snapshots, inventory, and site cooldowns (§3.4, §5.3). */
interface AdministrationEventRepository {

    /**
     * Emits one compound's dose history as pages, newest first (§4.3.8), narrowed to [status] when
     * §4.3.7's chip picked one — null is the All chip.
     *
     * One entry per dose component naming the compound, not per event: a multi-compound log (§4.10.3)
     * belongs in both histories, and each shows only the dose that was its own.
     *
     * Paged rather than listed because a history has no upper bound: §2.3.2's scroll SLO is met by
     * reading a window of it, and the status filter belongs in the query for the same reason.
     */
    fun pagedHistoryForCompound(
        compoundSupplyId: Long,
        status: AdministrationEventStatus?,
    ): Flow<PagingData<CompoundHistoryEntry>>

    /** §4.3.6's badge: this compound's Taken + Partial components, all-time and chip-independent. */
    fun observeLoggedDoseCount(compoundSupplyId: Long): Flow<Int>

    /**
     * Emits one protocol's dose history as pages, newest first (§4.8.7) — the doses logged against
     * this protocol, whichever compound each named.
     *
     * Paged for the same reason the compound history is, but unfiltered: §4.8.7 carries the header
     * and the rows of §4.3.6/§4.3.8 without §4.3.7's status chips.
     */
    fun pagedHistoryForProtocol(protocolId: Long): Flow<PagingData<CompoundHistoryEntry>>

    /** §4.8.7's badge: this protocol's Taken + Partial components, all-time. */
    fun observeLoggedDoseCountForProtocol(protocolId: Long): Flow<Int>

    /**
     * The doses that named an injection site in `[from, until)`, newest first (§4.12.3).
     *
     * Site-bearing only, because that is the question both callers ask: §4.12.3's "This month" tile
     * counts them, and §4.12.4's heat map weighs them per site. A dose with no site — oral, topical,
     * or one logged without picking one — is not a use of anything.
     */
    fun observeSiteUsesBetween(from: Instant, until: Instant): Flow<List<SiteUse>>

    suspend fun log(event: AdministrationEvent, components: List<DoseComponent>): Result<Long, DataError.Local>

    suspend fun edit(id: Long, edits: AdministrationEventEdit): EmptyResult<DataError.Local>

    suspend fun delete(id: Long): EmptyResult<DataError.Local>
}
