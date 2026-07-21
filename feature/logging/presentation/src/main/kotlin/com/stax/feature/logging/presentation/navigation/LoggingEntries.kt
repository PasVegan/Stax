package com.stax.feature.logging.presentation.navigation

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
import com.stax.core.design.system.paneInsets

/**
 * Contributes the Logging `NavEntry` to the app's `NavDisplay` `entryProvider`.
 *
 * The optional `compoundId` argument reaches the screen through the typed [NavKey] passed to the
 * entry (never `toRoute<T>()`); back navigation is the [onBack] lambda supplied by `:app`
 * (spec §10.3).
 */
fun EntryProviderScope<NavKey>.loggingEntries(onBack: () -> Unit) {
    entry<LogDoseRoute> { key ->
        val subtitle = key.compoundId?.let { "compound #$it" } ?: "pick compound"
        PlaceholderScreen(title = "Log dose ($subtitle)") {
            Button(onClick = onBack) { Text(text = "Back") }
        }
    }
}

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
