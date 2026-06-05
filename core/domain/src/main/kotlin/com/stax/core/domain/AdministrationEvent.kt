package com.stax.core.domain

import kotlin.time.Instant

enum class AdministrationEventStatus { TAKEN, SKIPPED, PARTIAL }

data class AdministrationEvent(
    val id: Long,
    val loggedAt: Instant,
    val route: Route,
    val status: AdministrationEventStatus,
    val injectionSiteId: Long?,
    val notes: String?,
    val components: List<DoseComponent>,
    val createdAt: Instant,
    val updatedAt: Instant,
)
