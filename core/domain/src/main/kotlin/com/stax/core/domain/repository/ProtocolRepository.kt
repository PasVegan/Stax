package com.stax.core.domain.repository

import com.stax.core.domain.DataError
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.Protocol
import com.stax.core.domain.Result
import kotlinx.coroutines.flow.Flow

/**
 * Owns all reads and writes for [Protocol] (§3.2, §5.2, §5.4, §5.5).
 *
 * Every save operation (create / update) triggers deletion of pending unlogged
 * [com.stax.core.domain.ScheduledDose] rows followed by fresh generation for the
 * upcoming 7-day horizon (§5.4 pending-regen scope rule, §5.2).
 * All multi-step operations run inside a Room transaction (§5.8.5).
 */
interface ProtocolRepository {

    /** Emits the active (non-deleted) protocols, each with its dosage times. */
    fun observeAll(): Flow<List<Protocol>>

    /** Emits the protocol with [id], or null if not found (includes soft-deleted rows). */
    fun observeById(id: Long): Flow<Protocol?>

    /** Emits active protocols linked to [compoundSupplyId]. */
    fun observeByCompoundSupplyId(compoundSupplyId: Long): Flow<List<Protocol>>

    /**
     * Inserts a new protocol, its dosage-time rows, and generates the initial
     * 7-day batch of [com.stax.core.domain.ScheduledDose] rows.
     *
     * @return the auto-generated ID of the new protocol.
     */
    suspend fun create(protocol: Protocol): Result<Long, DataError.Local>

    /**
     * Persists changes to an existing protocol.
     *
     * Pending-regen scope rule (§5.4):
     * 1. Delete all `Pending + unlogged` ScheduledDoses for this protocol.
     * 2. Regenerate a fresh 7-day batch.
     * Historical doses (Taken / Skipped / Missed / Partial) are never touched.
     */
    suspend fun update(protocol: Protocol): EmptyResult<DataError.Local>

    /** Soft-deletes the protocol by setting `deletedAt` and purges pending doses (§5.5). */
    suspend fun archive(id: Long): EmptyResult<DataError.Local>

    /** Sets `status = Paused`. No dose generation while paused. */
    suspend fun pause(id: Long): EmptyResult<DataError.Local>

    /**
     * Sets `status = Active` and regenerates the 7-day horizon of pending doses
     * (protocol was not generating while paused).
     */
    suspend fun resume(id: Long): EmptyResult<DataError.Local>

    /** Sets `status = Completed` and purges remaining pending doses. */
    suspend fun complete(id: Long): EmptyResult<DataError.Local>
}
