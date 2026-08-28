package com.stax.core.domain

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/** A Friday, so the weekday cases are not accidentally anchored on a Monday. */
private val start = LocalDate(2026, 1, 2)

/**
 * The schedule rule of §5.2, tested where it now lives. `ScheduledDoseGeneratorTest` covers the same
 * rules end to end through the rows they produce; this file pins the rule itself, which is what a
 * screen previewing an unsaved schedule (§4.9.3) reads.
 */
class ScheduleEngineTest {

    @Test
    fun `Daily doses every day, with no time of day when dosageTimes is empty`() {
        val protocol = protocol(Schedule(ScheduleType.DAILY, null, null, null, null, null))

        assertThat(protocol.dosingTimesOn(start)).containsExactly(null)
        assertThat(protocol.dosingTimesOn(start.plus(3, DateTimeUnit.DAY))).containsExactly(null)
    }

    @Test
    fun `dosageTimes yield one dose per time on every dosing day`() {
        val times = listOf(LocalTime(8, 0), LocalTime(20, 0))
        val protocol = protocol(
            Schedule(ScheduleType.EVERY_X_DAYS, interval = 2, null, null, null, null),
            dosageTimes = times,
        )

        assertThat(protocol.dosingTimesOn(start)).isEqualTo(times)
        assertThat(protocol.dosingTimesOn(start.plus(1, DateTimeUnit.DAY))).isEmpty()
        assertThat(protocol.dosingTimesOn(start.plus(2, DateTimeUnit.DAY))).isEqualTo(times)
    }

    @Test
    fun `XTimesPerDay without dosageTimes spreads its doses evenly over the day`() {
        val protocol = protocol(Schedule(ScheduleType.X_TIMES_PER_DAY, null, timesPerDay = 3, null, null, null))

        assertThat(protocol.dosingTimesOn(start))
            .containsExactly(LocalTime(0, 0), LocalTime(8, 0), LocalTime(16, 0))
    }

    @Test
    fun `SpecificWeekdays doses only on the selected days`() {
        val protocol = protocol(
            Schedule(
                ScheduleType.SPECIFIC_WEEKDAYS,
                null,
                null,
                selectedWeekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                null,
                null,
            ),
        )

        // start is a Friday; Monday is 3 days on, Thursday 6.
        assertThat(protocol.dosingTimesOn(start)).isEmpty()
        assertThat(protocol.dosingTimesOn(start.plus(3, DateTimeUnit.DAY))).containsExactly(null)
        assertThat(protocol.dosingTimesOn(start.plus(6, DateTimeUnit.DAY))).containsExactly(null)
    }

    @Test
    fun `XTimesPerWeek places exactly n doses a week, spread rather than clustered`() {
        val protocol = protocol(Schedule(ScheduleType.X_TIMES_PER_WEEK, null, null, null, timesPerWeek = 3, null))

        val dosingDays = (0..6).filter { protocol.dosingTimesOn(start.plus(it, DateTimeUnit.DAY)).isNotEmpty() }
        assertThat(dosingDays).containsExactly(0, 3, 5)
    }

    @Test
    fun `XTimesPerMonth places exactly n doses over the 30-day cycle`() {
        val protocol = protocol(Schedule(ScheduleType.X_TIMES_PER_MONTH, null, null, null, null, timesPerMonth = 2))

        assertThat(protocol.dosesBetween(start, start.plus(30, DateTimeUnit.DAY))).isEqualTo(2)
    }

    @Test
    fun `nothing is placed before startDate or after endDate`() {
        val protocol = protocol(
            Schedule(ScheduleType.DAILY, null, null, null, null, null),
            endDate = start.plus(2, DateTimeUnit.DAY),
        )

        assertThat(protocol.dosingTimesOn(start.plus(-1, DateTimeUnit.DAY))).isEmpty()
        assertThat(protocol.dosingTimesOn(start.plus(2, DateTimeUnit.DAY))).containsExactly(null)
        assertThat(protocol.dosingTimesOn(start.plus(3, DateTimeUnit.DAY))).isEmpty()
    }

    @Test
    fun `a break skips its off-days and resumes on the next cycle`() {
        val protocol = protocol(
            Schedule(ScheduleType.DAILY, null, null, null, null, null),
            protocolBreak = ProtocolBreak(daysOn = 5, daysOff = 2),
        )

        assertThat(protocol.isInBreak(start.plus(4, DateTimeUnit.DAY))).isFalse()
        assertThat(protocol.isInBreak(start.plus(5, DateTimeUnit.DAY))).isTrue()
        assertThat(protocol.isInBreak(start.plus(7, DateTimeUnit.DAY))).isFalse()
        assertThat(protocol.dosingTimesOn(start.plus(5, DateTimeUnit.DAY))).isEmpty()
        // Five on, two off: a week of a daily protocol places five doses, not seven.
        assertThat(protocol.dosesBetween(start, start.plus(SCHEDULE_HORIZON_DAYS, DateTimeUnit.DAY))).isEqualTo(5)
    }

    @Test
    fun `dosesBetween counts every dose of every dosing day in the range`() {
        val protocol = protocol(
            Schedule(ScheduleType.DAILY, null, null, null, null, null),
            dosageTimes = listOf(LocalTime(8, 0), LocalTime(20, 0)),
        )

        assertThat(protocol.dosesBetween(start, start.plus(SCHEDULE_HORIZON_DAYS, DateTimeUnit.DAY))).isEqualTo(14)
        // A range that opens before the protocol does starts counting at startDate, not at `from`.
        assertThat(protocol.dosesBetween(start.plus(-5, DateTimeUnit.DAY), start.plus(1, DateTimeUnit.DAY)))
            .isEqualTo(2)
    }
}

private fun protocol(
    schedule: Schedule,
    dosageTimes: List<LocalTime> = emptyList(),
    endDate: LocalDate? = null,
    protocolBreak: ProtocolBreak? = null,
) = Protocol(
    id = 1,
    name = "Test",
    compoundSupplyId = 1,
    plannedDose = Quantity(Decimal.parse("5"), UnitCode.MG),
    route = Route.SUBCUTANEOUS,
    schedule = schedule,
    dosageTimes = dosageTimes,
    escalation = null,
    protocolBreak = protocolBreak,
    startDate = start,
    endDate = endDate,
    reminderEnabled = false,
    reminderOffsetMinutes = 0,
    reminderBucket = null,
    injectionSiteRestriction = null,
    siteCooldownDays = null,
    notes = null,
    status = ProtocolStatus.ACTIVE,
    deletedAt = null,
    createdAt = Instant.fromEpochSeconds(0),
    updatedAt = Instant.fromEpochSeconds(0),
)
