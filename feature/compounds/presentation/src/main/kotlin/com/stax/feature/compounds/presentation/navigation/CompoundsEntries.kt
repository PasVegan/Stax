package com.stax.feature.compounds.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.stax.feature.compounds.presentation.detail.CompoundDetailArgs
import com.stax.feature.compounds.presentation.detail.CompoundDetailRoot
import com.stax.feature.compounds.presentation.form.CompoundFormArgs
import com.stax.feature.compounds.presentation.form.CompoundFormRoot
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
 *
 * [onOnboardingStepDone] ends onboarding step 2 (§4.14) — the Create form reaches it both by Skip and
 * by a successful Save (§4.4.4, "Onboarding step 2 progresses to step 3"), because from this module's
 * side both are the same statement: the step is over. Which step follows is `:app`'s to know.
 *
 * [onProtocolClick], [onLogDose] and [onAdministrationEventClick] are the three ways out of Compound
 * Detail (§4.3.4, §4.3.8, §4.3.9) — Protocol Detail, Log Dose and Administration Event detail all
 * belong to other features, so this module names the intent and `:app` names the destination (§10.3).
 */
fun EntryProviderScope<NavKey>.compoundsEntries(
    onCompoundClick: (Long) -> Unit,
    onCreateCompound: () -> Unit,
    onEditCompound: (Long) -> Unit,
    // Nullable: the Helper button on the Create form (§4.4.3) has no compound to pre-select yet, and
    // §4.6's standalone calculator is exactly what that case wants.
    onReconstitute: (Long?) -> Unit,
    onProtocolClick: (Long) -> Unit,
    onLogDose: (Long) -> Unit,
    onAdministrationEventClick: (Long) -> Unit,
    onBack: () -> Unit,
    onOnboardingStepDone: () -> Unit,
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
        CompoundDetailRoot(
            args = CompoundDetailArgs(compoundId = key.compoundId),
            onBack = onBack,
            onProtocolClick = onProtocolClick,
            onLogDose = onLogDose,
            onEditCompound = onEditCompound,
            onAdministrationEventClick = onAdministrationEventClick,
        )
    }
    entry<CreateCompoundRoute> { key ->
        // Onboarding step 2 reuses this form (§4.14 step 2): same screen, app bar titled
        // "Add your first compound · 2 of 3" with Skip in its trailing slot, driven by the flag.
        // Both Skip and a saved compound end the step, so both leave through the same callback.
        CompoundFormRoot(
            args = CompoundFormArgs(compoundId = null, isOnboarding = key.onboarding),
            onDone = if (key.onboarding) onOnboardingStepDone else onBack,
            onReconstitute = onReconstitute,
        )
    }
    entry<EditCompoundRoute> { key ->
        CompoundFormRoot(
            args = CompoundFormArgs(compoundId = key.compoundId),
            onDone = onBack,
            onReconstitute = onReconstitute,
        )
    }
}

/**
 * Identifies the Compounds list-detail scene. Each scaffold scene in the app's single `NavDisplay`
 * needs its own key or they share one `AnimatedContent` slot and crash (see `StaxListDetailScene`).
 */
private const val COMPOUNDS_SCENE_KEY = "compounds"

/** The list-detail Scene's empty detail pane at Medium+ (§6.4.2), before anything is selected. */
@Suppress("FunctionName")
@Composable
private fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .paneInsets()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
    }
}
