package com.stax.core.domain

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The injection-site rotation rule (§4.12.4, §4.12.5, §5.3): which site a dose should go into next,
 * and how long the one it just went into stays out of the rotation.
 *
 * It lives here rather than in the repository or in a ViewModel because four surfaces read it and
 * they have to agree: §4.12.5's hero, §4.12.7's picker, the `primary` ring §4.12.4 draws on the map,
 * and `InjectionSiteRepository.suggestNext`, which is what §4.10.1's Take Dose sheet prefills from.
 * Two copies of this rule is how the hero and the picker end up naming two different sites.
 *
 * Everything here is a pure function of rows the caller already holds — the rotation reads fourteen
 * preset sites (§5.8.6), so filtering them in Kotlin is cheaper than a query per surface.
 */

/** §5.3's last resort when neither the protocol nor Settings names a cooldown. */
const val FALLBACK_SITE_COOLDOWN_DAYS_SC = 5

/** §5.3's last resort when neither the protocol nor Settings names a cooldown. */
const val FALLBACK_SITE_COOLDOWN_DAYS_IM = 7

/** Whether a dose on this route lands in an injection site at all — an oral one has none (§3.6). */
fun Route.requiresInjectionSite(): Boolean = this == Route.SUBCUTANEOUS || this == Route.INTRAMUSCULAR

/**
 * The routes a region can take (§4.12.2).
 *
 * Derived rather than stored: an [InjectionSite] carries no route (§3.6), and both the map's SC / IM
 * chips and the rotation still have to narrow to the sites that route is given at. Muscle bellies —
 * deltoid, glute — take an intramuscular dose; subcutaneous tissue takes a subcutaneous one; the
 * lateral thigh is both, which is why this returns a set and not a route.
 */
fun BodyRegion.routes(): Set<Route> = when (this) {
    BodyRegion.DELT, BodyRegion.GLUTE -> INTRAMUSCULAR_ONLY
    BodyRegion.QUADRICEPS -> BOTH_ROUTES
    else -> SUBCUTANEOUS_ONLY
}

private val SUBCUTANEOUS_ONLY = setOf(Route.SUBCUTANEOUS)
private val INTRAMUSCULAR_ONLY = setOf(Route.INTRAMUSCULAR)
private val BOTH_ROUTES = setOf(Route.SUBCUTANEOUS, Route.INTRAMUSCULAR)

/**
 * §5.3's cooldown source order, first non-null wins: the protocol's override, then the Settings
 * default for the route, then the hardcoded fallback.
 *
 * @param protocolCooldownDays `Protocol.siteCooldownDays` of the protocol the dose belongs to, or
 *   null for a manual log (§4.10.2-b) or a protocol that sets no override.
 * @param settings the app settings, or null before they have been read — the fallback stands in.
 * @return whole days; 0 for a route that has no site to cool (§3.6).
 */
fun siteCooldownDays(route: Route, protocolCooldownDays: Int?, settings: Settings?): Int = when {
    protocolCooldownDays != null -> protocolCooldownDays
    route == Route.SUBCUTANEOUS -> settings?.defaultSiteCooldownDaysSC ?: FALLBACK_SITE_COOLDOWN_DAYS_SC
    route == Route.INTRAMUSCULAR -> settings?.defaultSiteCooldownDaysIM ?: FALLBACK_SITE_COOLDOWN_DAYS_IM
    else -> 0
}

/**
 * Whether this site is still cooling at [now] (§4.12.3's Cooling tile, §4.12.7's "Cool 2d" pill).
 *
 * [avoidUntil] is what §5.3 stamped when the site was last used, under whichever cooldown applied to
 * *that* dose. [cooldownDays] is the cooldown that applies to the dose being placed now — pass it
 * where the caller knows the protocol and route (`suggestNext`), leave it null where it does not
 * (the Sites screen is not dosing, it is reporting). The later of the two wins: a protocol that asks
 * for ten days may not spend a site the stamp cleared after five, and a stamp that has not run out
 * is not cleared by a protocol that asks for less.
 */
fun InjectionSite.isCoolingAt(now: Instant, cooldownDays: Int? = null): Boolean =
    listOfNotNull(avoidUntil, cooldownDays?.let { days -> lastUsedAt?.plus(days.days) })
        .maxOrNull()
        ?.let { it > now } == true

/**
 * The rotation's order (§4.12.5): a site never used yet before one that has been, then the least
 * recently used.
 *
 * Fully ordered, down to the id, because §4.12.4's ring and §4.12.7's "Best" row are derived
 * separately from the same rows and a tie broken two ways would put them on two different sites.
 */
val SITE_ROTATION_ORDER: Comparator<InjectionSite> = compareBy<InjectionSite> { it.lastUsedAt != null }
    .thenBy { it.lastUsedAt }
    .thenBy { it.name.lowercase() }
    .thenBy { it.id }

/**
 * The rotation's next pick out of these sites (§4.12.4 Suggested, §4.12.5): the oldest-used site
 * that is available, in the region the protocol restricts to, takes the route, and is done cooling.
 *
 * @param route the route the dose is given by; null offers every region (§4.12.5's "Pick another"
 *   is choosing a site, not yet a dose to give at it). A route with no site takes none of them.
 * @param restriction `Protocol.injectionSiteRestriction`; null offers every region.
 * @param cooldownDays see [isCoolingAt] — [siteCooldownDays] resolves it where a protocol is known.
 * @return the site, or null when the filters leave nothing — every site cooling is a real answer
 *   the caller has to state (§4.12.5), not an error.
 */
fun List<InjectionSite>.suggestNextSite(
    now: Instant,
    route: Route? = null,
    restriction: BodyRegion? = null,
    cooldownDays: Int? = null,
): InjectionSite? = filter { site ->
    site.isAvailable &&
        (restriction == null || site.bodyRegion == restriction) &&
        (route == null || route in site.bodyRegion.routes()) &&
        !site.isCoolingAt(now, cooldownDays)
}.minWithOrNull(SITE_ROTATION_ORDER)
