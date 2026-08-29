package com.stax.core.data.mapper

import com.stax.core.database.AdministrationEventEntity
import com.stax.core.database.CompoundHistoryRow
import com.stax.core.database.DoseComponentEntity
import com.stax.core.database.SiteDoseRow
import com.stax.core.domain.AdministrationEvent
import com.stax.core.domain.CompoundHistoryEntry
import com.stax.core.domain.Concentration
import com.stax.core.domain.DoseComponent
import com.stax.core.domain.Quantity
import com.stax.core.domain.SiteDose
import com.stax.core.domain.SiteUse
import com.stax.core.domain.UnitFamily

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
fun AdministrationEvent.toEntity(): AdministrationEventEntity = AdministrationEventEntity(
    id = id,
    loggedAt = loggedAt,
    route = route.toEntity(),
    status = status.toEntity(),
    injectionSiteId = injectionSiteId,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * Projects an event onto [SiteUse] (§4.12.3), or null when it named no site — an oral or topical
 * dose, or one logged without picking one, is not a use of any site.
 */
fun AdministrationEventEntity.toSiteUse(): SiteUse? = injectionSiteId?.let { siteId ->
    SiteUse(injectionSiteId = siteId, route = route.toDomain(), loggedAt = loggedAt)
}

/** Produces the companion [DoseComponentEntity] rows for this event's components. */
fun AdministrationEvent.toComponentEntities(): List<DoseComponentEntity> = components.map { it.toEntity() }

// ---------------------------------------------------------------------------
// CompoundHistoryRow → CompoundHistoryEntry  (§4.3.8)
// ---------------------------------------------------------------------------

/**
 * Maps one joined history row to its domain read model.
 *
 * The volume is derived here rather than stored, from the concentration snapshotted at log time
 * (§3.5) — but only when that concentration's units divide into the dose. `Quantity.div` throws on a
 * cross-family divisor and on count units, both of which are reachable data (a compound whose
 * concentration is `500 mg / 1 capsule` logged in tablets), and a history row is not the place to
 * raise them: no volume simply means the row shows the dose alone.
 */
fun CompoundHistoryRow.toDomain(): CompoundHistoryEntry {
    val dose = Quantity(actualDoseValue, actualDoseUnit)
    val concentration = concentrationAtLog()
    return CompoundHistoryEntry(
        eventId = eventId,
        loggedAt = loggedAt,
        status = status.toDomain(),
        dose = dose,
        volume = concentration?.takeIf { it.dividesInto(dose) }?.let { dose / it },
        injectionSiteName = injectionSiteName,
    )
}

private fun CompoundHistoryRow.concentrationAtLog(): Concentration? = Concentration(
    amount = Quantity(concentrationAmountValue ?: return null, concentrationAmountUnit ?: return null),
    per = Quantity(concentrationPerValue ?: return null, concentrationPerUnit ?: return null),
)

/** The preconditions `Quantity.div(Concentration)` asserts, asked instead of caught. */
private fun Concentration.dividesInto(dose: Quantity): Boolean = dose.unit.family == amount.unit.family &&
    (dose.unit == amount.unit || dose.unit.family != UnitFamily.COUNT)

// ---------------------------------------------------------------------------
// SiteDoseRow → SiteDose  (§4.12.8)
// ---------------------------------------------------------------------------

/** Maps one joined site-dose row to its domain read model. */
fun SiteDoseRow.toDomain(): SiteDose = SiteDose(
    eventId = eventId,
    loggedAt = loggedAt,
    compoundName = compoundName,
    dose = Quantity(doseValue, doseUnit),
)
