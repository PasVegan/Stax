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

/**
 * One row of a compound's dose history (§4.3.8): an [AdministrationEvent] joined to the one
 * [DoseComponent] that names the compound the history is scoped to.
 *
 * A projection rather than the whole event, because that is all the history list renders — the full
 * event, with every component it carries, is §4.11's business.
 *
 * [volume] is what [dose] works out to at the concentration snapshotted when it was logged (§3.5),
 * and is null whenever there was none to snapshot or its units do not divide into the dose.
 */
data class CompoundHistoryEntry(
    val eventId: Long,
    val loggedAt: Instant,
    val status: AdministrationEventStatus,
    val dose: Quantity,
    val volume: Quantity?,
    val injectionSiteName: String?,
)
