package com.stax.core.data.scheduler

import com.stax.core.data.mapper.toEntity
import com.stax.core.database.ScheduledDoseEntity
import com.stax.core.database.ScheduledDoseStatus
import com.stax.core.domain.EscalationIncreaseEvery
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.Quantity
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.plannedDoseAt
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant

/** Days of Pending rows kept ahead of today (§5.2). */
private const val HORIZON_DAYS = 7

/** Cycle length used to spread an `XTimesPerMonth` schedule. */
private const val MONTH_CYCLE_DAYS = 30

private const val DAYS_PER_WEEK = 7
private const val MINUTES_PER_DAY = 24 * 60

/**
 * Pure generation logic — no I/O. Produces [ScheduledDoseEntity] rows for a protocol
 * within a half-open date range `[from, until)`.
 *
 * Rules implemented (§5.2):
 * - All six [ScheduleType]s; `dosageTimes` yields one dose per time on every dosing day, and an
 *   empty `dosageTimes` yields one dose at start-of-day with `hasTimeOfDay = false`.
 * - [com.stax.core.domain.ProtocolBreak]: off-days skipped (in-break formula, §3.2).
 * - `endDate`: no generation past it.
 * - Escalation (§3.2): the rule itself is [com.stax.core.domain.plannedDoseAt] in `:core:domain`;
 *   this class only feeds it the counter it cannot know — for
 *   [EscalationIncreaseEvery.AFTER_X_DOSES], the doses this schedule places between `startDate` and
 *   the dose being generated, so the same date always yields the same dose whatever range it is
 *   generated in.
 * - Only `Active`, non-archived protocols generate; a paused or completed one yields nothing.
 * - Wall-clock components are captured per §5.7 (`originalLocalDate/Time/Zone`).
 *
 * Generation is idempotent by `(protocolId, scheduledAt)` uniqueness — the DAO inserts with
 * `IGNORE` on conflict, so re-running an overlapping range is safe.
 */
class ScheduledDoseGenerator {

    /**
     * Generate the [HORIZON_DAYS]-day horizon for [protocol], starting at [today] or at
     * [Protocol.startDate] when the protocol has not started yet.
     */
    fun generateHorizon(
        protocol: Protocol,
        zone: TimeZone,
        today: LocalDate,
        createdAt: Instant = Clock.System.now(),
    ): List<ScheduledDoseEntity> {
        val from = maxOf(today, protocol.startDate)
        return generate(protocol, from, from.plus(HORIZON_DAYS, DateTimeUnit.DAY), zone, createdAt)
    }

    /**
     * Generate doses for [protocol] from [from] (inclusive) to [until] (exclusive),
     * interpreted in [zone].
     */
    fun generate(
        protocol: Protocol,
        from: LocalDate,
        until: LocalDate,
        zone: TimeZone,
        createdAt: Instant = Clock.System.now(),
    ): List<ScheduledDoseEntity> {
        if (from >= until) return emptyList()
        if (protocol.status != ProtocolStatus.ACTIVE || protocol.deletedAt != null) return emptyList()

        val result = mutableListOf<ScheduledDoseEntity>()
        var doseIndex = dosesBefore(protocol, from)
        var date = maxOf(from, protocol.startDate)

        while (date < until) {
            val endDate = protocol.endDate
            if (endDate != null && date > endDate) break

            for (time in timesOn(protocol, date)) {
                result += entity(protocol, date, time, zone, doseIndex, createdAt)
                doseIndex++
            }
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return result
    }

    private fun entity(
        protocol: Protocol,
        date: LocalDate,
        time: LocalTime?,
        zone: TimeZone,
        doseIndex: Int,
        createdAt: Instant,
    ): ScheduledDoseEntity {
        val plannedDose = computePlannedDose(protocol, date, doseIndex)
        return ScheduledDoseEntity(
            protocolId = protocol.id,
            compoundSupplyId = protocol.compoundSupplyId,
            scheduledAt = if (time != null) date.atTime(time).toInstant(zone) else date.atStartOfDayIn(zone),
            hasTimeOfDay = time != null,
            plannedDoseValue = plannedDose.value,
            plannedDoseUnit = plannedDose.unit,
            route = protocol.route.toEntity(),
            status = ScheduledDoseStatus.PENDING,
            administrationEventId = null,
            originalLocalDate = date,
            originalLocalTime = time,
            originalZone = zone.id,
            createdAt = createdAt,
        )
    }

    // -----------------------------------------------------------------------
    // Which days, and at what times
    // -----------------------------------------------------------------------

    /**
     * The times to dose at on [date] — one entry per dose, `null` meaning "no time of day".
     * Empty when [date] is not a dosing day.
     */
    internal fun timesOn(protocol: Protocol, date: LocalDate): List<LocalTime?> {
        if (date < protocol.startDate) return emptyList()
        protocol.endDate?.let { if (date > it) return emptyList() }
        if (isInBreak(protocol, date)) return emptyList()
        if (!isDoseDay(protocol, date)) return emptyList()

        if (protocol.dosageTimes.isNotEmpty()) return protocol.dosageTimes

        val perDay = if (protocol.schedule.type == ScheduleType.X_TIMES_PER_DAY) {
            (protocol.schedule.timesPerDay ?: 1).coerceAtLeast(1)
        } else {
            1
        }
        // A single dose a day carries no time of day (§5.2); several need distinct
        // `scheduledAt` values, so spread them evenly over the day.
        if (perDay == 1) return listOf(null)
        return List(perDay) { i ->
            val minuteOfDay = i * MINUTES_PER_DAY / perDay
            LocalTime(minuteOfDay / 60, minuteOfDay % 60)
        }
    }

    /** Whether [protocol]'s schedule places any dose on [date], breaks and `endDate` aside. */
    private fun isDoseDay(protocol: Protocol, date: LocalDate): Boolean {
        val schedule = protocol.schedule
        val daysSinceStart = protocol.startDate.daysUntil(date)

        return when (schedule.type) {
            ScheduleType.DAILY, ScheduleType.X_TIMES_PER_DAY -> true

            ScheduleType.EVERY_X_DAYS ->
                daysSinceStart % (schedule.interval ?: 1).coerceAtLeast(1) == 0

            ScheduleType.SPECIFIC_WEEKDAYS ->
                date.dayOfWeek in (schedule.selectedWeekdays ?: return false)

            ScheduleType.X_TIMES_PER_WEEK ->
                spreadsOver(daysSinceStart % DAYS_PER_WEEK, schedule.timesPerWeek ?: 0, DAYS_PER_WEEK)

            ScheduleType.X_TIMES_PER_MONTH ->
                spreadsOver(daysSinceStart % MONTH_CYCLE_DAYS, schedule.timesPerMonth ?: 0, MONTH_CYCLE_DAYS)
        }
    }

    /**
     * True on exactly [times] of the [cycle] days (as long as `times <= cycle`), spread as evenly
     * as whole days allow — 3×/week lands on days 0, 3 and 5 rather than clustering on 0, 1, 2.
     */
    private fun spreadsOver(dayInCycle: Int, times: Int, cycle: Int): Boolean = (dayInCycle * times) % cycle < times

    // -----------------------------------------------------------------------
    // Protocol break
    // -----------------------------------------------------------------------

    internal fun isInBreak(protocol: Protocol, date: LocalDate): Boolean {
        val pb = protocol.protocolBreak ?: return false
        val daysSinceStart = protocol.startDate.daysUntil(date)
        if (daysSinceStart < 0) return false
        val cycleLen = pb.daysOn + pb.daysOff
        if (cycleLen <= 0) return false
        return daysSinceStart % cycleLen >= pb.daysOn
    }

    // -----------------------------------------------------------------------
    // Escalation dose
    // -----------------------------------------------------------------------

    /**
     * How many doses this schedule places between `startDate` (inclusive) and [date] (exclusive).
     * Only [EscalationIncreaseEvery.AFTER_X_DOSES] needs it, so nothing else pays for the walk.
     */
    private fun dosesBefore(protocol: Protocol, date: LocalDate): Int {
        if (protocol.escalation?.increaseEvery != EscalationIncreaseEvery.AFTER_X_DOSES) return 0
        var count = 0
        var d = protocol.startDate
        while (d < date) {
            count += timesOn(protocol, d).size
            d = d.plus(1, DateTimeUnit.DAY)
        }
        return count
    }

    /**
     * The planned dose for [date] / [doseIndex] — the escalation rule engine's
     * [plannedDoseAt] (§3.2), which falls back to [Protocol.plannedDose] without an escalation.
     */
    internal fun computePlannedDose(protocol: Protocol, date: LocalDate, doseIndex: Int): Quantity =
        protocol.plannedDoseAt(date, doseIndex)
}
