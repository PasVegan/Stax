package com.stax.feature.sites.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stax.feature.sites.presentation.SitesRoot

/**
 * Contributes the Sites `NavEntry` to the app's `NavDisplay` `entryProvider`.
 *
 * Every way out ends in another feature — a dose logged against the site §4.12.5 picked, the full
 * site picker (§4.12.7), and §4.12.8's full history of one site — so the screen names the intent and
 * `:app` names the destination (spec §10.3).
 */
fun EntryProviderScope<NavKey>.sitesEntries(
    onUseSite: (Long) -> Unit,
    onPickAnotherSite: () -> Unit,
    onViewSiteHistory: (Long) -> Unit,
) {
    entry<SitesRoute> {
        SitesRoot(
            onUseSite = onUseSite,
            onPickAnotherSite = onPickAnotherSite,
            onViewSiteHistory = onViewSiteHistory,
        )
    }
}
