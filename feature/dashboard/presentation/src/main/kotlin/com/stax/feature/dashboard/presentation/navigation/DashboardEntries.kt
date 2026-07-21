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
import com.stax.core.design.system.StaxSupportingPaneScene
import com.stax.core.design.system.paneInsets

/**
 * Contributes the Dashboard (Home) `NavEntry`s to the app's `NavDisplay` `entryProvider`.
 *
 * The Dashboard uses the supporting-pane Scene (§6.4.2): [DashboardRoute] is the main pane and
 * [DashboardSupportingRoute] the supporting pane (inventory warnings + recent activity), rendered
 * beside the main pane at Medium+. Opening a compound from a Dashboard card is a cross-feature jump,
 * so it is a lambda callback supplied by `:app` (spec §10.3) — this module never references the
 * Compounds routes.
 */
fun EntryProviderScope<NavKey>.dashboardEntries(onCompoundClick: (Long) -> Unit, onShowSupporting: () -> Unit) {
    entry<DashboardRoute>(metadata = StaxSupportingPaneScene.mainPane()) {
        PlaceholderScreen(title = "Dashboard") {
            Button(onClick = { onCompoundClick(SAMPLE_COMPOUND_ID) }) { Text(text = "Open compound") }
            Button(onClick = onShowSupporting) { Text(text = "Show supporting") }
        }
    }
    entry<DashboardSupportingRoute>(metadata = StaxSupportingPaneScene.supportingPane()) {
        PlaceholderScreen(title = "Inventory & activity")
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
            .paneInsets()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        actions()
    }
}
