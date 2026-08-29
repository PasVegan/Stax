package com.stax.feature.sites.presentation

/** Everything the user can do on the Sites screen (§4.12). */
sealed interface SitesAction {

    /** Taps one of §4.12.2's mutually exclusive All routes / SC / IM chips. */
    data class OnRouteFilterClick(val filter: RouteFilter) : SitesAction

    /** Taps the body map's Front / Back tab (§4.12.4). Expanded renders both and hides the tabs. */
    data class OnBodyViewClick(val view: BodyView) : SitesAction

    /** Taps the body map's Dots / Heat toggle (§4.12.4). */
    data class OnMapModeClick(val mode: MapMode) : SitesAction

    /** Taps a dot on the body map (§4.12.4) or a carousel card (§4.12.6) — opens §4.12.8's sheet. */
    data class OnSiteClick(val siteId: Long) : SitesAction

    /** Dismisses §4.12.8's sheet — the scrim, the back gesture, or a drag past the handle. */
    data object OnSiteDetailDismiss : SitesAction

    /** Taps §4.12.8's "View full history" — the site's whole dose history, outside this screen. */
    data object OnViewSiteHistoryClick : SitesAction

    /** Taps §4.12.8's "Mark unavailable" — flips the site's `isAvailable` (§3.6). */
    data object OnToggleSiteAvailabilityClick : SitesAction

    /** Taps §4.12.5's "Use this site" — hands the suggested site back to the caller flow. */
    data object OnUseSuggestedSiteClick : SitesAction

    /** Taps §4.12.5's "Pick another" — opens the full site picker (§4.12.7). */
    data object OnPickAnotherSiteClick : SitesAction
}
