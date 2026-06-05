package com.stax.core.data.mapper

import com.stax.core.database.AdministrationEventEntity
import com.stax.core.database.DoseComponentEntity
import com.stax.core.domain.AdministrationEvent
import com.stax.core.domain.DoseComponent

// ---------------------------------------------------------------------------
// AdministrationEventEntity ↔ AdministrationEvent
// ---------------------------------------------------------------------------

/**
 * Maps an [AdministrationEventEntity] to [AdministrationEvent].
 *
 * [components] must be pre-mapped domain objects fetched from the `dose_component`
 * table for this event. Repositories are responsible for providing these.
 */
fun AdministrationEventEntity.toDomain(components: List<DoseComponent> = emptyList()): AdministrationEvent =
    AdministrationEvent(
        id = id,
        loggedAt = loggedAt,
        route = route.toDomain(),
        status = status.toDomain(),
        injectionSiteId = injectionSiteId,
        notes = notes,
        components = components,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** Maps [AdministrationEvent] back to [AdministrationEventEntity]. Components live in a separate table — see [toComponentEntities]. */
fun AdministrationEvent.toEntity(): AdministrationEventEntity =
    AdministrationEventEntity(
        id = id,
        loggedAt = loggedAt,
        route = route.toEntity(),
        status = status.toEntity(),
        injectionSiteId = injectionSiteId,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** Produces the companion [DoseComponentEntity] rows for this event's components. */
fun AdministrationEvent.toComponentEntities(): List<DoseComponentEntity> =
    components.map { it.toEntity() }
