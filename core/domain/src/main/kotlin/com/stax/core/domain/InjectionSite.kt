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
