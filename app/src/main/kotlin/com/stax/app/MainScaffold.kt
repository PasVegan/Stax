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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
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
 * breakpoints — short bottom bar at compact (<600dp), collapsed wide rail at medium (600dp+), and
 * an expanded wide rail at expanded (840dp+) — and wraps the app's single `NavDisplay`. A
 * [rememberNavigationSuiteScaffoldState] is held so screen scroll behaviour can hide/show the chrome
 * later (§6.4.9).
 *
 * Each destination owns its own saveable back stack ([MainNavigationState], §6.2 / §6.4.5):
 * switching destinations preserves each section's history, and re-tapping the active item pops it
 * back to its root.
 */
@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Suppress("FunctionName")
@Composable
fun MainScaffold(modifier: Modifier = Modifier) {
    val topLevelRoutes = remember { TopLevelDestination.entries.map { it.route }.toSet() }
    val navState = rememberMainNavigationState(
        startRoute = TopLevelDestination.Home.route,
        topLevelRoutes = topLevelRoutes,
    )
    val navSuiteState = rememberNavigationSuiteScaffoldState()

    val selected = TopLevelDestination.entries.firstOrNull { it.route == navState.topLevelRoute }
        ?: TopLevelDestination.Home

    // M3 Expressive nav types, width-driven (§6.4.1): short bottom bar < 600dp · collapsed wide rail
    // 600dp+ · expanded wide rail 840dp+. The 1.5 default never width-expands the rail, so the
    // expanded breakpoint is selected explicitly here.
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val navSuiteType = when {
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
            NavigationSuiteType.WideNavigationRailExpanded
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            NavigationSuiteType.WideNavigationRailCollapsed
        else -> NavigationSuiteType.ShortNavigationBarCompact
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val isSelected = destination == selected
                item(
                    selected = isSelected,
                    onClick = { navState.onTopLevelSelected(destination.route) },
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
        StaxNavDisplay(navState = navState, modifier = Modifier.fillMaxSize())
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
 * The app's single Navigation 3 host. The active destination's back stack — and every other
 * destination's — is saveable (survives configuration changes and process death), and route
 * arguments reach each screen through the typed `NavKey` passed to its entry. The `entryProvider` is
 * assembled from each feature's `<feature>Entries` extension; stacked screens push onto the active
 * destination's stack and all cross-feature navigation is wired here as lambda callbacks so feature
 * modules never reference one another (spec §10.3).
 */
@Suppress("FunctionName")
@Composable
private fun StaxNavDisplay(navState: MainNavigationState, modifier: Modifier = Modifier) {
    val entryProvider = entryProvider {
        dashboardEntries(
            onCompoundClick = { compoundId -> navState.push(CompoundDetailRoute(compoundId)) },
        )
        compoundsEntries(
            onCompoundClick = { compoundId -> navState.push(CompoundDetailRoute(compoundId)) },
            onCreateCompound = { navState.push(CreateCompoundRoute) },
            onEditCompound = { compoundId -> navState.push(EditCompoundRoute(compoundId)) },
            onReconstitute = { compoundId -> navState.push(ReconstitutionRoute(compoundId)) },
            onBack = { navState.goBack() },
        )
        protocolsEntries(
            onProtocolClick = { protocolId -> navState.push(ProtocolDetailRoute(protocolId)) },
            onCreateProtocol = { navState.push(CreateProtocolRoute) },
            onBack = { navState.goBack() },
        )
        sitesEntries()
        settingsEntries()
        reconstitutionEntries(
            onBack = { navState.goBack() },
        )
        loggingEntries(
            onBack = { navState.goBack() },
        )
        onboardingEntries(
            onOnboardingComplete = { navState.goBack() },
        )
    }

    NavDisplay(
        entries = navState.toDecoratedEntries(entryProvider),
        modifier = modifier,
        onBack = { navState.goBack() },
    )
}
