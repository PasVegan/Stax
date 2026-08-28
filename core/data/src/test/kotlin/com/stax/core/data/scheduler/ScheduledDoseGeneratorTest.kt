package com.stax.core.data.scheduler

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.stax.core.domain.Decimal
import com.stax.core.domain.Escalation
import com.stax.core.domain.EscalationIncreaseEvery
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolBreak
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.Quantity
import com.stax.core.domain.Route
import com.stax.core.domain.Schedule
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.UnitCode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class ScheduledDoseGeneratorTest {

    private val generator = ScheduledDoseGenerator()
    private val zone = TimeZone.UTC
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val start = LocalDate(2026, 1, 1)
    private val defaultDose = Quantity(Decimal.parse("0.25"), UnitCode.MG)

    // -----------------------------------------------------------------------
    // Basic schedule types
    // -----------------------------------------------------------------------

    @Test
    fun `Daily - generates one dose per day over 7 days`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.DAILY),
            from = start,
            until = start.plus(7, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses).hasSize(7)
    }

    @Test
    fun `Daily with dosageTimes - generates one dose per time per day`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.DAILY, dosageTimes = listOf(LocalTime(8, 0), LocalTime(20, 0))),
            from = start,
            until = start.plus(3, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses).hasSize(6) // 2 times × 3 days
    }

    @Test
    fun `EveryXDays - generates on correct interval`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.EVERY_X_DAYS, interval = 3),
            from = start,
            until = start.plus(9, DateTimeUnit.DAY),
            zone = zone,
        )
        // Days 0, 3, 6 → 3 doses in 9-day range [0,9)
        assertThat(doses).hasSize(3)
    }

    @Test
    fun `EveryXDays - no dose on off days`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.EVERY_X_DAYS, interval = 2),
            from = start.plus(1, DateTimeUnit.DAY), // starts mid-interval
            until = start.plus(2, DateTimeUnit.DAY),
            zone = zone,
        )
        // Day 1 is an off-day for interval=2 starting from day 0
        assertThat(doses).isEmpty()
    }

    @Test
    fun `XTimesPerDay - generates correct dose count per day`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.X_TIMES_PER_DAY, timesPerDay = 3),
            from = start,
            until = start.plus(1, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses).hasSize(3)
    }

    @Test
    fun `SpecificWeekdays - only generates on selected days`() {
        // Jan 1 2026 is a Thursday (ISO=4), Jan 2 is Friday (ISO=5)
        val monday = kotlinx.datetime.DayOfWeek.MONDAY
        val doses = generator.generate(
            protocol = protocol(
                ScheduleType.SPECIFIC_WEEKDAYS,
                selectedWeekdays = setOf(monday),
            ),
            from = start, // Thu
            until = start.plus(7, DateTimeUnit.DAY),
            zone = zone,
        )
        // One Monday in a 7-day span starting Thursday
        assertThat(doses).hasSize(1)
    }

    @Test
    fun `XTimesPerWeek - generates correct total count per week`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.X_TIMES_PER_WEEK, timesPerWeek = 3),
            from = start,
            until = start.plus(7, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses).hasSize(3)
    }

    @Test
    fun `XTimesPerMonth - generates correct total count per 30-day period`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.X_TIMES_PER_MONTH, timesPerMonth = 2),
            from = start,
            until = start.plus(30, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses).hasSize(2)
    }

    @Test
    fun `EveryXDays with dosageTimes - generates one dose per time on each dosing day`() {
        val doses = generator.generate(
            protocol = protocol(
                ScheduleType.EVERY_X_DAYS,
                interval = 3,
                dosageTimes = listOf(LocalTime(8, 0), LocalTime(20, 0)),
            ),
            from = start,
            until = start.plus(9, DateTimeUnit.DAY),
            zone = zone,
        )
        // Days 0, 3, 6 × 2 times each
        assertThat(doses).hasSize(6)
        assertThat(doses.map { it.originalLocalTime }.distinct()).hasSize(2)
    }

    @Test
    fun `SpecificWeekdays with dosageTimes - every time is generated`() {
        val doses = generator.generate(
            protocol = protocol(
                ScheduleType.SPECIFIC_WEEKDAYS,
                selectedWeekdays = setOf(kotlinx.datetime.DayOfWeek.MONDAY),
                dosageTimes = listOf(LocalTime(8, 0), LocalTime(20, 0)),
            ),
            from = start,
            until = start.plus(7, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses).hasSize(2)
    }

    @Test
    fun `XTimesPerWeek - spreads doses across the week instead of clustering`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.X_TIMES_PER_WEEK, timesPerWeek = 3),
            from = start,
            until = start.plus(7, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses.map { start.daysUntil(it.originalLocalDate) }).isEqualTo(listOf(0, 3, 5))
    }

    @Test
    fun `XTimesPerMonth - count holds for values that do not divide 30`() {
        listOf(1, 2, 3, 4, 5, 7, 11).forEach { timesPerMonth ->
            val doses = generator.generate(
                protocol = protocol(ScheduleType.X_TIMES_PER_MONTH, timesPerMonth = timesPerMonth),
                from = start,
                until = start.plus(30, DateTimeUnit.DAY),
                zone = zone,
            )
            assertThat(doses, name = "timesPerMonth=$timesPerMonth").hasSize(timesPerMonth)
        }
    }

    @Test
    fun `XTimesPerWeek - count holds for every value`() {
        (1..7).forEach { timesPerWeek ->
            val doses = generator.generate(
                protocol = protocol(ScheduleType.X_TIMES_PER_WEEK, timesPerWeek = timesPerWeek),
                from = start,
                until = start.plus(7, DateTimeUnit.DAY),
                zone = zone,
            )
            assertThat(doses, name = "timesPerWeek=$timesPerWeek").hasSize(timesPerWeek)
        }
    }

    // -----------------------------------------------------------------------
    // Protocol status gate
    // -----------------------------------------------------------------------

    @Test
    fun `paused protocol - generates nothing`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.DAILY, status = ProtocolStatus.PAUSED),
            from = start,
            until = start.plus(7, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses).isEmpty()
    }

    @Test
    fun `completed protocol - generates nothing`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.DAILY, status = ProtocolStatus.COMPLETED),
            from = start,
            until = start.plus(7, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses).isEmpty()
    }

    @Test
    fun `archived protocol - generates nothing`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.DAILY, deletedAt = now),
            from = start,
            until = start.plus(7, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses).isEmpty()
    }

    // -----------------------------------------------------------------------
    // Horizon
    // -----------------------------------------------------------------------

    @Test
    fun `generateHorizon - covers seven days from today`() {
        val doses = generator.generateHorizon(protocol(ScheduleType.DAILY), zone, today = start)
        assertThat(doses).hasSize(7)
        assertThat(doses.last().originalLocalDate).isEqualTo(start.plus(6, DateTimeUnit.DAY))
    }

    @Test
    fun `generateHorizon - starts at startDate when the protocol has not started yet`() {
        val futureStart = start.plus(10, DateTimeUnit.DAY)
        val doses = generator.generateHorizon(
            protocol = protocol(ScheduleType.DAILY, startDate = futureStart),
            zone = zone,
            today = start,
        )
        assertThat(doses).hasSize(7)
        assertThat(doses.first().originalLocalDate).isEqualTo(futureStart)
    }

    @Test
    fun `generateHorizon - never generates before startDate for a protocol already running`() {
        val doses = generator.generateHorizon(
            protocol = protocol(ScheduleType.DAILY),
            zone = zone,
            today = start.plus(3, DateTimeUnit.DAY),
        )
        assertThat(doses.first().originalLocalDate).isEqualTo(start.plus(3, DateTimeUnit.DAY))
    }

    // -----------------------------------------------------------------------
    // Idempotency (§5.2 — (protocolId, scheduledAt) uniqueness)
    // -----------------------------------------------------------------------

    @Test
    fun `overlapping ranges produce identical rows for the shared dates`() {
        val proto = protocol(ScheduleType.DAILY, dosageTimes = listOf(LocalTime(8, 0)))
        val first = generator.generate(proto, start, start.plus(7, DateTimeUnit.DAY), zone, createdAt = now)
        val second = generator.generate(
            proto,
            start.plus(3, DateTimeUnit.DAY),
            start.plus(10, DateTimeUnit.DAY),
            zone,
            createdAt = now,
        )
        val shared = second.filter { it.originalLocalDate < start.plus(7, DateTimeUnit.DAY) }
        assertThat(shared).isEqualTo(first.drop(3))
    }

    // -----------------------------------------------------------------------
    // hasTimeOfDay and originalLocalTime
    // -----------------------------------------------------------------------

    @Test
    fun `no dosageTimes - hasTimeOfDay is false`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.DAILY),
            from = start,
            until = start.plus(1, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses.first().hasTimeOfDay).isEqualTo(false)
        assertThat(doses.first().originalLocalTime).isNull()
    }

    @Test
    fun `with dosageTimes - hasTimeOfDay is true and originalLocalTime set`() {
        val time = LocalTime(8, 30)
        val doses = generator.generate(
            protocol = protocol(ScheduleType.DAILY, dosageTimes = listOf(time)),
            from = start,
            until = start.plus(1, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses.first().hasTimeOfDay).isEqualTo(true)
        assertThat(doses.first().originalLocalTime).isEqualTo(time)
    }

    // -----------------------------------------------------------------------
    // Protocol break
    // -----------------------------------------------------------------------

    @Test
    fun `protocol break - skips off days`() {
        // 5 on / 2 off cycle → 5 doses in first week, 0 on days 5–6
        val doses = generator.generate(
            protocol = protocol(ScheduleType.DAILY, protocolBreak = ProtocolBreak(daysOn = 5, daysOff = 2)),
            from = start,
            until = start.plus(7, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses).hasSize(5)
    }

    // The in-break formula itself moved to `:core:domain` with the schedule rule (§3.2, §5.2);
    // `ScheduleEngineTest` pins it there. What stays here is what it does to generated rows, above.

    // -----------------------------------------------------------------------
    // endDate respected
    // -----------------------------------------------------------------------

    @Test
    fun `endDate - stops generation after endDate`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.DAILY, endDate = start.plus(2, DateTimeUnit.DAY)),
            from = start,
            until = start.plus(7, DateTimeUnit.DAY),
            zone = zone,
        )
        // days 0, 1, 2 → 3 doses (endDate inclusive)
        assertThat(doses).hasSize(3)
    }

    // -----------------------------------------------------------------------
    // Empty range
    // -----------------------------------------------------------------------

    @Test
    fun `empty range - returns no doses`() {
        assertThat(
            generator.generate(protocol(ScheduleType.DAILY), start, start, zone),
        ).isEmpty()
    }

    // -----------------------------------------------------------------------
    // Escalation
    // -----------------------------------------------------------------------

    @Test
    fun `escalation EveryXDays - increases dose on schedule`() {
        val esc = Escalation(
            startDose = Quantity(Decimal.parse("0.25"), UnitCode.MG),
            targetDose = Quantity(Decimal.parse("1.0"), UnitCode.MG),
            increaseAmount = Quantity(Decimal.parse("0.25"), UnitCode.MG),
            increaseEvery = EscalationIncreaseEvery.EVERY_X_DAYS,
            increaseEveryValue = 7,
            maxDose = null,
            stopAtTarget = false,
        )
        val proto = protocol(ScheduleType.DAILY, escalation = esc)

        val day0 = generator.computePlannedDose(proto, start, 0)
        val day7 = generator.computePlannedDose(proto, start.plus(7, DateTimeUnit.DAY), 7)
        val day14 = generator.computePlannedDose(proto, start.plus(14, DateTimeUnit.DAY), 14)

        // Use toPlainString() to avoid BigDecimal scale-mismatch (0.50 != 0.5 by equals).
        assertThat(day0.value.toPlainString()).isEqualTo("0.25")
        assertThat(day7.value.toPlainString()).isEqualTo("0.5")
        assertThat(day14.value.toPlainString()).isEqualTo("0.75")
    }

    @Test
    fun `escalation stopAtTarget - clamps to targetDose`() {
        val esc = Escalation(
            startDose = Quantity(Decimal.parse("0.25"), UnitCode.MG),
            targetDose = Quantity(Decimal.parse("0.5"), UnitCode.MG),
            increaseAmount = Quantity(Decimal.parse("0.25"), UnitCode.MG),
            increaseEvery = EscalationIncreaseEvery.EVERY_X_DAYS,
            increaseEveryValue = 7,
            maxDose = null,
            stopAtTarget = true,
        )
        val proto = protocol(ScheduleType.DAILY, escalation = esc)
        val day21 = generator.computePlannedDose(proto, start.plus(21, DateTimeUnit.DAY), 21)
        assertThat(day21.value).isEqualTo(Decimal.parse("0.5"))
    }

    @Test
    fun `escalation maxDose - clamps dose`() {
        val esc = Escalation(
            startDose = Quantity(Decimal.parse("0.25"), UnitCode.MG),
            targetDose = Quantity(Decimal.parse("2.0"), UnitCode.MG),
            increaseAmount = Quantity(Decimal.parse("0.25"), UnitCode.MG),
            increaseEvery = EscalationIncreaseEvery.EVERY_X_DAYS,
            increaseEveryValue = 7,
            maxDose = Quantity(Decimal.parse("0.75"), UnitCode.MG),
            stopAtTarget = false,
        )
        val proto = protocol(ScheduleType.DAILY, escalation = esc)
        // day 28 = 4 increases = startDose + 4×0.25 = 1.25, but maxDose = 0.75
        val day28 = generator.computePlannedDose(proto, start.plus(28, DateTimeUnit.DAY), 28)
        assertThat(day28.value).isEqualTo(Decimal.parse("0.75"))
    }

    @Test
    fun `no escalation - uses plannedDose`() {
        val proto = protocol(ScheduleType.DAILY)
        val dose = generator.computePlannedDose(proto, start, 0)
        assertThat(dose).isEqualTo(defaultDose)
    }

    @Test
    fun `escalation AfterXDoses - dose is the same whatever range it is generated in`() {
        val esc = Escalation(
            startDose = Quantity(Decimal.parse("0.25"), UnitCode.MG),
            targetDose = Quantity(Decimal.parse("2.0"), UnitCode.MG),
            increaseAmount = Quantity(Decimal.parse("0.25"), UnitCode.MG),
            increaseEvery = EscalationIncreaseEvery.AFTER_X_DOSES,
            increaseEveryValue = 4,
            maxDose = null,
            stopAtTarget = false,
        )
        val proto = protocol(ScheduleType.DAILY, escalation = esc)

        val fromStart = generator.generate(proto, start, start.plus(12, DateTimeUnit.DAY), zone, createdAt = now)
        val horizonOnly = generator.generate(
            proto,
            start.plus(8, DateTimeUnit.DAY),
            start.plus(12, DateTimeUnit.DAY),
            zone,
            createdAt = now,
        )

        // Doses 0–3 = 0.25, 4–7 = 0.5, 8–11 = 0.75 — day 8 onwards must agree either way.
        assertThat(horizonOnly.map { it.plannedDoseValue.toPlainString() })
            .isEqualTo(listOf("0.75", "0.75", "0.75", "0.75"))
        assertThat(horizonOnly).isEqualTo(fromStart.drop(8))
    }

    @Test
    fun `escalation AfterXDoses - counts every dose of the day, not every day`() {
        val esc = Escalation(
            startDose = Quantity(Decimal.parse("0.25"), UnitCode.MG),
            targetDose = Quantity(Decimal.parse("2.0"), UnitCode.MG),
            increaseAmount = Quantity(Decimal.parse("0.25"), UnitCode.MG),
            increaseEvery = EscalationIncreaseEvery.AFTER_X_DOSES,
            increaseEveryValue = 2,
            maxDose = null,
            stopAtTarget = false,
        )
        val proto = protocol(
            ScheduleType.DAILY,
            dosageTimes = listOf(LocalTime(8, 0), LocalTime(20, 0)),
            escalation = esc,
        )
        val doses = generator.generate(proto, start, start.plus(2, DateTimeUnit.DAY), zone, createdAt = now)

        assertThat(doses.map { it.plannedDoseValue.toPlainString() })
            .isEqualTo(listOf("0.25", "0.25", "0.5", "0.5"))
    }

    @Test
    fun `escalation AfterXDoses - break days do not advance the counter`() {
        val esc = Escalation(
            startDose = Quantity(Decimal.parse("0.25"), UnitCode.MG),
            targetDose = Quantity(Decimal.parse("2.0"), UnitCode.MG),
            increaseAmount = Quantity(Decimal.parse("0.25"), UnitCode.MG),
            increaseEvery = EscalationIncreaseEvery.AFTER_X_DOSES,
            increaseEveryValue = 5,
            maxDose = null,
            stopAtTarget = false,
        )
        val proto = protocol(
            ScheduleType.DAILY,
            protocolBreak = ProtocolBreak(daysOn = 5, daysOff = 2),
            escalation = esc,
        )
        // Days 5–6 are off, so the 5 doses of week 1 are all at startDose and week 2 steps up once.
        val doses = generator.generate(proto, start, start.plus(14, DateTimeUnit.DAY), zone, createdAt = now)

        assertThat(doses).hasSize(10)
        assertThat(doses.map { it.plannedDoseValue.toPlainString() })
            .isEqualTo(List(5) { "0.25" } + List(5) { "0.5" })
    }

    // -----------------------------------------------------------------------
    // Entity fields
    // -----------------------------------------------------------------------

    @Test
    fun `generated entities have correct protocolId and compoundSupplyId`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.DAILY).copy(id = 42L, compoundSupplyId = 7L),
            from = start,
            until = start.plus(1, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses.first().protocolId).isEqualTo(42L)
        assertThat(doses.first().compoundSupplyId).isEqualTo(7L)
    }

    @Test
    fun `generated entities have PENDING status and null administrationEventId`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.DAILY),
            from = start,
            until = start.plus(1, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses.first().status).isEqualTo(com.stax.core.database.ScheduledDoseStatus.PENDING)
        assertThat(doses.first().administrationEventId).isNull()
    }

    @Test
    fun `originalLocalDate matches generation date`() {
        val doses = generator.generate(
            protocol = protocol(ScheduleType.DAILY),
            from = start,
            until = start.plus(3, DateTimeUnit.DAY),
            zone = zone,
        )
        assertThat(doses[0].originalLocalDate).isEqualTo(start)
        assertThat(doses[1].originalLocalDate).isEqualTo(start.plus(1, DateTimeUnit.DAY))
        assertThat(doses[2].originalLocalDate).isEqualTo(start.plus(2, DateTimeUnit.DAY))
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun protocol(
        scheduleType: ScheduleType,
        interval: Int? = null,
        timesPerDay: Int? = null,
        timesPerWeek: Int? = null,
        timesPerMonth: Int? = null,
        selectedWeekdays: Set<kotlinx.datetime.DayOfWeek>? = null,
        dosageTimes: List<LocalTime> = emptyList(),
        protocolBreak: ProtocolBreak? = null,
        escalation: Escalation? = null,
        endDate: LocalDate? = null,
        startDate: LocalDate = start,
        status: ProtocolStatus = ProtocolStatus.ACTIVE,
        deletedAt: Instant? = null,
    ): Protocol = Protocol(
        id = 1L,
        name = "Test protocol",
        compoundSupplyId = 1L,
        plannedDose = defaultDose,
        route = Route.SUBCUTANEOUS,
        schedule = Schedule(
            type = scheduleType,
            interval = interval,
            timesPerDay = timesPerDay,
            selectedWeekdays = selectedWeekdays,
            timesPerWeek = timesPerWeek,
            timesPerMonth = timesPerMonth,
        ),
        dosageTimes = dosageTimes,
        escalation = escalation,
        protocolBreak = protocolBreak,
        startDate = startDate,
        endDate = endDate,
        reminderEnabled = false,
        reminderOffsetMinutes = 0,
        reminderBucket = null,
        injectionSiteRestriction = null,
        siteCooldownDays = null,
        notes = null,
        status = status,
        deletedAt = deletedAt,
        createdAt = now,
        updatedAt = now,
    )
}
