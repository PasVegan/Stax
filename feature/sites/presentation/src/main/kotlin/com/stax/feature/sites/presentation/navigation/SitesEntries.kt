package com.stax.feature.sites.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stax.feature.sites.presentation.SitesRoot
import com.stax.feature.sites.presentation.picker.SitePickerArgs
import com.stax.feature.sites.presentation.picker.SitePickerRoot

/**
 * Contributes the Sites feature's `NavEntry`s to the app's `NavDisplay` `entryProvider`.
 *
 * Every way out ends in another feature — a dose logged against the site §4.12.5 picked, and §4.12.8's
 * full history of one site — so the screen names the intent and `:app` names the destination
 * (spec §10.3).
 *
 * [onSitePicked] is §4.12.7's "returns the selected site to the caller": the picker is stacked on
 * whichever screen opened it (§6.2), and only `:app` knows which that is — so the picker states which
 * site was chosen and `:app` pops it and hands the site on. [onPickerDismiss] is the same statement
 * without a site: Cancel, the back arrow and the back gesture all leave the caller as it was.
 */
fun EntryProviderScope<NavKey>.sitesEntries(
    onUseSite: (Long) -> Unit,
    onPickAnotherSite: () -> Unit,
    onViewSiteHistory: (Long) -> Unit,
    onSitePicked: (Long) -> Unit,
    onPickerDismiss: () -> Unit,
) {
    entry<SitesRoute> {
        SitesRoot(
            onUseSite = onUseSite,
            onPickAnotherSite = onPickAnotherSite,
            onViewSiteHistory = onViewSiteHistory,
        )
    }
    entry<SitePickerRoute> { key ->
        SitePickerRoot(
            args = SitePickerArgs(compoundName = key.compoundName, route = key.route),
            onSitePicked = onSitePicked,
            onDismiss = onPickerDismiss,
        )
    }
}
