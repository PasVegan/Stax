package com.stax.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
import com.stax.feature.onboarding.presentation.navigation.NotificationGateRoute
import com.stax.feature.onboarding.presentation.navigation.OnboardingRoute
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
 *
 * [onboardingCompleted] is `Settings.onboardingCompleted` (§4.14): when it is `false`, the flow is
 * seeded on top of Home's stack so first launch opens on onboarding.
 */
@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Suppress("FunctionName")
@Composable
fun MainScaffold(onboardingCompleted: Boolean, modifier: Modifier = Modifier) {
    val topLevelRoutes = remember { TopLevelDestination.entries.map { it.route }.toSet() }
    val navState = rememberMainNavigationState(
        startRoute = TopLevelDestination.Home.route,
        topLevelRoutes = topLevelRoutes,
        // Onboarding is a flow, not a top-level destination, so first run stacks it on Home rather
        // than changing the start route (§4.14). The caller holds all content back until the flag is
        // known, so Dashboard never renders behind it.
        initialStackedRoute = OnboardingRoute.takeUnless { onboardingCompleted },
    )

    // The first-run flow has to be answered before the app is usable, so its screens hide the chrome:
    // a visible nav item is a one-tap exit out of a flow the user has neither finished nor skipped
    // (§4.14, §4.15). Seeded as the initial value too, so first launch never flashes the bar in.
    val chromeHidden = navState.currentRoute.isFirstRunFlow()
    val navSuiteState = rememberNavigationSuiteScaffoldState(
        initialValue = if (chromeHidden) NavigationSuiteScaffoldValue.Hidden else NavigationSuiteScaffoldValue.Visible,
    )
    LaunchedEffect(chromeHidden) {
        if (chromeHidden) navSuiteState.hide() else navSuiteState.show()
    }

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
                    label = {
                        // Five items on a very narrow window (a folded cover screen is ~330dp) leave
                        // "Compounds" too little room, and the item's label slot puts no limit on the
                        // text: it wraps to a second line and breaks the bar's rhythm. Pin it to one
                        // line and let the label shrink to fit instead, down to a floor below which it
                        // ellipsizes. Only the labels that need it shrink; the rest keep the token size.
                        Text(
                            text = stringResource(destination.labelRes),
                            autoSize = rememberNavLabelAutoSize(),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                        )
                    },
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

/**
 * The screens that make up the first-run flow (§4.14, §4.15): Welcome, the Create Compound / Create
 * Protocol forms reused as steps 2 and 3, and the notification gate. Both forms are ordinary stacked
 * screens when they are not flagged as onboarding, and keep the chrome then.
 */
private fun NavKey.isFirstRunFlow(): Boolean = when (this) {
    OnboardingRoute, NotificationGateRoute -> true
    is CreateCompoundRoute -> onboarding
    is CreateProtocolRoute -> onboarding
    else -> false
}

/**
 * Whether §4.15's gate has anything left to ask for. Its trigger is "app launch AND
 * `POST_NOTIFICATIONS` not granted", so an already-granted permission skips the screen outright —
 * showing it would offer an Allow button for a permission the user has already given.
 */
private fun Context.hasNotificationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

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

    // Finishing or skipping onboarding persists completion and ends the flow: the whole stepper leaves
    // the stack first (§4.14), because pushing the gate on top of it would leave a back gesture walking
    // right back into the forms the user just finished — with onboarding already marked complete. What
    // is left is the notification gate sitting on Dashboard's root (§4.15) — the last thing before
    // Dashboard, backing out to it — and nothing at all when the permission is already granted.
    val context = LocalContext.current
    val finishOnboarding = {
        completeOnboarding()
        navState.goToStartRoot()
        if (!context.hasNotificationPermission()) navState.push(NotificationGateRoute)
    }

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
            // the completion (onboarding's own business, hence the callback) and hand off to the
            // notification gate (§4.15) before Dashboard.
            onFinishOnboarding = finishOnboarding,
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
            // Skip on step 1 is skip-anywhere: it ends onboarding just like finishing step 3, so it
            // persists completion and hands off to the notification gate — not a plain back-pop, or
            // the flag stays false and onboarding returns on the next launch (§4.14).
            onSkip = finishOnboarding,
            // By now the gate is all that is left of the flow, so answering it lands on Dashboard (§4.15).
            onNotificationGateProceed = { navState.goToStartRoot() },
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

/**
 * Shrink-to-fit for a nav item's label, ceilinged at whatever size the item already provides — each
 * nav suite type styles its label slot with its own token, so reading [LocalTextStyle] keeps that
 * size as the maximum and only ever scales down from it.
 *
 * `StepBased` requires `min < max` and would throw otherwise, so a token at or below the floor
 * returns `null`: the label then simply ellipsizes at one line. Remembered because `TextAutoSize`
 * documents itself as identity-sensitive on the text layout path.
 */
@Composable
private fun rememberNavLabelAutoSize(): TextAutoSize? {
    val labelFontSize = LocalTextStyle.current.fontSize
    return remember(labelFontSize) {
        labelFontSize
            .takeIf { it.isSp && it > NAV_LABEL_MIN_FONT_SIZE }
            ?.let { TextAutoSize.StepBased(minFontSize = NAV_LABEL_MIN_FONT_SIZE, maxFontSize = it) }
    }
}

/**
 * Floor for the auto-sized nav labels (§4.0). It is an `sp` value, so it still tracks the user's font
 * scale; below it the label ellipsizes rather than shrinking further.
 */
private val NAV_LABEL_MIN_FONT_SIZE = 9.sp

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
