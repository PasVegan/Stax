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
import com.stax.feature.protocols.presentation.form.ProtocolFormArgs
import com.stax.feature.protocols.presentation.form.ProtocolFormRoot

/**
 * Contributes the Protocols feature's `NavEntry`s to the app's `NavDisplay` `entryProvider`.
 *
 * Navigation actions are lambda callbacks supplied by `:app` (spec §10.3); this module never
 * references another feature's routes. Route arguments reach the screen through the typed [NavKey]
 * passed to the entry.
 *
 * Protocols list + detail are tagged as the list-detail Scene's panes (§6.4.2): two panes at 600dp+,
 * single-pane push below.
 *
 * [onCreateCompound] is §4.0.2's empty-picker CTA: a protocol needs a compound, and with none to pick
 * the only useful thing left is Create Compound — which belongs to another feature, so this module
 * names the intent and `:app` names the destination.
 */
fun EntryProviderScope<NavKey>.protocolsEntries(
    onProtocolClick: (Long) -> Unit,
    onCreateProtocol: () -> Unit,
    onEditProtocol: (Long) -> Unit,
    onCreateCompound: () -> Unit,
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
            // §4.8.2's Edit quick action, until Protocol Detail itself lands (M9-06).
            Button(onClick = { onEditProtocol(key.protocolId) }) { Text(text = "Edit") }
            Button(onClick = onBack) { Text(text = "Back") }
        }
    }
    entry<CreateProtocolRoute> { key ->
        // Onboarding step 3 reuses this form (§4.14 step 3): same screen, app bar titled
        // "Create your first protocol · 3 of 3" with Skip in its trailing slot, driven by the flag.
        // It is the last step, so a saved protocol and Skip both end the flow through one callback.
        ProtocolFormRoot(
            args = ProtocolFormArgs(protocolId = null, isOnboarding = key.onboarding),
            onDone = if (key.onboarding) onFinishOnboarding else onBack,
            onCreateCompound = onCreateCompound,
        )
    }
    entry<EditProtocolRoute> { key ->
        ProtocolFormRoot(
            args = ProtocolFormArgs(protocolId = key.protocolId),
            onDone = onBack,
            onCreateCompound = onCreateCompound,
        )
    }
}

/**
 * Identifies the Protocols list-detail scene. Each scaffold scene in the app's single `NavDisplay`
 * needs its own key or they share one `AnimatedContent` slot and crash (see `StaxListDetailScene`).
 */
private const val PROTOCOLS_SCENE_KEY = "protocols"

/** Placeholder id used to demonstrate the list → detail push until the real list lands. */
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
