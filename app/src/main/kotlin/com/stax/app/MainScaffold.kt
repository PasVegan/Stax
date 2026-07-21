package com.stax.app

import androidx.annotation.StringRes
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
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
import com.stax.core.design.system.StaxListDetailScene
import com.stax.core.design.system.StaxMotion
import com.stax.core.design.system.StaxSupportingPaneScene
import com.stax.feature.compounds.presentation.navigation.CompoundDetailRoute
import com.stax.feature.compounds.presentation.navigation.CompoundsRoute
import com.stax.feature.compounds.presentation.navigation.CreateCompoundRoute
import com.stax.feature.compounds.presentation.navigation.EditCompoundRoute
import com.stax.feature.compounds.presentation.navigation.compoundsEntries
import com.stax.feature.dashboard.presentation.navigation.DashboardRoute
import com.stax.feature.dashboard.presentation.navigation.DashboardSupportingRoute
import com.stax.feature.dashboard.presentation.navigation.dashboardEntries
import com.stax.feature.logging.presentation.navigation.loggingEntries
import com.stax.feature.onboarding.presentation.completion.rememberOnboardingCompletion
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
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("FunctionName")
@Composable
private fun StaxNavDisplay(navState: MainNavigationState, modifier: Modifier = Modifier) {
    // List-detail Scene (§6.4.2): Compounds / Protocols list entries pair with their detail entries.
    val listDetailSceneStrategy = StaxListDetailScene.rememberSceneStrategy<NavKey>()
    // Supporting-pane Scene (§6.4.2): Dashboard main pane + supporting pane.
    val supportingPaneSceneStrategy = StaxSupportingPaneScene.rememberSceneStrategy<NavKey>()

    // Onboarding ends on a screen it does not own (§4.14 step 3 reuses Create Protocol), so the
    // completion write is hoisted out of that feature and handed back to onboarding from here.
    val completeOnboarding = rememberOnboardingCompletion()

    val entryProvider = entryProvider {
        dashboardEntries(
            onCompoundClick = { compoundId -> navState.push(CompoundDetailRoute(compoundId)) },
            onShowSupporting = { navState.push(DashboardSupportingRoute) },
        )
        compoundsEntries(
            onCompoundClick = { compoundId -> navState.showDetail(CompoundDetailRoute(compoundId)) },
            onCreateCompound = { navState.push(CreateCompoundRoute()) },
            onEditCompound = { compoundId -> navState.push(EditCompoundRoute(compoundId)) },
            onReconstitute = { compoundId -> navState.push(ReconstitutionRoute(compoundId)) },
            onBack = { navState.goBack() },
            // §4.14 step 2: skipping advances to step 3 — the Create Protocol form, flagged the same
            // way — and saves nothing, because the form persists nothing before its own Save (M7-04).
            onSkipOnboardingStep = { navState.push(CreateProtocolRoute(onboarding = true)) },
        )
        protocolsEntries(
            onProtocolClick = { protocolId -> navState.showDetail(ProtocolDetailRoute(protocolId)) },
            onCreateProtocol = { navState.push(CreateProtocolRoute()) },
            onBack = { navState.goBack() },
            // §4.14 step 3 is the last step, so finishing or skipping it ends onboarding: persist
            // the completion (onboarding's own business, hence the callback) and drop the whole
            // stepper to land on Dashboard.
            onFinishOnboarding = {
                completeOnboarding()
                navState.goToStartRoot()
            },
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
            // Step 2 reuses the Create Compound form (§4.14 step 2). The reuse is wired here
            // because features never depend on features: onboarding names the intent, `:app`
            // names the destination (§10.3).
            onContinue = { navState.push(CreateCompoundRoute(onboarding = true)) },
            onSkip = { navState.goBack() },
        )
    }

    NavDisplay(
        entries = navState.toDecoratedEntries(entryProvider),
        modifier = modifier,
        sceneStrategies = listOf(listDetailSceneStrategy, supportingPaneSceneStrategy),
        transitionSpec = { STAX_FORWARD_TRANSITION },
        popTransitionSpec = { STAX_BACK_TRANSITION },
        // Predictive back: NavDisplay seeks this peek by the system back-gesture progress, and the
        // active Scene strategy resolves the detail → list transition (§6.4.5).
        predictivePopTransitionSpec = { STAX_BACK_TRANSITION },
        onBack = { navState.goBack() },
    )
}

/** Outgoing scene scales down toward this fraction during a back / predictive-pop peek (§6.4.5). */
private const val PREDICTIVE_PEEK_SCALE = 0.92f

/** Incoming (previous) scene sits behind the outgoing one during a back peek so it is revealed. */
private const val BACK_TARGET_Z_INDEX = -1f

/** Incoming scene sits on top during a forward push. */
private const val FORWARD_TARGET_Z_INDEX = 1f

/**
 * Back / predictive-pop peek (§6.4.5, §5.9): the outgoing scene scales down + fades on top, revealing
 * the incoming (previous) scene behind it. Driven by [StaxMotion] specs — no inline `tween`.
 */
private val STAX_BACK_TRANSITION: ContentTransform = ContentTransform(
    targetContentEnter = fadeIn(StaxMotion.defaultEffectsSpec()),
    initialContentExit = scaleOut(StaxMotion.defaultSpatialSpec(), targetScale = PREDICTIVE_PEEK_SCALE) +
        fadeOut(StaxMotion.defaultEffectsSpec()),
    targetContentZIndex = BACK_TARGET_Z_INDEX,
)

/** Forward push: the incoming scene scales up from the peek scale + fades in on top (§5.9). */
private val STAX_FORWARD_TRANSITION: ContentTransform = ContentTransform(
    targetContentEnter = scaleIn(StaxMotion.defaultSpatialSpec(), initialScale = PREDICTIVE_PEEK_SCALE) +
        fadeIn(StaxMotion.defaultEffectsSpec()),
    initialContentExit = fadeOut(StaxMotion.defaultEffectsSpec()),
    targetContentZIndex = FORWARD_TARGET_Z_INDEX,
)
