package com.stax.feature.settings.presentation.navigation

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
 * Contributes the Settings `NavEntry` to the app's `NavDisplay` `entryProvider`. A self-contained
 * top-level destination; any future sub-screen navigation is wired as `:app` callbacks (spec §10.3).
 */
fun EntryProviderScope<NavKey>.settingsEntries() {
    entry<SettingsRoute> {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .paneInsets()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
