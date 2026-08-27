package com.stax.core.data.repository

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.stax.core.data.scheduler.ScheduledDoseGenerator
import com.stax.core.database.ScheduledDoseStatus
import com.stax.core.database.StaxDatabase
import com.stax.core.domain.Decimal
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.Route
import com.stax.core.domain.Schedule
import com.stax.core.domain.ScheduleType
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
import com.stax.core.database.ProtocolStatus as DbProtocolStatus

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProtocolRepositoryTest {

    private lateinit var database: StaxDatabase
    private lateinit var repository: RoomProtocolRepository

    // A fixed compound is required because protocol has an FK → compound_supply
    private var compoundId: Long = 0L

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StaxDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        repository = RoomProtocolRepository(
            database = database,
            protocolDao = database.protocolDao(),
            dosageTimeDao = database.protocolDosageTimeDao(),
            scheduledDoseDao = database.scheduledDoseDao(),
            generator = ScheduledDoseGenerator(),
        )

        // Pre-insert a compound so the FK constraint is satisfied
        compoundId = database.compoundSupplyDao().insert(compoundEntity())
    }

    @After
    fun tearDown() = database.close()

    // -----------------------------------------------------------------------
    // observeAll
    // -----------------------------------------------------------------------

    @Test
    fun `observeAll emits empty list initially`() = runTest {
        assertThat(repository.observeAll().first()).isEmpty()
    }

    @Test
    fun `observeAll emits protocol after creation`() = runTest {
        repository.create(dailyProtocol())
        assertThat(repository.observeAll().first()).hasSize(1)
    }

    @Test
    fun `observeAll excludes archived protocols`() = runTest {
        val id = (repository.create(dailyProtocol()) as Result.Success).data
        repository.archive(id)
        assertThat(repository.observeAll().first()).isEmpty()
    }

    // -----------------------------------------------------------------------
    // observeById
    // -----------------------------------------------------------------------

    @Test
    fun `observeById returns null for unknown id`() = runTest {
        assertThat(repository.observeById(999L).first()).isNull()
    }

    @Test
    fun `observeById returns protocol with correct fields`() = runTest {
        val id = (repository.create(dailyProtocol()) as Result.Success).data
        val protocol = repository.observeById(id).first()!!
        assertThat(protocol.name).isEqualTo("Sema weekly")
        assertThat(protocol.status).isEqualTo(ProtocolStatus.ACTIVE)
    }

    // -----------------------------------------------------------------------
    // create
    // -----------------------------------------------------------------------

    @Test
    fun `create returns new id`() = runTest {
        val result = repository.create(dailyProtocol())
        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat((result as Result.Success).data > 0).isEqualTo(true)
    }

    @Test
    fun `create generates scheduled doses for 7-day horizon`() = runTest {
        val id = (repository.create(dailyProtocol()) as Result.Success).data
        val doses = database.scheduledDoseDao().observeByProtocolId(id).first()
        assertThat(doses).hasSize(7)
    }

    @Test
    fun `create generated doses are all PENDING`() = runTest {
        val id = (repository.create(dailyProtocol()) as Result.Success).data
        val doses = database.scheduledDoseDao().observeByProtocolId(id).first()
        assertThat(doses.all { it.status == ScheduledDoseStatus.PENDING }).isEqualTo(true)
    }

    @Test
    fun `create persists dosage times`() = runTest {
        val protocol = dailyProtocol().copy(
            dosageTimes = listOf(
                kotlinx.datetime.LocalTime(8, 0),
                kotlinx.datetime.LocalTime(20, 0),
            ),
        )
        val id = (repository.create(protocol) as Result.Success).data
        val times = database.protocolDosageTimeDao().getByProtocolId(id)
        assertThat(times).hasSize(2)
    }

    // -----------------------------------------------------------------------
    // update — pending-regen scope rule (acceptance criterion §5.4)
    // -----------------------------------------------------------------------

    @Test
    fun `update regenerates pending doses (pending-regen scope rule)`() = runTest {
        val id = (repository.create(dailyProtocol()) as Result.Success).data
        val beforeCount = database.scheduledDoseDao().observeByProtocolId(id).first().size

        // Update name — should delete pending and regenerate
        val updated = repository.observeById(id).first()!!.copy(name = "Renamed")
        repository.update(updated)

        val afterCount = database.scheduledDoseDao().observeByProtocolId(id).first().size
        assertThat(afterCount).isEqualTo(beforeCount) // same 7-day horizon
    }

    @Test
    fun `update does NOT delete taken doses (pending-regen scope rule)`() = runTest {
        val id = (repository.create(dailyProtocol()) as Result.Success).data

        // Mark first dose as TAKEN (simulating a logged dose)
        val doses = database.scheduledDoseDao().observeByProtocolId(id).first()
        markAsTaken(doses.first().id)

        // Update protocol → only pending doses should be deleted + regenerated
        val updated = repository.observeById(id).first()!!.copy(name = "Renamed")
        repository.update(updated)

        val remaining = database.scheduledDoseDao().observeByProtocolId(id).first()
        val takenCount = remaining.count { it.status == ScheduledDoseStatus.TAKEN }
        assertThat(takenCount).isEqualTo(1)
    }

    @Test
    fun `update does not re-seed doses for a paused protocol`() = runTest {
        val id = (repository.create(dailyProtocol()) as Result.Success).data
        repository.pause(id)
        database.scheduledDoseDao().deletePendingUnloggedForProtocol(id)

        val updated = repository.observeById(id).first()!!.copy(name = "Renamed", status = ProtocolStatus.PAUSED)
        repository.update(updated)

        assertThat(database.scheduledDoseDao().observeByProtocolId(id).first()).isEmpty()
    }

    @Test
    fun `update replaces dosage times`() = runTest {
        val id = (repository.create(dailyProtocol()) as Result.Success).data
        val updated = repository.observeById(id).first()!!.copy(
            dosageTimes = listOf(kotlinx.datetime.LocalTime(9, 0)),
        )
        repository.update(updated)
        val times = database.protocolDosageTimeDao().getByProtocolId(id)
        assertThat(times).hasSize(1)
        assertThat(times.first().time).isEqualTo(kotlinx.datetime.LocalTime(9, 0))
    }

    // -----------------------------------------------------------------------
    // archive
    // -----------------------------------------------------------------------

    @Test
    fun `archive soft-deletes and purges pending doses`() = runTest {
        val id = (repository.create(dailyProtocol()) as Result.Success).data
        repository.archive(id)

        val protocol = repository.observeById(id).first()!!
        assertThat(protocol.deletedAt).isNotNull()

        val doses = database.scheduledDoseDao().observeByProtocolId(id).first()
        assertThat(doses.none { it.status == ScheduledDoseStatus.PENDING }).isEqualTo(true)
    }

    @Test
    fun `archive preserves taken doses`() = runTest {
        val id = (repository.create(dailyProtocol()) as Result.Success).data
        markAsTaken(database.scheduledDoseDao().observeByProtocolId(id).first().first().id)
        repository.archive(id)

        val doses = database.scheduledDoseDao().observeByProtocolId(id).first()
        assertThat(doses.count { it.status == ScheduledDoseStatus.TAKEN }).isEqualTo(1)
    }

    // -----------------------------------------------------------------------
    // pause / resume / complete
    // -----------------------------------------------------------------------

    @Test
    fun `pause sets status to PAUSED`() = runTest {
        val id = (repository.create(dailyProtocol()) as Result.Success).data
        repository.pause(id)
        val entity = database.protocolDao().getById(id)!!
        assertThat(entity.status).isEqualTo(DbProtocolStatus.PAUSED)
    }

    @Test
    fun `resume sets status to ACTIVE and regenerates doses`() = runTest {
        val id = (repository.create(dailyProtocol()) as Result.Success).data
        repository.pause(id)
        // Delete all pending manually to simulate that no doses existed while paused
        database.scheduledDoseDao().deletePendingUnloggedForProtocol(id)

        repository.resume(id)

        val entity = database.protocolDao().getById(id)!!
        assertThat(entity.status).isEqualTo(DbProtocolStatus.ACTIVE)
        val doses = database.scheduledDoseDao().observeByProtocolId(id).first()
        assertThat(doses.isNotEmpty()).isEqualTo(true)
    }

    @Test
    fun `complete sets status to COMPLETED and purges pending`() = runTest {
        val id = (repository.create(dailyProtocol()) as Result.Success).data
        repository.complete(id)

        val entity = database.protocolDao().getById(id)!!
        assertThat(entity.status).isEqualTo(DbProtocolStatus.COMPLETED)

        val pending = database.scheduledDoseDao().observeByProtocolId(id).first()
            .count { it.status == ScheduledDoseStatus.PENDING }
        assertThat(pending).isEqualTo(0)
    }

    // -----------------------------------------------------------------------
    // observeByCompoundSupplyId
    // -----------------------------------------------------------------------

    @Test
    fun `observeByCompoundSupplyId returns protocols for compound`() = runTest {
        repository.create(dailyProtocol())
        repository.create(dailyProtocol().copy(name = "Protocol B"))
        val protocols = repository.observeByCompoundSupplyId(compoundId).first()
        assertThat(protocols).hasSize(2)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val today = LocalDate(2026, 1, 1)

    private fun dailyProtocol(): Protocol = Protocol(
        id = 0L,
        name = "Sema weekly",
        compoundSupplyId = compoundId,
        plannedDose = Quantity(Decimal.parse("0.25"), UnitCode.MG),
        route = Route.SUBCUTANEOUS,
        schedule = Schedule(
            type = ScheduleType.DAILY,
            interval = null,
            timesPerDay = null,
            selectedWeekdays = null,
            timesPerWeek = null,
            timesPerMonth = null,
        ),
        dosageTimes = emptyList(),
        escalation = null,
        protocolBreak = null,
        startDate = today,
        endDate = null,
        reminderEnabled = false,
        reminderOffsetMinutes = 0,
        reminderBucket = null,
        injectionSiteRestriction = null,
        siteCooldownDays = null,
        notes = null,
        status = ProtocolStatus.ACTIVE,
        deletedAt = null,
        createdAt = now,
        updatedAt = now,
    )

    private fun markAsTaken(doseId: Long) {
        database.compileStatement(
            "UPDATE scheduled_dose SET status = 'TAKEN' WHERE id = $doseId",
        ).executeUpdateDelete()
    }

    private fun compoundEntity() = com.stax.core.database.CompoundSupplyEntity(
        id = 0,
        name = "Semaglutide",
        category = com.stax.core.database.CompoundCategory.PEPTIDE,
        form = com.stax.core.database.CompoundForm.INJECTABLE,
        containerType = com.stax.core.database.ContainerType.VIAL,
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
        storageLocation = com.stax.core.database.StorageLocation.FRIDGE,
        batchNumber = null,
        supplier = null,
        notes = null,
        deletedAt = null,
        createdAt = now,
        updatedAt = now,
    )
}
