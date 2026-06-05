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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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
class ScheduledDoseDaoTest {

    private lateinit var database: StaxDatabase
    private lateinit var compoundSupplyDao: CompoundSupplyDao
    private lateinit var protocolDao: ProtocolDao
    private lateinit var scheduledDoseDao: ScheduledDoseDao

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
        scheduledDoseDao = database.scheduledDoseDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert stores entity and observeByProtocolId returns it`() = runTest {
        val (_, protocolId) = insertProtocol()
        val dose = scheduledDose(protocolId = protocolId)

        scheduledDoseDao.insertOrIgnore(dose)

        assertThat(scheduledDoseDao.observeByProtocolId(protocolId).first())
            .containsExactly(dose.copy(id = 1))
    }

    @Test
    fun `insertOrIgnore is idempotent on duplicate protocolId and scheduledAt`() = runTest {
        val (_, protocolId) = insertProtocol()
        val dose = scheduledDose(protocolId = protocolId, scheduledAt = Instant.parse("2026-06-06T08:00:00Z"))

        val firstId = scheduledDoseDao.insertOrIgnore(dose)
        val secondId = scheduledDoseDao.insertOrIgnore(dose)

        assertThat(secondId).isEqualTo(-1L)
        assertThat(scheduledDoseDao.observeByProtocolId(protocolId).first())
            .containsExactly(dose.copy(id = firstId))
    }

    @Test
    fun `insertOrIgnore allows same scheduledAt for different protocols`() = runTest {
        val (compoundId, protocolId1) = insertProtocol(name = "Protocol 1")
        val protocolId2 = protocolDao.insert(protocol(name = "Protocol 2", compoundSupplyId = compoundId))
        val scheduledAt = Instant.parse("2026-06-06T08:00:00Z")

        scheduledDoseDao.insertOrIgnore(scheduledDose(protocolId = protocolId1, scheduledAt = scheduledAt))
        scheduledDoseDao.insertOrIgnore(scheduledDose(protocolId = protocolId2, scheduledAt = scheduledAt))

        assertThat(scheduledDoseDao.observeByProtocolId(protocolId1).first().size).isEqualTo(1)
        assertThat(scheduledDoseDao.observeByProtocolId(protocolId2).first().size).isEqualTo(1)
    }

    @Test
    fun `observePendingByProtocolId returns only pending doses with null administrationEventId`() = runTest {
        val (_, protocolId) = insertProtocol()
        val pending = scheduledDose(protocolId = protocolId, scheduledAt = Instant.parse("2026-06-06T08:00:00Z"), status = ScheduledDoseStatus.PENDING, administrationEventId = null)
        val taken = scheduledDose(protocolId = protocolId, scheduledAt = Instant.parse("2026-06-07T08:00:00Z"), status = ScheduledDoseStatus.TAKEN, administrationEventId = null)
        val pendingLinked = scheduledDose(protocolId = protocolId, scheduledAt = Instant.parse("2026-06-08T08:00:00Z"), status = ScheduledDoseStatus.PENDING, administrationEventId = 99L)

        val pendingId = scheduledDoseDao.insertOrIgnore(pending)
        scheduledDoseDao.insertOrIgnore(taken)
        scheduledDoseDao.insertOrIgnore(pendingLinked)

        assertThat(scheduledDoseDao.observePendingByProtocolId(protocolId).first())
            .containsExactly(pending.copy(id = pendingId))
    }

    @Test
    fun `deletePendingByProtocolId removes only pending doses with null administrationEventId`() = runTest {
        val (_, protocolId) = insertProtocol()
        val pending = scheduledDose(protocolId = protocolId, scheduledAt = Instant.parse("2026-06-06T08:00:00Z"), status = ScheduledDoseStatus.PENDING, administrationEventId = null)
        val taken = scheduledDose(protocolId = protocolId, scheduledAt = Instant.parse("2026-06-07T08:00:00Z"), status = ScheduledDoseStatus.TAKEN, administrationEventId = null)
        val pendingLinked = scheduledDose(protocolId = protocolId, scheduledAt = Instant.parse("2026-06-08T08:00:00Z"), status = ScheduledDoseStatus.PENDING, administrationEventId = 99L)

        scheduledDoseDao.insertOrIgnore(pending)
        val takenId = scheduledDoseDao.insertOrIgnore(taken)
        val linkedId = scheduledDoseDao.insertOrIgnore(pendingLinked)

        val deleted = scheduledDoseDao.deletePendingByProtocolId(protocolId)

        assertThat(deleted).isEqualTo(1)
        assertThat(scheduledDoseDao.observeByProtocolId(protocolId).first())
            .containsExactly(taken.copy(id = takenId), pendingLinked.copy(id = linkedId))
    }

    @Test
    fun `deleting protocol cascades scheduled doses`() = runTest {
        val (_, protocolId) = insertProtocol()
        scheduledDoseDao.insertOrIgnore(scheduledDose(protocolId = protocolId))

        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM protocol WHERE id = ?",
            arrayOf(protocolId),
        )

        assertThat(scheduledDoseDao.observeByProtocolId(protocolId).first()).containsExactly()
    }

    @Test
    fun `updateScheduledAt changes scheduledAt for given id`() = runTest {
        val (_, protocolId) = insertProtocol()
        val original = Instant.parse("2026-06-06T08:00:00Z")
        val updated = Instant.parse("2026-06-06T20:00:00Z")
        val id = scheduledDoseDao.insertOrIgnore(scheduledDose(protocolId = protocolId, scheduledAt = original))

        scheduledDoseDao.updateScheduledAt(id, updated)

        assertThat(scheduledDoseDao.observeByProtocolId(protocolId).first().first().scheduledAt)
            .isEqualTo(updated)
    }

    @Test
    fun `originalLocalTime is nullable and stored correctly`() = runTest {
        val (_, protocolId) = insertProtocol()
        val withTime = scheduledDose(
            protocolId = protocolId,
            scheduledAt = Instant.parse("2026-06-06T08:00:00Z"),
            hasTimeOfDay = true,
            originalLocalTime = LocalTime.parse("08:00"),
        )
        val withoutTime = scheduledDose(
            protocolId = protocolId,
            scheduledAt = Instant.parse("2026-06-07T23:59:59Z"),
            hasTimeOfDay = false,
            originalLocalTime = null,
        )

        val id1 = scheduledDoseDao.insertOrIgnore(withTime)
        val id2 = scheduledDoseDao.insertOrIgnore(withoutTime)

        val rows = scheduledDoseDao.observeByProtocolId(protocolId).first()
        assertThat(rows[0].originalLocalTime).isEqualTo(LocalTime.parse("08:00"))
        assertThat(rows[1].originalLocalTime).isNull()
    }

    private suspend fun insertProtocol(name: String = "Titration"): Pair<Long, Long> {
        val compoundId = compoundSupplyDao.insert(compound())
        val protocolId = protocolDao.insert(protocol(name = name, compoundSupplyId = compoundId))
        return compoundId to protocolId
    }

    private fun scheduledDose(
        id: Long = 0,
        protocolId: Long,
        compoundSupplyId: Long = 1,
        scheduledAt: Instant = Instant.parse("2026-06-06T08:00:00Z"),
        hasTimeOfDay: Boolean = true,
        plannedDoseValue: Decimal = Decimal.parse("0.5"),
        plannedDoseUnit: UnitCode = UnitCode.MG,
        route: Route = Route.SUBCUTANEOUS,
        status: ScheduledDoseStatus = ScheduledDoseStatus.PENDING,
        administrationEventId: Long? = null,
        originalLocalDate: LocalDate = LocalDate.parse("2026-06-06"),
        originalLocalTime: LocalTime? = LocalTime.parse("08:00"),
        originalZone: String = "Europe/Paris",
        createdAt: Instant = Instant.parse("2026-06-06T00:00:00Z"),
    ): ScheduledDoseEntity = ScheduledDoseEntity(
        id = id,
        protocolId = protocolId,
        compoundSupplyId = compoundSupplyId,
        scheduledAt = scheduledAt,
        hasTimeOfDay = hasTimeOfDay,
        plannedDoseValue = plannedDoseValue,
        plannedDoseUnit = plannedDoseUnit,
        route = route,
        status = status,
        administrationEventId = administrationEventId,
        originalLocalDate = originalLocalDate,
        originalLocalTime = originalLocalTime,
        originalZone = originalZone,
        createdAt = createdAt,
    )

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
        escalation: EscalationEmbed? = null,
        protocolBreak: ProtocolBreakEmbed? = null,
        startDate: LocalDate = LocalDate.parse("2026-06-06"),
        endDate: LocalDate? = LocalDate.parse("2026-12-31"),
        reminderEnabled: Boolean = true,
        reminderOffsetMinutes: Int = -15,
        reminderBucket: ReminderBucket? = ReminderBucket.MORNING,
        injectionSiteRestriction: BodyRegion? = BodyRegion.ABDOMEN,
        notes: String? = null,
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
