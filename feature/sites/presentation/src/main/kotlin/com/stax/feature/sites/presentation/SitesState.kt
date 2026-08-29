package com.stax.feature.sites.presentation

import androidx.compose.runtime.Immutable
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.InjectionSide
import com.stax.core.domain.Route
import com.stax.core.domain.Sublocation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * §4.12.2's three chips. Single select, [ALL] by default.
 *
 * Only the two injected routes get a chip: an oral or topical dose has no site to rotate, so a chip
 * for it would filter every site off the screen.
 */
enum class RouteFilter(val route: Route?) {
    ALL(null),
    SUBCUTANEOUS(Route.SUBCUTANEOUS),
    INTRAMUSCULAR(Route.INTRAMUSCULAR),
    ;

    /** Whether a site in [region] can take the filtered route (§4.12.2). */
    fun accepts(region: BodyRegion): Boolean = route == null || route in region.routes()

    /** Whether a dose logged by [doseRoute] counts toward the filtered "This month" tile (§4.12.3). */
    fun accepts(doseRoute: Route): Boolean = route == null || route == doseRoute
}

/** §4.12.4's Front / Back tabs — which half of the body a site is on. */
enum class BodyView { FRONT, BACK }

/** §4.12.4's Dots / Heat toggle. Heat itself lands with M10-03; the toggle is the screen's state. */
enum class MapMode { DOTS, HEAT }

/**
 * §4.12.4's four dot states, in the order the legend lists them.
 *
 * A site the user marked unavailable (§4.12.8) has no state of its own — §4.12.4's legend has these
 * four and no more — so it simply never becomes [SUGGESTED] and never counts as Ready (§4.12.3).
 */
enum class SiteStatus { SUGGESTED, COOLING, RECENT, READY }

/**
 * One injection site as the body map and the recent-activity carousel read it (§4.12.4, §4.12.6).
 *
 * [bodyRegion] / [side] / [sublocation] travel with the site because the map places its dot from
 * them (§4.12.4, M10-02) — the name is what the carousel and the detail sheet write, not what the
 * renderer positions by.
 *
 * [daysSinceLastUse] is null for a site that has never been used, which is a different thing from
 * zero and reads differently in every place it appears.
 */
@Immutable
data class SiteUi(
    val id: Long,
    val name: String,
    val bodyRegion: BodyRegion,
    val side: InjectionSide,
    val sublocation: Sublocation?,
    val status: SiteStatus,
    val daysSinceLastUse: Int?,
) {
    /** Which of §4.12.4's two body views this dot belongs to. */
    val bodyView: BodyView get() = bodyRegion.bodyView
}

/**
 * §4.12.5's hero: the site the rotation points at next, with the two facts that justify it.
 *
 * [isCoolingComplete] is "this site was cooling and no longer is" — a site that never carried an
 * `avoidUntil` has no cooldown to have completed, and the chip stays off rather than claiming one.
 */
@Immutable
data class SuggestedSiteUi(val id: Long, val name: String, val daysRested: Int?, val isCoolingComplete: Boolean)

/**
 * UI state of the Sites screen (§4.12).
 *
 * Every list here is already narrowed by [routeFilter] — §4.12.2 filters the stats, the map and the
 * carousel together, so the ViewModel keeps the unfiltered sites and the screen only ever sees what
 * the active chip left.
 *
 * The map's sites arrive pre-split into [frontSites] and [backSites] because Expanded renders both
 * halves at once (§6.4.2) and the two narrower layouts render whichever [bodyView] names — one
 * partition either way.
 */
data class SitesState(
    val routeFilter: RouteFilter = RouteFilter.ALL,
    val bodyView: BodyView = BodyView.FRONT,
    val mapMode: MapMode = MapMode.DOTS,
    val readyCount: Int = 0,
    val coolingCount: Int = 0,
    val usesThisMonth: Int = 0,
    val frontSites: ImmutableList<SiteUi> = persistentListOf(),
    val backSites: ImmutableList<SiteUi> = persistentListOf(),
    val suggested: SuggestedSiteUi? = null,
    val recent: ImmutableList<SiteUi> = persistentListOf(),
    val isLoading: Boolean = true,
) {
    /** The dots of whichever body view the tabs are on (§4.12.4). */
    fun sitesOn(view: BodyView): ImmutableList<SiteUi> = if (view == BodyView.FRONT) frontSites else backSites
}

/**
 * Which body view a region is seen from (§4.12.4): the glutes, hamstrings and lower back are the
 * Back tab, everything the presets place (§5.8.6) is the Front one.
 */
internal val BodyRegion.bodyView: BodyView
    get() = when (this) {
        BodyRegion.GLUTE, BodyRegion.HAMSTRING, BodyRegion.LOWER_BACK -> BodyView.BACK
        else -> BodyView.FRONT
    }

/**
 * The routes a region can take (§4.12.2).
 *
 * Derived rather than stored: an [com.stax.core.domain.InjectionSite] carries no route (§3.6), and
 * §4.12.2's SC / IM chips still have to narrow the map to the sites that route is given at. Muscle
 * bellies — deltoid, glute, vastus lateralis — take an intramuscular dose; subcutaneous tissue takes
 * a subcutaneous one; the lateral thigh is both, which is why this returns a set and not a route.
 */
internal fun BodyRegion.routes(): Set<Route> = when (this) {
    BodyRegion.DELT, BodyRegion.GLUTE -> INTRAMUSCULAR_ONLY
    BodyRegion.QUADRICEPS -> BOTH_ROUTES
    else -> SUBCUTANEOUS_ONLY
}

private val SUBCUTANEOUS_ONLY = setOf(Route.SUBCUTANEOUS)
private val INTRAMUSCULAR_ONLY = setOf(Route.INTRAMUSCULAR)
private val BOTH_ROUTES = setOf(Route.SUBCUTANEOUS, Route.INTRAMUSCULAR)
