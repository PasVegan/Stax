package com.stax.feature.compounds.presentation.navigation

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
 * Contributes the Compounds feature's `NavEntry`s to the app's `NavDisplay` `entryProvider`.
 *
 * Every navigation action is a lambda callback supplied by `:app` (spec §10.3) — this module never
 * references another feature's routes. Route arguments reach the screen (and, later, its ViewModel)
 * through the typed [NavKey] passed to the entry, never via `toRoute<T>()`.
 *
 * Compounds list + detail are tagged as the list-detail Scene's panes (§6.4.2): two panes at 600dp+,
 * single-pane push below.
 */
fun EntryProviderScope<NavKey>.compoundsEntries(
    onCompoundClick: (Long) -> Unit,
    onCreateCompound: () -> Unit,
    onEditCompound: (Long) -> Unit,
    onReconstitute: (Long) -> Unit,
    onBack: () -> Unit,
) {
    entry<CompoundsRoute>(
        metadata = StaxListDetailScene.listPane(
            detailPlaceholder = { PlaceholderScreen(title = "Select a compound") },
        ),
    ) {
        PlaceholderScreen(title = "Compounds") {
            Button(onClick = onCreateCompound) { Text(text = "New compound") }
            Button(onClick = { onCompoundClick(SAMPLE_COMPOUND_ID) }) { Text(text = "Open compound") }
        }
    }
    entry<CompoundDetailRoute>(metadata = StaxListDetailScene.detailPane()) { key ->
        PlaceholderScreen(title = "Compound #${key.compoundId}") {
            Button(onClick = { onEditCompound(key.compoundId) }) { Text(text = "Edit") }
            Button(onClick = { onReconstitute(key.compoundId) }) { Text(text = "Reconstitute") }
            Button(onClick = onBack) { Text(text = "Back") }
        }
    }
    entry<CreateCompoundRoute> {
        PlaceholderScreen(title = "New compound") {
            Button(onClick = onBack) { Text(text = "Cancel") }
        }
    }
    entry<EditCompoundRoute> { key ->
        PlaceholderScreen(title = "Edit compound #${key.compoundId}") {
            Button(onClick = onBack) { Text(text = "Done") }
        }
    }
}

/** Placeholder id used to demonstrate the list → detail push until the real list lands. */
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
