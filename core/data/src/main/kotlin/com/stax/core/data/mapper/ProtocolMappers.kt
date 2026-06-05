package com.stax.core.data.mapper

import com.stax.core.database.EscalationEmbed
import com.stax.core.database.ProtocolBreakEmbed
import com.stax.core.database.ProtocolDosageTimeEntity
import com.stax.core.database.ProtocolEntity
import com.stax.core.database.ScheduleEmbed
import com.stax.core.domain.Escalation
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolBreak
import com.stax.core.domain.Quantity
import com.stax.core.domain.Schedule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber

// ---------------------------------------------------------------------------
// ProtocolEntity ↔ Protocol
// ---------------------------------------------------------------------------

/**
 * Maps a [ProtocolEntity] to [Protocol].
 *
 * [dosageTimes] is the companion rows from `protocol_dosage_time`, projected to
 * their time values. Callers (repositories) must fetch these separately.
 */
fun ProtocolEntity.toDomain(dosageTimes: List<LocalTime> = emptyList()): Protocol =
    Protocol(
        id = id,
        name = name,
        compoundSupplyId = compoundSupplyId,
        plannedDose = Quantity(plannedDoseValue, plannedDoseUnit),
        route = route.toDomain(),
        schedule = schedule.toDomain(selectedWeekdaysBitmask),
        dosageTimes = dosageTimes,
        escalation = escalation.toDomain(),
        protocolBreak = protocolBreak.toDomain(),
        startDate = startDate,
        endDate = endDate,
        reminderEnabled = reminderEnabled,
        reminderOffsetMinutes = reminderOffsetMinutes,
        reminderBucket = reminderBucket?.toDomain(),
        injectionSiteRestriction = injectionSiteRestriction?.toDomain(),
        siteCooldownDays = siteCooldownDays,
        notes = notes,
        status = status.toDomain(),
        deletedAt = deletedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** Maps [Protocol] back to [ProtocolEntity]. `dosageTimes` live in a separate table — see [toDosageTimeEntities]. */
fun Protocol.toEntity(): ProtocolEntity =
    ProtocolEntity(
        id = id,
        name = name,
        compoundSupplyId = compoundSupplyId,
        plannedDoseValue = plannedDose.value,
        plannedDoseUnit = plannedDose.unit,
        route = route.toEntity(),
        schedule = schedule.toEmbed(),
        selectedWeekdaysBitmask = schedule.toSelectedWeekdaysBitmask(),
        escalation = escalation?.toEmbed(),
        protocolBreak = protocolBreak?.toEmbed(),
        startDate = startDate,
        endDate = endDate,
        reminderEnabled = reminderEnabled,
        reminderOffsetMinutes = reminderOffsetMinutes,
        reminderBucket = reminderBucket?.toEntity(),
        injectionSiteRestriction = injectionSiteRestriction?.toEntity(),
        notes = notes,
        status = status.toEntity(),
        siteCooldownDays = siteCooldownDays,
        deletedAt = deletedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** Produces the companion `protocol_dosage_time` rows for this protocol. */
fun Protocol.toDosageTimeEntities(): List<ProtocolDosageTimeEntity> =
    dosageTimes.map { ProtocolDosageTimeEntity(protocolId = id, time = it) }

// ---------------------------------------------------------------------------
// ScheduleEmbed ↔ Schedule
// ---------------------------------------------------------------------------

private fun ScheduleEmbed.toDomain(selectedWeekdaysBitmask: Int): Schedule =
    Schedule(
        type = type.toDomain(),
        interval = interval,
        timesPerDay = timesPerDay,
        selectedWeekdays = selectedWeekdaysBitmask.toDayOfWeekSet().takeIf { it.isNotEmpty() },
        timesPerWeek = timesPerWeek,
        timesPerMonth = timesPerMonth,
    )

private fun Schedule.toEmbed(): ScheduleEmbed =
    ScheduleEmbed(
        type = type.toEntity(),
        interval = interval,
        timesPerDay = timesPerDay,
        timesPerWeek = timesPerWeek,
        timesPerMonth = timesPerMonth,
    )

private fun Schedule.toSelectedWeekdaysBitmask(): Int = selectedWeekdays?.toMask() ?: 0

// ---------------------------------------------------------------------------
// EscalationEmbed ↔ Escalation
// ---------------------------------------------------------------------------

private fun EscalationEmbed?.toDomain(): Escalation? {
    if (this == null || startDoseValue == null) return null
    return Escalation(
        startDose = Quantity(startDoseValue!!, startDoseUnit!!),
        targetDose = Quantity(targetDoseValue!!, targetDoseUnit!!),
        increaseAmount = Quantity(increaseAmountValue!!, increaseAmountUnit!!),
        increaseEvery = increaseEvery!!.toDomain(),
        increaseEveryValue = increaseEveryValue!!,
        maxDose = if (maxDoseValue != null) Quantity(maxDoseValue!!, maxDoseUnit!!) else null,
        stopAtTarget = stopAtTarget!!,
    )
}

private fun Escalation.toEmbed(): EscalationEmbed =
    EscalationEmbed(
        startDoseValue = startDose.value,
        startDoseUnit = startDose.unit,
        targetDoseValue = targetDose.value,
        targetDoseUnit = targetDose.unit,
        increaseAmountValue = increaseAmount.value,
        increaseAmountUnit = increaseAmount.unit,
        increaseEvery = increaseEvery.toEntity(),
        increaseEveryValue = increaseEveryValue,
        maxDoseValue = maxDose?.value,
        maxDoseUnit = maxDose?.unit,
        stopAtTarget = stopAtTarget,
    )

// ---------------------------------------------------------------------------
// ProtocolBreakEmbed ↔ ProtocolBreak
// ---------------------------------------------------------------------------

private fun ProtocolBreakEmbed?.toDomain(): ProtocolBreak? {
    if (this == null || daysOn == null) return null
    return ProtocolBreak(daysOn = daysOn!!, daysOff = daysOff!!)
}

private fun ProtocolBreak.toEmbed(): ProtocolBreakEmbed =
    ProtocolBreakEmbed(daysOn = daysOn, daysOff = daysOff)

// ---------------------------------------------------------------------------
// DayOfWeek bitmask helpers (bit N = ISO day N+1, Monday=0)
// ---------------------------------------------------------------------------

private fun Int.toDayOfWeekSet(): Set<DayOfWeek> {
    if (this == 0) return emptySet()
    return enumValues<DayOfWeek>().filter { day -> (this and (1 shl (day.isoDayNumber - 1))) != 0 }.toSet()
}

private fun Set<DayOfWeek>.toMask(): Int = fold(0) { acc, day -> acc or (1 shl (day.isoDayNumber - 1)) }
