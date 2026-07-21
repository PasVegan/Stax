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
import com.stax.core.design.system.paneInsets

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
    onFinishOnboarding: () -> Unit,
) {
    entry<ProtocolsRoute>(
        metadata = StaxListDetailScene.listPane(
            sceneKey = PROTOCOLS_SCENE_KEY,
            detailPlaceholder = { PlaceholderScreen(title = "Select a protocol") },
        ),
    ) {
        PlaceholderScreen(title = "Protocols") {
            Button(onClick = onCreateProtocol) { Text(text = "New protocol") }
            Button(onClick = { onProtocolClick(SAMPLE_PROTOCOL_ID) }) { Text(text = "Open protocol") }
        }
    }
    entry<ProtocolDetailRoute>(metadata = StaxListDetailScene.detailPane(PROTOCOLS_SCENE_KEY)) { key ->
        PlaceholderScreen(title = "Protocol #${key.protocolId}") {
            Button(onClick = onBack) { Text(text = "Back") }
        }
    }
    entry<CreateProtocolRoute> { key ->
        // Onboarding step 3 reuses this form (§4.14 step 3): same screen, app bar titled
        // "Create your first protocol · 3 of 3" with Skip in its trailing slot. It is the last step,
        // so Save and Skip both end the flow and both report it through [onFinishOnboarding] — the
        // real app bar and Save arrive with the form itself (M9-03), which is a placeholder for now.
        val title = if (key.onboarding) "Create your first protocol · 3 of 3" else "New protocol"
        PlaceholderScreen(title = title) {
            if (key.onboarding) {
                Button(onClick = onFinishOnboarding) { Text(text = "Skip") }
            } else {
                Button(onClick = onBack) { Text(text = "Cancel") }
            }
        }
    }
}

/** Placeholder id used to demonstrate the list → detail push until the real list lands. */
private const val PROTOCOLS_SCENE_KEY = "protocols"

private const val SAMPLE_PROTOCOL_ID = 1L

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
