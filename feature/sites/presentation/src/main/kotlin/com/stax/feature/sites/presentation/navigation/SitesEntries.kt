package com.stax.feature.sites.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stax.feature.sites.presentation.SitesRoot

/**
 * Contributes the Sites `NavEntry` to the app's `NavDisplay` `entryProvider`.
 *
 * Both ways out of §4.12.5's hero end in another feature — a dose logged against the site it picked,
 * and the full site picker (§4.12.7) — so the screen names the intent and `:app` names the
 * destination (spec §10.3).
 */
fun EntryProviderScope<NavKey>.sitesEntries(onUseSite: (Long) -> Unit, onPickAnotherSite: () -> Unit) {
    entry<SitesRoute> {
        SitesRoot(onUseSite = onUseSite, onPickAnotherSite = onPickAnotherSite)
    }
}
