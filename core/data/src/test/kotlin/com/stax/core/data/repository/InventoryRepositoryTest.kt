package com.stax.core.data.repository

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.stax.core.database.BodyRegion
import com.stax.core.database.CompoundCategory
import com.stax.core.database.CompoundForm
import com.stax.core.database.CompoundSupplyEntity
import com.stax.core.database.ContainerType
import com.stax.core.database.OpenedContainerEntity
import com.stax.core.database.ProtocolEntity
import com.stax.core.database.ProtocolStatus
import com.stax.core.database.ReminderBucket
import com.stax.core.database.Route
import com.stax.core.database.ScheduleEmbed
import com.stax.core.database.ScheduleType
import com.stax.core.database.StaxDatabase
import com.stax.core.database.StorageLocation
import com.stax.core.domain.Decimal
import com.stax.core.domain.InventoryWarning
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
class InventoryRepositoryTest {

    private lateinit var database: StaxDatabase
    private lateinit var repository: RoomInventoryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StaxDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        repository = RoomInventoryRepository(
            compoundDao = database.compoundSupplyDao(),
            protocolDao = database.protocolDao(),
            today = { TODAY },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `observeDosesLeftPerCompound uses most frequent active protocol`() = runTest {
        val compoundId = insertCompound(
            name = "Semaglutide",
            amountPerContainerValue = "10",
            concentrationAmountValue = "5",
            concentrationPerValue = "1",
            numberOfContainers = 1,
        )
        insertOpened(compoundId, remainingAmountValue = "5")
        insertProtocol(
            compoundSupplyId = compoundId,
            name = "Daily",
            plannedDoseValue = "5",
            scheduleType = ScheduleType.DAILY,
        )
        insertProtocol(
            compoundSupplyId = compoundId,
            name = "Every other day",
            plannedDoseValue = "20",
            scheduleType = ScheduleType.EVERY_X_DAYS,
            interval = 2,
        )

        val result = repository.observeDosesLeftPerCompound().first().single()

        assertThat(result.compoundSupplyId).isEqualTo(compoundId)
        assertThat(result.dosesPerActualInjection!!.value.toPlainString()).isEqualTo("1")
        assertThat(result.dosesPerActualInjection!!.unit).isEqualTo(UnitCode.ML)
        assertThat(result.dosesLeft).isEqualTo(15)
        assertThat(result.daysLeft).isEqualTo(10)
    }

    @Test
    fun `dosesPerActualInjection tie resolves by max planned dose`() = runTest {
        val compoundId = insertCompound(
            name = "BPC-157",
            amountPerContainerValue = "10",
            concentrationAmountValue = "2",
            concentrationPerValue = "1",
            numberOfContainers = 1,
        )
        insertProtocol(compoundId, name = "Small", plannedDoseValue = "2", scheduleType = ScheduleType.DAILY)
        insertProtocol(compoundId, name = "Large", plannedDoseValue = "4", scheduleType = ScheduleType.DAILY)

        val result = repository.observeDosesLeftPerCompound().first().single()

        assertThat(result.dosesPerActualInjection!!.value.toPlainString()).isEqualTo("2")
        assertThat(result.dosesLeft).isEqualTo(5)
    }

    @Test
    fun `observeRunOutDate simulates scheduled doses from today`() = runTest {
        val compoundId = insertCompound(
            name = "Tirzepatide",
            amountPerContainerValue = "5",
            concentrationAmountValue = "5",
            concentrationPerValue = "1",
            numberOfContainers = 1,
        )
        val protocolId = insertProtocol(
            compoundSupplyId = compoundId,
            name = "Daily",
            plannedDoseValue = "5",
            scheduleType = ScheduleType.DAILY,
        )

        val result = repository.observeRunOutDate(protocolId).first()

        assertThat(result).isEqualTo(LocalDate.parse("2026-06-10"))
    }

    @Test
    fun `observeWarnings reports fixture warning set`() = runTest {
        val compoundId = insertCompound(
            name = "Low stock",
            amountPerContainerValue = "5",
            concentrationAmountValue = "5",
            concentrationPerValue = "1",
            numberOfContainers = 1,
            batchExpiryDate = LocalDate.parse("2026-06-08"),
        )
        insertOpened(
            compoundSupplyId = compoundId,
            remainingAmountValue = "0",
            predictedExpiryDate = LocalDate.parse("2026-06-12"),
        )
        insertProtocol(
            compoundSupplyId = compoundId,
            name = "Daily through end",
            plannedDoseValue = "5",
            scheduleType = ScheduleType.DAILY,
            endDate = LocalDate.parse("2026-06-20"),
        )

        val warnings = repository.observeWarnings().first()
        val warningTypes = warnings.map { it::class.simpleName }

        assertThat(warningTypes).contains("LowStock")
        assertThat(warningTypes).contains("OpenedContainerExpiring")
        assertThat(warningTypes).contains("BatchExpiresBeforeRunOut")
        assertThat(warningTypes).contains("ProtocolNeedsMore")
        assertThat(warnings).hasSize(4)
        assertThat(warnings.first { it is InventoryWarning.LowStock })
            .isInstanceOf(InventoryWarning.LowStock::class)
        val protocolNeedsMore = warnings.first { it is InventoryWarning.ProtocolNeedsMore }
            as InventoryWarning.ProtocolNeedsMore
        assertThat(protocolNeedsMore.required.value.toPlainString()).isEqualTo("15")
        assertThat(protocolNeedsMore.available.value.toPlainString()).isEqualTo("5")
    }

    @Test
    fun `compound without active protocol has unknown doses left`() = runTest {
        val compoundId = insertCompound(name = "No protocol")

        val result = repository.observeDosesLeftPerCompound().first().single()

        assertThat(result.compoundSupplyId).isEqualTo(compoundId)
        assertThat(result.dosesLeft).isEqualTo(null)
        assertThat(result.dosesPerActualInjection).isEqualTo(null)
    }

    @Test
    fun `observeWarnings does not read append-only ledger as stock truth`() = runTest {
        val compoundId = insertCompound(
            name = "Authoritative stock",
            amountPerContainerValue = "10",
            concentrationAmountValue = "5",
            concentrationPerValue = "1",
            numberOfContainers = 1,
        )
        insertProtocol(compoundId, name = "Daily", plannedDoseValue = "5", scheduleType = ScheduleType.DAILY)

        val result = repository.observeDosesLeftPerCompound().first().single()

        assertThat(result.dosesLeft).isEqualTo(10)
    }

    private suspend fun insertCompound(
        name: String,
        amountPerContainerValue: String = "10",
        concentrationAmountValue: String? = null,
        concentrationPerValue: String? = null,
        numberOfContainers: Int = 1,
        batchExpiryDate: LocalDate? = null,
    ): Long = database.compoundSupplyDao().insert(
        CompoundSupplyEntity(
            id = 0,
            name = name,
            category = CompoundCategory.PEPTIDE,
            form = CompoundForm.INJECTABLE,
            containerType = ContainerType.VIAL,
            primaryUnit = UnitCode.MG,
            amountPerContainerValue = Decimal.parse(amountPerContainerValue),
            amountPerContainerUnit = UnitCode.ML,
            concentrationAmountValue = concentrationAmountValue?.let { Decimal.parse(it) },
            concentrationAmountUnit = concentrationAmountValue?.let { UnitCode.MG },
            concentrationPerValue = concentrationPerValue?.let { Decimal.parse(it) },
            concentrationPerUnit = concentrationPerValue?.let { UnitCode.ML },
            numberOfContainers = numberOfContainers,
            batchExpiryDate = batchExpiryDate,
            expiryAfterOpeningDays = 28,
            storageLocation = StorageLocation.FRIDGE,
            batchNumber = null,
            supplier = null,
            notes = null,
            deletedAt = null,
            createdAt = NOW,
            updatedAt = NOW,
        ),
    )

    private suspend fun insertOpened(
        compoundSupplyId: Long,
        remainingAmountValue: String,
        predictedExpiryDate: LocalDate? = null,
    ): Long = database.openedContainerDao().insert(
        OpenedContainerEntity(
            id = 0,
            compoundSupplyId = compoundSupplyId,
            openedAt = NOW,
            remainingAmountValue = Decimal.parse(remainingAmountValue),
            remainingAmountUnit = UnitCode.ML,
            expiryAfterOpeningDays = 28,
            userDefinedExpiryDate = null,
            predictedExpiryDate = predictedExpiryDate,
        ),
    )

    private suspend fun insertProtocol(
        compoundSupplyId: Long,
        name: String,
        plannedDoseValue: String,
        scheduleType: ScheduleType,
        interval: Int? = null,
        endDate: LocalDate? = null,
    ): Long = database.protocolDao().insert(
        ProtocolEntity(
            id = 0,
            name = name,
            compoundSupplyId = compoundSupplyId,
            plannedDoseValue = Decimal.parse(plannedDoseValue),
            plannedDoseUnit = UnitCode.MG,
            route = Route.SUBCUTANEOUS,
            schedule = ScheduleEmbed(
                type = scheduleType,
                interval = interval,
                timesPerDay = null,
                timesPerWeek = null,
                timesPerMonth = null,
            ),
            selectedWeekdaysBitmask = 0,
            escalation = null,
            protocolBreak = null,
            startDate = TODAY,
            endDate = endDate,
            reminderEnabled = false,
            reminderOffsetMinutes = 0,
            reminderBucket = ReminderBucket.MORNING,
            injectionSiteRestriction = BodyRegion.ABDOMEN,
            notes = null,
            status = ProtocolStatus.ACTIVE,
            siteCooldownDays = null,
            deletedAt = null,
            createdAt = NOW,
            updatedAt = NOW,
        ),
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.parse("2026-06-06")
        val NOW: Instant = Instant.parse("2026-06-06T00:00:00Z")
    }
}
