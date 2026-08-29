package com.stax.feature.sites.presentation

/**
 * One-time effects of the Sites screen (§4.12, §10.1). The ViewModel names the destination;
 * `SitesRoot` hands it to the `:app` back stack.
 */
sealed interface SitesEvent {

    /** §4.12.5's "Use this site": the suggested site goes back to whatever asked for one. */
    data class UseSite(val siteId: Long) : SitesEvent

    /** §4.12.5's "Pick another" → the full-screen site picker (§4.12.7). */
    data object PickAnotherSite : SitesEvent

    /** §4.12.8's "View full history" → the dose history scoped to this site. */
    data class ViewSiteHistory(val siteId: Long) : SitesEvent
}
