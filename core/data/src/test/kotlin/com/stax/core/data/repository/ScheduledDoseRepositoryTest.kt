package com.stax.core.data.repository

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.stax.core.database.BodyRegion
import com.stax.core.database.CompoundCategory
import com.stax.core.database.CompoundForm
import com.stax.core.database.CompoundSupplyEntity
import com.stax.core.database.ContainerType
import com.stax.core.database.ProtocolEntity
import com.stax.core.database.ReminderBucket
import com.stax.core.database.Route
import com.stax.core.database.ScheduleEmbed
import com.stax.core.database.ScheduleType
import com.stax.core.database.ScheduledDoseEntity
import com.stax.core.database.StaxDatabase
import com.stax.core.database.StorageLocation
import com.stax.core.domain.DataError
import com.stax.core.domain.Decimal
import com.stax.core.domain.Result
import com.stax.core.domain.UnitCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import com.stax.core.database.ProtocolStatus as DbProtocolStatus
import com.stax.core.database.ScheduledDoseStatus as DbScheduledDoseStatus
import com.stax.core.domain.ScheduledDoseStatus as DomainScheduledDoseStatus

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScheduledDoseRepositoryTest {

    private lateinit var database: StaxDatabase
    private lateinit var repository: RoomScheduledDoseRepository
    private var compoundId: Long = 0L
    private var protocolId: Long = 0L

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StaxDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        repository = RoomScheduledDoseRepository(database.scheduledDoseDao())
        compoundId = database.compoundSupplyDao().insert(compound())
        protocolId = database.protocolDao().insert(protocol(compoundSupplyId = compoundId))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `observePending emits only pending unlogged doses for date with all-day doses last`() = runTest {
        val zone = TimeZone.of("Europe/Paris")
        val timedId = insertDose(
            scheduledAt = Instant.parse("2026-06-06T08:00:00Z"),
            hasTimeOfDay = true,
            originalLocalTime = LocalTime.parse("10:00"),
        )
        val allDayId = insertDose(
            scheduledAt = Instant.parse("2026-06-05T22:00:00Z"),
            hasTimeOfDay = false,
            originalLocalDate = LocalDate.parse("2026-06-06"),
            originalLocalTime = null,
        )
        insertDose(
            scheduledAt = Instant.parse("2026-06-06T09:00:00Z"),
            status = DbScheduledDoseStatus.TAKEN,
        )
        insertDose(
            scheduledAt = Instant.parse("2026-06-06T10:00:00Z"),
            administrationEventId = 44L,
        )
        insertDose(scheduledAt = Instant.parse("2026-06-06T22:30:00Z"))

        val result = repository.observePending(LocalDate.parse("2026-06-06"), zone).first()

        assertThat(result.map { it.id }).containsExactly(timedId, allDayId)
    }

    @Test
    fun `observeForProtocol emits all doses for protocol`() = runTest {
        val pendingId = insertDose(status = DbScheduledDoseStatus.PENDING)
        val skippedId = insertDose(
            scheduledAt = Instant.parse("2026-06-07T08:00:00Z"),
            status = DbScheduledDoseStatus.SKIPPED,
        )

        val result = repository.observeForProtocol(protocolId).first()

        assertThat(result.map { it.id }).containsExactly(pendingId, skippedId)
    }

    @Test
    fun `snooze updates scheduledAt and preserves originalLocalTime`() = runTest {
        val originalScheduledAt = Instant.parse("2026-06-06T08:00:00Z")
        val id = insertDose(
            scheduledAt = originalScheduledAt,
            originalLocalTime = LocalTime.parse("10:00"),
        )

        val result = repository.snooze(id, 3.hours)

        assertThat(result).isInstanceOf(Result.Success::class)
        val stored = database.scheduledDoseDao().getById(id)!!
        assertThat(stored.scheduledAt).isEqualTo(originalScheduledAt + 3.hours)
        assertThat(stored.originalLocalTime).isEqualTo(LocalTime.parse("10:00"))
    }

    @Test
    fun `snooze returns NOT_FOUND for non-pending dose`() = runTest {
        val id = insertDose(status = DbScheduledDoseStatus.SKIPPED)

        val result = repository.snooze(id, 1.days)

        assertThat(result).isInstanceOf(Result.Error::class)
        assertThat((result as Result.Error).error).isEqualTo(DataError.Local.NOT_FOUND)
    }

    @Test
    fun `skip marks pending dose as skipped`() = runTest {
        val id = insertDose()

        repository.skip(id)

        val stored = repository.observeForProtocol(protocolId).first().single()
        assertThat(stored.status).isEqualTo(DomainScheduledDoseStatus.SKIPPED)
        assertThat(stored.administrationEventId).isNull()
    }

    @Test
    fun `markMissed marks pending dose as missed without event`() = runTest {
        val id = insertDose()

        repository.markMissed(id)

        val stored = repository.observeForProtocol(protocolId).first().single()
        assertThat(stored.status).isEqualTo(DomainScheduledDoseStatus.MISSED)
        assertThat(stored.administrationEventId).isNull()
    }

    @Test
    fun `markTaken marks pending dose as taken and links event`() = runTest {
        val id = insertDose()

        repository.markTaken(id, eventId = 99L)

        val stored = repository.observeForProtocol(protocolId).first().single()
        assertThat(stored.status).isEqualTo(DomainScheduledDoseStatus.TAKEN)
        assertThat(stored.administrationEventId).isEqualTo(99L)
    }

    private suspend fun insertDose(
        scheduledAt: Instant = Instant.parse("2026-06-06T08:00:00Z"),
        hasTimeOfDay: Boolean = true,
        status: DbScheduledDoseStatus = DbScheduledDoseStatus.PENDING,
        administrationEventId: Long? = null,
        originalLocalDate: LocalDate = LocalDate.parse("2026-06-06"),
        originalLocalTime: LocalTime? = LocalTime.parse("08:00"),
    ): Long = database.scheduledDoseDao().insertOrIgnore(
        ScheduledDoseEntity(
            protocolId = protocolId,
            compoundSupplyId = compoundId,
            scheduledAt = scheduledAt,
            hasTimeOfDay = hasTimeOfDay,
            plannedDoseValue = Decimal.parse("0.5"),
            plannedDoseUnit = UnitCode.MG,
            route = Route.SUBCUTANEOUS,
            status = status,
            administrationEventId = administrationEventId,
            originalLocalDate = originalLocalDate,
            originalLocalTime = originalLocalTime,
            originalZone = "Europe/Paris",
            createdAt = Instant.parse("2026-06-06T00:00:00Z"),
        ),
    )

    private fun protocol(compoundSupplyId: Long, name: String = "Titration"): ProtocolEntity = ProtocolEntity(
        id = 0,
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
        endDate = LocalDate.parse("2026-12-31"),
        reminderEnabled = true,
        reminderOffsetMinutes = -15,
        reminderBucket = ReminderBucket.MORNING,
        injectionSiteRestriction = BodyRegion.ABDOMEN,
        notes = null,
        status = DbProtocolStatus.ACTIVE,
        siteCooldownDays = 5,
        deletedAt = null,
        createdAt = Instant.parse("2026-06-06T00:00:00Z"),
        updatedAt = Instant.parse("2026-06-06T00:00:00Z"),
    )

    private fun compound(): CompoundSupplyEntity = CompoundSupplyEntity(
        id = 0,
        name = "Compound",
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
        batchExpiryDate = LocalDate.parse("2026-12-31"),
        expiryAfterOpeningDays = 28,
        storageLocation = StorageLocation.FRIDGE,
        batchNumber = "BATCH-1",
        supplier = "Supplier",
        notes = null,
        deletedAt = null,
        createdAt = Instant.parse("2026-06-06T00:00:00Z"),
        updatedAt = Instant.parse("2026-06-06T00:00:00Z"),
    )
}
