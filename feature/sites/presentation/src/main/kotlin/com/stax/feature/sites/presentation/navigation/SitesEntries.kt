package com.stax.feature.sites.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stax.core.design.system.paneInsets

/**
 * Contributes the Sites `NavEntry` to the app's `NavDisplay` `entryProvider`. A self-contained
 * top-level destination with no cross-feature navigation (spec §10.3).
 */
fun EntryProviderScope<NavKey>.sitesEntries() {
    entry<SitesRoute> {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .paneInsets()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Sites", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
