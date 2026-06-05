package com.stax.core.data.mapper

import com.stax.core.database.ScheduledDoseEntity
import com.stax.core.domain.Quantity
import com.stax.core.domain.ScheduledDose
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

// ---------------------------------------------------------------------------
// ScheduledDoseEntity ↔ ScheduledDose
// ---------------------------------------------------------------------------

fun ScheduledDoseEntity.toDomain(): ScheduledDose =
    ScheduledDose(
        id = id,
        protocolId = protocolId,
        compoundSupplyId = compoundSupplyId,
        scheduledAt = scheduledAt,
        hasTimeOfDay = hasTimeOfDay,
        plannedDose = Quantity(plannedDoseValue, plannedDoseUnit),
        route = route.toDomain(),
        status = status.toDomain(),
        administrationEventId = administrationEventId,
        createdAt = createdAt,
    )

/**
 * Maps [ScheduledDose] back to [ScheduledDoseEntity].
 *
 * [originalLocalDate], [originalLocalTime], and [originalZone] are DB-only fields
 * used for snooze timezone reconstruction. Pass the original entity's values when
 * updating an existing row, or the computed initial values on insert.
 */
fun ScheduledDose.toEntity(
    originalLocalDate: LocalDate,
    originalLocalTime: LocalTime?,
    originalZone: String,
): ScheduledDoseEntity =
    ScheduledDoseEntity(
        id = id,
        protocolId = protocolId,
        compoundSupplyId = compoundSupplyId,
        scheduledAt = scheduledAt,
        hasTimeOfDay = hasTimeOfDay,
        plannedDoseValue = plannedDose.value,
        plannedDoseUnit = plannedDose.unit,
        route = route.toEntity(),
        status = status.toEntity(),
        administrationEventId = administrationEventId,
        originalLocalDate = originalLocalDate,
        originalLocalTime = originalLocalTime,
        originalZone = originalZone,
        createdAt = createdAt,
    )
