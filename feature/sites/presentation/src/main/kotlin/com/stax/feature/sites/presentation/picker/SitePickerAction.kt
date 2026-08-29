package com.stax.feature.sites.presentation.picker

/** Everything the user can do in the site picker (§4.12.7). */
sealed interface SitePickerAction {

    /** Taps one of §4.12.7's mutually exclusive All / Ready / Cooling chips. */
    data class OnFilterClick(val filter: PickerFilter) : SitePickerAction

    /** Taps a row — the suggested one or any in the full list. Selects it; nothing leaves the screen. */
    data class OnSiteClick(val siteId: Long) : SitePickerAction

    /** Taps the dock's Cancel, the app bar's `arrow_back`, or the back gesture — leaves with nothing. */
    data object OnCancelClick : SitePickerAction

    /** Taps the dock's "Pick site" — hands the selected site back to the caller. */
    data object OnPickClick : SitePickerAction
}
