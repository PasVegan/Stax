package com.stax.feature.protocols.presentation.navigation

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
import com.stax.core.design.system.StaxListDetailScene

/**
 * Contributes the Protocols feature's `NavEntry`s to the app's `NavDisplay` `entryProvider`.
 *
 * Navigation actions are lambda callbacks supplied by `:app` (spec §10.3); this module never
 * references another feature's routes. Route arguments reach the screen through the typed [NavKey]
 * passed to the entry.
 *
 * Protocols list + detail are tagged as the list-detail Scene's panes (§6.4.2): two panes at 600dp+,
 * single-pane push below.
 */
fun EntryProviderScope<NavKey>.protocolsEntries(
    onProtocolClick: (Long) -> Unit,
    onCreateProtocol: () -> Unit,
    onBack: () -> Unit,
) {
    entry<ProtocolsRoute>(
        metadata = StaxListDetailScene.listPane(
            detailPlaceholder = { PlaceholderScreen(title = "Select a protocol") },
        ),
    ) {
        PlaceholderScreen(title = "Protocols") {
            Button(onClick = onCreateProtocol) { Text(text = "New protocol") }
            Button(onClick = { onProtocolClick(SAMPLE_PROTOCOL_ID) }) { Text(text = "Open protocol") }
        }
    }
    entry<ProtocolDetailRoute>(metadata = StaxListDetailScene.detailPane()) { key ->
        PlaceholderScreen(title = "Protocol #${key.protocolId}") {
            Button(onClick = onBack) { Text(text = "Back") }
        }
    }
    entry<CreateProtocolRoute> {
        PlaceholderScreen(title = "New protocol") {
            Button(onClick = onBack) { Text(text = "Cancel") }
        }
    }
}

/** Placeholder id used to demonstrate the list → detail push until the real list lands. */
private const val SAMPLE_PROTOCOL_ID = 1L

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
