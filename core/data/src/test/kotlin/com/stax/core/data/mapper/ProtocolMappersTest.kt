package com.stax.core.data.mapper

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.stax.core.database.BodyRegion as DbBodyRegion
import com.stax.core.database.EscalationEmbed
import com.stax.core.database.EscalationIncreaseEvery as DbEscalationIncreaseEvery
import com.stax.core.database.ProtocolBreakEmbed
import com.stax.core.database.ProtocolDosageTimeEntity
import com.stax.core.database.ProtocolEntity
import com.stax.core.database.ProtocolStatus as DbProtocolStatus
import com.stax.core.database.ReminderBucket as DbReminderBucket
import com.stax.core.database.Route as DbRoute
import com.stax.core.database.ScheduleEmbed
import com.stax.core.database.ScheduleType as DbScheduleType
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant
import org.junit.jupiter.api.Test

private val NOW: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
private val TODAY: LocalDate = LocalDate(2023, 11, 14)
private fun dec(s: String): Decimal = Decimal.parse(s)

private fun dailySchedule() = ScheduleEmbed(
    type = DbScheduleType.DAILY,
    interval = null,
    timesPerDay = null,
    timesPerWeek = null,
    timesPerMonth = null,
)

private fun minimalEntity() = ProtocolEntity(
    id = 10L,
    name = "Sema weekly",
    compoundSupplyId = 1L,
    plannedDoseValue = dec("0.25"),
    plannedDoseUnit = UnitCode.MG,
    route = DbRoute.SUBCUTANEOUS,
    schedule = dailySchedule(),
    selectedWeekdaysBitmask = 0,
    escalation = null,
    protocolBreak = null,
    startDate = TODAY,
    endDate = null,
    reminderEnabled = false,
    reminderOffsetMinutes = 0,
    reminderBucket = null,
    injectionSiteRestriction = null,
    siteCooldownDays = null,
    notes = null,
    status = DbProtocolStatus.ACTIVE,
    deletedAt = null,
    createdAt = NOW,
    updatedAt = NOW,
)

class ProtocolMappersTest {

    // -----------------------------------------------------------------------
    // ProtocolEntity ↔ Protocol — minimal (no escalation, no break, no weekdays)
    // -----------------------------------------------------------------------

    @Test
    fun `round-trip minimal entity`() {
        val entity = minimalEntity()
        val roundTripped = entity.toDomain(emptyList()).toEntity()
        assertThat(roundTripped).isEqualTo(entity)
    }

    // -----------------------------------------------------------------------
    // Escalation embed
    // -----------------------------------------------------------------------

    @Test
    fun `round-trip with full escalation`() {
        val entity = minimalEntity().copy(
            escalation = EscalationEmbed(
                startDoseValue = dec("0.25"),
                startDoseUnit = UnitCode.MG,
                targetDoseValue = dec("1.0"),
                targetDoseUnit = UnitCode.MG,
                increaseAmountValue = dec("0.25"),
                increaseAmountUnit = UnitCode.MG,
                increaseEvery = DbEscalationIncreaseEvery.EVERY_X_WEEKS,
                increaseEveryValue = 4,
                maxDoseValue = null,
                maxDoseUnit = null,
                stopAtTarget = true,
            ),
        )
        val roundTripped = entity.toDomain(emptyList()).toEntity()
        assertThat(roundTripped).isEqualTo(entity)
    }

    @Test
    fun `round-trip with escalation max dose`() {
        val entity = minimalEntity().copy(
            escalation = EscalationEmbed(
                startDoseValue = dec("0.25"),
                startDoseUnit = UnitCode.MG,
                targetDoseValue = dec("2.0"),
                targetDoseUnit = UnitCode.MG,
                increaseAmountValue = dec("0.25"),
                increaseAmountUnit = UnitCode.MG,
                increaseEvery = DbEscalationIncreaseEvery.AFTER_X_DOSES,
                increaseEveryValue = 2,
                maxDoseValue = dec("2.0"),
                maxDoseUnit = UnitCode.MG,
                stopAtTarget = false,
            ),
        )
        val roundTripped = entity.toDomain(emptyList()).toEntity()
        assertThat(roundTripped).isEqualTo(entity)
    }

    @Test
    fun `null escalation embed maps to null domain escalation`() {
        assertThat(minimalEntity().toDomain().escalation).isNull()
    }

    // -----------------------------------------------------------------------
    // Protocol break embed
    // -----------------------------------------------------------------------

    @Test
    fun `round-trip with protocol break`() {
        val entity = minimalEntity().copy(
            protocolBreak = ProtocolBreakEmbed(daysOn = 56, daysOff = 14),
        )
        val roundTripped = entity.toDomain(emptyList()).toEntity()
        assertThat(roundTripped).isEqualTo(entity)
    }

    // -----------------------------------------------------------------------
    // DayOfWeek bitmask — SPECIFIC_WEEKDAYS schedule
    // -----------------------------------------------------------------------

    @Test
    fun `round-trip specific weekdays bitmask Mon+Wed+Fri`() {
        // Monday=1, Wednesday=4, Friday=16 → bitmask = 0b_0_010_101 = 21
        val monWedFriMask = (1 shl 0) or (1 shl 2) or (1 shl 4)  // 21
        val entity = minimalEntity().copy(
            schedule = ScheduleEmbed(
                type = DbScheduleType.SPECIFIC_WEEKDAYS,
                interval = null,
                timesPerDay = null,
                timesPerWeek = null,
                timesPerMonth = null,
            ),
            selectedWeekdaysBitmask = monWedFriMask,
        )
        val domain = entity.toDomain()
        assertThat(domain.schedule.selectedWeekdays!!).containsExactlyInAnyOrder(
            DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY,
        )
        val roundTripped = domain.toEntity()
        assertThat(roundTripped).isEqualTo(entity)
    }

    @Test
    fun `zero bitmask maps to null selectedWeekdays`() {
        assertThat(minimalEntity().toDomain().schedule.selectedWeekdays).isNull()
    }

    // -----------------------------------------------------------------------
    // Optional fields: reminderBucket, injectionSiteRestriction, endDate
    // -----------------------------------------------------------------------

    @Test
    fun `round-trip with all optional fields set`() {
        val entity = minimalEntity().copy(
            endDate = TODAY,
            reminderEnabled = true,
            reminderOffsetMinutes = -15,
            reminderBucket = DbReminderBucket.MORNING,
            injectionSiteRestriction = DbBodyRegion.ABDOMEN,
            siteCooldownDays = 5,
            notes = "Take before bed",
        )
        val roundTripped = entity.toDomain(emptyList()).toEntity()
        assertThat(roundTripped).isEqualTo(entity)
    }

    // -----------------------------------------------------------------------
    // Dosage times
    // -----------------------------------------------------------------------

    @Test
    fun `toDosageTimeEntities round-trip`() {
        val times = listOf(LocalTime(8, 0), LocalTime(20, 0))
        val domain = minimalEntity().toDomain(times)
        val entities = domain.toDosageTimeEntities()
        val expected = times.map { ProtocolDosageTimeEntity(protocolId = 10L, time = it) }
        assertThat(entities).isEqualTo(expected)
    }

    @Test
    fun `toDomain preserves dosage times`() {
        val times = listOf(LocalTime(7, 30))
        val domain = minimalEntity().toDomain(times)
        assertThat(domain.dosageTimes).isEqualTo(times)
    }
}
