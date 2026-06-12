package com.stax.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.stax.core.design.system.StaxTheme
import com.stax.core.domain.AppTheme
import com.stax.core.domain.Settings
import com.stax.core.domain.repository.SettingsRepository
import com.stax.feature.compounds.presentation.navigation.CompoundDetailRoute
import com.stax.feature.compounds.presentation.navigation.CreateCompoundRoute
import com.stax.feature.compounds.presentation.navigation.EditCompoundRoute
import com.stax.feature.compounds.presentation.navigation.compoundsEntries
import com.stax.feature.dashboard.presentation.navigation.DashboardRoute
import com.stax.feature.dashboard.presentation.navigation.dashboardEntries
import com.stax.feature.logging.presentation.navigation.loggingEntries
import com.stax.feature.onboarding.presentation.navigation.onboardingEntries
import com.stax.feature.protocols.presentation.navigation.CreateProtocolRoute
import com.stax.feature.protocols.presentation.navigation.ProtocolDetailRoute
import com.stax.feature.protocols.presentation.navigation.protocolsEntries
import com.stax.feature.reconstitution.presentation.navigation.ReconstitutionRoute
import com.stax.feature.reconstitution.presentation.navigation.reconstitutionEntries
import com.stax.feature.settings.presentation.navigation.settingsEntries
import com.stax.feature.sites.presentation.navigation.sitesEntries
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            StaxApp()
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun StaxApp(settingsRepository: SettingsRepository = koinInject()) {
    val settings by settingsRepository.observe().collectAsStateWithLifecycle(initialValue = null)
    val systemDarkTheme = isSystemInDarkTheme()

    StaxTheme(
        darkTheme = settings.darkTheme(systemDarkTheme),
        dynamicColor = settings?.dynamicColor ?: true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            StaxNavDisplay(modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * The app's single Navigation 3 host. The [androidx.navigation3.runtime.NavBackStack] is saveable
 * (survives configuration changes and process death) and route arguments reach each screen through
 * the typed `NavKey` passed to its entry. The `entryProvider` is assembled from each feature's
 * `<feature>Entries` extension; all cross-feature navigation is wired here as lambda callbacks so
 * feature modules never reference one another (spec §10.3).
 *
 * Per-destination back stacks (M5-03) and the `NavigationSuiteScaffold` chrome (M5-02) land in
 * later milestones; this milestone establishes the typed-route + entry-provider skeleton.
 */
@Suppress("FunctionName")
@Composable
private fun StaxNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(DashboardRoute)

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            dashboardEntries(
                onCompoundClick = { compoundId -> backStack.add(CompoundDetailRoute(compoundId)) },
            )
            compoundsEntries(
                onCompoundClick = { compoundId -> backStack.add(CompoundDetailRoute(compoundId)) },
                onCreateCompound = { backStack.add(CreateCompoundRoute) },
                onEditCompound = { compoundId -> backStack.add(EditCompoundRoute(compoundId)) },
                onReconstitute = { compoundId -> backStack.add(ReconstitutionRoute(compoundId)) },
                onBack = { backStack.removeLastOrNull() },
            )
            protocolsEntries(
                onProtocolClick = { protocolId -> backStack.add(ProtocolDetailRoute(protocolId)) },
                onCreateProtocol = { backStack.add(CreateProtocolRoute) },
                onBack = { backStack.removeLastOrNull() },
            )
            sitesEntries()
            settingsEntries()
            reconstitutionEntries(
                onBack = { backStack.removeLastOrNull() },
            )
            loggingEntries(
                onBack = { backStack.removeLastOrNull() },
            )
            onboardingEntries(
                onOnboardingComplete = { backStack.removeLastOrNull() },
            )
        },
    )
}

private fun Settings?.darkTheme(systemDarkTheme: Boolean): Boolean = when (this?.theme) {
    AppTheme.LIGHT -> false
    AppTheme.DARK -> true
    AppTheme.SYSTEM, null -> systemDarkTheme
}
