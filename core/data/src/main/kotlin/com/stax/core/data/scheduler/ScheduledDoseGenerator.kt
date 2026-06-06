package com.stax.core.data.scheduler

import com.stax.core.data.mapper.toEntity
import com.stax.core.database.ScheduledDoseEntity
import com.stax.core.database.ScheduledDoseStatus
import com.stax.core.domain.Decimal
import com.stax.core.domain.EscalationIncreaseEvery
import com.stax.core.domain.Protocol
import com.stax.core.domain.Quantity
import com.stax.core.domain.ScheduleType
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

/**
 * Pure generation logic — no I/O. Produces [ScheduledDoseEntity] rows for a protocol
 * within a half-open date range (from), (until)).
 *
 * Rules implemented (§5.2):
 * - All six [ScheduleType]s.
 * - [com.stax.core.domain.ProtocolBreak]: off-days skipped.
 * - `endDate`: no generation past it.
 * - Escalation (§3.2): dose computed by time elapsed since `startDate` for
 *   [EscalationIncreaseEvery.EVERY_X_DAYS] / [EscalationIncreaseEvery.EVERY_X_WEEKS];
 *   approximate for [EscalationIncreaseEvery.AFTER_X_DOSES] (assumes one dose/day).
 *
 * Generation is idempotent by `(protocolId, scheduledAt)` uniqueness — the DAO uses
 * `IGNORE` on conflict so re-running the same range is safe.
 */
class ScheduledDoseGenerator {

    /**
     * Generate doses for [protocol] from [from] (inclusive) to [until] (exclusive),
     * interpreted in [zone].
     *
     * Pass [existingLoggedDoseCount] when the protocol has `AfterXDoses` escalation and
     * already-logged doses should offset the escalation counter.
     */
    fun generate(
        protocol: Protocol,
        from: LocalDate,
        until: LocalDate,
        zone: TimeZone,
        existingLoggedDoseCount: Int = 0,
        createdAt: Instant = Clock.System.now(),
    ): List<ScheduledDoseEntity> {
        if (from >= until) return emptyList()

        val zoneName = zone.id
        val result = mutableListOf<ScheduledDoseEntity>()

        var date = from
        var doseIndex = existingLoggedDoseCount // running index for AfterXDoses escalation

        while (date < until) {
            // Respect endDate
            val endDate = protocol.endDate
            if (endDate != null && date > endDate) break

            // Skip off-days from protocol break
            if (protocol.protocolBreak != null && isInBreak(protocol, date)) {
                date = date.plus(1, DateTimeUnit.DAY)
                continue
            }

            // Determine how many doses (possibly 0) fall on this date
            val dosesOnDate = dosesForDate(protocol, date)
            if (dosesOnDate == 0) {
                date = date.plus(1, DateTimeUnit.DAY)
                continue
            }

            // Resolve times: use explicit dosageTimes when available
            val times: List<LocalTime?> = when {
                protocol.dosageTimes.isNotEmpty() -> {
                    // XTimesPerDay may specify more times than needed — take the required count
                    protocol.dosageTimes.take(dosesOnDate).map { it }
                }

                dosesOnDate == 1 -> listOf(null)

                else -> {
                    // Multiple doses per day without explicit times: evenly distribute
                    List(dosesOnDate) { i ->
                        val minuteOfDay = (i * (24 * 60) / dosesOnDate)
                        LocalTime(minuteOfDay / 60, minuteOfDay % 60)
                    }
                }
            }

            for (time in times) {
                val plannedDose = computePlannedDose(protocol, date, doseIndex)
                val hasTimeOfDay = time != null
                val scheduledAt: Instant = if (time != null) {
                    date.atTime(time).toInstant(zone)
                } else {
                    date.atStartOfDayIn(zone)
                }

                result.add(
                    ScheduledDoseEntity(
                        protocolId = protocol.id,
                        compoundSupplyId = protocol.compoundSupplyId,
                        scheduledAt = scheduledAt,
                        hasTimeOfDay = hasTimeOfDay,
                        plannedDoseValue = plannedDose.value,
                        plannedDoseUnit = plannedDose.unit,
                        route = protocol.route.toEntity(),
                        status = ScheduledDoseStatus.PENDING,
                        administrationEventId = null,
                        originalLocalDate = date,
                        originalLocalTime = time,
                        originalZone = zoneName,
                        createdAt = createdAt,
                    ),
                )
                doseIndex++
            }

            date = date.plus(1, DateTimeUnit.DAY)
        }

        return result
    }

    // -----------------------------------------------------------------------
    // Protocol break
    // -----------------------------------------------------------------------

    internal fun isInBreak(protocol: Protocol, date: LocalDate): Boolean {
        val pb = protocol.protocolBreak ?: return false
        val daysSinceStart = protocol.startDate.daysUntil(date)
        if (daysSinceStart < 0) return false
        val cycleLen = pb.daysOn + pb.daysOff
        if (cycleLen <= 0) return false
        val cyclePos = daysSinceStart % cycleLen
        return cyclePos >= pb.daysOn
    }

    // -----------------------------------------------------------------------
    // Schedule type → doses per date
    // -----------------------------------------------------------------------

    /**
     * Returns the number of doses to generate on [date] given [protocol]'s schedule.
     * Returns 0 when this date does not fall on a scheduled day.
     */
    internal fun dosesForDate(protocol: Protocol, date: LocalDate): Int {
        val schedule = protocol.schedule
        val daysSinceStart = protocol.startDate.daysUntil(date)
        if (daysSinceStart < 0) return 0

        return when (schedule.type) {
            // When explicit times are given, generate one dose per time; otherwise one per day.
            ScheduleType.DAILY ->
                if (protocol.dosageTimes.isEmpty()) 1 else protocol.dosageTimes.size

            ScheduleType.EVERY_X_DAYS -> {
                val interval = schedule.interval ?: 1
                if (daysSinceStart % interval == 0) 1 else 0
            }

            ScheduleType.X_TIMES_PER_DAY ->
                schedule.timesPerDay ?: 1

            ScheduleType.SPECIFIC_WEEKDAYS -> {
                val weekdays = schedule.selectedWeekdays ?: return 0
                if (date.dayOfWeek in weekdays) 1 else 0
            }

            ScheduleType.X_TIMES_PER_WEEK -> {
                // Deterministic: treat the 7-day week relative to startDate.
                // Spread N doses across the first N ISO days of each 7-day cycle.
                val timesPerWeek = schedule.timesPerWeek ?: return 0
                val dayInCycle = daysSinceStart % 7
                if (dayInCycle < timesPerWeek) 1 else 0
            }

            ScheduleType.X_TIMES_PER_MONTH -> {
                // Deterministic: spread N doses across a 30-day cycle relative to startDate.
                val timesPerMonth = schedule.timesPerMonth ?: return 0
                val dayInCycle = daysSinceStart % 30
                val interval = 30 / timesPerMonth
                if (interval > 0 && dayInCycle % interval == 0) 1 else 0
            }
        }
    }

    // -----------------------------------------------------------------------
    // Escalation dose
    // -----------------------------------------------------------------------

    /**
     * Computes the planned dose for [date] / [doseIndex] accounting for escalation.
     * Falls back to [Protocol.plannedDose] when no escalation is defined.
     */
    internal fun computePlannedDose(protocol: Protocol, date: LocalDate, doseIndex: Int): Quantity {
        val esc = protocol.escalation ?: return protocol.plannedDose
        val daysSinceStart = protocol.startDate.daysUntil(date).coerceAtLeast(0)

        val increaseCount = when (esc.increaseEvery) {
            EscalationIncreaseEvery.EVERY_X_DAYS ->
                daysSinceStart / esc.increaseEveryValue

            EscalationIncreaseEvery.EVERY_X_WEEKS ->
                daysSinceStart / (esc.increaseEveryValue * 7)

            EscalationIncreaseEvery.AFTER_X_DOSES ->
                doseIndex / esc.increaseEveryValue
        }

        val increaseTotal = esc.increaseAmount * Decimal.parse(increaseCount.toString())
        val dose = esc.startDose + increaseTotal

        // Clamp to maxDose
        val clamped = esc.maxDose?.let { max ->
            if (dose.value > max.value) max else dose
        } ?: dose

        // Clamp to targetDose when stopAtTarget
        return if (esc.stopAtTarget && clamped.value >= esc.targetDose.value) {
            esc.targetDose
        } else {
            clamped
        }
    }
}
