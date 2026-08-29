package com.stax.feature.sites.presentation

/** Everything the user can do on the Sites screen (§4.12). */
sealed interface SitesAction {

    /** Taps one of §4.12.2's mutually exclusive All routes / SC / IM chips. */
    data class OnRouteFilterClick(val filter: RouteFilter) : SitesAction

    /** Taps the body map's Front / Back tab (§4.12.4). Expanded renders both and hides the tabs. */
    data class OnBodyViewClick(val view: BodyView) : SitesAction

    /** Taps the body map's Dots / Heat toggle (§4.12.4). */
    data class OnMapModeClick(val mode: MapMode) : SitesAction

    /** Taps §4.12.5's "Use this site" — hands the suggested site back to the caller flow. */
    data object OnUseSuggestedSiteClick : SitesAction

    /** Taps §4.12.5's "Pick another" — opens the full site picker (§4.12.7). */
    data object OnPickAnotherSiteClick : SitesAction
}
