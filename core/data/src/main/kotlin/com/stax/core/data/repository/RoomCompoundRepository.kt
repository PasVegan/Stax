package com.stax.core.data.repository

import androidx.room.withTransaction
import com.stax.core.data.mapper.toDomain
import com.stax.core.data.mapper.toEntity
import com.stax.core.database.CompoundSupplyDao
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

        if (compound.numberOfContainers <= 0) throw ConstraintException()
        if (openedContainerDao.getByCompoundSupplyId(id) != null) throw ConstraintException()

        val now = Clock.System.now()
        val openedDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val predictedExpiry = compound.expiryAfterOpeningDays?.let { days ->
            openedDate.plus(days, DateTimeUnit.DAY)
        }

        compoundDao.update(
            compound.copy(numberOfContainers = compound.numberOfContainers - 1, updatedAt = now),
        )
        openedContainerDao.insert(
            OpenedContainerEntity(
                compoundSupplyId = id,
                openedAt = now,
                remainingAmountValue = compound.amountPerContainerValue,
                remainingAmountUnit = compound.amountPerContainerUnit,
                expiryAfterOpeningDays = compound.expiryAfterOpeningDays,
                userDefinedExpiryDate = null,
                predictedExpiryDate = predictedExpiry,
            ),
        )
        inventoryDao.insert(
            InventoryTransactionEntity(
                compoundSupplyId = id,
                deltaValue = Decimal.parse("0"),
                deltaUnit = compound.amountPerContainerUnit,
                type = InventoryTransactionType.CONTAINER_OPEN,
                sourceEventId = null,
                reason = null,
                at = now,
            ),
        )
        Unit
    }

    // -----------------------------------------------------------------------
    // Close / discard container  (§3.1.1 lost/discarded path)
    // -----------------------------------------------------------------------

    override suspend fun closeContainer(id: Long, reason: String?): EmptyResult<DataError.Local> = runTx {
        val compound = compoundDao.getById(id) ?: throw NotFoundException()
        val deleted = openedContainerDao.deleteByCompoundSupplyId(id)
        if (deleted == 0) throw NotFoundException()

        val now = Clock.System.now()
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
        remainingAmount: Quantity?,
        expiryAfterOpeningDays: Int?,
        userDefinedExpiryDate: LocalDate?,
    ): EmptyResult<DataError.Local> = runOp {
        val existing = openedContainerDao.getByCompoundSupplyId(compoundSupplyId)
            ?: throw NotFoundException()

        val updated = existing.copy(
            remainingAmountValue = remainingAmount?.value ?: existing.remainingAmountValue,
            remainingAmountUnit = remainingAmount?.unit ?: existing.remainingAmountUnit,
            expiryAfterOpeningDays = expiryAfterOpeningDays ?: existing.expiryAfterOpeningDays,
            userDefinedExpiryDate = userDefinedExpiryDate ?: existing.userDefinedExpiryDate,
        )
        val rows = openedContainerDao.update(updated)
        if (rows == 0) throw NotFoundException()
    }

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
    }
}
