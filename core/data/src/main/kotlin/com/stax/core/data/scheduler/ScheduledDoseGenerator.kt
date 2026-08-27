package com.stax.core.data.scheduler

import com.stax.core.data.mapper.toEntity
import com.stax.core.database.ScheduledDoseEntity
import com.stax.core.database.ScheduledDoseStatus
import com.stax.core.domain.EscalationIncreaseEvery
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.Quantity
import com.stax.core.domain.SCHEDULE_HORIZON_DAYS
import com.stax.core.domain.dosesBetween
import com.stax.core.domain.dosingTimesOn
import com.stax.core.domain.plannedDoseAt
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Pure generation logic — no I/O. Produces [ScheduledDoseEntity] rows for a protocol
 * within a half-open date range `[from, until)`.
 *
 * Which days a protocol doses on, and at what times, is the schedule rule of `:core:domain`
 * ([dosingTimesOn], §5.2) — a screen previewing a schedule it has not saved reads the same rule.
 * What is this class's own is turning those days into rows: time zones, the escalation counter and
 * idempotence.
 *
 * Rules implemented (§5.2):
 * - All six [com.stax.core.domain.ScheduleType]s; `dosageTimes` yields one dose per time on every
 *   dosing day, and an empty `dosageTimes` yields one dose at start-of-day with `hasTimeOfDay = false`.
 * - [com.stax.core.domain.ProtocolBreak]: off-days skipped (in-break formula, §3.2).
 * - `endDate`: no generation past it.
 * - Escalation (§3.2): the rule itself is [plannedDoseAt] in `:core:domain`; this class only feeds
 *   it the counter it cannot know — for [EscalationIncreaseEvery.AFTER_X_DOSES], the doses this
 *   schedule places between `startDate` and the dose being generated, so the same date always
 *   yields the same dose whatever range it is generated in.
 * - Only `Active`, non-archived protocols generate; a paused or completed one yields nothing.
 * - Wall-clock components are captured per §5.7 (`originalLocalDate/Time/Zone`).
 *
 * Generation is idempotent by `(protocolId, scheduledAt)` uniqueness — the DAO inserts with
 * `IGNORE` on conflict, so re-running an overlapping range is safe.
 */
class ScheduledDoseGenerator {

    /**
     * Generate the [SCHEDULE_HORIZON_DAYS]-day horizon for [protocol], starting at [today] or at
     * [Protocol.startDate] when the protocol has not started yet.
     */
    fun generateHorizon(
        protocol: Protocol,
        zone: TimeZone,
        today: LocalDate,
        createdAt: Instant = Clock.System.now(),
    ): List<ScheduledDoseEntity> {
        val from = maxOf(today, protocol.startDate)
        return generate(protocol, from, from.plus(SCHEDULE_HORIZON_DAYS, DateTimeUnit.DAY), zone, createdAt)
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

            for (time in protocol.dosingTimesOn(date)) {
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

    /**
     * How many doses this schedule places between `startDate` (inclusive) and [date] (exclusive).
     * Only [EscalationIncreaseEvery.AFTER_X_DOSES] needs it, so nothing else pays for the walk.
     */
    private fun dosesBefore(protocol: Protocol, date: LocalDate): Int {
        if (protocol.escalation?.increaseEvery != EscalationIncreaseEvery.AFTER_X_DOSES) return 0
        return protocol.dosesBetween(protocol.startDate, date)
    }

    /**
     * The planned dose for [date] / [doseIndex] — the escalation rule engine's
     * [plannedDoseAt] (§3.2), which falls back to [Protocol.plannedDose] without an escalation.
     */
    internal fun computePlannedDose(protocol: Protocol, date: LocalDate, doseIndex: Int): Quantity =
        protocol.plannedDoseAt(date, doseIndex)
}
