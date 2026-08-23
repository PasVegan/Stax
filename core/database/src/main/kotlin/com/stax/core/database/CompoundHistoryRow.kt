package com.stax.core.database

import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import kotlin.time.Instant

/**
 * Room projection for one row of a compound's dose history (§4.3.8): the administration event joined
 * to the dose component that names the compound, plus the injection site's display name.
 *
 * Flat columns rather than `@Embedded` entities: `administration_event` and `dose_component` both
 * carry `id` and `notes`, so embedding either would need every column aliased anyway — and the row
 * needs neither of the colliding ones.
 */
data class CompoundHistoryRow(
    val eventId: Long,
    val loggedAt: Instant,
    val status: AdministrationEventStatus,
    val actualDoseValue: Decimal,
    val actualDoseUnit: UnitCode,
    val concentrationAmountValue: Decimal?,
    val concentrationAmountUnit: UnitCode?,
    val concentrationPerValue: Decimal?,
    val concentrationPerUnit: UnitCode?,
    val injectionSiteName: String?,
)
