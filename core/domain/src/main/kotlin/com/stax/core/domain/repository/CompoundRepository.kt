package com.stax.core.domain.repository

import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.DataError
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.OpenedContainer
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Owns all reads and writes for [CompoundSupply] and its nested [OpenedContainer] (§3.1).
 *
 * All mutating operations that touch more than one table run inside a Room transaction (§5.8.5).
 */
interface CompoundRepository {

    /** Emits the active (non-deleted) compounds, each with its current opened container. */
    fun observeAll(): Flow<List<CompoundSupply>>

    /** Emits the compound with [id], or null if not found (includes soft-deleted rows). */
    fun observeById(id: Long): Flow<CompoundSupply?>

    /**
     * Inserts a new compound, its optional opened container, and an [InitialStock] inventory
     * transaction whose delta equals the total initial stock (§5.3, §5.8.5).
     *
     * @return the auto-generated ID of the new compound.
     */
    suspend fun create(compound: CompoundSupply): Result<Long, DataError.Local>

    /**
     * Updates the compound's own fields. Does not modify the opened container unless
     * [capOpenedContainer] is set.
     *
     * [capOpenedContainer] is §4.4.4's "Cap to new size": when the edit shrinks
     * `amountPerContainer` below what is left in the opened container, the caller may clamp the
     * remaining amount down to the new size, which records the difference as a `Manual` inventory
     * transaction so the ledger still sums to the physical stock (§5.8.0). Whether the container
     * actually holds more than the new size is the caller's question — this only does as told, and
     * does nothing at all when there is no opened container to cap.
     */
    suspend fun update(compound: CompoundSupply, capOpenedContainer: Boolean = false): EmptyResult<DataError.Local>

    /** Soft-deletes the compound by setting `deletedAt` (§5.5). */
    suspend fun archive(id: Long): EmptyResult<DataError.Local>

    /**
     * Creates a copy of the compound with a fresh ID, a `" (copy)"` name suffix, new timestamps,
     * no opened container, no batch number, and an [InitialStock] transaction matching the copied
     * container count (§4.2.4, §5.3).
     *
     * @return the auto-generated ID of the duplicate.
     */
    suspend fun duplicate(id: Long): Result<Long, DataError.Local>

    /**
     * Opens the next sealed container for this compound (§5.3, §5.8.5):
     * - Decrements `numberOfContainers` by 1.
     * - Creates an `OpenedContainer` with `remainingAmount = amountPerContainer`.
     * - Inserts a `ContainerOpen` (delta = 0) audit transaction.
     *
     * Returns [DataError.Local.NOT_FOUND] if the compound does not exist.
     * Returns [DataError.Local.CONSTRAINT_VIOLATION] if `numberOfContainers == 0` or an
     * opened container already exists.
     */
    suspend fun openContainer(id: Long): EmptyResult<DataError.Local>

    /**
     * Discards / closes the current opened container (§3.1.1 lost/discarded path):
     * - Deletes the `OpenedContainer` row without changing `numberOfContainers`.
     * - Inserts a `ContainerClose` (delta = 0) audit transaction.
     */
    suspend fun closeContainer(id: Long, reason: String?): EmptyResult<DataError.Local>

    /**
     * Edits mutable fields of the current opened container.
     * Only non-null parameters are applied; null means "leave unchanged".
     */
    suspend fun editOpenedContainer(
        compoundSupplyId: Long,
        remainingAmount: Quantity?,
        expiryAfterOpeningDays: Int?,
        userDefinedExpiryDate: LocalDate?,
    ): EmptyResult<DataError.Local>
}
