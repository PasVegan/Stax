package com.stax.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.stax.core.design.system.StaxIcons
import com.stax.feature.compounds.presentation.navigation.CompoundDetailRoute
import com.stax.feature.compounds.presentation.navigation.CompoundsRoute
import com.stax.feature.compounds.presentation.navigation.CreateCompoundRoute
import com.stax.feature.compounds.presentation.navigation.EditCompoundRoute
import com.stax.feature.compounds.presentation.navigation.compoundsEntries
import com.stax.feature.dashboard.presentation.navigation.DashboardRoute
import com.stax.feature.dashboard.presentation.navigation.dashboardEntries
import com.stax.feature.logging.presentation.navigation.loggingEntries
import com.stax.feature.onboarding.presentation.navigation.onboardingEntries
import com.stax.feature.protocols.presentation.navigation.CreateProtocolRoute
import com.stax.feature.protocols.presentation.navigation.ProtocolDetailRoute
import com.stax.feature.protocols.presentation.navigation.ProtocolsRoute
import com.stax.feature.protocols.presentation.navigation.protocolsEntries
import com.stax.feature.reconstitution.presentation.navigation.ReconstitutionRoute
import com.stax.feature.reconstitution.presentation.navigation.reconstitutionEntries
import com.stax.feature.settings.presentation.navigation.SettingsRoute
import com.stax.feature.settings.presentation.navigation.settingsEntries
import com.stax.feature.sites.presentation.navigation.SitesRoute
import com.stax.feature.sites.presentation.navigation.sitesEntries

/**
 * The five top-level destinations of the bottom nav / side rail (§4.0, §6.1), each with its
 * Nav3 root [route] and its Material Symbols Rounded icon (outlined + `Filled` selected variant,
 * via [StaxIcons]).
 */
enum class TopLevelDestination(val route: NavKey, @StringRes val labelRes: Int) {
    Home(DashboardRoute, R.string.nav_home),
    Compounds(CompoundsRoute, R.string.nav_compounds),
    Protocols(ProtocolsRoute, R.string.nav_protocols),
    Sites(SitesRoute, R.string.nav_sites),
    Settings(SettingsRoute, R.string.nav_settings),
}

/**
 * Top-level adaptive navigation chrome (§6.4.1). `NavigationSuiteScaffold` swaps across window-size
 * breakpoints — bottom `NavigationBar` at compact (<600dp), `NavigationRail` at medium (600dp+),
 * wide rail at expanded (840dp+) — and wraps the app's single `NavDisplay`. A
 * [rememberNavigationSuiteScaffoldState] is held so screen scroll behaviour can hide/show the chrome
 * later (§6.4.9).
 *
 * Selection follows the active stack's root. Re-tapping a destination resets to its root. A single
 * shared back stack is used for now; per-destination back stacks land in M5-03.
 */
@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Suppress("FunctionName")
@Composable
fun MainScaffold(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(TopLevelDestination.Home.route)
    val navSuiteState = rememberNavigationSuiteScaffoldState()

    val currentRoot = backStack.firstOrNull()
    val selected = TopLevelDestination.entries.firstOrNull { it.route == currentRoot }
        ?: TopLevelDestination.Home

    // Bottom NavigationBar < 600dp · NavigationRail 600dp+ · wide rail expands at 840dp+ (§6.4.1).
    // The 1.5 default keeps a bottom bar through Medium, so the type is chosen explicitly here.
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val navSuiteType = when {
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
            NavigationSuiteType.WideNavigationRailExpanded
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            NavigationSuiteType.NavigationRail
        else -> NavigationSuiteType.NavigationBar
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val isSelected = destination == selected
                item(
                    selected = isSelected,
                    onClick = { backStack.switchTopLevel(destination) },
                    icon = {
                        Icon(
                            painter = destination.painter(isSelected),
                            contentDescription = null,
                        )
                    },
                    label = { Text(text = stringResource(destination.labelRes)) },
                )
            }
        },
        modifier = modifier,
        layoutType = navSuiteType,
        state = navSuiteState,
    ) {
        StaxNavDisplay(backStack = backStack, modifier = Modifier.fillMaxSize())
    }
}

/** Outlined when unselected, `Filled` when selected (§4.0). */
@Composable
private fun TopLevelDestination.painter(selected: Boolean): Painter = when (this) {
    TopLevelDestination.Home -> if (selected) StaxIcons.Filled.Home else StaxIcons.Home
    TopLevelDestination.Compounds -> if (selected) StaxIcons.Filled.Medication else StaxIcons.Medication
    TopLevelDestination.Protocols -> if (selected) StaxIcons.Filled.CalendarMonth else StaxIcons.CalendarMonth
    TopLevelDestination.Sites -> if (selected) StaxIcons.Filled.PersonPinCircle else StaxIcons.PersonPinCircle
    TopLevelDestination.Settings -> if (selected) StaxIcons.Filled.Settings else StaxIcons.Settings
}

/**
 * Switches the shared back stack to [destination]'s root, clearing any pushed detail (single-stack
 * model; per-destination retention lands in M5-03). Re-tapping the active destination pops to root.
 */
private fun NavBackStack<NavKey>.switchTopLevel(destination: TopLevelDestination) {
    clear()
    add(destination.route)
}

/**
 * The app's single Navigation 3 host. The [NavBackStack] is saveable (survives configuration
 * changes and process death) and route arguments reach each screen through the typed `NavKey`
 * passed to its entry. The `entryProvider` is assembled from each feature's `<feature>Entries`
 * extension; all cross-feature navigation is wired here as lambda callbacks so feature modules
 * never reference one another (spec §10.3).
 */
@Suppress("FunctionName")
@Composable
private fun StaxNavDisplay(backStack: NavBackStack<NavKey>, modifier: Modifier = Modifier) {
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
