package com.stax.core.database

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.stax.core.domain.Decimal
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
class InventoryTransactionDaoTest {

    private lateinit var database: StaxDatabase
    private lateinit var compoundSupplyDao: CompoundSupplyDao
    private lateinit var dao: InventoryTransactionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StaxDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        compoundSupplyDao = database.compoundSupplyDao()
        dao = database.inventoryTransactionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `InventoryTransactionDao exposes no update or delete methods`() {
        val methods = InventoryTransactionDao::class.java.declaredMethods.map { it.name }
        assertThat(methods.none { it.startsWith("update") || it.startsWith("delete") }).isEqualTo(true)
    }

    @Test
    fun `insert stores entity and observeByCompound returns it`() = runTest {
        val compoundId = compoundSupplyDao.insert(compound())
        val tx = transaction(compoundSupplyId = compoundId)

        val id = dao.insert(tx)

        assertThat(dao.observeByCompound(compoundId).first())
            .containsExactly(tx.copy(id = id))
    }

    @Test
    fun `observeByCompound returns transactions ordered by at ascending`() = runTest {
        val compoundId = compoundSupplyDao.insert(compound())
        val t1 = Instant.parse("2026-06-06T08:00:00Z")
        val t2 = Instant.parse("2026-06-07T08:00:00Z")
        val t3 = Instant.parse("2026-06-08T08:00:00Z")

        val id3 = dao.insert(transaction(compoundSupplyId = compoundId, at = t3))
        val id1 = dao.insert(transaction(compoundSupplyId = compoundId, at = t1))
        val id2 = dao.insert(transaction(compoundSupplyId = compoundId, at = t2))

        assertThat(dao.observeByCompound(compoundId).first().map { it.id })
            .containsExactly(id1, id2, id3)
    }

    @Test
    fun `observeByCompound filters by compoundSupplyId`() = runTest {
        val compound1 = compoundSupplyDao.insert(compound(name = "Compound A"))
        val compound2 = compoundSupplyDao.insert(compound(name = "Compound B"))

        val id = dao.insert(transaction(compoundSupplyId = compound1))
        dao.insert(transaction(compoundSupplyId = compound2))

        assertThat(dao.observeByCompound(compound1).first().map { it.id })
            .containsExactly(id)
    }

    @Test
    fun `sumDelta returns sum of signed deltas`() = runTest {
        val compoundId = compoundSupplyDao.insert(compound())

        dao.insert(transaction(compoundSupplyId = compoundId, deltaValue = Decimal.parse("10")))
        dao.insert(transaction(compoundSupplyId = compoundId, deltaValue = Decimal.parse("-0.5")))
        dao.insert(transaction(compoundSupplyId = compoundId, deltaValue = Decimal.parse("2.5")))

        val sum = dao.sumDelta(compoundId).first()

        assertThat(sum).isEqualTo(12.0)
    }

    @Test
    fun `sumDelta returns null when no transactions exist`() = runTest {
        val compoundId = compoundSupplyDao.insert(compound())

        assertThat(dao.sumDelta(compoundId).first()).isNull()
    }

    @Test
    fun `sumDelta ignores other compounds`() = runTest {
        val compound1 = compoundSupplyDao.insert(compound(name = "A"))
        val compound2 = compoundSupplyDao.insert(compound(name = "B"))

        dao.insert(transaction(compoundSupplyId = compound1, deltaValue = Decimal.parse("5")))
        dao.insert(transaction(compoundSupplyId = compound2, deltaValue = Decimal.parse("100")))

        val sum = dao.sumDelta(compound1).first()

        assertThat(sum).isEqualTo(5.0)
    }

    @Test
    fun `all InventoryTransactionType values round-trip through Room`() = runTest {
        val compoundId = compoundSupplyDao.insert(compound())
        for (type in InventoryTransactionType.entries) {
            val id = dao.insert(
                transaction(
                    compoundSupplyId = compoundId,
                    at = Instant.fromEpochMilliseconds(type.ordinal.toLong() * 1000),
                    type = type,
                ),
            )
            val stored = dao.observeByCompound(compoundId).first().first { it.id == id }
            assertThat(stored.type).isEqualTo(type)
        }
    }

    @Test
    fun `nullable sourceEventId stored and retrieved`() = runTest {
        val compoundId = compoundSupplyDao.insert(compound())

        val id = dao.insert(transaction(compoundSupplyId = compoundId, sourceEventId = null))

        assertThat(dao.observeByCompound(compoundId).first().first { it.id == id }.sourceEventId).isNull()
    }

    private fun transaction(
        id: Long = 0,
        compoundSupplyId: Long,
        deltaValue: Decimal = Decimal.parse("1"),
        deltaUnit: UnitCode = UnitCode.MG,
        type: InventoryTransactionType = InventoryTransactionType.INITIAL_STOCK,
        sourceEventId: Long? = null,
        reason: String? = null,
        at: Instant = Instant.parse("2026-06-06T08:00:00Z"),
    ): InventoryTransactionEntity = InventoryTransactionEntity(
        id = id,
        compoundSupplyId = compoundSupplyId,
        deltaValue = deltaValue,
        deltaUnit = deltaUnit,
        type = type,
        sourceEventId = sourceEventId,
        reason = reason,
        at = at,
    )

    private fun compound(
        id: Long = 0,
        name: String = "Compound",
    ): CompoundSupplyEntity = CompoundSupplyEntity(
        id = id,
        name = name,
        category = CompoundCategory.PEPTIDE,
        form = CompoundForm.INJECTABLE,
        containerType = ContainerType.VIAL,
        primaryUnit = UnitCode.MG,
        amountPerContainerValue = Decimal.parse("5"),
        amountPerContainerUnit = UnitCode.MG,
        concentrationAmountValue = null,
        concentrationAmountUnit = null,
        concentrationPerValue = null,
        concentrationPerUnit = null,
        numberOfContainers = 1,
        batchExpiryDate = null,
        expiryAfterOpeningDays = null,
        storageLocation = StorageLocation.FRIDGE,
        batchNumber = null,
        supplier = null,
        notes = null,
        deletedAt = null,
        createdAt = Instant.parse("2026-06-06T00:00:00Z"),
        updatedAt = Instant.parse("2026-06-06T00:00:00Z"),
    )
}
