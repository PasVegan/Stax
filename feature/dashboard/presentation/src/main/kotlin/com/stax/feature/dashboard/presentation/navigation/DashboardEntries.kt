package com.stax.feature.dashboard.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * Contributes the Dashboard (Home) `NavEntry` to the app's `NavDisplay` `entryProvider`.
 *
 * Opening a compound from a Dashboard card is a cross-feature jump, so it is expressed as a lambda
 * callback supplied by `:app` (spec §10.3) — this module never references the Compounds routes.
 */
fun EntryProviderScope<NavKey>.dashboardEntries(onCompoundClick: (Long) -> Unit) {
    entry<DashboardRoute> {
        PlaceholderScreen(title = "Dashboard") {
            Button(onClick = { onCompoundClick(SAMPLE_COMPOUND_ID) }) { Text(text = "Open compound") }
        }
    }
}

/** Placeholder id used to demonstrate the cross-feature jump until the real cards land. */
private const val SAMPLE_COMPOUND_ID = 1L

@Suppress("FunctionName")
@Composable
private fun PlaceholderScreen(title: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        actions()
    }
}
