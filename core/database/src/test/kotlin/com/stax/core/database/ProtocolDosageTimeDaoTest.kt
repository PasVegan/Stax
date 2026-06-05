package com.stax.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isNotNull
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
class ProtocolDosageTimeDaoTest {

    private lateinit var database: StaxDatabase
    private lateinit var compoundSupplyDao: CompoundSupplyDao
    private lateinit var protocolDao: ProtocolDao
    private lateinit var protocolDosageTimeDao: ProtocolDosageTimeDao

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
        protocolDosageTimeDao = database.protocolDosageTimeDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert stores dosage times ordered by ISO time`() = runTest {
        val protocolId = insertProtocol()
        val morning = ProtocolDosageTimeEntity(protocolId, LocalTime.parse("08:30"))
        val evening = ProtocolDosageTimeEntity(protocolId, LocalTime.parse("20:15"))

        protocolDosageTimeDao.insert(evening)
        protocolDosageTimeDao.insert(morning)

        assertThat(protocolDosageTimeDao.observeByProtocolId(protocolId).first())
            .containsExactly(morning, evening)
    }

    @Test
    fun `duplicate protocolId and time fails with constraint violation`() = runTest {
        val protocolId = insertProtocol()
        val dosageTime = ProtocolDosageTimeEntity(protocolId, LocalTime.parse("09:00"))
        protocolDosageTimeDao.insert(dosageTime)

        val error = try {
            protocolDosageTimeDao.insert(dosageTime)
            null
        } catch (e: SQLiteConstraintException) {
            e
        }

        assertThat(error).isNotNull()
    }

    @Test
    fun `deleting protocol cascades dosage times`() = runTest {
        val protocolId = insertProtocol()
        protocolDosageTimeDao.insert(
            ProtocolDosageTimeEntity(protocolId, LocalTime.parse("09:00")),
        )

        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM protocol WHERE id = ?",
            arrayOf(protocolId),
        )

        assertThat(protocolDosageTimeDao.observeByProtocolId(protocolId).first()).containsExactly()
    }

    private suspend fun insertProtocol(): Long {
        val compoundId = compoundSupplyDao.insert(compound())
        return protocolDao.insert(protocol(compoundSupplyId = compoundId))
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
