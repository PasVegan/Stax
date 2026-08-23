package com.stax.core.domain.repository

import com.stax.core.domain.AdministrationEvent
import com.stax.core.domain.AdministrationEventStatus
import com.stax.core.domain.CompoundHistoryEntry
import com.stax.core.domain.DataError
import com.stax.core.domain.DoseComponent
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.Result
import com.stax.core.domain.Route
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
     * Emits one compound's dose history, newest first (§4.3.8).
     *
     * One entry per dose component naming the compound, not per event: a multi-compound log (§4.10.3)
     * belongs in both histories, and each shows only the dose that was its own.
     */
    fun observeForCompound(compoundSupplyId: Long): Flow<List<CompoundHistoryEntry>>

    suspend fun log(event: AdministrationEvent, components: List<DoseComponent>): Result<Long, DataError.Local>

    suspend fun edit(id: Long, edits: AdministrationEventEdit): EmptyResult<DataError.Local>

    suspend fun delete(id: Long): EmptyResult<DataError.Local>
}
