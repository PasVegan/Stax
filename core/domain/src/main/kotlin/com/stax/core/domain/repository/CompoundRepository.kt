package com.stax.core.domain.repository

import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.DataError
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.OpenedContainer
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

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
     * §4.5.5 "Create Already Opened": the same operation as [openContainer], but for a container the
     * user opened before the app knew about it — so the opened date, what is left in it and its
     * expiry are theirs to state rather than derived from now and a full container.
     *
     * Books the difference between [remainingAmount] and a full container as a `Manual` transaction,
     * because §5.8.0 requires the ledger to sum to the physical stock and a part-used container
     * brings in less than a sealed one. `predictedExpiryDate` is derived here (§3.1.1), never passed.
     *
     * Same failure modes as [openContainer].
     */
    suspend fun addOpenedContainer(
        compoundSupplyId: Long,
        openedAt: Instant,
        remainingAmount: Quantity,
        expiryAfterOpeningDays: Int?,
        userDefinedExpiryDate: LocalDate?,
    ): EmptyResult<DataError.Local>

    /**
     * Discards / closes the current opened container (§3.1.1 lost/discarded path):
     * - Deletes the `OpenedContainer` row without changing `numberOfContainers`.
     * - Books whatever was still in it as a `Manual` transaction, so the ledger loses the stock that
     *   physically left with the container (§5.8.0). Nothing is booked for an empty container, which
     *   is the natural-depletion path — the deduction that emptied it is already in the ledger.
     * - Inserts a `ContainerClose` (delta = 0) audit transaction.
     */
    suspend fun closeContainer(id: Long, reason: String?): EmptyResult<DataError.Local>

    /**
     * Edits mutable fields of the current opened container (§4.5.5 Edit).
     * Only non-null parameters are applied; null means "leave unchanged".
     *
     * `predictedExpiryDate` is re-derived from the resulting `openedAt + expiryAfterOpeningDays`
     * (§3.1.1), and a changed [remainingAmount] books the difference as a `Manual` transaction so
     * the ledger keeps summing to the physical stock (§5.8.0).
     */
    suspend fun editOpenedContainer(
        compoundSupplyId: Long,
        openedAt: Instant?,
        remainingAmount: Quantity?,
        expiryAfterOpeningDays: Int?,
        userDefinedExpiryDate: LocalDate?,
    ): EmptyResult<DataError.Local>
}
