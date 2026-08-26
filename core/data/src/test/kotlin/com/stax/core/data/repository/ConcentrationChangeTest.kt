package com.stax.core.data.repository

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.stax.core.database.AdministrationEventEntity
import com.stax.core.database.AdministrationEventStatus
import com.stax.core.database.DoseComponentEntity
import com.stax.core.database.ProtocolEntity
import com.stax.core.database.ProtocolStatus
import com.stax.core.database.Route
import com.stax.core.database.ScheduleEmbed
import com.stax.core.database.ScheduleType
import com.stax.core.database.ScheduledDoseEntity
import com.stax.core.database.ScheduledDoseStatus
import com.stax.core.database.StaxDatabase
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.Concentration
import com.stax.core.domain.ContainerType
import com.stax.core.domain.Decimal
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.StorageLocation
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

/**
 * What changing a compound's concentration does to the doses around it (§4.6.7, M8-04).
 *
 * A `scheduled_dose` plans a **dose**, never the volume that dose comes to, so the volume a Pending
 * row displays is `plannedDose / concentration` read live off the compound: a new mix restates every
 * pending row without one of them being rewritten. A logged dose is the opposite — `dose_component`
 * snapshots the concentration it was given at (§3.5), so history keeps the volume that was actually
 * drawn into the syringe.
 *
 * Its own class rather than another case in `CompoundRepositoryTest` because it spans three tables:
 * what it asserts is what the write does *not* touch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConcentrationChangeTest {

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

    /** M8-04's acceptance: pending doses reflect the new concentration, logged history does not. */
    @Test
    fun `setting a concentration restates pending doses and leaves logged history alone`() = runTest {
        val compoundId = (repository.create(compound()) as Result.Success).data
        val protocolId = database.protocolDao().insert(protocol(compoundId))
        val pendingId = database.scheduledDoseDao().insertOrIgnore(pendingDose(protocolId, compoundId))
        val eventId = database.administrationEventDao().insert(loggedEvent())
        database.doseComponentDao().insert(loggedComponent(eventId, compoundId))
        val stored = repository.observeById(compoundId).first()!!

        val result = repository.update(stored.copy(concentration = concentrationOf("2.5")))

        assertThat(result).isInstanceOf<Result.Success<*>>()
        // The row every pending dose divides by now reads 2.5 mg/mL...
        assertThat(repository.observeById(compoundId).first()?.concentration).isEqualTo(concentrationOf("2.5"))
        // ...while the pending dose still plans the same 0.5 mg, which is 0.20 mL at the new mix
        // where it was 0.10 mL at the old one.
        val pending = database.scheduledDoseDao().getById(pendingId)!!
        assertThat(pending.plannedDoseValue).isEqualTo(Decimal.parse("0.5"))
        assertThat(pending.plannedDoseUnit).isEqualTo(UnitCode.MG)
        assertThat(pending.status).isEqualTo(ScheduledDoseStatus.PENDING)
        // ...and the dose already logged keeps the 5 mg/mL it was actually drawn at.
        val logged = database.doseComponentDao().getByAdministrationEventId(eventId).single()
        assertThat(logged.concentrationAmountValue).isEqualTo(Decimal.parse("5"))
        assertThat(logged.actualDoseValue).isEqualTo(Decimal.parse("0.5"))
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private val now = Instant.parse("2026-06-06T00:00:00Z")

    private fun concentrationOf(perMl: String) = Concentration(
        amount = Quantity(Decimal.parse(perMl), UnitCode.MG),
        per = Quantity(Decimal.parse("1"), UnitCode.ML),
    )

    /** Opened at 5 mg/mL — the mix the logged dose below was drawn at. */
    private fun compound() = CompoundSupply(
        id = 0L,
        name = "BPC-157",
        category = CompoundCategory.PEPTIDE,
        form = CompoundForm.INJECTABLE,
        containerType = ContainerType.VIAL,
        primaryUnit = UnitCode.MG,
        amountPerContainer = Quantity(Decimal.parse("5"), UnitCode.MG),
        concentration = concentrationOf("5"),
        numberOfContainers = 1,
        currentOpened = null,
        batchExpiryDate = null,
        expiryAfterOpeningDays = null,
        storageLocation = StorageLocation.FRIDGE,
        batchNumber = null,
        supplier = null,
        notes = null,
        deletedAt = null,
        createdAt = now,
        updatedAt = now,
    )

    private fun protocol(compoundSupplyId: Long) = ProtocolEntity(
        name = "Titration",
        compoundSupplyId = compoundSupplyId,
        plannedDoseValue = Decimal.parse("0.5"),
        plannedDoseUnit = UnitCode.MG,
        route = Route.SUBCUTANEOUS,
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
        createdAt = now,
        updatedAt = now,
    )

    private fun pendingDose(protocolId: Long, compoundSupplyId: Long) = ScheduledDoseEntity(
        protocolId = protocolId,
        compoundSupplyId = compoundSupplyId,
        scheduledAt = Instant.parse("2026-06-07T08:00:00Z"),
        hasTimeOfDay = true,
        plannedDoseValue = Decimal.parse("0.5"),
        plannedDoseUnit = UnitCode.MG,
        route = Route.SUBCUTANEOUS,
        status = ScheduledDoseStatus.PENDING,
        administrationEventId = null,
        originalLocalDate = LocalDate.parse("2026-06-07"),
        originalLocalTime = LocalTime.parse("10:00"),
        originalZone = "Europe/Paris",
        createdAt = now,
    )

    private fun loggedEvent() = AdministrationEventEntity(
        loggedAt = Instant.parse("2026-06-06T08:00:00Z"),
        route = Route.SUBCUTANEOUS,
        status = AdministrationEventStatus.TAKEN,
        injectionSiteId = null,
        notes = null,
        createdAt = now,
        updatedAt = now,
    )

    /** Logged at 5 mg/mL: 0.5 mg drawn as 0.10 mL, and that is what history has to keep saying. */
    private fun loggedComponent(administrationEventId: Long, compoundSupplyId: Long) = DoseComponentEntity(
        administrationEventId = administrationEventId,
        scheduledDoseId = null,
        protocolId = null,
        compoundSupplyId = compoundSupplyId,
        plannedDoseValue = Decimal.parse("0.5"),
        plannedDoseUnit = UnitCode.MG,
        actualDoseValue = Decimal.parse("0.5"),
        actualDoseUnit = UnitCode.MG,
        concentrationAmountValue = Decimal.parse("5"),
        concentrationAmountUnit = UnitCode.MG,
        concentrationPerValue = Decimal.parse("1"),
        concentrationPerUnit = UnitCode.ML,
        notes = null,
        inventoryDeductedValue = Decimal.parse("0.5"),
        inventoryDeductedUnit = UnitCode.MG,
    )
}
