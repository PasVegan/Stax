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
import com.stax.core.design.system.paneInsets
import com.stax.feature.compounds.presentation.list.CompoundsListRoot

/**
 * Contributes the Compounds feature's `NavEntry`s to the app's `NavDisplay` `entryProvider`.
 *
 * Every navigation action is a lambda callback supplied by `:app` (spec §10.3) — this module never
 * references another feature's routes. Route arguments reach the screen (and, later, its ViewModel)
 * through the typed [NavKey] passed to the entry, never via `toRoute<T>()`.
 *
 * Compounds list + detail are tagged as the list-detail Scene's panes (§6.4.2): two panes at 600dp+,
 * single-pane push below.
 *
 * [onSelectionModeChange] is the same arrangement applied to chrome: the list hides the bottom nav
 * while multi-select is on (§4.2.4), but the nav suite is `:app`'s, so the screen reports the mode
 * and `:app` decides what to do about it.
 */
fun EntryProviderScope<NavKey>.compoundsEntries(
    onCompoundClick: (Long) -> Unit,
    onCreateCompound: () -> Unit,
    onEditCompound: (Long) -> Unit,
    onReconstitute: (Long) -> Unit,
    onBack: () -> Unit,
    onSkipOnboardingStep: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
) {
    entry<CompoundsRoute>(
        metadata = StaxListDetailScene.listPane(
            sceneKey = COMPOUNDS_SCENE_KEY,
            detailPlaceholder = { PlaceholderScreen(title = "Select a compound") },
        ),
    ) {
        CompoundsListRoot(
            onCompoundClick = onCompoundClick,
            onCreateCompound = onCreateCompound,
            onSelectionModeChange = onSelectionModeChange,
        )
    }
    entry<CompoundDetailRoute>(metadata = StaxListDetailScene.detailPane(COMPOUNDS_SCENE_KEY)) { key ->
        PlaceholderScreen(title = "Compound #${key.compoundId}") {
            Button(onClick = { onEditCompound(key.compoundId) }) { Text(text = "Edit") }
            Button(onClick = { onReconstitute(key.compoundId) }) { Text(text = "Reconstitute") }
            Button(onClick = onBack) { Text(text = "Back") }
        }
    }
    entry<CreateCompoundRoute> { key ->
        // Onboarding step 2 reuses this form (§4.14 step 2): same screen, app bar titled
        // "Add your first compound · 2 of 3" with Skip in its trailing slot. The real app bar
        // arrives with the form itself (M7-04) — until then the placeholder carries the actions.
        val title = if (key.onboarding) "Add your first compound · 2 of 3" else "New compound"
        PlaceholderScreen(title = title) {
            if (key.onboarding) {
                Button(onClick = onSkipOnboardingStep) { Text(text = "Skip") }
            } else {
                Button(onClick = onBack) { Text(text = "Cancel") }
            }
        }
    }
    entry<EditCompoundRoute> { key ->
        PlaceholderScreen(title = "Edit compound #${key.compoundId}") {
            Button(onClick = onBack) { Text(text = "Done") }
        }
    }
}

/**
 * Identifies the Compounds list-detail scene. Each scaffold scene in the app's single `NavDisplay`
 * needs its own key or they share one `AnimatedContent` slot and crash (see `StaxListDetailScene`).
 */
private const val COMPOUNDS_SCENE_KEY = "compounds"

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
