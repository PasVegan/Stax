package com.stax.core.domain

import kotlin.time.Instant

enum class ScheduledDoseStatus { PENDING, TAKEN, SKIPPED, MISSED, PARTIAL }

data class ScheduledDose(
    val id: Long,
    val protocolId: Long,
    val compoundSupplyId: Long,
    val scheduledAt: Instant,
    val hasTimeOfDay: Boolean,
    val plannedDose: Quantity,
    val route: Route,
    val status: ScheduledDoseStatus,
    val administrationEventId: Long?,
    val createdAt: Instant,
)
