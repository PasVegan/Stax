package com.stax.core.data.repository

import androidx.room.withTransaction
import com.stax.core.data.mapper.toDomain
import com.stax.core.data.mapper.toEntity
import com.stax.core.database.CompoundSupplyDao
import com.stax.core.database.CompoundSupplyEntity
import com.stax.core.database.InventoryTransactionDao
import com.stax.core.database.InventoryTransactionEntity
import com.stax.core.database.InventoryTransactionType
import com.stax.core.database.OpenedContainerDao
import com.stax.core.database.OpenedContainerEntity
import com.stax.core.database.StaxDatabase
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.DataError
import com.stax.core.domain.Decimal
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.UnitCode
import com.stax.core.domain.repository.CompoundRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class RoomCompoundRepository(
    private val database: StaxDatabase,
    private val compoundDao: CompoundSupplyDao,
    private val openedContainerDao: OpenedContainerDao,
    private val inventoryDao: InventoryTransactionDao,
) : CompoundRepository {

    // -----------------------------------------------------------------------
    // Observe
    // -----------------------------------------------------------------------

    override fun observeAll(): Flow<List<CompoundSupply>> = compoundDao.observeActiveWithOpened().map { list ->
        list.map { it.compound.toDomain(it.opened) }
    }

    override fun observeById(id: Long): Flow<CompoundSupply?> = compoundDao.observeByIdWithOpened(id).map { row ->
        row?.compound?.toDomain(row.opened)
    }

    // -----------------------------------------------------------------------
    // Create  (§5.8.5 transaction boundary)
    // -----------------------------------------------------------------------

    override suspend fun create(compound: CompoundSupply): Result<Long, DataError.Local> = runTx {
        val now = Clock.System.now()
        val entity = compound.toEntity().copy(id = 0, createdAt = now, updatedAt = now)
        val newId = compoundDao.insert(entity)

        compound.currentOpened?.let { opened ->
            openedContainerDao.insert(opened.toEntity(id = 0, compoundSupplyId = newId))
        }

        val openedRemaining = compound.currentOpened?.remainingAmount?.value
            ?: Decimal.parse("0")
        val closedStock = compound.amountPerContainer.value *
            Decimal.parse(compound.numberOfContainers.toString())

        inventoryDao.insert(
            InventoryTransactionEntity(
                compoundSupplyId = newId,
                deltaValue = closedStock + openedRemaining,
                deltaUnit = compound.amountPerContainer.unit,
                type = InventoryTransactionType.INITIAL_STOCK,
                sourceEventId = null,
                reason = null,
                at = now,
            ),
        )
        newId
    }

    // -----------------------------------------------------------------------
    // Update
    // -----------------------------------------------------------------------

    override suspend fun update(compound: CompoundSupply, capOpenedContainer: Boolean): EmptyResult<DataError.Local> =
        runTx {
            val now = Clock.System.now()
            val rows = compoundDao.update(compound.toEntity().copy(updatedAt = now))
            if (rows == 0) throw NotFoundException()
            if (capOpenedContainer) clampOpenedContainer(compound, now)
        }

    /**
     * §4.4.4's "Cap to new size": clamps the opened container down to the compound's new
     * `amountPerContainer` and books the amount that disappears as a `Manual` transaction, so the
     * ledger keeps summing to the physical stock (§5.8.0).
     *
     * The old remaining is read in the container's own unit and converted, because the same edit may
     * have changed the unit as well as the amount. Nothing to cap is not a failure: the compound's
     * own update still stands.
     */
    private suspend fun clampOpenedContainer(compound: CompoundSupply, now: Instant) {
        val opened = openedContainerDao.getByCompoundSupplyId(compound.id) ?: return
        val newAmount = compound.amountPerContainer
        val oldRemaining = opened.remainingAmountUnit.convertTo(newAmount.unit, opened.remainingAmountValue)

        openedContainerDao.update(
            opened.copy(
                remainingAmountValue = newAmount.value,
                remainingAmountUnit = newAmount.unit,
            ),
        )
        inventoryDao.insert(
            InventoryTransactionEntity(
                compoundSupplyId = compound.id,
                deltaValue = newAmount.value - oldRemaining,
                deltaUnit = newAmount.unit,
                type = InventoryTransactionType.MANUAL,
                sourceEventId = null,
                reason = SIZE_REDUCED_REASON,
                at = now,
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Archive (soft-delete §5.5)
    // -----------------------------------------------------------------------

    override suspend fun archive(id: Long): EmptyResult<DataError.Local> = runOp {
        val rows = compoundDao.softDelete(id, Clock.System.now())
        if (rows == 0) throw NotFoundException()
    }

    // -----------------------------------------------------------------------
    // Duplicate  (§5.3 InitialStock for copy)
    // -----------------------------------------------------------------------

    override suspend fun duplicate(id: Long): Result<Long, DataError.Local> = runTx {
        val original = compoundDao.getById(id) ?: throw NotFoundException()
        val now = Clock.System.now()

        val newId = compoundDao.insert(
            original.copy(
                id = 0,
                name = original.name + COPY_SUFFIX,
                // A batch number identifies one physical batch, so it cannot be shared by two rows
                // the user is meant to tell apart; the copy starts without one (§4.2.4).
                batchNumber = null,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
        val closedStock = original.amountPerContainerValue *
            Decimal.parse(original.numberOfContainers.toString())

        inventoryDao.insert(
            InventoryTransactionEntity(
                compoundSupplyId = newId,
                deltaValue = closedStock,
                deltaUnit = original.amountPerContainerUnit,
                type = InventoryTransactionType.INITIAL_STOCK,
                sourceEventId = null,
                reason = null,
                at = now,
            ),
        )
        newId
    }

    // -----------------------------------------------------------------------
    // Open container  (§5.3, §5.8.5)
    // -----------------------------------------------------------------------

    override suspend fun openContainer(id: Long): EmptyResult<DataError.Local> = runTx {
        val compound = compoundDao.getById(id) ?: throw NotFoundException()
        openNextContainer(
            compound = compound,
            openedAt = Clock.System.now(),
            remainingAmount = Quantity(compound.amountPerContainerValue, compound.amountPerContainerUnit),
            expiryAfterOpeningDays = compound.expiryAfterOpeningDays,
            userDefinedExpiryDate = null,
        )
    }

    override suspend fun addOpenedContainer(
        compoundSupplyId: Long,
        openedAt: Instant,
        remainingAmount: Quantity,
        expiryAfterOpeningDays: Int?,
        userDefinedExpiryDate: LocalDate?,
    ): EmptyResult<DataError.Local> = runTx {
        val compound = compoundDao.getById(compoundSupplyId) ?: throw NotFoundException()
        openNextContainer(
            compound = compound,
            openedAt = openedAt,
            remainingAmount = remainingAmount,
            expiryAfterOpeningDays = expiryAfterOpeningDays,
            userDefinedExpiryDate = userDefinedExpiryDate,
        )
    }

    /**
     * The one container-opening operation §5.3 describes, whether the container is being opened now
     * or was opened before the app knew about it (§4.5.5).
     *
     * The `ContainerOpen` row stays the delta-0 audit marker §5.3 defines — a sealed container moving
     * into the opened pool changes no stock. What *is* stock is the gap between a full container and
     * what the user says is left in this one, and that goes in as a `Manual` transaction so the
     * ledger still sums to the physical stock (§5.8.0). Opening a fresh container leaves no gap and
     * so writes no such row.
     */
    private suspend fun openNextContainer(
        compound: CompoundSupplyEntity,
        openedAt: Instant,
        remainingAmount: Quantity,
        expiryAfterOpeningDays: Int?,
        userDefinedExpiryDate: LocalDate?,
    ) {
        if (compound.numberOfContainers <= 0) throw ConstraintException()
        if (openedContainerDao.getByCompoundSupplyId(compound.id) != null) throw ConstraintException()

        val now = Clock.System.now()
        compoundDao.update(
            compound.copy(numberOfContainers = compound.numberOfContainers - 1, updatedAt = now),
        )
        openedContainerDao.insert(
            OpenedContainerEntity(
                compoundSupplyId = compound.id,
                openedAt = openedAt,
                remainingAmountValue = remainingAmount.value,
                remainingAmountUnit = remainingAmount.unit,
                expiryAfterOpeningDays = expiryAfterOpeningDays,
                userDefinedExpiryDate = userDefinedExpiryDate,
                predictedExpiryDate = predictedExpiryOf(openedAt, expiryAfterOpeningDays),
            ),
        )
        inventoryDao.insert(
            audit(compound.id, InventoryTransactionType.CONTAINER_OPEN, compound.amountPerContainerUnit, now),
        )
        val alreadyUsed = remainingAmount.unit.convertTo(compound.amountPerContainerUnit, remainingAmount.value) -
            compound.amountPerContainerValue
        // `!= ZERO` would compare `BigDecimal` scales, where `0` and `0.00` are two different values.
        if (alreadyUsed.raw.signum() != 0) {
            inventoryDao.insert(
                manual(compound.id, alreadyUsed, compound.amountPerContainerUnit, ALREADY_OPENED_REASON, now),
            )
        }
    }

    // -----------------------------------------------------------------------
    // Close / discard container  (§3.1.1 lost/discarded path)
    // -----------------------------------------------------------------------

    override suspend fun closeContainer(id: Long, reason: String?): EmptyResult<DataError.Local> = runTx {
        val compound = compoundDao.getById(id) ?: throw NotFoundException()
        val opened = openedContainerDao.getByCompoundSupplyId(id) ?: throw NotFoundException()
        openedContainerDao.deleteByCompoundSupplyId(id)

        val now = Clock.System.now()
        // What was still in the container leaves the user's stock with it, so the ledger has to lose
        // it too (§5.8.0) — `ContainerClose` itself is a delta-0 audit marker (§5.3) and cannot carry
        // it. An already-empty container is the natural-depletion path, where whatever emptied it is
        // in the ledger already; booking it again would double-count.
        val remaining = opened.remainingAmountUnit.convertTo(
            compound.amountPerContainerUnit,
            opened.remainingAmountValue,
        )
        if (remaining.raw.signum() > 0) {
            inventoryDao.insert(
                manual(id, -remaining, compound.amountPerContainerUnit, reason ?: DISCARDED_REASON, now),
            )
        }
        inventoryDao.insert(
            InventoryTransactionEntity(
                compoundSupplyId = id,
                deltaValue = Decimal.parse("0"),
                deltaUnit = compound.amountPerContainerUnit,
                type = InventoryTransactionType.CONTAINER_CLOSE,
                sourceEventId = null,
                reason = reason,
                at = now,
            ),
        )
        Unit
    }

    // -----------------------------------------------------------------------
    // Edit opened container
    // -----------------------------------------------------------------------

    override suspend fun editOpenedContainer(
        compoundSupplyId: Long,
        openedAt: Instant?,
        remainingAmount: Quantity?,
        expiryAfterOpeningDays: Int?,
        userDefinedExpiryDate: LocalDate?,
    ): EmptyResult<DataError.Local> = runTx {
        val existing = openedContainerDao.getByCompoundSupplyId(compoundSupplyId)
            ?: throw NotFoundException()

        val newOpenedAt = openedAt ?: existing.openedAt
        val newExpiryDays = expiryAfterOpeningDays ?: existing.expiryAfterOpeningDays
        val updated = existing.copy(
            openedAt = newOpenedAt,
            remainingAmountValue = remainingAmount?.value ?: existing.remainingAmountValue,
            remainingAmountUnit = remainingAmount?.unit ?: existing.remainingAmountUnit,
            expiryAfterOpeningDays = newExpiryDays,
            userDefinedExpiryDate = userDefinedExpiryDate ?: existing.userDefinedExpiryDate,
            // Derived, never stored on the user's word (§3.1.1): moving the opened date moves the
            // prediction with it, or the container would keep an expiry it no longer implies.
            predictedExpiryDate = predictedExpiryOf(newOpenedAt, newExpiryDays),
        )
        val rows = openedContainerDao.update(updated)
        if (rows == 0) throw NotFoundException()

        // Correcting what is left in the container is a stock change like any other, so it goes in
        // the ledger (§5.8.0). Read in the new unit, since the edit may have changed that too.
        if (remainingAmount != null) {
            val delta = remainingAmount.value -
                existing.remainingAmountUnit.convertTo(remainingAmount.unit, existing.remainingAmountValue)
            if (delta.raw.signum() != 0) {
                inventoryDao.insert(
                    manual(
                        compoundSupplyId,
                        delta,
                        remainingAmount.unit,
                        REMAINING_ADJUSTED_REASON,
                        Clock.System.now(),
                    ),
                )
            }
        }
        Unit
    }

    // -----------------------------------------------------------------------
    // Ledger + derived-field helpers
    // -----------------------------------------------------------------------

    /** §3.1.1: `predictedExpiryDate = openedAt.date + expiryAfterOpeningDays`, in the user's own zone. */
    private fun predictedExpiryOf(openedAt: Instant, expiryAfterOpeningDays: Int?): LocalDate? =
        expiryAfterOpeningDays?.let { days ->
            openedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date.plus(days, DateTimeUnit.DAY)
        }

    private fun audit(compoundSupplyId: Long, type: InventoryTransactionType, unit: UnitCode, at: Instant) =
        InventoryTransactionEntity(
            compoundSupplyId = compoundSupplyId,
            deltaValue = Decimal.parse("0"),
            deltaUnit = unit,
            type = type,
            sourceEventId = null,
            reason = null,
            at = at,
        )

    private fun manual(compoundSupplyId: Long, delta: Decimal, unit: UnitCode, reason: String, at: Instant) =
        InventoryTransactionEntity(
            compoundSupplyId = compoundSupplyId,
            deltaValue = delta,
            deltaUnit = unit,
            type = InventoryTransactionType.MANUAL,
            sourceEventId = null,
            reason = reason,
            at = at,
        )

    // -----------------------------------------------------------------------
    // Transaction helpers
    // -----------------------------------------------------------------------

    /** Runs [block] inside a Room transaction, returning the block's value or a typed error. */
    private suspend fun <T> runTx(block: suspend () -> T): Result<T, DataError.Local> = try {
        Result.Success(database.withTransaction { block() })
    } catch (e: NotFoundException) {
        Result.Error(DataError.Local.NOT_FOUND)
    } catch (e: ConstraintException) {
        Result.Error(DataError.Local.CONSTRAINT_VIOLATION)
    } catch (e: Exception) {
        Result.Error(DataError.Local.UNKNOWN)
    }

    /** Runs [block] (optionally inside a transaction), returning success or a typed error. */
    private suspend fun runOp(block: suspend () -> Unit): EmptyResult<DataError.Local> = try {
        block()
        Result.Success(Unit)
    } catch (e: NotFoundException) {
        Result.Error(DataError.Local.NOT_FOUND)
    } catch (e: ConstraintException) {
        Result.Error(DataError.Local.CONSTRAINT_VIOLATION)
    } catch (e: Exception) {
        Result.Error(DataError.Local.UNKNOWN)
    }

    private class NotFoundException : Exception()
    private class ConstraintException : Exception()

    private companion object {
        /**
         * §4.2.4. Not a resource string: it is written into `name`, which is stored data — a
         * localized suffix would freeze whichever language was active at the moment of the copy and
         * then disagree with the app around it.
         */
        const val COPY_SUFFIX = " (copy)"

        /**
         * §4.4.4. Stored in the ledger's `reason` column, so it is data rather than UI: a localized
         * string would freeze whichever language was active when the container was capped.
         */
        const val SIZE_REDUCED_REASON = "Compound size reduced"

        /** §4.5.5. The stock a part-used container did *not* bring in. Stored data, like the two above. */
        const val ALREADY_OPENED_REASON = "Already-opened container"

        /** §4.5.5 Edit: the user corrected what is left in the opened container. */
        const val REMAINING_ADJUSTED_REASON = "Remaining amount corrected"

        /** §4.5.4: the fallback reason for a container thrown away with something still in it. */
        const val DISCARDED_REASON = "Opened container discarded"
    }
}
