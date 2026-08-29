package com.stax.feature.sites.presentation.picker

/**
 * One-time effects of the site picker (§4.12.7, §10.1).
 *
 * Both ways out are the same statement — the picker is done — and differ only in whether it carries
 * a site. Which screen is underneath is `:app`'s to know (§10.3).
 */
sealed interface SitePickerEvent {

    /** §4.12.7's "Pick site": the chosen site goes back to whoever opened the picker. */
    data class SitePicked(val siteId: Long) : SitePickerEvent

    /** §4.12.7's Cancel, its back arrow and the back gesture — the caller keeps whatever it had. */
    data object Dismissed : SitePickerEvent
}
