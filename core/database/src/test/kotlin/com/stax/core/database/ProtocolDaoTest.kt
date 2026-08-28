package com.stax.core.database

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
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
class ProtocolDaoTest {

    private lateinit var database: StaxDatabase
    private lateinit var compoundSupplyDao: CompoundSupplyDao
    private lateinit var protocolDao: ProtocolDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StaxDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        compoundSupplyDao = database.compoundSupplyDao()
        protocolDao = database.protocolDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `embeds round trip through DAO without loss`() = runTest {
        val compoundId = compoundSupplyDao.insert(compound())
        val protocol = protocol(compoundSupplyId = compoundId)

        val id = protocolDao.insert(protocol)

        assertThat(protocolDao.observeById(id).first()).isEqualTo(protocol.copy(id = id))
    }

    @Test
    fun `nullable embeds round trip through DAO without loss`() = runTest {
        val compoundId = compoundSupplyDao.insert(compound())
        val protocol = protocol(
            compoundSupplyId = compoundId,
            schedule = ScheduleEmbed(
                type = ScheduleType.DAILY,
                interval = null,
                timesPerDay = null,
                timesPerWeek = null,
                timesPerMonth = null,
            ),
            selectedWeekdaysBitmask = 0,
            escalation = null,
            protocolBreak = null,
            reminderBucket = null,
            injectionSiteRestriction = null,
            siteCooldownDays = null,
        )

        val id = protocolDao.insert(protocol)

        assertThat(protocolDao.observeById(id).first()).isEqualTo(protocol.copy(id = id))
    }

    @Test
    fun `archived and active queries are complementary halves of the table`() = runTest {
        val compoundId = compoundSupplyDao.insert(compound())
        val liveId = protocolDao.insert(protocol(name = "Live", compoundSupplyId = compoundId))
        val archivedId = protocolDao.insert(
            protocol(
                name = "Archived",
                compoundSupplyId = compoundId,
                // Archived is `deletedAt != null` whatever the status is (§4.7.2), so this row is
                // Active and still belongs to the Archived half.
                status = ProtocolStatus.ACTIVE,
                deletedAt = Instant.parse("2026-06-06T10:00:00Z"),
            ),
        )

        assertThat(protocolDao.observeActiveWithDosageTimes().first().map { it.protocol.id })
            .containsExactly(liveId)
        assertThat(protocolDao.observeArchivedWithDosageTimes().first().map { it.protocol.id })
            .containsExactly(archivedId)
    }

    private fun protocol(
        id: Long = 0,
        name: String = "Titration",
        compoundSupplyId: Long,
        plannedDoseValue: Decimal = Decimal.parse("0.5"),
        plannedDoseUnit: UnitCode = UnitCode.MG,
        route: Route = Route.SUBCUTANEOUS,
        schedule: ScheduleEmbed = ScheduleEmbed(
            type = ScheduleType.SPECIFIC_WEEKDAYS,
            interval = null,
            timesPerDay = null,
            timesPerWeek = null,
            timesPerMonth = null,
        ),
        selectedWeekdaysBitmask: Int = 0b0010101,
        escalation: EscalationEmbed? = EscalationEmbed(
            startDoseValue = Decimal.parse("0.25"),
            startDoseUnit = UnitCode.MG,
            targetDoseValue = Decimal.parse("1"),
            targetDoseUnit = UnitCode.MG,
            increaseAmountValue = Decimal.parse("0.25"),
            increaseAmountUnit = UnitCode.MG,
            increaseEvery = EscalationIncreaseEvery.EVERY_X_WEEKS,
            increaseEveryValue = 2,
            maxDoseValue = Decimal.parse("1.5"),
            maxDoseUnit = UnitCode.MG,
            stopAtTarget = true,
        ),
        protocolBreak: ProtocolBreakEmbed? = ProtocolBreakEmbed(
            daysOn = 56,
            daysOff = 28,
        ),
        startDate: LocalDate = LocalDate.parse("2026-06-06"),
        endDate: LocalDate? = LocalDate.parse("2026-12-31"),
        reminderEnabled: Boolean = true,
        reminderOffsetMinutes: Int = -15,
        reminderBucket: ReminderBucket? = ReminderBucket.MORNING,
        injectionSiteRestriction: BodyRegion? = BodyRegion.ABDOMEN,
        notes: String? = "Rotate injection sites.",
        status: ProtocolStatus = ProtocolStatus.ACTIVE,
        siteCooldownDays: Int? = 5,
        deletedAt: Instant? = null,
        createdAt: Instant = Instant.parse("2026-06-06T00:00:00Z"),
        updatedAt: Instant = createdAt,
    ): ProtocolEntity = ProtocolEntity(
        id = id,
        name = name,
        compoundSupplyId = compoundSupplyId,
        plannedDoseValue = plannedDoseValue,
        plannedDoseUnit = plannedDoseUnit,
        route = route,
        schedule = schedule,
        selectedWeekdaysBitmask = selectedWeekdaysBitmask,
        escalation = escalation,
        protocolBreak = protocolBreak,
        startDate = startDate,
        endDate = endDate,
        reminderEnabled = reminderEnabled,
        reminderOffsetMinutes = reminderOffsetMinutes,
        reminderBucket = reminderBucket,
        injectionSiteRestriction = injectionSiteRestriction,
        notes = notes,
        status = status,
        siteCooldownDays = siteCooldownDays,
        deletedAt = deletedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
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
