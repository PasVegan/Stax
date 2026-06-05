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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompoundSupplyDaoTest {

    private lateinit var database: StaxDatabase
    private lateinit var dao: CompoundSupplyDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StaxDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.compoundSupplyDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert returns row id and observeById returns inserted row`() = runTest {
        val entity = compound(name = "Tirzepatide")
        val id = dao.insert(entity)

        assertThat(dao.observeById(id).first()).isEqualTo(entity.copy(id = id))
    }

    @Test
    fun `observeById returns null when row does not exist`() = runTest {
        assertThat(dao.observeById(404).first()).isNull()
    }

    @Test
    fun `update replaces matching row`() = runTest {
        val id = dao.insert(compound(name = "B12", notes = "original"))
        val updated = compound(
            id = id,
            name = "B12",
            notes = "updated",
            numberOfContainers = 3,
            updatedAt = Instant.parse("2026-06-07T00:00:00Z"),
        )

        val changedRows = dao.update(updated)

        assertThat(changedRows).isEqualTo(1)
        assertThat(dao.observeById(id).first()).isEqualTo(updated)
    }

    @Test
    fun `observeActive returns active rows ordered by name`() = runTest {
        val zinc = insert(compound(name = "Zinc"))
        val magnesium = insert(compound(name = "Magnesium"))
        insert(compound(name = "Archived", deletedAt = Instant.parse("2026-06-06T01:00:00Z")))

        assertThat(dao.observeActive().first()).containsExactly(magnesium, zinc)
    }

    @Test
    fun `softDelete sets deletedAt and excludes row from observeActive`() = runTest {
        val kept = insert(compound(name = "Kept"))
        val deleted = insert(compound(name = "Deleted"))
        val deletedAt = Instant.parse("2026-06-06T02:00:00Z")

        val changedRows = dao.softDelete(deleted.id, deletedAt)

        assertThat(changedRows).isEqualTo(1)
        assertThat(dao.observeById(deleted.id).first()).isEqualTo(deleted.copy(deletedAt = deletedAt))
        assertThat(dao.observeActive().first()).containsExactly(kept)
    }

    @Test
    fun `observeLowStock returns active rows with no unopened containers`() = runTest {
        val emptyStock = insert(compound(name = "Empty stock", numberOfContainers = 0))
        insert(compound(name = "In stock", numberOfContainers = 1))
        insert(
            compound(
                name = "Archived empty stock",
                numberOfContainers = 0,
                deletedAt = Instant.parse("2026-06-06T03:00:00Z"),
            ),
        )

        assertThat(dao.observeLowStock().first()).containsExactly(emptyStock)
    }

    @Test
    fun `observeExpiringSoon returns active rows expiring within requested days`() = runTest {
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        val expiringToday = insert(compound(name = "Expires today", batchExpiryDate = today))
        val expiringSoon = insert(
            compound(
                name = "Expires soon",
                batchExpiryDate = today.plus(DatePeriod(days = 10)),
            ),
        )
        insert(compound(name = "Expires later", batchExpiryDate = today.plus(DatePeriod(days = 40))))
        insert(compound(name = "No expiry", batchExpiryDate = null))
        insert(
            compound(
                name = "Archived expiring",
                batchExpiryDate = today.plus(DatePeriod(days = 5)),
                deletedAt = Instant.parse("2026-06-06T04:00:00Z"),
            ),
        )

        assertThat(dao.observeExpiringSoon(days = 30).first()).containsExactly(expiringToday, expiringSoon)
    }

    private suspend fun insert(entity: CompoundSupplyEntity): CompoundSupplyEntity {
        val id = dao.insert(entity)
        return entity.copy(id = id)
    }

    private fun compound(
        id: Long = 0,
        name: String = "Compound",
        category: CompoundCategory = CompoundCategory.PEPTIDE,
        form: CompoundForm = CompoundForm.INJECTABLE,
        containerType: ContainerType = ContainerType.VIAL,
        primaryUnit: UnitCode = UnitCode.MG,
        amountPerContainerValue: Decimal = Decimal.parse("5"),
        amountPerContainerUnit: UnitCode = primaryUnit,
        concentrationAmountValue: Decimal? = Decimal.parse("2.5"),
        concentrationAmountUnit: UnitCode? = UnitCode.MG,
        concentrationPerValue: Decimal? = Decimal.parse("1"),
        concentrationPerUnit: UnitCode? = UnitCode.ML,
        numberOfContainers: Int = 1,
        batchExpiryDate: LocalDate? = LocalDate.parse("2026-12-31"),
        expiryAfterOpeningDays: Int? = 28,
        storageLocation: StorageLocation = StorageLocation.FRIDGE,
        batchNumber: String? = "BATCH-1",
        supplier: String? = "Supplier",
        notes: String? = null,
        deletedAt: Instant? = null,
        createdAt: Instant = Instant.parse("2026-06-06T00:00:00Z"),
        updatedAt: Instant = createdAt,
    ): CompoundSupplyEntity = CompoundSupplyEntity(
        id = id,
        name = name,
        category = category,
        form = form,
        containerType = containerType,
        primaryUnit = primaryUnit,
        amountPerContainerValue = amountPerContainerValue,
        amountPerContainerUnit = amountPerContainerUnit,
        concentrationAmountValue = concentrationAmountValue,
        concentrationAmountUnit = concentrationAmountUnit,
        concentrationPerValue = concentrationPerValue,
        concentrationPerUnit = concentrationPerUnit,
        numberOfContainers = numberOfContainers,
        batchExpiryDate = batchExpiryDate,
        expiryAfterOpeningDays = expiryAfterOpeningDays,
        storageLocation = storageLocation,
        batchNumber = batchNumber,
        supplier = supplier,
        notes = notes,
        deletedAt = deletedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
