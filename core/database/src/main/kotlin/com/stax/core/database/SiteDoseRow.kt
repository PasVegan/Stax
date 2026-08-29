package com.stax.core.database

import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import kotlin.time.Instant

/**
 * Room projection for one dose given at an injection site (§4.12.8): the administration event joined
 * to a dose component and the compound that component named.
 *
 * Flat columns for the same reason [CompoundHistoryRow] uses them — `administration_event`,
 * `dose_component` and `compound_supply` all carry `id`, so `@Embedded` would need every column
 * aliased anyway.
 */
data class SiteDoseRow(
    val eventId: Long,
    val loggedAt: Instant,
    val compoundName: String,
    val doseValue: Decimal,
    val doseUnit: UnitCode,
)
