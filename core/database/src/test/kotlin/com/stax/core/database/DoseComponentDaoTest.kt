package com.stax.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
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
class DoseComponentDaoTest {

    private lateinit var database: StaxDatabase
    private lateinit var compoundSupplyDao: CompoundSupplyDao
    private lateinit var protocolDao: ProtocolDao
    private lateinit var scheduledDoseDao: ScheduledDoseDao
    private lateinit var administrationEventDao: AdministrationEventDao
    private lateinit var doseComponentDao: DoseComponentDao

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
        administrationEventDao = database.administrationEventDao()
        doseComponentDao = database.doseComponentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert stores entity and observeByAdministrationEventId returns it`() = runTest {
        val (compoundId, _, eventId) = insertParents()
        val component = doseComponent(administrationEventId = eventId, compoundSupplyId = compoundId)

        val id = doseComponentDao.insert(component)

        assertThat(doseComponentDao.observeByAdministrationEventId(eventId).first())
            .containsExactlyInAnyOrder(component.copy(id = id))
    }

    @Test
    fun `unique scheduledDoseId blocks double-logging the same scheduled dose`() = runTest {
        val (compoundId, protocolId, eventId1) = insertParents()
        val eventId2 = administrationEventDao.insert(administrationEvent())
        val scheduledDoseId = scheduledDoseDao.insertOrIgnore(
            scheduledDose(protocolId = protocolId, compoundSupplyId = compoundId),
        )

        doseComponentDao.insert(
            doseComponent(
                administrationEventId = eventId1,
                compoundSupplyId = compoundId,
                scheduledDoseId = scheduledDoseId,
            ),
        )

        val error = try {
            doseComponentDao.insert(
                doseComponent(
                    administrationEventId = eventId2,
                    compoundSupplyId = compoundId,
                    scheduledDoseId = scheduledDoseId,
                ),
            )
            null
        } catch (e: SQLiteConstraintException) {
            e
        }

        assertThat(error).isNotNull()
    }

    @Test
    fun `multiple null scheduledDoseId rows are allowed`() = runTest {
        val (compoundId, _, eventId) = insertParents()

        doseComponentDao.insert(
            doseComponent(administrationEventId = eventId, compoundSupplyId = compoundId, scheduledDoseId = null),
        )
        doseComponentDao.insert(
            doseComponent(administrationEventId = eventId, compoundSupplyId = compoundId, scheduledDoseId = null),
        )

        assertThat(doseComponentDao.observeByAdministrationEventId(eventId).first().size).isEqualTo(2)
    }

    @Test
    fun `cascade delete removes components when administration_event is deleted`() = runTest {
        val (compoundId, _, eventId) = insertParents()
        doseComponentDao.insert(doseComponent(administrationEventId = eventId, compoundSupplyId = compoundId))

        administrationEventDao.deleteById(eventId)

        assertThat(doseComponentDao.observeByAdministrationEventId(eventId).first()).containsExactlyInAnyOrder()
    }

    @Test
    fun `findByScheduledDoseId returns matching component`() = runTest {
        val (compoundId, protocolId, eventId) = insertParents()
        val scheduledDoseId = scheduledDoseDao.insertOrIgnore(
            scheduledDose(protocolId = protocolId, compoundSupplyId = compoundId),
        )
        val component =
            doseComponent(
                administrationEventId = eventId,
                compoundSupplyId = compoundId,
                scheduledDoseId = scheduledDoseId,
            )
        val id = doseComponentDao.insert(component)

        assertThat(doseComponentDao.findByScheduledDoseId(scheduledDoseId))
            .isEqualTo(component.copy(id = id))
    }

    @Test
    fun `findByScheduledDoseId returns null when no match`() = runTest {
        assertThat(doseComponentDao.findByScheduledDoseId(999L)).isNull()
    }

    @Test
    fun `nullable concentration fields stored and retrieved correctly`() = runTest {
        val (compoundId, _, eventId) = insertParents()
        val component = doseComponent(
            administrationEventId = eventId,
            compoundSupplyId = compoundId,
            concentrationAmountValue = null,
            concentrationAmountUnit = null,
            concentrationPerValue = null,
            concentrationPerUnit = null,
        )
        val id = doseComponentDao.insert(component)

        val stored = doseComponentDao.observeByAdministrationEventId(eventId).first().first()
        assertThat(stored.concentrationAmountValue).isNull()
        assertThat(stored.concentrationAmountUnit).isNull()
        assertThat(stored.concentrationPerValue).isNull()
        assertThat(stored.concentrationPerUnit).isNull()
    }

    @Test
    fun `nullable planned dose stored and retrieved correctly`() = runTest {
        val (compoundId, _, eventId) = insertParents()
        val component = doseComponent(
            administrationEventId = eventId,
            compoundSupplyId = compoundId,
            plannedDoseValue = null,
            plannedDoseUnit = null,
        )
        doseComponentDao.insert(component)

        val stored = doseComponentDao.observeByAdministrationEventId(eventId).first().first()
        assertThat(stored.plannedDoseValue).isNull()
        assertThat(stored.plannedDoseUnit).isNull()
    }

    @Test
    fun `insertAll inserts multiple components atomically`() = runTest {
        val (compoundId, _, eventId) = insertParents()
        val components = listOf(
            doseComponent(administrationEventId = eventId, compoundSupplyId = compoundId),
            doseComponent(administrationEventId = eventId, compoundSupplyId = compoundId),
        )

        doseComponentDao.insertAll(components)

        assertThat(doseComponentDao.observeByAdministrationEventId(eventId).first().size).isEqualTo(2)
    }

    private suspend fun insertParents(): Triple<Long, Long, Long> {
        val compoundId = compoundSupplyDao.insert(compound())
        val protocolId = protocolDao.insert(protocol(compoundSupplyId = compoundId))
        val eventId = administrationEventDao.insert(administrationEvent())
        return Triple(compoundId, protocolId, eventId)
    }

    private fun doseComponent(
        id: Long = 0,
        administrationEventId: Long,
        scheduledDoseId: Long? = null,
        protocolId: Long? = null,
        compoundSupplyId: Long,
        plannedDoseValue: Decimal? = Decimal.parse("0.5"),
        plannedDoseUnit: UnitCode? = UnitCode.MG,
        actualDoseValue: Decimal = Decimal.parse("0.5"),
        actualDoseUnit: UnitCode = UnitCode.MG,
        concentrationAmountValue: Decimal? = Decimal.parse("2.5"),
        concentrationAmountUnit: UnitCode? = UnitCode.MG,
        concentrationPerValue: Decimal? = Decimal.parse("1"),
        concentrationPerUnit: UnitCode? = UnitCode.ML,
        notes: String? = null,
        inventoryDeductedValue: Decimal = Decimal.parse("0.2"),
        inventoryDeductedUnit: UnitCode = UnitCode.ML,
    ): DoseComponentEntity = DoseComponentEntity(
        id = id,
        administrationEventId = administrationEventId,
        scheduledDoseId = scheduledDoseId,
        protocolId = protocolId,
        compoundSupplyId = compoundSupplyId,
        plannedDoseValue = plannedDoseValue,
        plannedDoseUnit = plannedDoseUnit,
        actualDoseValue = actualDoseValue,
        actualDoseUnit = actualDoseUnit,
        concentrationAmountValue = concentrationAmountValue,
        concentrationAmountUnit = concentrationAmountUnit,
        concentrationPerValue = concentrationPerValue,
        concentrationPerUnit = concentrationPerUnit,
        notes = notes,
        inventoryDeductedValue = inventoryDeductedValue,
        inventoryDeductedUnit = inventoryDeductedUnit,
    )

    private fun administrationEvent(
        id: Long = 0,
        loggedAt: Instant = Instant.parse("2026-06-06T08:00:00Z"),
        route: Route = Route.SUBCUTANEOUS,
        status: AdministrationEventStatus = AdministrationEventStatus.TAKEN,
        injectionSiteId: Long? = null,
        notes: String? = null,
        createdAt: Instant = Instant.parse("2026-06-06T00:00:00Z"),
        updatedAt: Instant = createdAt,
    ): AdministrationEventEntity = AdministrationEventEntity(
        id = id,
        loggedAt = loggedAt,
        route = route,
        status = status,
        injectionSiteId = injectionSiteId,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun scheduledDose(
        id: Long = 0,
        protocolId: Long,
        compoundSupplyId: Long,
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

    private fun protocol(id: Long = 0, name: String = "Titration", compoundSupplyId: Long): ProtocolEntity =
        ProtocolEntity(
            id = id,
            name = name,
            compoundSupplyId = compoundSupplyId,
            plannedDoseValue = Decimal.parse("0.5"),
            plannedDoseUnit = UnitCode.MG,
            route = Route.SUBCUTANEOUS,
            schedule = ScheduleEmbed(
                type = ScheduleType.SPECIFIC_WEEKDAYS,
                interval = null,
                timesPerDay = null,
                timesPerWeek = null,
                timesPerMonth = null,
            ),
            selectedWeekdaysBitmask = 0b0010101,
            escalation = null,
            protocolBreak = null,
            startDate = LocalDate.parse("2026-06-06"),
            endDate = null,
            reminderEnabled = false,
            reminderOffsetMinutes = 0,
            reminderBucket = null,
            injectionSiteRestriction = null,
            notes = null,
            status = ProtocolStatus.ACTIVE,
            siteCooldownDays = null,
            deletedAt = null,
            createdAt = Instant.parse("2026-06-06T00:00:00Z"),
            updatedAt = Instant.parse("2026-06-06T00:00:00Z"),
        )

    private fun compound(id: Long = 0, name: String = "Compound"): CompoundSupplyEntity = CompoundSupplyEntity(
        id = id,
        name = name,
        category = CompoundCategory.PEPTIDE,
        form = CompoundForm.INJECTABLE,
        containerType = ContainerType.VIAL,
        primaryUnit = UnitCode.MG,
        amountPerContainerValue = Decimal.parse("5"),
        amountPerContainerUnit = UnitCode.MG,
        concentrationAmountValue = Decimal.parse("2.5"),
        concentrationAmountUnit = UnitCode.MG,
        concentrationPerValue = Decimal.parse("1"),
        concentrationPerUnit = UnitCode.ML,
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
