package com.stax.core.data.repository

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.stax.core.database.InventoryTransactionType
import com.stax.core.database.StaxDatabase
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.ContainerType
import com.stax.core.domain.DataError
import com.stax.core.domain.Decimal
import com.stax.core.domain.OpenedContainer
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.StorageLocation
import com.stax.core.domain.UnitCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompoundRepositoryTest {

    private lateinit var database: StaxDatabase
    private lateinit var repository: RoomCompoundRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StaxDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        repository = RoomCompoundRepository(
            database = database,
            compoundDao = database.compoundSupplyDao(),
            openedContainerDao = database.openedContainerDao(),
            inventoryDao = database.inventoryTransactionDao(),
        )
    }

    @After
    fun tearDown() = database.close()

    // -----------------------------------------------------------------------
    // observeAll
    // -----------------------------------------------------------------------

    @Test
    fun `observeAll emits empty list when table empty`() = runTest {
        assertThat(repository.observeAll().first()).isEmpty()
    }

    @Test
    fun `observeAll emits created compound`() = runTest {
        repository.create(minimalCompound())
        assertThat(repository.observeAll().first()).hasSize(1)
    }

    @Test
    fun `observeAll excludes soft-deleted compounds`() = runTest {
        val result = repository.create(minimalCompound()) as Result.Success
        repository.archive(result.data)
        assertThat(repository.observeAll().first()).isEmpty()
    }

    // -----------------------------------------------------------------------
    // observeById
    // -----------------------------------------------------------------------

    @Test
    fun `observeById returns null when compound not found`() = runTest {
        assertThat(repository.observeById(999L).first()).isNull()
    }

    @Test
    fun `observeById returns compound after creation`() = runTest {
        val result = repository.create(minimalCompound()) as Result.Success
        val compound = repository.observeById(result.data).first()
        assertThat(compound).isNotNull()
        assertThat(compound!!.name).isEqualTo("BPC-157")
    }

    @Test
    fun `observeById includes opened container when present`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 2)) as Result.Success).data
        repository.openContainer(id)
        val compound = repository.observeById(id).first()!!
        assertThat(compound.currentOpened).isNotNull()
    }

    // -----------------------------------------------------------------------
    // create
    // -----------------------------------------------------------------------

    @Test
    fun `create inserts compound and returns new id`() = runTest {
        val result = repository.create(minimalCompound())
        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat((result as Result.Success).data > 0).isEqualTo(true)
    }

    @Test
    fun `create emits InitialStock transaction for closed containers only`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 3)) as Result.Success).data
        val txns = database.inventoryTransactionDao().observeByCompound(id).first()
        assertThat(txns).hasSize(1)
        assertThat(txns[0].type).isEqualTo(InventoryTransactionType.INITIAL_STOCK)
        // delta = 3 × 5 mg = 15 mg
        assertThat(txns[0].deltaValue).isEqualTo(Decimal.parse("15"))
        assertThat(txns[0].deltaUnit).isEqualTo(UnitCode.MG)
    }

    @Test
    fun `create with already-opened container inserts opened_container row`() = runTest {
        val compound = minimalCompound(numberOfContainers = 1).copy(
            currentOpened = OpenedContainer(
                openedAt = now,
                remainingAmount = Quantity(Decimal.parse("3.5"), UnitCode.MG),
                expiryAfterOpeningDays = 30,
                userDefinedExpiryDate = null,
                predictedExpiryDate = null,
            ),
        )
        val id = (repository.create(compound) as Result.Success).data
        val opened = database.openedContainerDao().getByCompoundSupplyId(id)
        assertThat(opened).isNotNull()
        assertThat(opened!!.remainingAmountValue).isEqualTo(Decimal.parse("3.5"))
    }

    @Test
    fun `create with opened container includes opened remaining in InitialStock delta`() = runTest {
        // 1 closed × 5 mg + 3.5 mg opened = 8.5 mg
        val compound = minimalCompound(numberOfContainers = 1).copy(
            currentOpened = OpenedContainer(
                openedAt = now,
                remainingAmount = Quantity(Decimal.parse("3.5"), UnitCode.MG),
                expiryAfterOpeningDays = null,
                userDefinedExpiryDate = null,
                predictedExpiryDate = null,
            ),
        )
        val id = (repository.create(compound) as Result.Success).data
        val txn = database.inventoryTransactionDao().observeByCompound(id).first().first()
        assertThat(txn.deltaValue).isEqualTo(Decimal.parse("8.5"))
    }

    // -----------------------------------------------------------------------
    // update
    // -----------------------------------------------------------------------

    @Test
    fun `update persists changes to compound row`() = runTest {
        val id = (repository.create(minimalCompound()) as Result.Success).data
        val updated = repository.observeById(id).first()!!.copy(name = "Semaglutide")
        val result = repository.update(updated)
        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat(repository.observeById(id).first()!!.name).isEqualTo("Semaglutide")
    }

    @Test
    fun `update returns NOT_FOUND for non-existent compound`() = runTest {
        val ghost = minimalCompound().copy(id = 999L)
        val result = repository.update(ghost)
        assertThat(result).isInstanceOf(Result.Error::class)
        assertThat((result as Result.Error).error).isEqualTo(DataError.Local.NOT_FOUND)
    }

    // -----------------------------------------------------------------------
    // update — capping the opened container (§4.4.4 Edit case)
    // -----------------------------------------------------------------------

    @Test
    fun `update caps the opened container and books the difference as a Manual transaction`() = runTest {
        // A 5 mg vial, opened and untouched, shrunk to a 2 mg one.
        val id = (repository.create(minimalCompound(numberOfContainers = 1)) as Result.Success).data
        repository.openContainer(id)
        val shrunk = repository.observeById(id).first()!!
            .copy(amountPerContainer = Quantity(Decimal.parse("2"), UnitCode.MG))

        val result = repository.update(shrunk, capOpenedContainer = true)

        assertThat(result).isInstanceOf(Result.Success::class)
        val opened = database.openedContainerDao().getByCompoundSupplyId(id)!!
        assertThat(opened.remainingAmountValue).isEqualTo(Decimal.parse("2"))

        val manual = manualTransactionsFor(id).single()
        assertThat(manual.deltaValue).isEqualTo(Decimal.parse("-3"))
        assertThat(manual.deltaUnit).isEqualTo(UnitCode.MG)
        assertThat(manual.reason).isEqualTo("Compound size reduced")
    }

    /** §4.4.4 "Keep remaining": the container is allowed to hold more than the compound's new size. */
    @Test
    fun `update leaves the opened container alone when not asked to cap`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 1)) as Result.Success).data
        repository.openContainer(id)
        val shrunk = repository.observeById(id).first()!!
            .copy(amountPerContainer = Quantity(Decimal.parse("2"), UnitCode.MG))

        repository.update(shrunk)

        val opened = database.openedContainerDao().getByCompoundSupplyId(id)!!
        assertThat(opened.remainingAmountValue).isEqualTo(Decimal.parse("5"))
        assertThat(manualTransactionsFor(id)).isEmpty()
    }

    /** The cap converts before it subtracts: 5 mg capped to 2000 mcg loses 3000 mcg, not 3. */
    @Test
    fun `update caps across a unit change`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 1)) as Result.Success).data
        repository.openContainer(id)
        val shrunk = repository.observeById(id).first()!!
            .copy(primaryUnit = UnitCode.MCG, amountPerContainer = Quantity(Decimal.parse("2000"), UnitCode.MCG))

        repository.update(shrunk, capOpenedContainer = true)

        val opened = database.openedContainerDao().getByCompoundSupplyId(id)!!
        assertThat(opened.remainingAmountValue).isEqualTo(Decimal.parse("2000"))
        assertThat(opened.remainingAmountUnit).isEqualTo(UnitCode.MCG)

        val manual = manualTransactionsFor(id).single()
        assertThat(manual.deltaValue).isEqualTo(Decimal.parse("-3000"))
        assertThat(manual.deltaUnit).isEqualTo(UnitCode.MCG)
    }

    /** Nothing to cap is not a failure — the compound's own edit still stands. */
    @Test
    fun `update asked to cap without an opened container still saves the compound`() = runTest {
        val id = (repository.create(minimalCompound()) as Result.Success).data
        val renamed = repository.observeById(id).first()!!.copy(name = "Semaglutide")

        val result = repository.update(renamed, capOpenedContainer = true)

        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat(repository.observeById(id).first()!!.name).isEqualTo("Semaglutide")
        assertThat(manualTransactionsFor(id)).isEmpty()
    }

    // -----------------------------------------------------------------------
    // archive
    // -----------------------------------------------------------------------

    @Test
    fun `archive soft-deletes compound`() = runTest {
        val id = (repository.create(minimalCompound()) as Result.Success).data
        repository.archive(id)
        // compound still exists via observeById (includes soft-deleted)
        val compound = repository.observeById(id).first()!!
        assertThat(compound.deletedAt).isNotNull()
    }

    @Test
    fun `archive returns NOT_FOUND for non-existent id`() = runTest {
        val result = repository.archive(999L)
        assertThat((result as Result.Error).error).isEqualTo(DataError.Local.NOT_FOUND)
    }

    // -----------------------------------------------------------------------
    // duplicate
    // -----------------------------------------------------------------------

    @Test
    fun `duplicate creates new compound with same fields under a copy name`() = runTest {
        val origId = (repository.create(minimalCompound(numberOfContainers = 2)) as Result.Success).data
        val newId = (repository.duplicate(origId) as Result.Success).data

        assertThat(newId != origId).isEqualTo(true)
        val copy = repository.observeById(newId).first()!!
        assertThat(copy.name).isEqualTo("BPC-157 (copy)")
        assertThat(copy.numberOfContainers).isEqualTo(2)
    }

    @Test
    fun `duplicate does not copy the batch number`() = runTest {
        val origId = (repository.create(minimalCompound(batchNumber = "LOT-42")) as Result.Success).data
        val newId = (repository.duplicate(origId) as Result.Success).data

        assertThat(repository.observeById(newId).first()!!.batchNumber).isNull()
    }

    @Test
    fun `duplicate emits InitialStock transaction for copy`() = runTest {
        val origId = (repository.create(minimalCompound(numberOfContainers = 2)) as Result.Success).data
        val newId = (repository.duplicate(origId) as Result.Success).data

        val txns = database.inventoryTransactionDao().observeByCompound(newId).first()
        assertThat(txns).hasSize(1)
        assertThat(txns[0].type).isEqualTo(InventoryTransactionType.INITIAL_STOCK)
    }

    @Test
    fun `duplicate does not copy opened container`() = runTest {
        val origId = (repository.create(minimalCompound(numberOfContainers = 2)) as Result.Success).data
        repository.openContainer(origId)
        val newId = (repository.duplicate(origId) as Result.Success).data

        assertThat(repository.observeById(newId).first()!!.currentOpened).isNull()
    }

    @Test
    fun `duplicate returns NOT_FOUND for unknown id`() = runTest {
        val result = repository.duplicate(999L)
        assertThat((result as Result.Error).error).isEqualTo(DataError.Local.NOT_FOUND)
    }

    // -----------------------------------------------------------------------
    // openContainer
    // -----------------------------------------------------------------------

    @Test
    fun `openContainer creates opened_container row`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 1)) as Result.Success).data
        repository.openContainer(id)
        val opened = database.openedContainerDao().getByCompoundSupplyId(id)
        assertThat(opened).isNotNull()
        assertThat(opened!!.remainingAmountValue).isEqualTo(Decimal.parse("5"))
    }

    @Test
    fun `openContainer decrements numberOfContainers`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 2)) as Result.Success).data
        repository.openContainer(id)
        val compound = repository.observeById(id).first()!!
        assertThat(compound.numberOfContainers).isEqualTo(1)
    }

    @Test
    fun `openContainer inserts ContainerOpen audit transaction`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 1)) as Result.Success).data
        repository.openContainer(id)

        val txns = database.inventoryTransactionDao().observeByCompound(id).first()
        assertThat(txns.any { it.type == InventoryTransactionType.CONTAINER_OPEN }).isEqualTo(true)
        val openTxn = txns.first { it.type == InventoryTransactionType.CONTAINER_OPEN }
        assertThat(openTxn.deltaValue).isEqualTo(Decimal.parse("0"))
    }

    @Test
    fun `openContainer returns CONSTRAINT_VIOLATION when no containers left`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 0)) as Result.Success).data
        val result = repository.openContainer(id)
        assertThat((result as Result.Error).error).isEqualTo(DataError.Local.CONSTRAINT_VIOLATION)
    }

    @Test
    fun `openContainer returns CONSTRAINT_VIOLATION when already open`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 2)) as Result.Success).data
        repository.openContainer(id)
        val result = repository.openContainer(id)
        assertThat((result as Result.Error).error).isEqualTo(DataError.Local.CONSTRAINT_VIOLATION)
    }

    @Test
    fun `openContainer returns NOT_FOUND for unknown id`() = runTest {
        val result = repository.openContainer(999L)
        assertThat((result as Result.Error).error).isEqualTo(DataError.Local.NOT_FOUND)
    }

    // -----------------------------------------------------------------------
    // closeContainer
    // -----------------------------------------------------------------------

    @Test
    fun `closeContainer removes opened_container row`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 1)) as Result.Success).data
        repository.openContainer(id)
        repository.closeContainer(id, "lost")
        assertThat(database.openedContainerDao().getByCompoundSupplyId(id)).isNull()
    }

    @Test
    fun `closeContainer does not change numberOfContainers`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 2)) as Result.Success).data
        repository.openContainer(id) // numberOfContainers: 2 → 1
        repository.closeContainer(id, null)
        val compound = repository.observeById(id).first()!!
        assertThat(compound.numberOfContainers).isEqualTo(1)
    }

    @Test
    fun `closeContainer inserts ContainerClose audit transaction`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 1)) as Result.Success).data
        repository.openContainer(id)
        repository.closeContainer(id, "lost")

        val txns = database.inventoryTransactionDao().observeByCompound(id).first()
        assertThat(txns.any { it.type == InventoryTransactionType.CONTAINER_CLOSE }).isEqualTo(true)
        val closeTxn = txns.first { it.type == InventoryTransactionType.CONTAINER_CLOSE }
        assertThat(closeTxn.deltaValue).isEqualTo(Decimal.parse("0"))
        assertThat(closeTxn.reason).isEqualTo("lost")
    }

    @Test
    fun `closeContainer returns NOT_FOUND when no open container`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 1)) as Result.Success).data
        val result = repository.closeContainer(id, null)
        assertThat((result as Result.Error).error).isEqualTo(DataError.Local.NOT_FOUND)
    }

    // -----------------------------------------------------------------------
    // editOpenedContainer
    // -----------------------------------------------------------------------

    @Test
    fun `editOpenedContainer updates remaining amount`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 1)) as Result.Success).data
        repository.openContainer(id)

        repository.editOpenedContainer(
            compoundSupplyId = id,
            remainingAmount = Quantity(Decimal.parse("2.5"), UnitCode.MG),
            expiryAfterOpeningDays = null,
            userDefinedExpiryDate = null,
        )

        val opened = database.openedContainerDao().getByCompoundSupplyId(id)!!
        assertThat(opened.remainingAmountValue).isEqualTo(Decimal.parse("2.5"))
    }

    @Test
    fun `editOpenedContainer preserves unchanged fields`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 1)) as Result.Success).data
        repository.openContainer(id)

        repository.editOpenedContainer(
            compoundSupplyId = id,
            remainingAmount = null,
            expiryAfterOpeningDays = 60,
            userDefinedExpiryDate = null,
        )

        val opened = database.openedContainerDao().getByCompoundSupplyId(id)!!
        // remainingAmount unchanged (still amountPerContainer = 5 mg)
        assertThat(opened.remainingAmountValue).isEqualTo(Decimal.parse("5"))
        assertThat(opened.expiryAfterOpeningDays).isEqualTo(60)
    }

    @Test
    fun `editOpenedContainer returns NOT_FOUND when no opened container`() = runTest {
        val id = (repository.create(minimalCompound(numberOfContainers = 1)) as Result.Success).data
        val result = repository.editOpenedContainer(
            compoundSupplyId = id,
            remainingAmount = Quantity(Decimal.parse("1"), UnitCode.MG),
            expiryAfterOpeningDays = null,
            userDefinedExpiryDate = null,
        )
        assertThat((result as Result.Error).error).isEqualTo(DataError.Local.NOT_FOUND)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private val now = Instant.parse("2026-06-06T00:00:00Z")

    /** The ledger rows §4.4.4's cap writes, and the ones the other paths must not. */
    private suspend fun manualTransactionsFor(compoundId: Long) =
        database.inventoryTransactionDao().observeByCompound(compoundId).first()
            .filter { it.type == InventoryTransactionType.MANUAL }

    private fun minimalCompound(numberOfContainers: Int = 1, batchNumber: String? = null): CompoundSupply =
        CompoundSupply(
            id = 0L,
            name = "BPC-157",
            category = CompoundCategory.PEPTIDE,
            form = CompoundForm.INJECTABLE,
            containerType = ContainerType.VIAL,
            primaryUnit = UnitCode.MG,
            amountPerContainer = Quantity(Decimal.parse("5"), UnitCode.MG),
            concentration = null,
            numberOfContainers = numberOfContainers,
            currentOpened = null,
            batchExpiryDate = null,
            expiryAfterOpeningDays = null,
            storageLocation = StorageLocation.FRIDGE,
            batchNumber = batchNumber,
            supplier = null,
            notes = null,
            deletedAt = null,
            createdAt = now,
            updatedAt = now,
        )
}
