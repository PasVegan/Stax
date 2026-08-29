package com.stax.core.domain

import kotlin.time.Instant

enum class InjectionSide { LEFT, RIGHT, CENTER, NOT_APPLICABLE }

enum class Sublocation { UPPER, LOWER, INNER, OUTER }

data class InjectionSite(
    val id: Long,
    val name: String,
    val bodyRegion: BodyRegion,
    val side: InjectionSide,
    val sublocation: Sublocation?,
    val lastUsedAt: Instant?,
    val avoidUntil: Instant?,
    val notes: String?,
    val isAvailable: Boolean,
)

/**
 * One logged dose that named an injection site (§4.12.3, §4.12.6).
 *
 * A projection of [AdministrationEvent] rather than the event itself: the Sites screen counts uses
 * and reads their route, and loading every dose component to do that would be most of a history for
 * a number on a tile.
 */
data class SiteUse(val injectionSiteId: Long, val route: Route, val loggedAt: Instant)

/**
 * One dose given at an injection site, as §4.12.8's detail sheet reads it.
 *
 * A row per dose *component*, not per event: the sheet names the compound that went in, and a dose
 * that stacked two of them (§4.10.3) put both into the same site. [eventId] is what tells the two
 * apart again — the sheet counts uses by event and lists them by component.
 *
 * Skipped doses are not here: nothing was administered, so the site was not used (§3.4).
 */
data class SiteDose(val eventId: Long, val loggedAt: Instant, val compoundName: String, val dose: Quantity)
