package com.stax.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
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
class OpenedContainerDaoTest {

    private lateinit var database: StaxDatabase
    private lateinit var compoundSupplyDao: CompoundSupplyDao
    private lateinit var openedContainerDao: OpenedContainerDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StaxDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        compoundSupplyDao = database.compoundSupplyDao()
        openedContainerDao = database.openedContainerDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert returns row id and observeByCompoundSupplyId returns inserted row`() = runTest {
        val compoundId = compoundSupplyDao.insert(compound())
        val openedContainer = openedContainer(compoundSupplyId = compoundId)

        val id = openedContainerDao.insert(openedContainer)

        assertThat(openedContainerDao.observeByCompoundSupplyId(compoundId).first())
            .isEqualTo(openedContainer.copy(id = id))
    }

    @Test
    fun `unique compoundSupplyId constraint allows one opened container per compound`() = runTest {
        val compoundId = compoundSupplyDao.insert(compound())
        openedContainerDao.insert(openedContainer(compoundSupplyId = compoundId))

        val error = try {
            openedContainerDao.insert(openedContainer(compoundSupplyId = compoundId))
            null
        } catch (e: SQLiteConstraintException) {
            e
        }

        assertThat(error).isNotNull()
    }

    @Test
    fun `deleting parent compound cascades to opened container`() = runTest {
        val compoundId = compoundSupplyDao.insert(compound())
        openedContainerDao.insert(openedContainer(compoundSupplyId = compoundId))

        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM compound_supply WHERE id = ?",
            arrayOf(compoundId),
        )

        assertThat(openedContainerDao.observeByCompoundSupplyId(compoundId).first()).isNull()
    }

    private fun openedContainer(
        id: Long = 0,
        compoundSupplyId: Long,
        openedAt: Instant = Instant.parse("2026-06-06T00:00:00Z"),
        remainingAmountValue: Decimal = Decimal.parse("2.5"),
        remainingAmountUnit: UnitCode = UnitCode.MG,
        expiryAfterOpeningDays: Int? = 28,
        userDefinedExpiryDate: LocalDate? = null,
        predictedExpiryDate: LocalDate? = LocalDate.parse("2026-07-04"),
    ): OpenedContainerEntity = OpenedContainerEntity(
        id = id,
        compoundSupplyId = compoundSupplyId,
        openedAt = openedAt,
        remainingAmountValue = remainingAmountValue,
        remainingAmountUnit = remainingAmountUnit,
        expiryAfterOpeningDays = expiryAfterOpeningDays,
        userDefinedExpiryDate = userDefinedExpiryDate,
        predictedExpiryDate = predictedExpiryDate,
    )

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
